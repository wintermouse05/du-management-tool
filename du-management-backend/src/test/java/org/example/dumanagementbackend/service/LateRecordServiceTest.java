package org.example.dumanagementbackend.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.example.dumanagementbackend.dto.late.LateRecordRequest;
import org.example.dumanagementbackend.dto.late.LateRecordResponse;
import org.example.dumanagementbackend.dto.late.LateSummaryResponse;
import org.example.dumanagementbackend.entity.LateRecord;
import org.example.dumanagementbackend.entity.PointRule;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.LateRecordRepository;
import org.example.dumanagementbackend.repository.PointHistoryRepository;
import org.example.dumanagementbackend.repository.PointRuleRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.nio.charset.StandardCharsets;

@ExtendWith(MockitoExtension.class)
class LateRecordServiceTest {

    @Mock
    private LateRecordRepository lateRecordRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PointRuleRepository pointRuleRepository;

    @Mock
    private PointHistoryRepository pointHistoryRepository;

    @InjectMocks
    private LateRecordService lateRecordService;

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void create_throwsNotFoundWhenUserMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        LateRecordRequest req = new LateRecordRequest(99L, LocalDate.now(), 15, "Traffic");

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> lateRecordService.create(req));
        assertEquals("User not found with id=99", ex.getMessage());
    }

    @Test
    void create_savesRecordWithoutPenaltyWhenNoLateRule() {
        User user = buildUser(1L, "Alice");
        LateRecordRequest req = new LateRecordRequest(1L, LocalDate.of(2026, 4, 1), 10, "Bus delay");

        LateRecord saved = buildRecord(10L, user, LocalDate.of(2026, 4, 1), 10, "Bus delay");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(lateRecordRepository.save(any(LateRecord.class))).thenReturn(saved);
        when(pointRuleRepository.findByActionCode("LATE_PENALTY")).thenReturn(Optional.empty());

        LateRecordResponse response = lateRecordService.create(req);

        assertEquals(10L, response.id());
        assertEquals(1L, response.userId());
        assertEquals(10, response.minutesLate());
    }

    @Test
    void create_appliesPenaltyWhenLateRuleExists() {
        User user = buildUser(1L, "Alice");
        LateRecordRequest req = new LateRecordRequest(1L, LocalDate.of(2026, 4, 1), 20, "Overslept");

        LateRecord saved = buildRecord(11L, user, LocalDate.of(2026, 4, 1), 20, "Overslept");

        PointRule rule = new PointRule();
        rule.setId(5L);
        rule.setActionCode("LATE_PENALTY");
        rule.setPointValue(-10);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(lateRecordRepository.save(any(LateRecord.class))).thenReturn(saved);
        when(pointRuleRepository.findByActionCode("LATE_PENALTY")).thenReturn(Optional.of(rule));

        lateRecordService.create(req);

        verify(pointHistoryRepository).save(any());
        verify(userRepository).incrementTotalPoints(1L, -10);
    }

    // ── getAll ───────────────────────────────────────────────────────────────

    @Test
    void getAll_returnsMappedPage() {
        User user = buildUser(1L, "Bob");
        LateRecord record = buildRecord(1L, user, LocalDate.now(), 5, "Meeting");
        Pageable pageable = PageRequest.of(0, 10);
        Page<LateRecord> page = new PageImpl<>(List.of(record), pageable, 1);

        when(lateRecordRepository.findAll(pageable)).thenReturn(page);

        Page<LateRecordResponse> result = lateRecordService.getAll(pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("Bob", result.getContent().get(0).fullName());
    }

    // ── getByUser ─────────────────────────────────────────────────────────────

    @Test
    void getByUser_returnsMappedPage() {
        User user = buildUser(2L, "Carol");
        LateRecord record = buildRecord(1L, user, LocalDate.now(), 30, "Doctor");
        Pageable pageable = PageRequest.of(0, 5);
        Page<LateRecord> page = new PageImpl<>(List.of(record), pageable, 1);

        when(lateRecordRepository.findByUserId(2L, pageable)).thenReturn(page);

        Page<LateRecordResponse> result = lateRecordService.getByUser(2L, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(30, result.getContent().get(0).minutesLate());
    }

    // ── getMonthlySummary ─────────────────────────────────────────────────────

    @Test
    void getMonthlySummary_groupsAndSortsByTotalLateTimes() {
        User alice = buildUser(1L, "Alice");
        User bob = buildUser(2L, "Bob");

        LateRecord r1 = buildRecord(1L, alice, LocalDate.of(2026, 3, 5), 10, null);
        LateRecord r2 = buildRecord(2L, alice, LocalDate.of(2026, 3, 10), 20, null);
        LateRecord r3 = buildRecord(3L, bob, LocalDate.of(2026, 3, 7), 5, null);

        when(lateRecordRepository.findByRecordDateBetween(
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .thenReturn(List.of(r1, r2, r3));

        Pageable pageable = PageRequest.of(0, 10);
        Page<LateSummaryResponse> result = lateRecordService.getMonthlySummary(2026, 3, pageable);

        assertEquals(2, result.getTotalElements());
        // Alice has 2 late records; Bob has 1 — sorted descending by totalLateTimes
        LateSummaryResponse first = result.getContent().get(0);
        assertEquals("Alice", first.fullName());
        assertEquals(2, first.totalLateTimes());
        assertEquals(30L, first.totalLateMinutes());
    }

    @Test
    void getMonthlySummary_returnsEmptyPageWhenOffsetBeyondResults() {
        User user = buildUser(1L, "Dave");
        LateRecord r = buildRecord(1L, user, LocalDate.of(2026, 3, 5), 10, null);
        when(lateRecordRepository.findByRecordDateBetween(any(), any())).thenReturn(List.of(r));

        // Page 2 of page size 10 will be beyond the 1 result
        Pageable pageable = PageRequest.of(1, 10);
        Page<LateSummaryResponse> result = lateRecordService.getMonthlySummary(2026, 3, pageable);

        assertEquals(0, result.getContent().size());
        assertEquals(1, result.getTotalElements());
    }

    // ── exportCsv ─────────────────────────────────────────────────────────────

    @Test
    void exportCsv_includesHeaderAndDataRow() {
        User user = buildUser(1L, "Export User");
        LateRecord record = buildRecord(5L, user, LocalDate.of(2026, 4, 10), 15, "Late bus");

        when(lateRecordRepository.findByRecordDateBetween(any(), any())).thenReturn(List.of(record));

        byte[] csv = lateRecordService.exportCsv(2026, 4);
        String content = new String(csv, StandardCharsets.UTF_8);

        assertTrue(content.startsWith("id,userId,fullName,recordDate,minutesLate,reason\n"));
        assertTrue(content.contains("Export User"));
        assertTrue(content.contains("2026-04-10"));
        assertTrue(content.contains("15"));
        assertTrue(content.contains("Late bus"));
    }

    @Test
    void exportCsv_returnsAllRecordsWhenNoMonthFilter() {
        User user = buildUser(1L, "All Records User");
        LateRecord r1 = buildRecord(1L, user, LocalDate.of(2026, 1, 5), 10, null);
        LateRecord r2 = buildRecord(2L, user, LocalDate.of(2026, 2, 8), 20, null);

        when(lateRecordRepository.findAll()).thenReturn(List.of(r1, r2));

        byte[] csv = lateRecordService.exportCsv(null, null);
        String content = new String(csv, StandardCharsets.UTF_8);

        // Should have header + 2 data rows
        long lines = content.lines().filter(l -> !l.isBlank()).count();
        assertEquals(3, lines);
    }

    @Test
    void exportCsv_escapesCommasInReason() {
        User user = buildUser(1L, "Test User");
        LateRecord record = buildRecord(7L, user, LocalDate.of(2026, 4, 1), 5, "Traffic, rain");

        when(lateRecordRepository.findByRecordDateBetween(any(), any())).thenReturn(List.of(record));

        byte[] csv = lateRecordService.exportCsv(2026, 4);
        String content = new String(csv, StandardCharsets.UTF_8);

        assertTrue(content.contains("\"Traffic, rain\""));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User buildUser(Long id, String fullName) {
        User user = new User();
        user.setId(id);
        user.setFullName(fullName);
        return user;
    }

    private LateRecord buildRecord(Long id, User user, LocalDate date, int minutes, String reason) {
        LateRecord record = new LateRecord();
        record.setId(id);
        record.setUser(user);
        record.setRecordDate(date);
        record.setMinutesLate(minutes);
        record.setReason(reason);
        return record;
    }
}
