package org.example.dumanagementbackend.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.NotificationScheduleType;
import org.example.dumanagementbackend.repository.NotificationScheduleRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import org.example.dumanagementbackend.entity.NotificationSchedule;
import org.example.dumanagementbackend.entity.enums.UserStatus;
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
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");

    private final ChatopsService chatopsService;
    private final UserRepository userRepository;
    private final NotificationScheduleRepository notificationScheduleRepository;

    @Value("${chatops.channel-id}")
    private String channelId;

    // ---- basic sending ----

    public String sendToChannel(String message) {
        return chatopsService.sendMessageWithResponse(channelId, message, null);
    }

    public String sendThreadReply(String message, String rootPostId) {
        return chatopsService.sendMessageWithResponse(channelId, message, rootPostId);
    }

    public void sendDirectMessage(String senderEmail, String receiverEmail, String message) {
        try {
            var receiver = chatopsService.getUserByEmail(receiverEmail);
            String receiverId = (String) receiver.get("id");

            var sender = chatopsService.getUserByEmail(senderEmail);
            String senderId = (String) sender.get("id");

            String dmChannelId = chatopsService.getDirectChannelId(senderId, receiverId);
            chatopsService.sendMessage(dmChannelId, message);
        } catch (Exception e) {
            log.error("Failed to send DM from {} to {}: {}", senderEmail, receiverEmail, e.getMessage());
        }
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
            String message = "@all\n🎂 Today is the birthday of" + mentions + " 🎉\n"
                    + "Let's send them our best wishes! :tada:";
            String postId = sendThreadReply(message, schedule.getChatopsPostId());

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
                mentions.append("🎉 Today marks ").append(years).append(" years since @")
                        .append(emailMention).append(" joined the company!\n");
            }
        }

        if (!mentions.isEmpty()) {
            mentions.insert(0, "@all\n");
            mentions.append("Thank you for being part of the team! ❤️");
            String postId = sendThreadReply(mentions.toString(), schedule.getChatopsPostId());

            if (schedule.getChatopsPostId() == null && postId != null) {
                schedule.setChatopsPostId(postId);
                notificationScheduleRepository.save(schedule);
            }
        }
    }

    // ---- late penalty ----

    public void sendLatePenaltyNotification() {
        Optional<NotificationSchedule> scheduleOpt = notificationScheduleRepository
                .findByType(NotificationScheduleType.LATE);
        if (scheduleOpt.isEmpty() || !scheduleOpt.get().isEnabled()) return;

        NotificationSchedule schedule = scheduleOpt.get();
        LocalDate today = LocalDate.now();
        List<Object[]> repeatUsers = userRepository.findRepeatLateOffendersInMonth(
                today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()));

        if (repeatUsers.isEmpty()) {
            log.info("No repeat late offenders for {} month", today.getMonth());
            return;
        }

        StringBuilder table = new StringBuilder("@all\n");
        table.append("| Name | Late count |\n|------|------------|\n");
        for (Object[] row : repeatUsers) {
            String fullName = (String) row[0];
            long count = ((Number) row[1]).longValue();
            table.append("| ").append(fullName).append(" | ").append(count).append(" |\n");
        }

        String message = "📋 **Late penalty report for month " + today.getMonthValue() + "/" + today.getYear() + "**\n"
                + "The following members had 2+ late check-ins this month:\n"
                + table;

        String postId = sendThreadReply(message, schedule.getChatopsPostId());
        if (schedule.getChatopsPostId() == null && postId != null) {
            schedule.setChatopsPostId(postId);
            notificationScheduleRepository.save(schedule);
        }
    }

    // ---- event reminder ----

    public void sendEventNotification() {
        Optional<NotificationSchedule> scheduleOpt = notificationScheduleRepository
                .findByType(NotificationScheduleType.EVENT);
        if (scheduleOpt.isEmpty() || !scheduleOpt.get().isEnabled()) return;

        NotificationSchedule schedule = scheduleOpt.get();
        String message = "📅 **Upcoming events** — check the events page for details: /events";
        String postId = sendThreadReply(message, schedule.getChatopsPostId());

        if (schedule.getChatopsPostId() == null && postId != null) {
            schedule.setChatopsPostId(postId);
            notificationScheduleRepository.save(schedule);
        }
    }
}
