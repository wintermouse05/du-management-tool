package org.example.dumanagementbackend.service;

import java.util.List;

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

    public List<NotificationSchedule> getAll() {
        return repository.findAll();
    }

    public NotificationSchedule getByType(NotificationScheduleType type) {
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
        schedule.setSendTime(input.getSendTime());
        schedule.setChannelId(input.getChannelId());
        schedule.setEnabled(input.isEnabled());
        return repository.save(schedule);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
