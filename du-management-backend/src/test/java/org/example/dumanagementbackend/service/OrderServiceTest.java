package org.example.dumanagementbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.example.dumanagementbackend.dto.order.UserOrderRequest;
import org.example.dumanagementbackend.dto.order.UserOrderResponse;
import org.example.dumanagementbackend.entity.MenuItem;
import org.example.dumanagementbackend.entity.OrderSession;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.UserOrder;
import org.example.dumanagementbackend.entity.enums.OrderSessionStatus;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.MenuItemRepository;
import org.example.dumanagementbackend.repository.OrderSessionRepository;
import org.example.dumanagementbackend.repository.UserOrderRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private OrderSessionRepository orderSessionRepository;

    @Mock
    private UserOrderRepository userOrderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private OrderService orderService;

    @Test
    void placeOrder_setsPaidFalseWhenRequestPaidIsNull() {
        OrderSession session = buildSession(1L, OrderSessionStatus.OPEN);
        User user = buildUser(2L, "Order User");
        MenuItem item = buildItem(3L, "Pho");

        when(orderSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(menuItemRepository.findById(3L)).thenReturn(Optional.of(item));
        when(userOrderRepository.save(any(UserOrder.class))).thenAnswer(invocation -> {
            UserOrder order = invocation.getArgument(0);
            order.setId(50L);
            return order;
        });

        UserOrderRequest request = new UserOrderRequest(1L, 2L, 3L, 2, "less spicy", null);

        UserOrderResponse response = orderService.placeOrder(request);

        assertEquals(50L, response.id());
        assertEquals("Order User", response.fullName());
        assertEquals("Pho", response.itemName());
        assertFalse(response.paid());
    }

    @Test
    void updateSessionStatus_throwsWhenSessionNotFound() {
        when(orderSessionRepository.findById(9L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> orderService.updateSessionStatus(9L, OrderSessionStatus.CLOSED)
        );

        assertEquals("Order session not found with id=9", ex.getMessage());
    }

    @Test
    void markPaid_updatesPaidStatus() {
        OrderSession session = buildSession(1L, OrderSessionStatus.OPEN);
        User user = buildUser(2L, "Mark Paid User");
        MenuItem item = buildItem(3L, "Bun Bo");

        UserOrder existing = new UserOrder();
        existing.setId(77L);
        existing.setSession(session);
        existing.setUser(user);
        existing.setItem(item);
        existing.setQuantity(1);
        existing.setNote("note");
        existing.setPaid(false);

        when(userOrderRepository.findById(77L)).thenReturn(Optional.of(existing));
        when(userOrderRepository.save(any(UserOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserOrderResponse response = orderService.markPaid(77L, true);

        assertTrue(existing.isPaid());
        assertTrue(response.paid());
        assertEquals(77L, response.id());
    }

    @Test
    void getOrdersBySession_returnsMappedPage() {
        Pageable pageable = PageRequest.of(0, 5);

        OrderSession session = buildSession(1L, OrderSessionStatus.OPEN);
        User user = buildUser(2L, "Paged User");
        MenuItem item = buildItem(3L, "Com Tam");

        UserOrder order = new UserOrder();
        order.setId(88L);
        order.setSession(session);
        order.setUser(user);
        order.setItem(item);
        order.setQuantity(3);
        order.setNote("extra");
        order.setPaid(true);

        Page<UserOrder> page = new PageImpl<>(List.of(order), pageable, 1);
        when(userOrderRepository.findBySessionId(1L, pageable)).thenReturn(page);

        Page<UserOrderResponse> result = orderService.getOrdersBySession(1L, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("Com Tam", result.getContent().get(0).itemName());
        assertTrue(result.getContent().get(0).paid());
    }

    private OrderSession buildSession(Long id, OrderSessionStatus status) {
        OrderSession session = new OrderSession();
        session.setId(id);
        session.setStatus(status);
        session.setDeadline(LocalDateTime.now().plusHours(1));
        return session;
    }

    private User buildUser(Long id, String fullName) {
        User user = new User();
        user.setId(id);
        user.setFullName(fullName);
        return user;
    }

    private MenuItem buildItem(Long id, String name) {
        MenuItem item = new MenuItem();
        item.setId(id);
        item.setName(name);
        item.setPrice(BigDecimal.valueOf(30000));
        return item;
    }
}
