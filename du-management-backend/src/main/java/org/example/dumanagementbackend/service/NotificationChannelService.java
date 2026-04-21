package org.example.dumanagementbackend.service;

import java.util.List;

import org.example.dumanagementbackend.dto.notification.NotificationChannelRequest;
import org.example.dumanagementbackend.dto.notification.NotificationChannelResponse;
import org.example.dumanagementbackend.entity.NotificationChannel;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.NotificationChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationChannelService {

    private final NotificationChannelRepository notificationChannelRepository;

    public List<NotificationChannelResponse> getChannels() {
        return notificationChannelRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NotificationChannelResponse createChannel(NotificationChannelRequest request) {
        NotificationChannel channel = new NotificationChannel();
        apply(channel, request);
        return toResponse(notificationChannelRepository.save(channel));
    }

    @Transactional
    public NotificationChannelResponse updateChannel(Long id, NotificationChannelRequest request) {
        NotificationChannel channel = notificationChannelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification channel not found with id=" + id));
        apply(channel, request);
        return toResponse(notificationChannelRepository.save(channel));
    }

    @Transactional
    public void deleteChannel(Long id) {
        NotificationChannel channel = notificationChannelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification channel not found with id=" + id));
        notificationChannelRepository.delete(channel);
    }

    private void apply(NotificationChannel channel, NotificationChannelRequest request) {
        channel.setType(request.type());
        channel.setEndpoint(request.endpoint().trim());
        channel.setEnabled(Boolean.TRUE.equals(request.enabled()));
    }

    private NotificationChannelResponse toResponse(NotificationChannel channel) {
        return new NotificationChannelResponse(channel.getId(), channel.getType(), channel.getEndpoint(), channel.isEnabled());
    }
}
