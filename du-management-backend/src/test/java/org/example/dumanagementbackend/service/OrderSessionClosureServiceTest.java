package org.example.dumanagementbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;

import org.example.dumanagementbackend.entity.OrderSession;
import org.example.dumanagementbackend.entity.enums.OrderSessionStatus;
import org.example.dumanagementbackend.repository.OrderSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class OrderSessionClosureServiceTest {

    @Mock
    private OrderSessionRepository orderSessionRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private OrderSessionClosureService orderSessionClosureService;

    @Test
    void closeExpiredOpenSession_usesNewTransaction() throws NoSuchMethodException {
        Method method = OrderSessionClosureService.class.getMethod("closeExpiredOpenSession", Long.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }

    @Test
    void closeExpiredOpenSession_closesOpenSession() {
        OrderSession session = buildSession(1L, OrderSessionStatus.OPEN);
        when(orderSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(orderSessionRepository.save(any(OrderSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean closed = orderSessionClosureService.closeExpiredOpenSession(1L);

        assertTrue(closed);
        assertEquals(OrderSessionStatus.CLOSED, session.getStatus());
        verify(orderSessionRepository).save(session);
        verify(messagingTemplate).convertAndSend("/topic/orders", "SESSION_UPDATED");
    }

    @Test
    void closeExpiredOpenSession_skipsAlreadyClosedSession() {
        OrderSession session = buildSession(1L, OrderSessionStatus.CLOSED);
        when(orderSessionRepository.findById(1L)).thenReturn(Optional.of(session));

        boolean closed = orderSessionClosureService.closeExpiredOpenSession(1L);

        assertEquals(false, closed);
        verify(orderSessionRepository, never()).save(any(OrderSession.class));
        verifyNoInteractions(messagingTemplate);
    }

    private OrderSession buildSession(Long id, OrderSessionStatus status) {
        OrderSession session = new OrderSession();
        session.setId(id);
        session.setStatus(status);
        session.setCreatedBy("admin");
        session.setName("Lunch");
        session.setDeadline(LocalDateTime.now().minusMinutes(1));
        return session;
    }
}
