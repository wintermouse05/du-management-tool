package org.example.dumanagementbackend.controller;

import org.example.dumanagementbackend.dto.order.MenuItemRequest;
import org.example.dumanagementbackend.dto.order.MenuItemResponse;
import org.example.dumanagementbackend.dto.order.OrderSessionRequest;
import org.example.dumanagementbackend.dto.order.OrderSessionResponse;
import org.example.dumanagementbackend.dto.order.UserOrderRequest;
import org.example.dumanagementbackend.dto.order.UserOrderResponse;
import org.example.dumanagementbackend.entity.enums.OrderSessionStatus;
import org.example.dumanagementbackend.service.OrderService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HR','MEMBER')")
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/menu-items")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<MenuItemResponse> createMenuItem(@RequestBody MenuItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createMenuItem(request));
    }

    @GetMapping("/menu-items")
    public ResponseEntity<List<MenuItemResponse>> getMenuItems() {
        return ResponseEntity.ok(orderService.getMenuItems());
    }

    @PostMapping("/sessions")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<OrderSessionResponse> createSession(@RequestBody OrderSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createSession(request));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<OrderSessionResponse>> getSessions() {
        return ResponseEntity.ok(orderService.getSessions());
    }

    @PatchMapping("/sessions/status")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<OrderSessionResponse> updateSessionStatus(
            @RequestParam Long sessionId,
            @RequestParam OrderSessionStatus status
    ) {
        return ResponseEntity.ok(orderService.updateSessionStatus(sessionId, status));
    }

    @PostMapping("/user-orders")
    public ResponseEntity<UserOrderResponse> placeOrder(@RequestBody UserOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(request));
    }

    @GetMapping("/user-orders")
    public ResponseEntity<List<UserOrderResponse>> getOrdersBySession(@RequestParam Long sessionId) {
        return ResponseEntity.ok(orderService.getOrdersBySession(sessionId));
    }

    @PatchMapping("/user-orders/paid")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<UserOrderResponse> markPaid(@RequestParam Long orderId, @RequestParam boolean paid) {
        return ResponseEntity.ok(orderService.markPaid(orderId, paid));
    }
}
