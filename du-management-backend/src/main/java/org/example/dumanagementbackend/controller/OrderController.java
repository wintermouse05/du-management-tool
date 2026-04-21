package org.example.dumanagementbackend.controller;

import org.example.dumanagementbackend.dto.order.MenuItemRequest;
import org.example.dumanagementbackend.dto.order.MenuItemResponse;
import org.example.dumanagementbackend.dto.order.OrderSessionRequest;
import org.example.dumanagementbackend.dto.order.OrderSessionResponse;
import org.example.dumanagementbackend.dto.order.UserOrderRequest;
import org.example.dumanagementbackend.dto.order.UserOrderResponse;
import org.example.dumanagementbackend.entity.enums.OrderSessionStatus;
import org.example.dumanagementbackend.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    public ResponseEntity<MenuItemResponse> createMenuItem(@Valid @RequestBody MenuItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createMenuItem(request));
    }

    @GetMapping("/menu-items")
    public ResponseEntity<Page<MenuItemResponse>> getMenuItems(Pageable pageable) {
        return ResponseEntity.ok(orderService.getMenuItems(pageable));
    }

    @PostMapping("/sessions")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<OrderSessionResponse> createSession(@Valid @RequestBody OrderSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createSession(request));
    }

    @GetMapping("/sessions")
    public ResponseEntity<Page<OrderSessionResponse>> getSessions(Pageable pageable) {
        return ResponseEntity.ok(orderService.getSessions(pageable));
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
    public ResponseEntity<UserOrderResponse> placeOrder(@Valid @RequestBody UserOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(request));
    }

    @GetMapping("/user-orders")
    public ResponseEntity<Page<UserOrderResponse>> getOrdersBySession(@RequestParam Long sessionId, Pageable pageable) {
        return ResponseEntity.ok(orderService.getOrdersBySession(sessionId, pageable));
    }

    @PatchMapping("/user-orders/paid")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<UserOrderResponse> markPaid(@RequestParam Long orderId, @RequestParam boolean paid) {
        return ResponseEntity.ok(orderService.markPaid(orderId, paid));
    }
}
