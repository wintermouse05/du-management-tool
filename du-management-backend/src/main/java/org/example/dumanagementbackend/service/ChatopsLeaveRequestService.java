package org.example.dumanagementbackend.service;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.example.dumanagementbackend.dto.chatops.ChatopsLeaveRequestResponse;
import org.example.dumanagementbackend.dto.chatops.ChatopsLeaveRequestSummaryResponse;
import org.example.dumanagementbackend.dto.chatops.ChatopsLeaveRequestType;
import org.example.dumanagementbackend.entity.enums.ChatopsChannelPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatopsLeaveRequestService {

    private static final Logger log = LoggerFactory.getLogger(ChatopsLeaveRequestService.class);
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Pattern WFH_PATTERN = Pattern.compile("(?<![a-z0-9])(?:w\\.?f\\.?h\\.?|work\\s+from\\s+home)(?![a-z0-9])");
    private static final Pattern OFF_PATTERN = Pattern.compile("(?<![a-z0-9])off(?![a-z0-9])");
    private static final Pattern NGHI_PATTERN = Pattern.compile("(?<![a-z0-9])nghi(?:\\s+phep)?(?![a-z0-9])");
    private static final Pattern LATE_REPORT_TABLE_PATTERN = Pattern.compile("\\|\\s*name\\s*\\|\\s*checkin\\s+at\\s*\\|");
    private static final Pattern LATE_REPORT_HINT_PATTERN = Pattern.compile(
            "(danh\\s+sach\\s+di\\s+lam\\s+muon|thong\\s+bao\\s+danh\\s+sach\\s+di\\s+lam\\s+muon|#checkin-statistic|checkin-statistic)"
    );

    private final ObjectProvider<ChatopsService> chatopsServiceProvider;

    @Value("${chatops.leave-requests.cache-ttl-minutes:10}")
    private long cacheTtlMinutes;

    private volatile CachedLeaveRequests cache = CachedLeaveRequests.empty();

    public ChatopsLeaveRequestSummaryResponse getTodayRequests() {
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        LocalDate today = now.toLocalDate();
        CachedLeaveRequests snapshot = cache;
        if (isFresh(snapshot, today, now)) {
            return toSummary(snapshot);
        }
        return refreshToday();
    }

    public synchronized ChatopsLeaveRequestSummaryResponse refreshToday() {
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        LocalDate today = now.toLocalDate();
        ChatopsService chatopsService = chatopsServiceProvider.getIfAvailable();
        if (chatopsService == null) {
            cache = new CachedLeaveRequests(today, now, false, null, List.of());
            return toSummary(cache);
        }

        try {
            String channelId = chatopsService.getInputChannelId();
            long sinceTimestamp = today
                    .atStartOfDay(VIETNAM_ZONE)
                    .toInstant()
                    .toEpochMilli();
            Map<String, Object> response = chatopsService.getChannelPosts(channelId, sinceTimestamp);
            List<PostCandidate> candidates = extractCandidates(response, today);
            Map<String, String> requesterNames = resolveRequesterNames(chatopsService, candidates);

            List<ChatopsLeaveRequestResponse> requests = candidates.stream()
                    .map(candidate -> new ChatopsLeaveRequestResponse(
                            candidate.postId(),
                            candidate.userId(),
                            requesterNames.getOrDefault(candidate.userId(), fallbackRequesterName(candidate.userId())),
                            candidate.type(),
                            today,
                            candidate.postedAt(),
                            candidate.message(),
                            candidate.matchedText()
                    ))
                    .sorted(Comparator.comparing(ChatopsLeaveRequestResponse::postedAt))
                    .toList();

            cache = new CachedLeaveRequests(today, now, true, null, requests);
            return toSummary(cache);
        } catch (Exception ex) {
            log.error("Failed to refresh ChatOps WFH/OFF requests: {}", ex.getMessage(), ex);
            cache = new CachedLeaveRequests(today, now, true, "Unable to fetch ChatOps messages.", List.of());
            return toSummary(cache);
        }
    }

    ChatopsLeaveRequestType detectType(String rawMessage) {
        String normalized = normalizeForComparison(rawMessage);
        if (OFF_PATTERN.matcher(normalized).find() || NGHI_PATTERN.matcher(normalized).find()) {
            return ChatopsLeaveRequestType.OFF;
        }
        if (WFH_PATTERN.matcher(normalized).find()) {
            return ChatopsLeaveRequestType.WFH;
        }
        return null;
    }

    boolean wasSentOnTargetDate(LocalDate postDate, LocalDate targetDate) {
        return targetDate.equals(postDate);
    }

    boolean isLikelyLateReportMessage(String rawMessage) {
        String normalized = normalizeForComparison(rawMessage);
        return LATE_REPORT_TABLE_PATTERN.matcher(normalized).find()
                || LATE_REPORT_HINT_PATTERN.matcher(normalized).find();
    }

    private boolean isFresh(CachedLeaveRequests snapshot, LocalDate today, LocalDateTime now) {
        if (!today.equals(snapshot.date())) {
            return false;
        }
        if (!snapshot.chatopsEnabled()) {
            return true;
        }
        if (cacheTtlMinutes <= 0) {
            return false;
        }
        return Duration.between(snapshot.fetchedAt(), now).toMinutes() < cacheTtlMinutes;
    }

    private ChatopsLeaveRequestSummaryResponse toSummary(CachedLeaveRequests snapshot) {
        int wfhCount = (int) snapshot.requests().stream()
                .filter(request -> request.type() == ChatopsLeaveRequestType.WFH)
                .count();
        int offCount = (int) snapshot.requests().stream()
                .filter(request -> request.type() == ChatopsLeaveRequestType.OFF)
                .count();
        return new ChatopsLeaveRequestSummaryResponse(
                snapshot.date(),
                snapshot.fetchedAt(),
                snapshot.chatopsEnabled(),
                snapshot.errorMessage(),
                snapshot.requests().size(),
                wfhCount,
                offCount,
                snapshot.requests()
        );
    }

    @SuppressWarnings("unchecked")
    private List<PostCandidate> extractCandidates(Map<String, Object> response, LocalDate targetDate) {
        if (response == null || !(response.get("posts") instanceof Map<?, ?> posts)) {
            return List.of();
        }

        List<PostCandidate> candidates = new ArrayList<>();
        for (Object value : posts.values()) {
            if (!(value instanceof Map<?, ?> rawPost)) {
                continue;
            }

            Map<String, Object> post = (Map<String, Object>) rawPost;
            String message = trimToNull(asString(post.get("message")));
            if (message == null) {
                continue;
            }
            if (isLikelyLateReportMessage(message)) {
                continue;
            }

            ChatopsLeaveRequestType type = detectType(message);
            if (type == null) {
                continue;
            }

            Long createdAtMillis = asLong(post.get("create_at"));
            if (createdAtMillis == null) {
                createdAtMillis = asLong(post.get("update_at"));
            }
            if (createdAtMillis == null) {
                continue;
            }

            LocalDateTime postedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(createdAtMillis), VIETNAM_ZONE);
            if (!wasSentOnTargetDate(postedAt.toLocalDate(), targetDate)) {
                continue;
            }

            candidates.add(new PostCandidate(
                    asString(post.get("id")),
                    asString(post.get("user_id")),
                    postedAt,
                    message,
                    type,
                    matchedText(type, message)
            ));
        }
        return candidates;
    }

    private Map<String, String> resolveRequesterNames(ChatopsService chatopsService, List<PostCandidate> candidates) {
        Set<String> userIds = new LinkedHashSet<>();
        for (PostCandidate candidate : candidates) {
            String userId = trimToNull(candidate.userId());
            if (userId != null) {
                userIds.add(userId);
            }
        }
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<String, String> names = new HashMap<>();
        try {
            List<Map<String, Object>> users = chatopsService.getUsersByIds(new ArrayList<>(userIds), ChatopsChannelPurpose.LATE_INPUT);
            for (Map<String, Object> user : users) {
                String id = trimToNull(asString(user.get("id")));
                if (id != null) {
                    names.put(id, displayName(user));
                }
            }
        } catch (Exception ex) {
            log.warn("Unable to resolve ChatOps requester names: {}", ex.getMessage());
        }
        return names;
    }

    private String matchedText(ChatopsLeaveRequestType type, String rawMessage) {
        String normalized = normalizeForComparison(rawMessage);
        if (type == ChatopsLeaveRequestType.OFF) {
            if (OFF_PATTERN.matcher(normalized).find()) {
                return "off";
            }
            return "nghi";
        }
        if (WFH_PATTERN.matcher(normalized).find()) {
            return "wfh";
        }
        return type.name().toLowerCase(Locale.ROOT);
    }

    private String displayName(Map<String, Object> user) {
        String firstName = trimToEmpty(asString(user.get("first_name")));
        String lastName = trimToEmpty(asString(user.get("last_name")));
        String fullName = (firstName + " " + lastName).trim();
        if (!fullName.isBlank()) {
            return fullName;
        }
        String nickname = trimToNull(asString(user.get("nickname")));
        if (nickname != null) {
            return nickname;
        }
        String username = trimToNull(asString(user.get("username")));
        if (username != null) {
            return username;
        }
        String email = trimToNull(asString(user.get("email")));
        if (email != null) {
            return email;
        }
        return fallbackRequesterName(asString(user.get("id")));
    }

    private String normalizeForComparison(String value) {
        String normalized = trimToEmpty(value)
                .replace('\u0110', 'D')
                .replace('\u0111', 'd');
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private String trimToNull(String value) {
        String trimmed = trimToEmpty(value);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToEmpty(String value) {
        return value != null ? value.trim() : "";
    }

    private String fallbackRequesterName(String userId) {
        String trimmed = trimToNull(userId);
        return trimmed != null ? trimmed : "Unknown";
    }

    private record PostCandidate(
            String postId,
            String userId,
            LocalDateTime postedAt,
            String message,
            ChatopsLeaveRequestType type,
            String matchedText
    ) {
    }

    private record CachedLeaveRequests(
            LocalDate date,
            LocalDateTime fetchedAt,
            boolean chatopsEnabled,
            String errorMessage,
            List<ChatopsLeaveRequestResponse> requests
    ) {
        private static CachedLeaveRequests empty() {
            LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
            return new CachedLeaveRequests(now.toLocalDate().minusDays(1), now, false, null, List.of());
        }
    }
}
