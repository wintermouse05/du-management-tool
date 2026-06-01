package org.example.dumanagementbackend.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderSessionAutoCloseScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderSessionAutoCloseScheduler.class);

    private final OrderService orderService;

    @Scheduled(fixedDelayString = "${app.order.session.auto-close-ms:60000}")
    public void closeExpiredOpenSessions() {
        int closedCount = orderService.closeExpiredOpenSessions(LocalDateTime.now());
        if (closedCount > 0) {
            LOGGER.info("Auto-closed {} expired order session(s)", closedCount);
        }
    }
}
