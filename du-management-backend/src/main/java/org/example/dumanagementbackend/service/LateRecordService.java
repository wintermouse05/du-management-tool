package org.example.dumanagementbackend.service;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.example.dumanagementbackend.dto.late.LateRecordRequest;
import org.example.dumanagementbackend.dto.late.LateRecordResponse;
import org.example.dumanagementbackend.dto.late.LateSummaryResponse;
import org.example.dumanagementbackend.entity.LateRecord;
import org.example.dumanagementbackend.entity.PointHistory;
import org.example.dumanagementbackend.entity.PointRule;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.LateRecordStatus;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.LateRecordRepository;
import org.example.dumanagementbackend.repository.PointHistoryRepository;
import org.example.dumanagementbackend.repository.PointRuleRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LateRecordService {

    private static final Logger log = LoggerFactory.getLogger(LateRecordService.class);
    private static final int FINE_PER_REPEAT_LATE = 50_000;
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Pattern REPORT_DATE_PATTERN = Pattern.compile("(\\d{4}/\\d{2}/\\d{2})");
    private static final Pattern TABLE_PATTERN = Pattern.compile("\\|\\s*([^|\\n]+?)\\s*\\|\\s*([^|\\n]+?)\\s*\\|");
    private static final Pattern TIME_PATTERN = Pattern.compile("\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b");
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("^-+$");
    private static final DateTimeFormatter REPORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final LateRecordRepository lateRecordRepository;
    private final UserRepository userRepository;
    private final PointRuleRepository pointRuleRepository;
    private final PointHistoryRepository pointHistoryRepository;

    @Autowired(required = false)
    private ChatopsService chatopsService;

    @Transactional
    @CacheEvict(cacheNames = "lateMonthlySummary", allEntries = true)
    public LateRecordResponse create(LateRecordRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id=" + request.userId()));

        LateRecord record = new LateRecord();
        record.setUser(user);
        record.setRecordDate(request.recordDate());
        record.setMinutesLate(request.minutesLate());
        record.setReason(request.reason());
        record.setStatus(LateRecordStatus.FIRST_TIME);
        record.setFineAmount(0);
        LateRecord saved = lateRecordRepository.save(record);

        normalizeUserMonthStatuses(user.getId(), YearMonth.from(saved.getRecordDate()));
        LateRecord normalized = lateRecordRepository.findById(saved.getId()).orElse(saved);

        pointRuleRepository.findByActionCode("LATE_PENALTY").ifPresent(rule -> applyLatePenalty(user, rule, normalized));
        return toResponse(normalized);
    }

    public Page<LateRecordResponse> getAll(Pageable pageable) {
        Pageable resolvedPageable = PaginationUtils.toZeroBasedPageable(pageable);
        return lateRecordRepository.findAll(resolvedPageable).map(this::toResponse);
    }

    public Page<LateRecordResponse> getByUser(Long userId, Pageable pageable) {
        Pageable resolvedPageable = PaginationUtils.toZeroBasedPageable(pageable);
        return lateRecordRepository.findByUserId(userId, resolvedPageable).map(this::toResponse);
    }

    public Page<LateRecordResponse> getByDateRange(LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Pageable resolvedPageable = PaginationUtils.toZeroBasedPageable(pageable);
        if (fromDate != null && toDate != null) {
            return lateRecordRepository.findByRecordDateBetween(fromDate, toDate, resolvedPageable).map(this::toResponse);
        }
        return getAll(resolvedPageable);
    }

    @Cacheable(
            cacheNames = "lateMonthlySummary",
            key = "{#year,#month,#pageable.pageNumber,#pageable.pageSize,#pageable.sort.toString()}"
    )
    public Page<LateSummaryResponse> getMonthlySummary(int year, int month, Pageable pageable) {
        Pageable resolvedPageable = PaginationUtils.toZeroBasedPageable(pageable);
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        List<LateRecord> records = lateRecordRepository.findByRecordDateBetween(start, end);
        Map<User, List<LateRecord>> grouped = records.stream().collect(Collectors.groupingBy(LateRecord::getUser));

        List<LateSummaryResponse> summaries = grouped.entrySet().stream()
                .map(entry -> new LateSummaryResponse(
                        entry.getKey().getId(),
                        entry.getKey().getFullName(),
                        entry.getValue().size(),
                        entry.getValue().stream().mapToLong(LateRecord::getMinutesLate).sum()
                ))
                .sorted(Comparator.comparingLong(LateSummaryResponse::totalLateTimes).reversed())
                .toList();

        int pageStart = (int) resolvedPageable.getOffset();
        if (pageStart >= summaries.size()) {
            return new PageImpl<>(List.of(), resolvedPageable, summaries.size());
        }
        int pageEnd = Math.min(pageStart + resolvedPageable.getPageSize(), summaries.size());
        return new PageImpl<>(summaries.subList(pageStart, pageEnd), resolvedPageable, summaries.size());
    }

    public byte[] exportCsv(Integer year, Integer month) {
        List<LateRecord> records;
        if (year != null && month != null) {
            YearMonth yearMonth = YearMonth.of(year, month);
            records = lateRecordRepository.findByRecordDateBetween(yearMonth.atDay(1), yearMonth.atEndOfMonth());
        } else {
            records = lateRecordRepository.findAll();
        }

        List<LateRecord> sorted = records.stream()
                .sorted(Comparator.comparing(LateRecord::getRecordDate).reversed())
                .toList();

        StringBuilder csv = new StringBuilder();
        csv.append("id,userId,fullName,recordDate,minutesLate,reason,status,fineAmount\n");
        for (LateRecord record : sorted) {
            csv.append(record.getId()).append(',')
                    .append(record.getUser().getId()).append(',')
                    .append(csvEscape(record.getUser().getFullName())).append(',')
                    .append(record.getRecordDate()).append(',')
                    .append(record.getMinutesLate()).append(',')
                    .append(csvEscape(record.getReason())).append(',')
                    .append(record.getStatus() != null ? record.getStatus() : "").append(',')
                    .append(record.getFineAmount() != null ? record.getFineAmount() : 0)
                    .append('\n');
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    @CacheEvict(cacheNames = "lateMonthlySummary", allEntries = true)
    public void deleteLateRecord(Long id) {
        LateRecord existing = lateRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Late record not found with id=" + id));
        Long userId = existing.getUser().getId();
        YearMonth month = YearMonth.from(existing.getRecordDate());
        lateRecordRepository.delete(existing);
        normalizeUserMonthStatuses(userId, month);
    }

    @Transactional
    @CacheEvict(cacheNames = "lateMonthlySummary", allEntries = true)
    public LateRecordResponse updateStatus(Long id, LateRecordStatus status) {
        ensureAdminCanManageStatus();
        if (status == null) {
            throw new BadRequestException("status is required");
        }
        if (status == LateRecordStatus.FIRST_TIME) {
            throw new BadRequestException("FIRST_TIME is auto-calculated and cannot be set manually.");
        }

        LateRecord record = lateRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Late record not found with id=" + id));

        if ((status == LateRecordStatus.PAID || status == LateRecordStatus.UNPAID)
                && !isChargeableAfterStatusChange(record, status)) {
            throw new BadRequestException("Only late records from the second time in month can be marked paid/unpaid.");
        }

        record.setStatus(status);
        if (status == LateRecordStatus.IGNORE) {
            record.setFineAmount(0);
        } else {
            record.setFineAmount(FINE_PER_REPEAT_LATE);
        }
        lateRecordRepository.save(record);

        normalizeUserMonthStatuses(record.getUser().getId(), YearMonth.from(record.getRecordDate()));
        LateRecord updated = lateRecordRepository.findById(id).orElse(record);
        return toResponse(updated);
    }

    // ---- ChatOps-powered automated late detection ----

    @Transactional
    public int checkNow(String channelId) {
        if (chatopsService == null) {
            throw new IllegalStateException("ChatOps is not enabled. Set chatops.enabled=true and configure chatops properties.");
        }
        String targetChannel = (channelId != null && !channelId.isBlank()) ? channelId : chatopsService.getChannelId();
        return fetchLateCheckinsFromChat(targetChannel, LocalTime.of(10, 0));
    }

    @Transactional
    public int fetchLateCheckinsFromChat(String channelId, LocalTime since) {
        if (chatopsService == null) return 0;

        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        String todayString = now.format(REPORT_DATE_FORMATTER);
        long timestamp = now.toLocalDate().atTime(since).atZone(VIETNAM_ZONE).toEpochSecond() * 1000;

        Map<String, Object> response;
        try {
            response = chatopsService.getChannelPosts(channelId, timestamp);
        } catch (Exception e) {
            log.error("Failed to fetch channel posts from chat server: {}", e.getMessage());
            return 0;
        }

        if (response == null || !response.containsKey("posts")) {
            log.info("No posts found in channel {}", channelId);
            return 0;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> posts = (Map<String, Object>) response.get("posts");
        if (posts == null || posts.isEmpty()) {
            log.info("Empty posts response from channel {}", channelId);
            return 0;
        }

        List<String> matchedMessages = posts.values().stream()
                .map(post -> (Map<String, Object>) post)
                .map(post -> (String) post.get("message"))
                .filter(message -> message != null && message.contains(todayString) && message.contains("|"))
                .toList();

        if (matchedMessages.isEmpty()) {
            log.info("No late notification message found for date {}", todayString);
            return 0;
        }

        int totalSaved = 0;
        for (String message : matchedMessages) {
            totalSaved += saveLateRecordsFromChat(message);
        }
        return totalSaved;
    }

    List<LateRecord> parseLateRecords(String rawMessage) {
        List<LateRecord> lateRecords = new ArrayList<>();

        Matcher dateMatcher = REPORT_DATE_PATTERN.matcher(rawMessage);
        LocalDate reportDate = dateMatcher.find()
                ? LocalDate.parse(dateMatcher.group(1), REPORT_DATE_FORMATTER)
                : LocalDate.now(VIETNAM_ZONE);

        Matcher tableMatcher = TABLE_PATTERN.matcher(rawMessage);
        while (tableMatcher.find()) {
            String name = normalizeWhitespace(tableMatcher.group(1));
            String checkinAt = normalizeWhitespace(tableMatcher.group(2));

            if (isHeaderRow(name, checkinAt) || isSeparatorRow(name, checkinAt) || isExcludedCheckinValue(checkinAt)) {
                continue;
            }

            Optional<User> userOpt = findUserByFullName(name);
            if (userOpt.isEmpty()) {
                log.warn("User not found with fullName: {}", name);
                continue;
            }

            LocalTime checkinTime = parseTime(checkinAt);
            boolean missingCheckin = isMissingCheckinValue(checkinAt);
            int minutesLate = calculateMinutesLate(checkinTime);

            LateRecord late = new LateRecord();
            late.setUser(userOpt.get());
            late.setRecordDate(reportDate);
            late.setMinutesLate(minutesLate);
            late.setReason(missingCheckin
                    ? "Did not check in"
                    : "Auto-detected late check-in at " + (checkinTime != null ? checkinTime : checkinAt));
            late.setStatus(LateRecordStatus.FIRST_TIME);
            late.setFineAmount(0);
            lateRecords.add(late);
        }

        return lateRecords;
    }

    @Transactional
    int saveLateRecordsFromChat(String rawMessage) {
        List<LateRecord> lateData = parseLateRecords(rawMessage);
        if (lateData.isEmpty()) {
            log.info("No late records parsed from message");
            return 0;
        }

        LocalDate date = lateData.get(0).getRecordDate();
        lateRecordRepository.deleteByRecordDate(date);
        lateRecordRepository.flush();
        List<LateRecord> savedRecords = lateRecordRepository.saveAll(lateData);

        Set<Long> userIds = savedRecords.stream()
                .map(record -> record.getUser().getId())
                .collect(Collectors.toCollection(HashSet::new));
        YearMonth month = YearMonth.from(date);
        userIds.forEach(userId -> normalizeUserMonthStatuses(userId, month));

        processRepeatOffenders(date);
        log.info("Saved {} late records for date {}", lateData.size(), date);
        return lateData.size();
    }

    void processRepeatOffenders(LocalDate targetDate) {
        var monthStart = targetDate.withDayOfMonth(1);
        var monthEnd = targetDate.withDayOfMonth(targetDate.lengthOfMonth());

        var dateRecords = lateRecordRepository.findByRecordDate(targetDate);
        var processedUserIds = new HashSet<Long>();

        for (LateRecord record : dateRecords) {
            Long userId = record.getUser().getId();
            if (!processedUserIds.add(userId)) continue;

            var userMonthRecords = lateRecordRepository.findByUser_IdAndRecordDateBetween(userId, monthStart, monthEnd);
            long countedTimes = userMonthRecords.stream()
                    .filter(monthRecord -> monthRecord.getStatus() != LateRecordStatus.IGNORE)
                    .count();
            if (countedTimes >= 2) {
                pointRuleRepository.findByActionCode("LATE_PENALTY")
                        .ifPresent(rule -> {
                            applyLatePenalty(record.getUser(), rule, record);
                            log.info("Applied late penalty to user {} for repeat offenses ({} times this month)",
                                    record.getUser().getFullName(), countedTimes);
                        });
            }
        }
    }

    private boolean isHeaderRow(String name, String checkinAt) {
        String normalizedName = normalizeForComparison(name);
        String normalizedCheckin = normalizeForComparison(checkinAt);
        return normalizedName.equals("name")
                || normalizedCheckin.equals("checkin at")
                || normalizedName.equals("employee")
                || normalizedName.equals("member");
    }

    private boolean isSeparatorRow(String name, String checkinAt) {
        String normalizedName = normalizeWhitespace(name).replace(" ", "");
        String normalizedCheckin = normalizeWhitespace(checkinAt).replace(" ", "");
        return SEPARATOR_PATTERN.matcher(normalizedName).matches()
                && SEPARATOR_PATTERN.matcher(normalizedCheckin).matches();
    }

    private boolean isExcludedCheckinValue(String checkinAt) {
        String normalized = normalizeForComparison(checkinAt);
        return normalized.contains("nghi")
                || normalized.contains("off")
                || normalized.contains("np")
                || normalized.contains("xin phep");
    }

    private boolean isMissingCheckinValue(String checkinAt) {
        String normalized = normalizeWhitespace(checkinAt);
        return normalized.isBlank() || SEPARATOR_PATTERN.matcher(normalized).matches();
    }

    private LocalTime parseTime(String time) {
        if (time == null || time.isEmpty()) return null;
        String trimmed = time.trim();
        if (SEPARATOR_PATTERN.matcher(trimmed).matches()) return null;
        Matcher matcher = TIME_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            trimmed = matcher.group();
        }
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("H:mm"),
                DateTimeFormatter.ofPattern("HH:mm"),
                DateTimeFormatter.ofPattern("H:mm:ss"),
                DateTimeFormatter.ofPattern("HH:mm:ss")
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalTime.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // try next formatter
            }
        }
        log.warn("Cannot parse time: {}", time);
        return null;
    }

    private Optional<User> findUserByFullName(String fullName) {
        return userRepository.findByFullName(fullName)
                .or(() -> userRepository.findByFullNameIgnoreCase(fullName));
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeForComparison(String value) {
        String normalized = normalizeWhitespace(value)
                .replace('\u0110', 'D')
                .replace('\u0111', 'd');
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private int calculateMinutesLate(LocalTime checkinTime) {
        if (checkinTime == null) return 0;
        LocalTime threshold = LocalTime.of(8, 0);
        if (checkinTime.isAfter(threshold)) {
            return (int) Duration.between(threshold, checkinTime).toMinutes();
        }
        return 0;
    }

    private boolean isChargeableAfterStatusChange(LateRecord targetRecord, LateRecordStatus nextStatus) {
        LocalDate recordDate = targetRecord.getRecordDate();
        YearMonth month = YearMonth.from(recordDate);
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        List<LateRecord> monthRecords = new ArrayList<>(lateRecordRepository.findByUser_IdAndRecordDateBetween(
                targetRecord.getUser().getId(), monthStart, monthEnd));

        monthRecords.sort(Comparator
                .comparing(LateRecord::getRecordDate)
                .thenComparing(LateRecord::getId));

        int counted = 0;
        for (LateRecord record : monthRecords) {
            LateRecordStatus effectiveStatus = record.getId().equals(targetRecord.getId())
                    ? nextStatus
                    : normalizeNullStatus(record.getStatus());
            if (effectiveStatus == LateRecordStatus.IGNORE) {
                continue;
            }
            counted++;
            if (record.getId().equals(targetRecord.getId())) {
                return counted >= 2;
            }
        }
        return false;
    }

    @Transactional
    void normalizeUserMonthStatuses(Long userId, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        List<LateRecord> records = new ArrayList<>(lateRecordRepository.findByUser_IdAndRecordDateBetween(userId, monthStart, monthEnd));
        if (records.isEmpty()) {
            return;
        }

        records.sort(Comparator
                .comparing(LateRecord::getRecordDate)
                .thenComparing(LateRecord::getId));

        int counted = 0;
        List<LateRecord> changedRecords = new ArrayList<>();
        for (LateRecord record : records) {
            LateRecordStatus status = normalizeNullStatus(record.getStatus());
            if (status == LateRecordStatus.IGNORE) {
                if (!Integer.valueOf(0).equals(record.getFineAmount())) {
                    record.setFineAmount(0);
                    changedRecords.add(record);
                }
                continue;
            }

            counted++;
            if (counted == 1) {
                if (status != LateRecordStatus.FIRST_TIME || !Integer.valueOf(0).equals(record.getFineAmount())) {
                    record.setStatus(LateRecordStatus.FIRST_TIME);
                    record.setFineAmount(0);
                    changedRecords.add(record);
                }
                continue;
            }

            LateRecordStatus nextStatus = (status == LateRecordStatus.PAID)
                    ? LateRecordStatus.PAID
                    : LateRecordStatus.UNPAID;
            if (status != nextStatus || !Integer.valueOf(FINE_PER_REPEAT_LATE).equals(record.getFineAmount())) {
                record.setStatus(nextStatus);
                record.setFineAmount(FINE_PER_REPEAT_LATE);
                changedRecords.add(record);
            }
        }

        if (!changedRecords.isEmpty()) {
            lateRecordRepository.saveAll(changedRecords);
        }
    }

    private LateRecordStatus normalizeNullStatus(LateRecordStatus status) {
        return status != null ? status : LateRecordStatus.FIRST_TIME;
    }

    private boolean isCurrentUserAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(authority ->
                "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private void ensureAdminCanManageStatus() {
        if (!isCurrentUserAdmin()) {
            throw new AccessDeniedException("Only admins can update late status.");
        }
    }

    // ---- private helpers ----

    private void applyLatePenalty(User user, PointRule rule, LateRecord lateRecord) {
        PointHistory history = new PointHistory();
        history.setUser(user);
        history.setRule(rule);
        history.setPointsChanged(rule.getPointValue());
        history.setReason("Late penalty - " + lateRecord.getRecordDate() + " (" + lateRecord.getMinutesLate() + " mins)");
        pointHistoryRepository.save(history);
        userRepository.incrementTotalPoints(user.getId(), rule.getPointValue());
    }

    private LateRecordResponse toResponse(LateRecord record) {
        LateRecordStatus status = normalizeNullStatus(record.getStatus());
        return new LateRecordResponse(
                record.getId(),
                record.getUser().getId(),
                record.getUser().getFullName(),
                record.getRecordDate(),
                record.getMinutesLate(),
                record.getReason(),
                status,
                record.getFineAmount() != null ? record.getFineAmount() : 0,
                status == LateRecordStatus.UNPAID || status == LateRecordStatus.PAID
        );
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
