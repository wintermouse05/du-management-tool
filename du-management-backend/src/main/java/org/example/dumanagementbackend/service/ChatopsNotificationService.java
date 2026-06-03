package org.example.dumanagementbackend.service;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.dumanagementbackend.dto.project.AvailableProjectMemberResponse;
import org.example.dumanagementbackend.dto.project.ProjectAvailabilitySummaryResponse;
import org.example.dumanagementbackend.entity.LateRecord;
import org.example.dumanagementbackend.entity.NotificationSchedule;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.LateRecordStatus;
import org.example.dumanagementbackend.entity.enums.NotificationScheduleType;
import org.example.dumanagementbackend.entity.enums.OrderSessionStatus;
import org.example.dumanagementbackend.entity.enums.SystemLogCategory;
import org.example.dumanagementbackend.entity.enums.SystemLogSeverity;
import org.example.dumanagementbackend.entity.enums.SystemLogStatus;
import org.example.dumanagementbackend.entity.enums.UserStatus;
import org.example.dumanagementbackend.logging.SystemLogContext;
import org.example.dumanagementbackend.logging.SystemLogSanitizer;
import org.example.dumanagementbackend.repository.LateRecordRepository;
import org.example.dumanagementbackend.repository.NotificationScheduleRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "chatops.enabled", havingValue = "true")
public class ChatopsNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ChatopsNotificationService.class);
    private static final int MAX_LEADERBOARD_ENTRIES = 50;
    private static final int DETAILS_PREVIEW_MAX_LENGTH = 500;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern RELATIVE_MARKDOWN_LINK_PATTERN = Pattern.compile("\\]\\((/[^)\\s]*)\\)");

    private final ChatopsService chatopsService;
    private final UserRepository userRepository;
    private final NotificationScheduleRepository notificationScheduleRepository;
    private final LateRecordRepository lateRecordRepository;
    private final SystemLogService systemLogService;

    @Value("${chatops.open-link-base-url:}")
    private String openLinkBaseUrl;

    // ---- basic sending ----

    public String sendToChannel(String message) {
        return sendToChannel(message, null);
    }

    public String sendToChannel(String message, String targetChannelId) {
        String resolvedChannelId = resolveTargetChannelId(targetChannelId);
        String postId = chatopsService.sendMessageWithResponse(resolvedChannelId, prepareOutgoingMessage(message), null);
        logChatMessage("CHAT_CHANNEL", resolvedChannelId, postId, postId == null ? SystemLogStatus.FAILED : SystemLogStatus.SUCCESS, null);
        return postId;
    }

    public String sendThreadReply(String message, String rootPostId) {
        return sendThreadReply(message, rootPostId, null);
    }

    public String sendThreadReply(String message, String rootPostId, String targetChannelId) {
        String resolvedChannelId = resolveTargetChannelId(targetChannelId);
        String postId = chatopsService.sendMessageWithResponse(resolvedChannelId, prepareOutgoingMessage(message), rootPostId);
        logChatMessage("CHAT_THREAD_REPLY", resolvedChannelId, postId, postId == null ? SystemLogStatus.FAILED : SystemLogStatus.SUCCESS, rootPostId);
        return postId;
    }

    public void sendDirectMessage(String senderEmail, String receiverEmail, String message) {
        try {
            var receiver = chatopsService.getUserByEmail(receiverEmail);
            String receiverId = (String) receiver.get("id");

            var sender = chatopsService.getUserByEmail(senderEmail);
            String senderId = (String) sender.get("id");

            String dmChannelId = chatopsService.getDirectChannelId(senderId, receiverId);
            chatopsService.sendMessage(dmChannelId, prepareOutgoingMessage(message));
            logChatMessage("CHAT_DIRECT_MESSAGE", dmChannelId, null, SystemLogStatus.SUCCESS, null);
        } catch (Exception e) {
            log.error("Failed to send DM from {} to {}: {}", senderEmail, receiverEmail, e.getMessage());
            logChatMessage("CHAT_DIRECT_MESSAGE", receiverEmail, null, SystemLogStatus.FAILED, null, e);
        }
    }

    // ---- created item announcements ----

    public String sendEventCreatedNotification(
            Long eventId,
            String name,
            LocalDateTime eventDate,
            String location,
            String description
    ) {
        StringBuilder message = new StringBuilder("@all\n");
        message.append("**New event created: ").append(valueOrFallback(name, "Untitled event")).append("**\n\n");
        message.append("- Time: ").append(formatDateTime(eventDate)).append("\n");
        appendOptionalLine(message, "Location", location);
        appendOptionalLine(message, "Details", truncate(description));
        appendActionLink(message, "Open event", eventId != null ? "/events/" + eventId : "/events");
        return sendToChannel(message.toString());
    }

    public String sendOrderSessionCreatedNotification(
            Long sessionId,
            String name,
            String restaurantName,
            LocalDateTime deadline,
            OrderSessionStatus status
    ) {
        StringBuilder message = new StringBuilder("@all\n");
        message.append("**New order session created: ").append(valueOrFallback(name, "Order session")).append("**\n\n");
        appendOptionalLine(message, "Restaurant", restaurantName);
        message.append("- Deadline: ").append(formatDateTime(deadline)).append("\n");
        if (status != null) {
            message.append("- Status: ").append(status.name()).append("\n");
        }
        appendActionLink(message, "Open orders", "/orders");
        return sendToChannel(message.toString());
    }

    public String sendSurveyCreatedNotification(
            Long surveyId,
            String title,
            String link,
            LocalDateTime deadline
    ) {
        StringBuilder message = new StringBuilder("@all\n");
        message.append("**New survey created: ").append(valueOrFallback(title, "Untitled survey")).append("**\n\n");
        message.append("- Deadline: ").append(formatDateTime(deadline)).append("\n");
        appendOptionalLine(message, "Link", link);
        appendActionLink(message, "Open surveys", "/surveys");
        return sendToChannel(message.toString());
    }

    // ---- birthday ----

    public void sendBirthdayNotification() {
        Optional<NotificationSchedule> scheduleOpt = notificationScheduleRepository
                .findByType(NotificationScheduleType.BIRTHDAY);
        if (scheduleOpt.isEmpty() || !scheduleOpt.get().isEnabled()) return;

        NotificationSchedule schedule = scheduleOpt.get();
        LocalDate today = LocalDate.now();
        List<User> allUsers = userRepository.findByStatusOrderByTotalPointsDesc(UserStatus.ACTIVE);

        StringBuilder mentions = new StringBuilder();
        for (User user : allUsers) {
            if (user.getDob() != null
                    && user.getDob().getMonthValue() == today.getMonthValue()
                    && user.getDob().getDayOfMonth() == today.getDayOfMonth()) {
                String emailMention = user.getEmail().replace("@", "-");
                mentions.append(" @").append(emailMention);
            }
        }

        if (!mentions.isEmpty()) {
            StringBuilder message = new StringBuilder("@all\nToday is the birthday of");
            message.append(mentions).append("\nLet's send best wishes!");
            appendActionLink(message, "Open members", "/members");
            String postId = sendThreadReply(message.toString(), schedule.getChatopsPostId(), resolveScheduleChannelId(schedule));

            if (schedule.getChatopsPostId() == null && postId != null) {
                schedule.setChatopsPostId(postId);
                notificationScheduleRepository.save(schedule);
            }
        }
    }

    // ---- anniversary ----

    public void sendAnniversaryNotification() {
        Optional<NotificationSchedule> scheduleOpt = notificationScheduleRepository
                .findByType(NotificationScheduleType.ANNIVERSARY);
        if (scheduleOpt.isEmpty() || !scheduleOpt.get().isEnabled()) return;

        NotificationSchedule schedule = scheduleOpt.get();
        LocalDate today = LocalDate.now();
        List<User> allUsers = userRepository.findByStatusOrderByTotalPointsDesc(UserStatus.ACTIVE);

        StringBuilder mentions = new StringBuilder();
        for (User user : allUsers) {
            if (user.getJoinDate() != null
                    && user.getJoinDate().getMonthValue() == today.getMonthValue()
                    && user.getJoinDate().getDayOfMonth() == today.getDayOfMonth()) {
                int years = today.getYear() - user.getJoinDate().getYear();
                String emailMention = user.getEmail().replace("@", "-");
                mentions.append("Today marks ").append(years).append(" years since @")
                        .append(emailMention).append(" joined the company.\n");
            }
        }

        if (!mentions.isEmpty()) {
            mentions.insert(0, "@all\n");
            mentions.append("Thank you for being part of the team!");
            appendActionLink(mentions, "Open members", "/members");
            String postId = sendThreadReply(mentions.toString(), schedule.getChatopsPostId(), resolveScheduleChannelId(schedule));

            if (schedule.getChatopsPostId() == null && postId != null) {
                schedule.setChatopsPostId(postId);
                notificationScheduleRepository.save(schedule);
            }
        }
    }

    // ---- late penalty ----

    public LatePenaltyNotificationResult sendLatePenaltyNotification() {
        Optional<NotificationSchedule> scheduleOpt = notificationScheduleRepository
                .findByType(NotificationScheduleType.LATE);
        if (scheduleOpt.isEmpty()) {
            log.info("Late penalty notification skipped because LATE schedule is not configured");
            return new LatePenaltyNotificationResult(false, false, 0, 0, "LATE schedule is not configured.");
        }
        if (!scheduleOpt.get().isEnabled()) {
            log.info("Late penalty notification skipped because LATE schedule is disabled");
            return new LatePenaltyNotificationResult(false, false, 0, 0, "LATE schedule is disabled.");
        }

        NotificationSchedule schedule = scheduleOpt.get();
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        List<LateRecord> unpaidRecords = lateRecordRepository.findByRecordDateBetweenAndStatus(
                monthStart, monthEnd, LateRecordStatus.UNPAID);

        if (unpaidRecords.isEmpty()) {
            log.info("No unpaid late penalties for {} month", today.getMonth());
            return new LatePenaltyNotificationResult(true, false, 0, 0,
                    "No unpaid late penalties found for " + today.getMonthValue() + "/" + today.getYear() + ".");
        }

        StringBuilder table = new StringBuilder("@all\n\n");
        table.append("| Name | Unpaid times | Total fine |\n|------|--------------|------------|\n");

        var unpaidByUser = unpaidRecords.stream()
                .collect(Collectors.groupingBy(record -> resolveDisplayName(record.getUser())));
        int unpaidUserCount = unpaidByUser.size();
        unpaidByUser.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getValue().size(), left.getValue().size()))
                .forEach(entry -> {
                    int totalFine = entry.getValue().stream()
                            .map(LateRecord::getFineAmount)
                            .filter(amount -> amount != null && amount > 0)
                            .mapToInt(Integer::intValue)
                            .sum();
                    table.append("| ").append(entry.getKey()).append(" | ")
                            .append(entry.getValue().size()).append(" | ")
                            .append(formatCurrency(totalFine)).append(" |\n");
                });

        long grandTotal = unpaidRecords.stream()
                .map(LateRecord::getFineAmount)
                .filter(amount -> amount != null && amount > 0)
                .mapToLong(Integer::longValue)
                .sum();
        if (grandTotal > 0) {
            table.append("\nTotal unpaid fine: ").append(formatCurrency(grandTotal));
        }
        appendActionLink(table, "Open late records", "/late-records");

        String message = "Late penalty report " + today.getMonthValue() + "/" + today.getYear() + "\n"
                + "Danh sach di tre chua nop phat:\n"
                + table+"\n";

        String postId = sendThreadReply(message, schedule.getChatopsPostId(), resolveScheduleChannelId(schedule));
        if (postId == null) {
            log.warn("Late penalty notification failed to send. unpaidRecords={}, unpaidUsers={}",
                    unpaidRecords.size(), unpaidUserCount);
            return new LatePenaltyNotificationResult(true, false, unpaidRecords.size(), unpaidUserCount,
                    "Found unpaid late penalties, but failed to send the ChatOps message. Check output channel configuration.");
        }
        if (schedule.getChatopsPostId() == null && postId != null) {
            schedule.setChatopsPostId(postId);
            notificationScheduleRepository.save(schedule);
        }
        log.info("Late penalty notification sent. unpaidRecords={}, unpaidUsers={}, postId={}",
                unpaidRecords.size(), unpaidUserCount, postId);
        return new LatePenaltyNotificationResult(true, true, unpaidRecords.size(), unpaidUserCount,
                "Sent late penalty report for " + unpaidUserCount + " user(s), "
                        + unpaidRecords.size() + " unpaid record(s).");
    }

    public record LatePenaltyNotificationResult(
            boolean scheduleEnabled,
            boolean sent,
            int unpaidRecordCount,
            int unpaidUserCount,
            String message
    ) {
    }

    private String formatCurrency(long amount) {
        return String.format("%,d VND", amount);
    }

    private String formatDateTime(LocalDateTime value) {
        return value != null ? value.format(DATE_TIME_FORMATTER) : "TBD";
    }

    private void appendOptionalLine(StringBuilder message, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        message.append("- ").append(label).append(": ").append(value.trim()).append("\n");
    }

    private void appendActionLink(StringBuilder message, String label, String actionUrl) {
        if (actionUrl == null || actionUrl.isBlank()) {
            return;
        }
        message.append("\n[").append(label).append("](").append(resolveOpenLink(actionUrl)).append(")");
    }

    private String valueOrFallback(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= DETAILS_PREVIEW_MAX_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, DETAILS_PREVIEW_MAX_LENGTH - 3) + "...";
    }

    // ---- event reminder ----

    public void sendEventNotification() {
        Optional<NotificationSchedule> scheduleOpt = notificationScheduleRepository
                .findByType(NotificationScheduleType.EVENT);
        if (scheduleOpt.isEmpty() || !scheduleOpt.get().isEnabled()) return;

        NotificationSchedule schedule = scheduleOpt.get();
        StringBuilder message = new StringBuilder("Upcoming events - check the events page for details.");
        appendActionLink(message, "Open events", "/events");
        String postId = sendThreadReply(message.toString(), schedule.getChatopsPostId(), resolveScheduleChannelId(schedule));

        if (schedule.getChatopsPostId() == null && postId != null) {
            schedule.setChatopsPostId(postId);
            notificationScheduleRepository.save(schedule);
        }
    }

    // ---- leaderboard (Friday) ----

    public void sendLeaderboardNotification() {
        Optional<NotificationSchedule> scheduleOpt = notificationScheduleRepository
                .findByType(NotificationScheduleType.LEADERBOARD);
        if (scheduleOpt.isEmpty() || !scheduleOpt.get().isEnabled()) return;

        List<User> rankedUsers = userRepository.findByStatusAndUsernameIgnoreCaseNotOrderByTotalPointsDesc(
                UserStatus.ACTIVE,
                SystemAccountUtils.ADMIN_USERNAME
        );
        if (rankedUsers.isEmpty()) {
            log.info("No active members found for leaderboard notification");
            return;
        }

        NotificationSchedule schedule = scheduleOpt.get();
        LocalDate today = LocalDate.now();
        int limit = Math.min(MAX_LEADERBOARD_ENTRIES, rankedUsers.size());

        StringBuilder table = new StringBuilder("@all\n");
        table.append("Weekly leaderboard update - ").append(today).append("\n");
        table.append("| Rank | Name | Points |\n|------|------|--------|\n");

        for (int index = 0; index < limit; index++) {
            User user = rankedUsers.get(index);
            int points = user.getTotalPoints() != null ? user.getTotalPoints() : 0;
            table.append("| #").append(index + 1).append(" | ")
                    .append(resolveDisplayName(user)).append(" | ")
                    .append(points).append(" |\n");
        }

        if (rankedUsers.size() > limit) {
            int hiddenCount = rankedUsers.size() - limit;
            table.append("\n...and ").append(hiddenCount)
                    .append(" more members.");
        }
        appendActionLink(table, "Open leaderboard", "/leaderboard");

        String postId = sendThreadReply(table.toString(), schedule.getChatopsPostId(), resolveScheduleChannelId(schedule));
        if (schedule.getChatopsPostId() == null && postId != null) {
            schedule.setChatopsPostId(postId);
            notificationScheduleRepository.save(schedule);
        }
    }

    public AvailableMembersReportResult sendAvailableMembersReport(
            ProjectAvailabilitySummaryResponse summary,
            List<AvailableProjectMemberResponse> availableMembers
    ) {
        int availableCount = availableMembers != null ? availableMembers.size() : 0;
        LocalDate today = LocalDate.now();
        StringBuilder message = new StringBuilder();
        message.append("**Available members report - ").append(today).append("**\n\n");
        message.append("- Currently open projects: ")
                .append(summary != null ? summary.openProjectCount() : 0)
                .append("\n");
        message.append("- Available members: ").append(availableCount).append("\n\n");

        if (availableCount == 0) {
            message.append("No active available members found.");
        } else {
            message.append("| Name | Role |\n");
            message.append("|------|------|\n");
            availableMembers.forEach(member -> message
                    .append("| ").append("@").append(escapeTableValue(member.email())).append(" | ")
                    .append(escapeTableValue(member.roleName())).append(" |\n"));
        }

        String postId = sendToChannel(message.toString());
        boolean sent = postId != null;
        if (sent) {
            log.info("Available members report sent. availableMembers={}, postId={}", availableCount, postId);
        } else {
            log.warn("Available members report failed to send. availableMembers={}", availableCount);
        }
        return new AvailableMembersReportResult(sent, availableCount, postId);
    }

    public record AvailableMembersReportResult(
            boolean sent,
            int availableMemberCount,
            String postId
    ) {
    }

    private String resolveScheduleChannelId(NotificationSchedule schedule) {
        if (schedule == null) {
            return chatopsService.getOutputChannelId();
        }
        return resolveTargetChannelId(schedule.getChannelId());
    }

    private String resolveDisplayName(User user) {
        if (user == null) {
            return "Unknown";
        }
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName().replace("|", "/");
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername().replace("|", "/");
        }
        return "Unknown";
    }

    private String escapeTableValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.trim().replace("|", "/").replace("\n", " ");
    }

    private String resolveTargetChannelId(String targetChannelId) {
        if (targetChannelId != null && !targetChannelId.isBlank()) {
            return targetChannelId;
        }
        return chatopsService.getOutputChannelId();
    }

    private String prepareOutgoingMessage(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }
        Matcher matcher = RELATIVE_MARKDOWN_LINK_PATTERN.matcher(message);
        StringBuffer rewritten = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(
                    rewritten,
                    Matcher.quoteReplacement("](" + resolveOpenLink(matcher.group(1)) + ")")
            );
        }
        matcher.appendTail(rewritten);
        return rewritten.toString();
    }

    private String resolveOpenLink(String actionUrl) {
        String trimmedActionUrl = actionUrl != null ? actionUrl.trim() : "";
        if (trimmedActionUrl.isBlank() || isAbsoluteUrl(trimmedActionUrl)) {
            return trimmedActionUrl;
        }
        String trimmedBaseUrl = openLinkBaseUrl != null ? openLinkBaseUrl.trim() : "";
        if (trimmedBaseUrl.isBlank()) {
            return trimmedActionUrl;
        }
        String baseUrl = stripTrailingSlash(trimmedBaseUrl);
        if (trimmedActionUrl.startsWith("/")) {
            return baseUrl + trimmedActionUrl;
        }
        return baseUrl + "/" + trimmedActionUrl;
    }

    private boolean isAbsoluteUrl(String value) {
        try {
            return URI.create(value).isAbsolute();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private void logChatMessage(String action, String channelId, String postId, SystemLogStatus status, String rootPostId) {
        logChatMessage(action, channelId, postId, status, rootPostId, null);
    }

    private void logChatMessage(String action, String channelId, String postId, SystemLogStatus status, String rootPostId, Throwable failure) {
        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("channelId", channelId);
        details.put("postId", postId);
        details.put("rootPostId", rootPostId);

        systemLogService.log(new SystemLogCreateRequest(
                SystemLogCategory.MESSAGE,
                failure == null && status != SystemLogStatus.FAILED ? SystemLogSeverity.INFO : SystemLogSeverity.ERROR,
                status,
                action,
                "ChatOps",
                SystemLogContext.getActorUsername(),
                SystemLogContext.getCorrelationId(),
                "ChatOpsChannel",
                channelId,
                null,
                action + " " + status.name().toLowerCase(),
                details,
                failure != null ? failure.getClass().getName() : null,
                SystemLogSanitizer.stackTrace(failure)
        ));
    }
}
