package org.example.dumanagementbackend.service;

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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LateRecordService {

    private final LateRecordRepository lateRecordRepository;
    private final UserRepository userRepository;
    private final PointRuleRepository pointRuleRepository;
    private final PointHistoryRepository pointHistoryRepository;

    @Transactional
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
