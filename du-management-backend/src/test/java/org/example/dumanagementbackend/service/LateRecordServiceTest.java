package org.example.dumanagementbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.example.dumanagementbackend.dto.late.LateRecordRequest;
import org.example.dumanagementbackend.dto.late.LateRecordResponse;
import org.example.dumanagementbackend.dto.late.LateSummaryResponse;
import org.example.dumanagementbackend.entity.LateRecord;
import org.example.dumanagementbackend.entity.PointRule;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.LateRecordStatus;
import org.example.dumanagementbackend.exception.BadRequestException;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

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

    @Test
    void create_throwsNotFoundWhenUserMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        LateRecordRequest req = new LateRecordRequest(99L, LocalDate.now(), 15, "Traffic");

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> lateRecordService.create(req));
        assertEquals("User not found with id=99", ex.getMessage());
    }

    @Test
    void create_setsFirstTimeForFirstLateInMonth() {
        User user = buildUser(1L, "Alice");
        LateRecordRequest req = new LateRecordRequest(1L, LocalDate.of(2026, 4, 1), 10, "Bus delay");
        LateRecord saved = buildRecord(10L, user, LocalDate.of(2026, 4, 1), 10, "Bus delay");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(lateRecordRepository.save(any(LateRecord.class))).thenReturn(saved);
        when(lateRecordRepository.findByUser_IdAndRecordDateBetween(any(), any(), any())).thenReturn(List.of(saved));
        when(lateRecordRepository.findById(10L)).thenReturn(Optional.of(saved));
        when(pointRuleRepository.findByActionCode("LATE_PENALTY")).thenReturn(Optional.empty());

        LateRecordResponse response = lateRecordService.create(req);

        assertEquals(LateRecordStatus.FIRST_TIME, response.status());
        assertEquals(0, response.fineAmount());
        assertFalse(response.payable());
    }

    @Test
    void create_marksSecondLateAsUnpaid() {
        User user = buildUser(1L, "Alice");
        LateRecord first = buildRecord(10L, user, LocalDate.of(2026, 4, 1), 10, "First");
        first.setStatus(LateRecordStatus.FIRST_TIME);
        first.setFineAmount(0);

        LateRecord second = buildRecord(11L, user, LocalDate.of(2026, 4, 2), 12, "Second");
        second.setStatus(LateRecordStatus.FIRST_TIME);
        second.setFineAmount(0);

        LateRecordRequest req = new LateRecordRequest(1L, LocalDate.of(2026, 4, 2), 12, "Second");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(lateRecordRepository.save(any(LateRecord.class))).thenReturn(second);
        when(lateRecordRepository.findByUser_IdAndRecordDateBetween(any(), any(), any())).thenReturn(List.of(first, second));
        when(lateRecordRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(lateRecordRepository.findById(11L)).thenReturn(Optional.of(second));
        when(pointRuleRepository.findByActionCode("LATE_PENALTY")).thenReturn(Optional.empty());

        LateRecordResponse response = lateRecordService.create(req);

        assertEquals(LateRecordStatus.UNPAID, response.status());
        assertEquals(50_000, response.fineAmount());
        assertTrue(response.payable());
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
        when(lateRecordRepository.findByUser_IdAndRecordDateBetween(any(), any(), any())).thenReturn(List.of(saved));
        when(lateRecordRepository.findById(11L)).thenReturn(Optional.of(saved));
        when(pointRuleRepository.findByActionCode("LATE_PENALTY")).thenReturn(Optional.of(rule));

        lateRecordService.create(req);

        verify(pointHistoryRepository).save(any());
        verify(userRepository).incrementTotalPoints(1L, -10);
    }

    @Test
    void updateStatus_marksPaidForChargeableRecord() {
        User user = buildUser(1L, "Alice");
        LateRecord first = buildRecord(10L, user, LocalDate.of(2026, 4, 1), 10, "First");
        first.setStatus(LateRecordStatus.FIRST_TIME);
        first.setFineAmount(0);

        LateRecord target = buildRecord(11L, user, LocalDate.of(2026, 4, 2), 20, "Second");
        target.setStatus(LateRecordStatus.UNPAID);
        target.setFineAmount(50_000);

        when(lateRecordRepository.findById(11L)).thenReturn(Optional.of(target));
        when(lateRecordRepository.findByUser_IdAndRecordDateBetween(any(), any(), any())).thenReturn(List.of(first, target));
        when(lateRecordRepository.save(any(LateRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TestingAuthenticationToken adminAuth = new TestingAuthenticationToken("admin", "password", "ROLE_ADMIN");
        adminAuth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(adminAuth);

        try {
            LateRecordResponse response = lateRecordService.updateStatus(11L, LateRecordStatus.PAID);
            assertEquals(LateRecordStatus.PAID, response.status());
            assertTrue(response.payable());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void updateStatus_rejectsPaidForFirstTimeRecord() {
        User user = buildUser(1L, "Alice");
        LateRecord first = buildRecord(10L, user, LocalDate.of(2026, 4, 1), 10, "First");
        first.setStatus(LateRecordStatus.FIRST_TIME);
        first.setFineAmount(0);

        when(lateRecordRepository.findById(10L)).thenReturn(Optional.of(first));
        when(lateRecordRepository.findByUser_IdAndRecordDateBetween(any(), any(), any())).thenReturn(List.of(first));

        TestingAuthenticationToken adminAuth = new TestingAuthenticationToken("admin", "password", "ROLE_ADMIN");
        adminAuth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(adminAuth);

        try {
            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> lateRecordService.updateStatus(10L, LateRecordStatus.PAID));
            assertTrue(ex.getMessage().contains("second time"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

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
        assertEquals(LateRecordStatus.FIRST_TIME, result.getContent().get(0).status());
    }

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

        Pageable pageable = PageRequest.of(1, 10);
        Page<LateSummaryResponse> result = lateRecordService.getMonthlySummary(2026, 3, pageable);

        assertEquals(0, result.getContent().size());
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void exportCsv_includesNewColumns() {
        User user = buildUser(1L, "Export User");
        LateRecord record = buildRecord(5L, user, LocalDate.of(2026, 4, 10), 15, "Late bus");
        record.setStatus(LateRecordStatus.UNPAID);
        record.setFineAmount(50_000);

        when(lateRecordRepository.findByRecordDateBetween(any(), any())).thenReturn(List.of(record));

        byte[] csv = lateRecordService.exportCsv(2026, 4);
        String content = new String(csv, StandardCharsets.UTF_8);

        assertTrue(content.startsWith("id,userId,fullName,recordDate,minutesLate,reason,status,fineAmount\n"));
        assertTrue(content.contains("UNPAID"));
        assertTrue(content.contains("50000"));
    }

    @Test
    void parseLateRecords_supportsChatOpsLatePostFormat() {
        User tho = buildUser(2L, "NGUYEN THI THO");
        User nhat = buildUser(4L, "DO DUC NHAT");
        String rawMessage = """
                :warning: THONG BAO DANH SACH DI LAM MUON 2025/03/24
                ---
                |NAME | CHECKIN AT|
                |--- | ---|
                |MAI DINH DONG | Nghi phep|
                |NGUYEN THI THO | 08:04:16|
                |NGUYEN THANH TU | 07:27:01 (Co don NP)|
                |DO DUC NHAT | -|
                """;

        when(userRepository.findByFullName("NGUYEN THI THO")).thenReturn(Optional.empty());
        when(userRepository.findByFullNameIgnoreCase("NGUYEN THI THO")).thenReturn(Optional.of(tho));
        when(userRepository.findByFullName("DO DUC NHAT")).thenReturn(Optional.of(nhat));

        List<LateRecord> records = lateRecordService.parseLateRecords(rawMessage);

        assertEquals(2, records.size());
        LateRecord record = records.get(0);
        assertEquals(LocalDate.of(2025, 3, 24), record.getRecordDate());
        assertEquals(4, record.getMinutesLate());
        assertEquals(tho, record.getUser());
        assertTrue(record.getReason().contains("08:04:16"));

        LateRecord missingCheckinRecord = records.get(1);
        assertEquals(LocalDate.of(2025, 3, 24), missingCheckinRecord.getRecordDate());
        assertEquals(0, missingCheckinRecord.getMinutesLate());
        assertEquals(nhat, missingCheckinRecord.getUser());
        assertEquals("Did not check in", missingCheckinRecord.getReason());
    }

    @Test
    void parseLateRecords_setsDidNotCheckInReasonForEmptyCheckin() {
        User emptyCheckinUser = buildUser(5L, "LE VAN A");
        String rawMessage = """
                Daily report 2025/03/24
                |NAME|CHECKIN AT|
                |LE VAN A||
                """;

        when(userRepository.findByFullName("LE VAN A")).thenReturn(Optional.of(emptyCheckinUser));

        List<LateRecord> records = lateRecordService.parseLateRecords(rawMessage);

        assertEquals(1, records.size());
        LateRecord record = records.get(0);
        assertEquals(0, record.getMinutesLate());
        assertEquals("Did not check in", record.getReason());
    }

    @Test
    void parseLateRecords_extractsTimeWhenCheckinContainsSuffixText() {
        User hai = buildUser(3L, "Nguyen Thanh Hai");
        String rawMessage = """
                Daily report 2025/03/24
                |NAME|CHECKIN AT|
                |NGUYEN THANH HAI|08:26:25 (WFH approved)|
                """;

        when(userRepository.findByFullName("NGUYEN THANH HAI")).thenReturn(Optional.of(hai));

        List<LateRecord> records = lateRecordService.parseLateRecords(rawMessage);

        assertEquals(1, records.size());
        LateRecord record = records.get(0);
        assertEquals(26, record.getMinutesLate());
        assertTrue(record.getReason().contains("08:26:25"));
    }

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
        record.setStatus(LateRecordStatus.FIRST_TIME);
        record.setFineAmount(0);
        return record;
    }
}
