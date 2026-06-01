package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.entity.OrderSession;
import org.example.dumanagementbackend.entity.enums.OrderSessionStatus;
import org.example.dumanagementbackend.repository.OrderSessionRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderSessionClosureService {

    private final OrderSessionRepository orderSessionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean closeExpiredOpenSession(Long sessionId) {
        OrderSession session = orderSessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getStatus() != OrderSessionStatus.OPEN) {
            return false;
        }

        session.setStatus(OrderSessionStatus.CLOSED);
        orderSessionRepository.save(session);
        messagingTemplate.convertAndSend("/topic/orders", "SESSION_UPDATED");
        return true;
    }
}
