package org.example.dumanagementbackend.service;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.example.dumanagementbackend.entity.NotificationSchedule;
import org.example.dumanagementbackend.entity.enums.NotificationScheduleType;
import org.example.dumanagementbackend.repository.NotificationScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationScheduleService {

    private final NotificationScheduleRepository repository;

    @Transactional
    public List<NotificationSchedule> getAll() {
        ensureDefaultSchedules();
        return repository.findAll().stream()
                .sorted(Comparator.comparing(NotificationSchedule::getType))
                .toList();
    }

    @Transactional
    public NotificationSchedule getByType(NotificationScheduleType type) {
        ensureDefaultSchedules();
        return repository.findByType(type)
                .orElseThrow(() -> new RuntimeException("Schedule not found for type: " + type));
    }

    @Transactional
    public NotificationSchedule upsert(NotificationScheduleType type, NotificationSchedule input) {
        NotificationSchedule schedule = repository.findByType(type)
                .orElseGet(() -> {
                    NotificationSchedule ns = new NotificationSchedule();
                    ns.setType(type);
                    return ns;
                });
        String existingChannelId = normalizeChannelId(schedule.getChannelId());
        String incomingChannelId = normalizeChannelId(input.getChannelId());
        if (!Objects.equals(existingChannelId, incomingChannelId)) {
            // Existing root post ID may belong to another channel; force a new thread root.
            schedule.setChatopsPostId(null);
        }
        schedule.setSendTime(input.getSendTime());
        schedule.setChannelId(incomingChannelId);
        schedule.setEnabled(input.isEnabled());
        return repository.save(schedule);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Transactional
    public void ensureDefaultSchedules() {
        Arrays.stream(NotificationScheduleType.values())
                .forEach(this::ensureScheduleExists);
    }

    private void ensureScheduleExists(NotificationScheduleType type) {
        if (repository.findByType(type).isPresent()) {
            return;
        }

        NotificationSchedule schedule = new NotificationSchedule();
        schedule.setType(type);
        schedule.setSendTime(getDefaultSendTime(type));
        schedule.setChannelId("");
        schedule.setEnabled(type == NotificationScheduleType.LEADERBOARD);
        repository.save(schedule);
    }

    private String normalizeChannelId(String channelId) {
        if (channelId == null) {
            return null;
        }
        String trimmed = channelId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LocalTime getDefaultSendTime(NotificationScheduleType type) {
        return switch (type) {
            case EVENT -> LocalTime.of(8, 0);
            case BIRTHDAY -> LocalTime.of(9, 5);
            case ANNIVERSARY -> LocalTime.of(9, 10);
            case LATE -> LocalTime.of(11, 0);
            case LEADERBOARD -> LocalTime.of(16, 0);
        };
    }
}
