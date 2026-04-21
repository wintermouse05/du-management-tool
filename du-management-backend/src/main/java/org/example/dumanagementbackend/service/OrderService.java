package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.dto.order.MenuItemRequest;
import org.example.dumanagementbackend.dto.order.MenuItemResponse;
import org.example.dumanagementbackend.dto.order.OrderSessionRequest;
import org.example.dumanagementbackend.dto.order.OrderSessionResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final MenuItemRepository menuItemRepository;
    private final OrderSessionRepository orderSessionRepository;
    private final UserOrderRepository userOrderRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public MenuItemResponse createMenuItem(MenuItemRequest request) {
        MenuItem item = new MenuItem();
        item.setName(request.name());
        item.setPrice(request.price());
        return toMenuItemResponse(menuItemRepository.save(item));
    }

    public Page<MenuItemResponse> getMenuItems(Pageable pageable) {
        return menuItemRepository.findAll(pageable).map(this::toMenuItemResponse);
    }

    @Transactional
    public OrderSessionResponse createSession(OrderSessionRequest request) {
        OrderSession session = new OrderSession();
        session.setStatus(request.status() != null ? request.status() : OrderSessionStatus.OPEN);
        session.setDeadline(request.deadline());
        OrderSessionResponse response = toSessionResponse(orderSessionRepository.save(session));
        messagingTemplate.convertAndSend("/topic/orders", "SESSION_CREATED");
        return response;
    }

    public Page<OrderSessionResponse> getSessions(Pageable pageable) {
        return orderSessionRepository.findAll(pageable).map(this::toSessionResponse);
    }

    @Transactional
    public OrderSessionResponse updateSessionStatus(Long id, OrderSessionStatus status) {
        OrderSession session = orderSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order session not found with id=" + id));
        session.setStatus(status);
        OrderSessionResponse response = toSessionResponse(orderSessionRepository.save(session));
        messagingTemplate.convertAndSend("/topic/orders", "SESSION_UPDATED");
        return response;
    }

    @Transactional
    public UserOrderResponse placeOrder(UserOrderRequest request) {
        OrderSession session = orderSessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Order session not found with id=" + request.sessionId()));
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id=" + request.userId()));
        MenuItem item = menuItemRepository.findById(request.itemId())
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found with id=" + request.itemId()));

        UserOrder order = new UserOrder();
        order.setSession(session);
        order.setUser(user);
        order.setItem(item);
        order.setQuantity(request.quantity());
        order.setNote(request.note());
        order.setPaid(Boolean.TRUE.equals(request.paid()));
        
        UserOrderResponse response = toUserOrderResponse(userOrderRepository.save(order));
        messagingTemplate.convertAndSend("/topic/orders", "ORDER_PLACED");
        return response;
    }

    public Page<UserOrderResponse> getOrdersBySession(Long sessionId, Pageable pageable) {
        return userOrderRepository.findBySessionId(sessionId, pageable).map(this::toUserOrderResponse);
    }

    @Transactional
    public UserOrderResponse markPaid(Long orderId, boolean paid) {
        UserOrder order = userOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id=" + orderId));
        order.setPaid(paid);
        UserOrderResponse response = toUserOrderResponse(userOrderRepository.save(order));
        messagingTemplate.convertAndSend("/topic/orders", "ORDER_UPDATED");
        return response;
    }

    private MenuItemResponse toMenuItemResponse(MenuItem item) {
        return new MenuItemResponse(item.getId(), item.getName(), item.getPrice());
    }

    private OrderSessionResponse toSessionResponse(OrderSession session) {
        return new OrderSessionResponse(session.getId(), session.getStatus(), session.getDeadline());
    }

    private UserOrderResponse toUserOrderResponse(UserOrder order) {
        return new UserOrderResponse(
                order.getId(),
                order.getSession().getId(),
                order.getUser().getId(),
                order.getUser().getFullName(),
                order.getItem().getId(),
                order.getItem().getName(),
                order.getQuantity(),
                order.getNote(),
                order.isPaid()
        );
    }
}
