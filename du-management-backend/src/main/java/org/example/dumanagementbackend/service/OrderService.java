package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.dto.order.MenuItemResponse;
import org.example.dumanagementbackend.dto.order.MenuScrapeItemResponse;
import org.example.dumanagementbackend.dto.order.OrderItemSummaryResponse;
import org.example.dumanagementbackend.dto.order.OrderSessionRequest;
import org.example.dumanagementbackend.dto.order.OrderSessionResponse;
import org.example.dumanagementbackend.dto.order.OrderSessionSummaryResponse;
import org.example.dumanagementbackend.dto.order.RestaurantRequest;
import org.example.dumanagementbackend.dto.order.RestaurantResponse;
import org.example.dumanagementbackend.dto.order.UserOrderRequest;
import org.example.dumanagementbackend.dto.order.UserOrderResponse;
import org.example.dumanagementbackend.entity.MenuItem;
import org.example.dumanagementbackend.entity.OrderSession;
import org.example.dumanagementbackend.entity.Restaurant;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.UserOrder;
import org.example.dumanagementbackend.entity.enums.OrderSessionStatus;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.MenuItemRepository;
import org.example.dumanagementbackend.repository.OrderSessionRepository;
import org.example.dumanagementbackend.repository.RestaurantRepository;
import org.example.dumanagementbackend.repository.UserOrderRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
    private final RestaurantRepository restaurantRepository;
    private final MenuScraperService menuScraperService;
    private final SimpMessagingTemplate messagingTemplate;

    // ==================== Restaurant ====================

    @Transactional
    public RestaurantResponse saveRestaurant(RestaurantRequest request) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(request.name());
        restaurant.setScrapeUrl(request.scrapeUrl());
        restaurant = restaurantRepository.save(restaurant);

        // Scrape and persist initial menu items
        List<MenuScrapeItemResponse> scraped = menuScraperService.scrape(request.scrapeUrl());
        for (MenuScrapeItemResponse item : scraped) {
            MenuItem menuItem = new MenuItem();
            menuItem.setRestaurant(restaurant);
            menuItem.setName(item.name());
            menuItem.setPrice(parsePrice(item.price()));
            menuItem.setDescription(item.description());
            menuItemRepository.save(menuItem);
        }

        return toRestaurantResponse(restaurant);
    }

    public List<RestaurantResponse> getRestaurants() {
        return restaurantRepository.findAll().stream()
                .map(this::toRestaurantResponse)
                .toList();
    }

    @Transactional
    public void deleteRestaurant(Long id) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found with id=" + id));

        List<MenuItem> items = menuItemRepository.findByRestaurantId(id);
        for (MenuItem item : items) {
            if (userOrderRepository.existsByItemId(item.getId())) {
                throw new BadRequestException("Cannot delete restaurant because some menu items have existing orders.");
            }
        }

        menuItemRepository.deleteByRestaurantId(id);
        restaurantRepository.delete(restaurant);
    }

    /**
     * Re-scrapes the restaurant's URL and syncs menu items:
     * - Existing items matched by name: update price/description
     * - New items: insert
     * - Removed items (no longer in scrape result): delete if not referenced by orders
     */
    @Transactional
    public List<MenuItemResponse> getMenuByRestaurant(Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found with id=" + restaurantId));

        List<MenuScrapeItemResponse> scraped = menuScraperService.scrape(restaurant.getScrapeUrl());

        // Index existing items by name for upsert
        List<MenuItem> existingItems = menuItemRepository.findByRestaurantId(restaurantId);
        Map<String, MenuItem> existingByName = existingItems.stream()
                .collect(Collectors.toMap(MenuItem::getName, Function.identity(), (a, b) -> a));

        Set<String> scrapedNames = new HashSet<>();
        List<MenuItem> finalItems = new ArrayList<>();

        // Upsert: update existing or insert new
        for (MenuScrapeItemResponse scrapeItem : scraped) {
            scrapedNames.add(scrapeItem.name());
            MenuItem existing = existingByName.get(scrapeItem.name());
            if (existing != null) {
                existing.setPrice(parsePrice(scrapeItem.price()));
                existing.setDescription(scrapeItem.description());
                finalItems.add(menuItemRepository.save(existing));
            } else {
                MenuItem newItem = new MenuItem();
                newItem.setRestaurant(restaurant);
                newItem.setName(scrapeItem.name());
                newItem.setPrice(parsePrice(scrapeItem.price()));
                newItem.setDescription(scrapeItem.description());
                finalItems.add(menuItemRepository.save(newItem));
            }
        }

        // Remove items no longer in the scraped list (only if not referenced by orders)
        for (MenuItem existing : existingItems) {
            if (!scrapedNames.contains(existing.getName())) {
                if (!userOrderRepository.existsByItemId(existing.getId())) {
                    menuItemRepository.delete(existing);
                }
                // If referenced by orders, keep it (won't appear in finalItems though)
            }
        }

        return finalItems.stream().map(this::toMenuItemResponse).toList();
    }

    // ==================== Menu Items (read-only for ordering) ====================

    public List<MenuItemResponse> getMenuItemsByRestaurant(Long restaurantId) {
        return menuItemRepository.findByRestaurantId(restaurantId).stream()
                .map(this::toMenuItemResponse)
                .toList();
    }

    // ==================== Sessions ====================

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

    @Cacheable(cacheNames = "orderSessionSummary", key = "#sessionId")
    public OrderSessionSummaryResponse getSessionSummary(Long sessionId) {
        orderSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Order session not found with id=" + sessionId));

        List<UserOrder> orders = userOrderRepository.findBySessionId(sessionId);
        Map<Long, SummaryAccumulator> summaryByItem = new LinkedHashMap<>();
        int totalQuantity = 0;
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (UserOrder order : orders) {
            BigDecimal lineTotal = order.getItem().getPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
            totalQuantity += order.getQuantity();
            grandTotal = grandTotal.add(lineTotal);

            SummaryAccumulator acc = summaryByItem.computeIfAbsent(order.getItem().getId(), ignored ->
                    new SummaryAccumulator(order.getItem().getId(), order.getItem().getName(), order.getItem().getPrice()));
            acc.totalQuantity += order.getQuantity();
            acc.totalAmount = acc.totalAmount.add(lineTotal);
        }

        List<OrderItemSummaryResponse> items = new ArrayList<>();
        for (SummaryAccumulator acc : summaryByItem.values()) {
            items.add(new OrderItemSummaryResponse(
                    acc.itemId,
                    acc.itemName,
                    acc.unitPrice,
                    acc.totalQuantity,
                    acc.totalAmount
            ));
        }

        return new OrderSessionSummaryResponse(
                sessionId,
                orders.size(),
                totalQuantity,
                grandTotal,
                items
        );
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

    // ==================== User Orders ====================

    @Transactional
    @CacheEvict(cacheNames = "orderSessionSummary", key = "#request.sessionId")
    public UserOrderResponse placeOrder(UserOrderRequest request) {
        OrderSession session = orderSessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Order session not found with id=" + request.sessionId()));
                
        if (session.getStatus() != OrderSessionStatus.OPEN) {
            throw new BadRequestException("Cannot place order because this session is not open.");
        }
        
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
    @CacheEvict(cacheNames = "orderSessionSummary", key = "#result.sessionId", condition = "#result != null")
    public UserOrderResponse markPaid(Long orderId, boolean paid) {
        UserOrder order = userOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id=" + orderId));
        order.setPaid(paid);
        UserOrderResponse response = toUserOrderResponse(userOrderRepository.save(order));
        messagingTemplate.convertAndSend("/topic/orders", "ORDER_UPDATED");
        return response;
    }

    // ==================== Helpers ====================

    private BigDecimal parsePrice(String priceStr) {
        if (priceStr == null || priceStr.isBlank()) {
            return BigDecimal.ZERO;
        }
        String cleaned = priceStr.replaceAll("[^0-9.]", "");
        if (cleaned.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private RestaurantResponse toRestaurantResponse(Restaurant restaurant) {
        return new RestaurantResponse(restaurant.getId(), restaurant.getName(), restaurant.getScrapeUrl());
    }

    private MenuItemResponse toMenuItemResponse(MenuItem item) {
        return new MenuItemResponse(
                item.getId(),
                item.getName(),
                item.getPrice(),
                item.getDescription(),
                item.getRestaurant().getId()
        );
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

    private static class SummaryAccumulator {
        private final Long itemId;
        private final String itemName;
        private final BigDecimal unitPrice;
        private int totalQuantity;
        private BigDecimal totalAmount = BigDecimal.ZERO;

        private SummaryAccumulator(Long itemId, String itemName, BigDecimal unitPrice) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.unitPrice = unitPrice;
        }
    }
}
