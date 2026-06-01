package org.example.dumanagementbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.dumanagementbackend.dto.chatops.ChatopsLeaveRequestResponse;
import org.example.dumanagementbackend.dto.chatops.ChatopsLeaveRequestSummaryResponse;
import org.example.dumanagementbackend.dto.chatops.ChatopsLeaveRequestType;
import org.example.dumanagementbackend.entity.enums.ChatopsChannelPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ChatopsLeaveRequestServiceTest {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private ChatopsLeaveRequestService service;
    private StubChatopsService chatopsService;

    @BeforeEach
    void setUp() {
        chatopsService = new StubChatopsService();
        service = new ChatopsLeaveRequestService(new StaticObjectProvider(chatopsService));
    }

    @Test
    void detectType_matchesWfhAndWorkFromHome() {
        assertEquals(ChatopsLeaveRequestType.WFH, service.detectType("Em xin phép WFH hôm nay"));
        assertEquals(ChatopsLeaveRequestType.WFH, service.detectType("I will Work From Home today"));
    }

    @Test
    void detectType_matchesOffAndVietnameseNghi() {
        assertEquals(ChatopsLeaveRequestType.OFF, service.detectType("[OFF_01/06] xin phép nghỉ phép hôm nay"));
        assertEquals(ChatopsLeaveRequestType.OFF, service.detectType("cho em xin phép nghỉ hôm nay"));
    }

    @Test
    void detectType_doesNotMatchOffInsideOffice() {
        assertEquals(null, service.detectType("I am at the office today"));
    }

    @Test
    void wasSentOnTargetDate_matchesOnlyMessageSentDate() {
        LocalDate postDate = LocalDate.of(2026, 6, 1);

        assertTrue(service.wasSentOnTargetDate(postDate, LocalDate.of(2026, 6, 1)));
        assertFalse(service.wasSentOnTargetDate(postDate, LocalDate.of(2026, 6, 2)));
    }

    @Test
    void dateWordsInsideMessageDoNotChangeSentDateFilter() {
        LocalDate postDate = LocalDate.of(2026, 5, 31);

        assertFalse(service.wasSentOnTargetDate(postDate, LocalDate.of(2026, 6, 1)));
    }

    @Test
    void explicitDatesInsideMessageDoNotChangeSentDateFilter() {
        LocalDate postDate = LocalDate.of(2026, 5, 28);

        assertFalse(service.wasSentOnTargetDate(postDate, LocalDate.of(2026, 6, 3)));
        assertFalse(service.wasSentOnTargetDate(postDate, LocalDate.of(2026, 6, 1)));
    }

    @Test
    void refreshToday_filtersOnlyTodayPostsWithLeaveKeywords() {
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        LocalDate yesterday = today.minusDays(1);
        chatopsService.postsResponse = Map.of(
                "posts", posts(
                        post("p1", "u1", "Em xin phép WFH ngày mai do có việc gia đình", atVietnam(today, 6, 24)),
                        post("p2", "u2", "[OFF_01/06] xin phép nghỉ phép hôm nay", atVietnam(today, 6, 42)),
                        post("p3", "u3", "Em xin phép WFH hôm nay nhưng message này gửi hôm qua", atVietnam(yesterday, 21, 17)),
                        post("p4", "u4", "I will be in the office today", atVietnam(today, 8, 15))
                )
        );
        chatopsService.usersResponse = List.of(
                user("u1", "Nguyen", "Van A", null),
                user("u2", "", "", "huynh-tu-lenh")
        );

        ChatopsLeaveRequestSummaryResponse summary = service.refreshToday();

        assertTrue(summary.chatopsEnabled());
        assertNull(summary.errorMessage());
        assertEquals(today, summary.date());
        assertEquals(2, summary.total());
        assertEquals(1, summary.wfhCount());
        assertEquals(1, summary.offCount());

        List<ChatopsLeaveRequestResponse> requests = summary.requests();
        assertEquals("p1", requests.get(0).postId());
        assertEquals("Nguyen Van A", requests.get(0).requesterName());
        assertEquals(ChatopsLeaveRequestType.WFH, requests.get(0).type());
        assertEquals("wfh", requests.get(0).matchedText());

        assertEquals("p2", requests.get(1).postId());
        assertEquals("huynh-tu-lenh", requests.get(1).requesterName());
        assertEquals(ChatopsLeaveRequestType.OFF, requests.get(1).type());
        assertEquals("off", requests.get(1).matchedText());
        assertEquals("chatops-channel", chatopsService.requestedChannelId);
        assertTrue(chatopsService.requestedSinceTimestamp > 0);
    }

    @Test
    void refreshToday_returnsDisabledSummaryWhenChatopsBeanIsUnavailable() {
        service = new ChatopsLeaveRequestService(new StaticObjectProvider(null));

        ChatopsLeaveRequestSummaryResponse summary = service.refreshToday();

        assertFalse(summary.chatopsEnabled());
        assertNull(summary.errorMessage());
        assertEquals(0, summary.total());
        assertTrue(summary.requests().isEmpty());
    }

    @Test
    void refreshToday_returnsErrorSummaryWhenChatopsFetchFails() {
        chatopsService.throwOnFetch = true;

        ChatopsLeaveRequestSummaryResponse summary = service.refreshToday();

        assertTrue(summary.chatopsEnabled());
        assertEquals("Unable to fetch ChatOps messages.", summary.errorMessage());
        assertEquals(0, summary.total());
        assertTrue(summary.requests().isEmpty());
    }

    @Test
    void refreshToday_ignoresLateReportPostContainingNghiPhepRows() {
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        String lateReportMessage = """
                :warning: THONG BAO DANH SACH DI LAM MUON 2026/05/25
                |NAME | CHECKIN AT|
                |--- | ---|
                |MAI DINH DONG | Nghi phep|
                |LE MINH TOAN | Nghi phep|
                #checkin-statistic
                """;
        chatopsService.postsResponse = Map.of(
                "posts", posts(
                        post("late-report", "bot", lateReportMessage, atVietnam(today, 9, 5)),
                        post("leave-request", "u1", "Em xin phep OFF hom nay do co viec gia dinh", atVietnam(today, 9, 8))
                )
        );
        chatopsService.usersResponse = List.of(user("u1", "Tran", "Thi B", null));

        ChatopsLeaveRequestSummaryResponse summary = service.refreshToday();

        assertEquals(1, summary.total());
        assertEquals(0, summary.wfhCount());
        assertEquals(1, summary.offCount());
        assertEquals("leave-request", summary.requests().get(0).postId());
    }

    private Map<String, Object> post(String id, String userId, String message, long createdAt) {
        Map<String, Object> post = new LinkedHashMap<>();
        post.put("id", id);
        post.put("user_id", userId);
        post.put("message", message);
        post.put("create_at", createdAt);
        return post;
    }

    private Map<String, Object> user(String id, String firstName, String lastName, String username) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", id);
        user.put("first_name", firstName);
        user.put("last_name", lastName);
        user.put("username", username);
        return user;
    }

    @SafeVarargs
    private Map<String, Object> posts(Map<String, Object>... posts) {
        Map<String, Object> responsePosts = new LinkedHashMap<>();
        for (Map<String, Object> post : posts) {
            responsePosts.put(String.valueOf(post.get("id")), post);
        }
        return responsePosts;
    }

    private long atVietnam(LocalDate date, int hour, int minute) {
        return LocalDateTime.of(date.getYear(), date.getMonth(), date.getDayOfMonth(), hour, minute)
                .atZone(VIETNAM_ZONE)
                .toInstant()
                .toEpochMilli();
    }

    private static class StaticObjectProvider implements ObjectProvider<ChatopsService> {

        private final ChatopsService chatopsService;

        StaticObjectProvider(ChatopsService chatopsService) {
            this.chatopsService = chatopsService;
        }

        @Override
        public ChatopsService getIfAvailable() {
            return chatopsService;
        }
    }

    private static class StubChatopsService extends ChatopsService {

        private Map<String, Object> postsResponse = Map.of("posts", Map.of());
        private List<Map<String, Object>> usersResponse = List.of();
        private String requestedChannelId;
        private long requestedSinceTimestamp;
        private boolean throwOnFetch;
        private List<String> requestedUserIds = new ArrayList<>();

        StubChatopsService() {
            super(null, null);
        }

        @Override
        public String getInputChannelId() {
            return "chatops-channel";
        }

        @Override
        public Map<String, Object> getChannelPosts(String targetChannelId, long sinceTimestamp) {
            requestedChannelId = targetChannelId;
            requestedSinceTimestamp = sinceTimestamp;
            if (throwOnFetch) {
                throw new IllegalStateException("boom");
            }
            return postsResponse;
        }

        @Override
        public List<Map<String, Object>> getUsersByIds(List<String> userIds, ChatopsChannelPurpose purpose) {
            requestedUserIds = new ArrayList<>(userIds);
            return usersResponse;
        }
    }
}
