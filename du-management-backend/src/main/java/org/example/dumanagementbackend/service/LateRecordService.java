package org.example.dumanagementbackend.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LateRecordService {

    private static final Logger log = LoggerFactory.getLogger(LateRecordService.class);

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
        LateRecord saved = lateRecordRepository.save(record);

        pointRuleRepository.findByActionCode("LATE_PENALTY").ifPresent(rule -> applyLatePenalty(user, rule, saved));

        return toResponse(saved);
    }

    public Page<LateRecordResponse> getAll(Pageable pageable) {
        return lateRecordRepository.findAll(pageable).map(this::toResponse);
    }

    public Page<LateRecordResponse> getByUser(Long userId, Pageable pageable) {
        return lateRecordRepository.findByUserId(userId, pageable).map(this::toResponse);
    }

    public Page<LateRecordResponse> getByDateRange(LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        if (fromDate != null && toDate != null) {
            return lateRecordRepository.findByRecordDateBetween(fromDate, toDate, pageable).map(this::toResponse);
        }
        return getAll(pageable);
    }

    @Cacheable(
            cacheNames = "lateMonthlySummary",
            key = "{#year,#month,#pageable.pageNumber,#pageable.pageSize,#pageable.sort.toString()}"
    )
    public Page<LateSummaryResponse> getMonthlySummary(int year, int month, Pageable pageable) {
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

        int pageStart = (int) pageable.getOffset();
        if (pageStart >= summaries.size()) {
            return new PageImpl<>(List.of(), pageable, summaries.size());
        }
        int pageEnd = Math.min(pageStart + pageable.getPageSize(), summaries.size());
        return new PageImpl<>(summaries.subList(pageStart, pageEnd), pageable, summaries.size());
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
        csv.append("id,userId,fullName,recordDate,minutesLate,reason\n");
        for (LateRecord record : sorted) {
            csv.append(record.getId()).append(',')
                    .append(record.getUser().getId()).append(',')
                    .append(csvEscape(record.getUser().getFullName())).append(',')
                    .append(record.getRecordDate()).append(',')
                    .append(record.getMinutesLate()).append(',')
                    .append(csvEscape(record.getReason()))
                    .append('\n');
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    @CacheEvict(cacheNames = "lateMonthlySummary", allEntries = true)
    public void deleteLateRecord(Long id) {
        lateRecordRepository.deleteById(id);
    }

    // ---- ChatOps-powered automated late detection ----

    public int checkNow(String channelId) {
        if (chatopsService == null) {
            throw new IllegalStateException("ChatOps is not enabled. Set chatops.enabled=true and configure chatops properties.");
        }
        String targetChannel = (channelId != null && !channelId.isBlank()) ? channelId : chatopsService.getChannelId();
        return fetchLateCheckinsFromChat(targetChannel, LocalTime.of(10, 0));
    }

    public int fetchLateCheckinsFromChat(String channelId, LocalTime since) {
        if (chatopsService == null) return 0;

        ZoneId vietnamZone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDateTime now = LocalDateTime.now(vietnamZone);
        String todayString = now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        long timestamp = now.toLocalDate().atTime(since).atZone(vietnamZone).toEpochSecond() * 1000;

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
                .map(post -> (String) ((Map<String, Object>) post).get("message"))
                .filter(message -> message != null && message.contains("THÔNG BÁO DANH SÁCH ĐI LÀM MUỘN " + todayString))
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

        Pattern datePattern = Pattern.compile("THÔNG BÁO DANH SÁCH ĐI LÀM MUỘN (\\d{4}/\\d{2}/\\d{2})");
        Matcher dateMatcher = datePattern.matcher(rawMessage);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");

        LocalDate reportDate = dateMatcher.find() ? LocalDate.parse(dateMatcher.group(1), formatter) : LocalDate.now();

        Pattern tablePattern = Pattern.compile("\\|(.*?)\\|\\s(.*?)\\|");
        Matcher tableMatcher = tablePattern.matcher(rawMessage);

        while (tableMatcher.find()) {
            String name = tableMatcher.group(1).trim();
            String checkinAt = tableMatcher.group(2).trim();

            if (name.equalsIgnoreCase("NAME") || checkinAt.equalsIgnoreCase("CHECKIN AT")) {
                continue;
            }

            if (checkinAt.contains("(Có đơn NP)") || checkinAt.equalsIgnoreCase("Nghỉ phép")) {
                continue;
            }

            Optional<User> userOpt = userRepository.findByFullName(name);
            if (userOpt.isEmpty()) {
                log.warn("User not found with fullName: {}", name);
                continue;
            }
            User user = userOpt.get();

            LocalTime checkinTime = parseTime(checkinAt);
            int minutesLate = calculateMinutesLate(checkinTime);

            LateRecord late = new LateRecord();
            late.setUser(user);
            late.setRecordDate(reportDate);
            late.setMinutesLate(minutesLate);
            late.setReason("Auto-detected late check-in at " + (checkinTime != null ? checkinTime.toString() : checkinAt));
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
        lateRecordRepository.saveAll(lateData);

        processRepeatOffenders();
        log.info("Saved {} late records for date {}", lateData.size(), date);
        return lateData.size();
    }

    void processRepeatOffenders() {
        LocalDate today = LocalDate.now();
        var monthStart = today.withDayOfMonth(1);
        var monthEnd = today.withDayOfMonth(today.lengthOfMonth());

        var todayRecords = lateRecordRepository.findByRecordDate(today);
        var processedUserIds = new java.util.HashSet<Long>();

        for (LateRecord record : todayRecords) {
            Long userId = record.getUser().getId();
            if (!processedUserIds.add(userId)) continue;

            var userMonthRecords = lateRecordRepository
                    .findByUser_IdAndRecordDateBetween(userId, monthStart, monthEnd);
            if (userMonthRecords.size() >= 2) {
                pointRuleRepository.findByActionCode("LATE_PENALTY")
                        .ifPresent(rule -> {
                            applyLatePenalty(record.getUser(), rule, record);
                            log.info("Applied late penalty to user {} for repeat offenses ({} times this month)",
                                    record.getUser().getFullName(), userMonthRecords.size());
                        });
            }
        }
    }

    private LocalTime parseTime(String time) {
        if (time == null || time.isEmpty()) return null;
        try {
            return LocalTime.parse(time);
        } catch (DateTimeParseException e) {
            log.warn("Cannot parse time: {}", time);
            return null;
        }
    }

    private int calculateMinutesLate(LocalTime checkinTime) {
        if (checkinTime == null) return 0;
        LocalTime threshold = LocalTime.of(8, 0);
        if (checkinTime.isAfter(threshold)) {
            return (int) java.time.Duration.between(threshold, checkinTime).toMinutes();
        }
        return 0;
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
        return new LateRecordResponse(
                record.getId(),
                record.getUser().getId(),
                record.getUser().getFullName(),
                record.getRecordDate(),
                record.getMinutesLate(),
                record.getReason()
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
