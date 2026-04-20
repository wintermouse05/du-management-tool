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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
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

    public List<LateRecordResponse> getAll() {
        return lateRecordRepository.findAll().stream().map(this::toResponse).toList();
    }

    public List<LateRecordResponse> getByUser(Long userId) {
        return lateRecordRepository.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    public List<LateSummaryResponse> getMonthlySummary(int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        List<LateRecord> records = lateRecordRepository.findByRecordDateBetween(start, end);
        Map<User, List<LateRecord>> grouped = records.stream().collect(Collectors.groupingBy(LateRecord::getUser));

        return grouped.entrySet().stream()
                .map(entry -> new LateSummaryResponse(
                        entry.getKey().getId(),
                        entry.getKey().getFullName(),
                        entry.getValue().size(),
                        entry.getValue().stream().mapToLong(LateRecord::getMinutesLate).sum()
                ))
                .sorted(Comparator.comparingLong(LateSummaryResponse::totalLateTimes).reversed())
                .toList();
    }

    private void applyLatePenalty(User user, PointRule rule, LateRecord lateRecord) {
        PointHistory history = new PointHistory();
        history.setUser(user);
        history.setRule(rule);
        history.setPointsChanged(rule.getPointValue());
        history.setReason("Late penalty - " + lateRecord.getRecordDate() + " (" + lateRecord.getMinutesLate() + " mins)");
        pointHistoryRepository.save(history);

        user.setTotalPoints(user.getTotalPoints() + rule.getPointValue());
        userRepository.save(user);
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
}
