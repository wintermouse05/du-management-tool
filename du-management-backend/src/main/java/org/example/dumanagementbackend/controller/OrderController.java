package org.example.dumanagementbackend.controller;

import org.example.dumanagementbackend.dto.order.MenuItemResponse;
import org.example.dumanagementbackend.dto.order.MenuScrapeRequest;
import org.example.dumanagementbackend.dto.order.MenuScrapeResponse;
import org.example.dumanagementbackend.dto.order.OrderSessionSummaryResponse;
import org.example.dumanagementbackend.dto.order.OrderSessionRequest;
import org.example.dumanagementbackend.dto.order.OrderSessionResponse;
import org.example.dumanagementbackend.dto.order.RestaurantRequest;
import org.example.dumanagementbackend.dto.order.RestaurantResponse;
import org.example.dumanagementbackend.dto.order.UserOrderBulkRequest;
import org.example.dumanagementbackend.dto.order.UserOrderRequest;
import org.example.dumanagementbackend.dto.order.UserOrderResponse;
import org.example.dumanagementbackend.dto.order.UserOrderUpdateRequest;
import org.example.dumanagementbackend.entity.enums.OrderSessionStatus;
import org.example.dumanagementbackend.service.MenuScraperService;
import org.example.dumanagementbackend.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HR','MEMBER')")
public class OrderController {

    private final OrderService orderService;
    private final MenuScraperService menuScraperService;

    // ==================== Restaurants ====================

    @PostMapping("/restaurants")
    public ResponseEntity<RestaurantResponse> saveRestaurant(@Valid @RequestBody RestaurantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.saveRestaurant(request));
    }

    @GetMapping("/restaurants")
    public ResponseEntity<List<RestaurantResponse>> getRestaurants() {
        return ResponseEntity.ok(orderService.getRestaurants());
    }

    @DeleteMapping("/restaurants/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<Void> deleteRestaurant(@PathVariable Long id) {
        orderService.deleteRestaurant(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/restaurants/{id}/menu")
    public ResponseEntity<List<MenuItemResponse>> getRestaurantMenu(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getMenuByRestaurant(id));
    }

    // ==================== Scrape (preview only) ====================

    @PostMapping("/scrape-menu")
    public ResponseEntity<MenuScrapeResponse> scrapeMenu(@Valid @RequestBody MenuScrapeRequest request) {
        return ResponseEntity.ok(menuScraperService.scrape(request.url()));
    }

    // ==================== Sessions ====================

    @PostMapping("/sessions")
    public ResponseEntity<OrderSessionResponse> createSession(@Valid @RequestBody OrderSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createSession(request));
    }

    @GetMapping("/sessions")
    public ResponseEntity<Page<OrderSessionResponse>> getSessions(Pageable pageable) {
        return ResponseEntity.ok(orderService.getSessions(pageable));
    }

    @GetMapping("/sessions/{sessionId}/summary")
    public ResponseEntity<OrderSessionSummaryResponse> getSessionSummary(@PathVariable Long sessionId) {
        return ResponseEntity.ok(orderService.getSessionSummary(sessionId));
    }

    @PatchMapping("/sessions/status")
    public ResponseEntity<OrderSessionResponse> updateSessionStatus(
            @RequestParam Long sessionId,
            @RequestParam OrderSessionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime deadline
    ) {
        return ResponseEntity.ok(orderService.updateSessionStatus(sessionId, status, deadline));
    }

    // ==================== User Orders ====================

    @PostMapping("/user-orders")
    public ResponseEntity<UserOrderResponse> placeOrder(@Valid @RequestBody UserOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(request));
    }

    @PostMapping("/user-orders/bulk")
    public ResponseEntity<List<UserOrderResponse>> placeOrdersForUsers(@Valid @RequestBody UserOrderBulkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrdersForUsers(request));
    }

    @GetMapping("/user-orders")
    public ResponseEntity<Page<UserOrderResponse>> getOrdersBySession(@RequestParam Long sessionId, Pageable pageable) {
        return ResponseEntity.ok(orderService.getOrdersBySession(sessionId, pageable));
    }

    @PatchMapping("/user-orders/{orderId}")
    public ResponseEntity<UserOrderResponse> updateOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody UserOrderUpdateRequest request
    ) {
        return ResponseEntity.ok(orderService.updateOrder(orderId, request));
    }

    @DeleteMapping("/user-orders/{orderId}")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long orderId) {
        orderService.cancelOrder(orderId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/user-orders/paid")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserOrderResponse> markPaid(@RequestParam Long orderId, @RequestParam boolean paid) {
        return ResponseEntity.ok(orderService.markPaid(orderId, paid));
    }
}
