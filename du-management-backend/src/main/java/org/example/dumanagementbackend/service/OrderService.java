package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.dto.order.MenuItemResponse;
import org.example.dumanagementbackend.dto.order.MenuScrapeItemResponse;
import org.example.dumanagementbackend.dto.order.OrderItemSummaryResponse;
import org.example.dumanagementbackend.dto.order.OrderSessionRequest;
import org.example.dumanagementbackend.dto.order.OrderSessionResponse;
import org.example.dumanagementbackend.dto.order.OrderSessionSummaryResponse;
import org.example.dumanagementbackend.dto.order.RestaurantRequest;
import org.example.dumanagementbackend.dto.order.RestaurantResponse;
import org.example.dumanagementbackend.dto.order.UserOrderBulkRequest;
import org.example.dumanagementbackend.dto.order.UserOrderRequest;
import org.example.dumanagementbackend.dto.order.UserOrderResponse;
import org.example.dumanagementbackend.dto.order.UserOrderUpdateRequest;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OrderService {

    private final MenuItemRepository menuItemRepository;
    private final OrderSessionRepository orderSessionRepository;
    private final UserOrderRepository userOrderRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final MenuScraperService menuScraperService;
    private final SimpMessagingTemplate messagingTemplate;
    private final OrderSessionClosureService orderSessionClosureService;

    @Autowired(required = false)
    private ChatopsNotificationService chatopsNotificationService;

    // ==================== Restaurant ====================

    @Transactional
    public RestaurantResponse saveRestaurant(RestaurantRequest request) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(request.name());
        restaurant.setScrapeUrl(request.scrapeUrl());
        restaurant = restaurantRepository.save(restaurant);

        // Scrape and persist initial menu items
        List<MenuScrapeItemResponse> scraped = menuScraperService.scrape(request.scrapeUrl()).items();
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
        return restaurantRepository.findByDeletedAtIsNull().stream()
                .map(this::toRestaurantResponse)
                .toList();
    }

    @Transactional
    public void deleteRestaurant(Long id) {
        Restaurant restaurant = restaurantRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found with id=" + id));

        if (orderSessionRepository.existsByRestaurant_IdAndStatus(id, OrderSessionStatus.OPEN)) {
            throw new BadRequestException("Cannot archive restaurant while it has open order sessions.");
        }

        List<MenuItem> items = menuItemRepository.findByRestaurantIdAndDeletedAtIsNull(id);
        items.forEach(SoftDeleteUtils::markDeleted);
        menuItemRepository.saveAll(items);
        SoftDeleteUtils.markDeleted(restaurant);
        restaurantRepository.save(restaurant);
    }

    @Transactional
    public List<MenuItemResponse> getMenuByRestaurant(Long restaurantId) {
        restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found with id=" + restaurantId));
        return getMenuItemsByRestaurant(restaurantId);
    }

    // ==================== Menu Items (read-only for ordering) ====================

    public List<MenuItemResponse> getMenuItemsByRestaurant(Long restaurantId) {
        return menuItemRepository.findByRestaurantIdAndDeletedAtIsNull(restaurantId).stream()
                .map(this::toMenuItemResponse)
                .toList();
    }

    // ==================== Sessions ====================

    @Transactional
    public OrderSessionResponse createSession(OrderSessionRequest request) {
        Restaurant restaurant = restaurantRepository.findByIdAndDeletedAtIsNull(request.restaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found with id=" + request.restaurantId()));

        OrderSession session = new OrderSession();
        session.setStatus(request.status() != null ? request.status() : OrderSessionStatus.OPEN);
        session.setName(request.name().trim());
        session.setRestaurant(restaurant);
        session.setDeadline(request.deadline());
        OrderSession saved = orderSessionRepository.save(session);
        OrderSessionResponse response = toSessionResponse(saved, resolveUserDisplayNames(singletonUsername(saved.getCreatedBy())));
        triggerOrderSessionCreatedNotification(saved);
        messagingTemplate.convertAndSend("/topic/orders", "SESSION_CREATED");
        return response;
    }

    public Page<OrderSessionResponse> getSessions(Pageable pageable) {
        Pageable resolvedPageable = withDefaultSort(pageable, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<OrderSession> page = orderSessionRepository.findAll(resolvedPageable);
        Map<String, String> displayNames = resolveUserDisplayNames(page.getContent().stream()
                .map(OrderSession::getCreatedBy)
                .collect(Collectors.toSet()));
        List<OrderSessionResponse> content = page.getContent().stream()
                .map(session -> toSessionResponse(session, displayNames))
                .toList();
        return new PageImpl<>(content, resolvedPageable, page.getTotalElements());
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
    public OrderSessionResponse updateSessionStatus(Long id, OrderSessionStatus status, LocalDateTime deadline) {
        OrderSession session = orderSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order session not found with id=" + id));
        ensureCanManageSession(session);
        validateAndApplyReopenDeadline(session, status, deadline);
        session.setStatus(status);
        OrderSession saved = orderSessionRepository.save(session);
        OrderSessionResponse response = toSessionResponse(saved, resolveUserDisplayNames(singletonUsername(saved.getCreatedBy())));
        messagingTemplate.convertAndSend("/topic/orders", "SESSION_UPDATED");
        return response;
    }

    @Transactional
    public int closeExpiredOpenSessions(LocalDateTime now) {
        LocalDateTime cutoff = now != null ? now : LocalDateTime.now();
        List<OrderSession> expiredSessions = orderSessionRepository.findByStatusAndDeadlineLessThanEqual(
                OrderSessionStatus.OPEN,
                cutoff
        );
        if (expiredSessions.isEmpty()) {
            return 0;
        }

        expiredSessions.forEach(session -> session.setStatus(OrderSessionStatus.CLOSED));
        orderSessionRepository.saveAll(expiredSessions);
        messagingTemplate.convertAndSend("/topic/orders", "SESSION_UPDATED");
        return expiredSessions.size();
    }

    // ==================== User Orders ====================

    @Transactional
    @CacheEvict(cacheNames = "orderSessionSummary", key = "#request.sessionId")
    public UserOrderResponse placeOrder(UserOrderRequest request) {
        OrderSession session = requireOpenSession(request.sessionId());
        MenuItem item = requireMenuItemForSession(request.itemId(), session);
        User user = requireUser(request.userId());
        ensureUserHasNoOrder(session.getId(), user.getId());
        UserOrder saved = userOrderRepository.save(buildOrderEntity(session, user, item, request.quantity(), request.note()));
        UserOrderResponse response = toUserOrderResponse(saved, resolveUserDisplayNames(singletonUsername(saved.getCreatedBy())));
        messagingTemplate.convertAndSend("/topic/orders", "ORDER_PLACED");
        return response;
    }

    @Transactional
    @CacheEvict(cacheNames = "orderSessionSummary", key = "#request.sessionId")
    public List<UserOrderResponse> placeOrdersForUsers(UserOrderBulkRequest request) {
        OrderSession session = requireOpenSession(request.sessionId());
        MenuItem item = requireMenuItemForSession(request.itemId(), session);

        List<Long> uniqueUserIds = request.userIds().stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (uniqueUserIds.isEmpty()) {
            throw new BadRequestException("userIds must contain valid user ids");
        }

        List<User> users = userRepository.findAllById(uniqueUserIds);
        if (users.size() != uniqueUserIds.size()) {
            throw new BadRequestException("Some userIds do not exist");
        }
        Map<Long, User> userById = users.stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        ensureUsersHaveNoOrders(session.getId(), uniqueUserIds);

        List<UserOrder> savedOrders = new ArrayList<>();
        for (Long userId : uniqueUserIds) {
            User user = userById.get(userId);
            UserOrder saved = userOrderRepository.save(
                    buildOrderEntity(session, user, item, request.quantity(), request.note())
            );
            savedOrders.add(saved);
        }

        Map<String, String> displayNames = resolveUserDisplayNames(savedOrders.stream()
                .map(UserOrder::getCreatedBy)
                .collect(Collectors.toSet()));
        List<UserOrderResponse> responses = savedOrders.stream()
                .map(order -> toUserOrderResponse(order, displayNames))
                .toList();

        messagingTemplate.convertAndSend("/topic/orders", "ORDER_PLACED");
        return responses;
    }

    @Transactional
    @CacheEvict(cacheNames = "orderSessionSummary", key = "#result.sessionId", condition = "#result != null")
    public UserOrderResponse updateOrder(Long orderId, UserOrderUpdateRequest request) {
        UserOrder order = requireOrder(orderId);
        ensureCanManageOrder(order);
        ensureOrderSessionOpen(order);
        MenuItem item = requireMenuItemForSession(request.itemId(), order.getSession());

        order.setItem(item);
        order.setQuantity(request.quantity());
        order.setNote(request.note());
        UserOrder saved = userOrderRepository.save(order);
        UserOrderResponse response = toUserOrderResponse(saved, resolveUserDisplayNames(singletonUsername(saved.getCreatedBy())));
        messagingTemplate.convertAndSend("/topic/orders", "ORDER_UPDATED");
        return response;
    }

    @Transactional
    @CacheEvict(cacheNames = "orderSessionSummary", key = "#result", condition = "#result != null")
    public Long cancelOrder(Long orderId) {
        UserOrder order = requireOrder(orderId);
        ensureCanManageOrder(order);
        ensureOrderSessionOpen(order);
        Long sessionId = order.getSession().getId();

        userOrderRepository.delete(order);
        messagingTemplate.convertAndSend("/topic/orders", "ORDER_CANCELLED");
        return sessionId;
    }

    public Page<UserOrderResponse> getOrdersBySession(Long sessionId, Pageable pageable) {
        Pageable resolvedPageable = withDefaultSort(pageable, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<UserOrder> page = userOrderRepository.findBySessionId(sessionId, resolvedPageable);
        Map<String, String> displayNames = resolveUserDisplayNames(page.getContent().stream()
                .map(UserOrder::getCreatedBy)
                .collect(Collectors.toSet()));
        List<UserOrderResponse> content = page.getContent().stream()
                .map(order -> toUserOrderResponse(order, displayNames))
                .toList();
        return new PageImpl<>(content, resolvedPageable, page.getTotalElements());
    }

    @Transactional
    @CacheEvict(cacheNames = "orderSessionSummary", key = "#result.sessionId", condition = "#result != null")
    public UserOrderResponse markPaid(Long orderId, boolean paid) {
        UserOrder order = userOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id=" + orderId));
        ensureAdminCanManagePaidStatus();
        order.setPaid(paid);
        UserOrder saved = userOrderRepository.save(order);
        UserOrderResponse response = toUserOrderResponse(saved, resolveUserDisplayNames(singletonUsername(saved.getCreatedBy())));
        messagingTemplate.convertAndSend("/topic/orders", "ORDER_UPDATED");
        return response;
    }

    // ==================== Helpers ====================

    private BigDecimal parsePrice(String priceStr) {
        if (priceStr == null || priceStr.isBlank()) {
            return BigDecimal.ZERO;
        }
        String cleaned = priceStr.replaceAll("[^0-9.,]", "");
        if (cleaned.isEmpty()) {
            return BigDecimal.ZERO;
        }
        // Vietnamese format: dot as thousands separator (e.g. "20.000" = 20000)
        if (cleaned.matches("\\d{1,3}(\\.\\d{3})+")) {
            cleaned = cleaned.replace(".", "");
        } else if (cleaned.matches("\\d{1,3}(,\\d{3})+")) {
            cleaned = cleaned.replace(",", "");
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

    private OrderSessionResponse toSessionResponse(OrderSession session, Map<String, String> displayNames) {
        String creatorUsername = session.getCreatedBy();
        return new OrderSessionResponse(
                session.getId(),
                sessionName(session),
                session.getStatus(),
                session.getDeadline(),
                session.getRestaurant() != null ? session.getRestaurant().getId() : null,
                session.getRestaurant() != null ? session.getRestaurant().getName() : null,
                displayNames.getOrDefault(creatorUsername, fallbackDisplayName(creatorUsername)),
                creatorUsername,
                canManageSession(session),
                session.getCreatedAt()
        );
    }

    private UserOrderResponse toUserOrderResponse(UserOrder order, Map<String, String> displayNames) {
        String orderedByUsername = order.getCreatedBy();
        String orderedByFullName = null;
        if (orderedByUsername != null
                && order.getUser() != null
                && order.getUser().getUsername() != null
                && !orderedByUsername.equalsIgnoreCase(order.getUser().getUsername())) {
            orderedByFullName = displayNames.getOrDefault(orderedByUsername, fallbackDisplayName(orderedByUsername));
        }

        return new UserOrderResponse(
                order.getId(),
                order.getSession().getId(),
                sessionName(order.getSession()),
                order.getSession().getStatus(),
                order.getUser().getId(),
                order.getUser().getFullName(),
                orderedByFullName,
                order.getItem().getId(),
                order.getItem().getName(),
                order.getItem().getPrice(),
                order.getQuantity(),
                order.getNote(),
                order.isPaid(),
                canManageOrder(order)
        );
    }

    private Pageable withDefaultSort(Pageable pageable, Sort defaultSort) {
        if (pageable == null) {
            return PageRequest.of(0, 20, defaultSort);
        }
        if (pageable.isUnpaged()) {
            return pageable;
        }

        Pageable resolvedPageable = PaginationUtils.toZeroBasedPageable(pageable);
        return resolvedPageable.getSort().isSorted()
                ? resolvedPageable
                : PageRequest.of(resolvedPageable.getPageNumber(), resolvedPageable.getPageSize(), defaultSort);
    }

    private Map<String, String> resolveUserDisplayNames(Set<String> usernames) {
        Set<String> cleanedUsernames = usernames.stream()
                .filter(username -> username != null && !username.isBlank() && !"system".equalsIgnoreCase(username))
                .collect(Collectors.toSet());
        if (cleanedUsernames.isEmpty()) {
            return Map.of();
        }

        Map<String, String> result = new HashMap<>();
        userRepository.findByUsernameIn(cleanedUsernames)
                .forEach(user -> result.put(user.getUsername(), user.getFullName()));
        return result;
    }

    private Set<String> singletonUsername(String username) {
        if (username == null || username.isBlank()) {
            return Set.of();
        }
        return Set.of(username);
    }

    private boolean canManageSession(OrderSession session) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        boolean elevated = authentication.getAuthorities().stream().anyMatch(authority ->
                "ROLE_ADMIN".equals(authority.getAuthority()) || "ROLE_HR".equals(authority.getAuthority()));
        if (elevated) {
            return true;
        }
        return session != null
                && session.getCreatedBy() != null
                && session.getCreatedBy().equalsIgnoreCase(authentication.getName());
    }

    private void ensureCanManageSession(OrderSession session) {
        if (!canManageSession(session)) {
            throw new AccessDeniedException("You do not have permission to manage this order session.");
        }
    }

    private boolean canManageOrder(UserOrder order) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (isCurrentUserAdmin()) {
            return true;
        }

        String username = authentication.getName();
        String orderedForUsername = order != null && order.getUser() != null ? order.getUser().getUsername() : null;
        String orderedByUsername = order != null ? order.getCreatedBy() : null;
        return username != null
                && ((orderedForUsername != null && username.equalsIgnoreCase(orderedForUsername))
                || (orderedByUsername != null && username.equalsIgnoreCase(orderedByUsername)));
    }

    private void ensureCanManageOrder(UserOrder order) {
        if (!canManageOrder(order)) {
            throw new AccessDeniedException("You do not have permission to manage this order.");
        }
    }

    private boolean isCurrentUserAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(authority ->
                "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private void ensureAdminCanManagePaidStatus() {
        if (!isCurrentUserAdmin()) {
            throw new AccessDeniedException("Only admins can update paid status.");
        }
    }

    private String sessionName(OrderSession session) {
        if (session.getName() != null && !session.getName().isBlank()) {
            return session.getName();
        }
        return "Session #" + session.getId();
    }

    private String fallbackDisplayName(String username) {
        if (username == null || username.isBlank()) {
            return "Unknown";
        }
        return username;
    }

    private OrderSession requireOpenSession(Long sessionId) {
        OrderSession session = orderSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Order session not found with id=" + sessionId));
        if (session.getStatus() == OrderSessionStatus.OPEN && isPastDeadline(session, LocalDateTime.now())) {
            orderSessionClosureService.closeExpiredOpenSession(session.getId());
            throw new BadRequestException(
                    "ORDER_SESSION_PAST_DEADLINE",
                    "Session is past deadline. This session has been automatically closed."
            );
        }
        if (session.getStatus() != OrderSessionStatus.OPEN) {
            throw new BadRequestException("Cannot place order because this session is not open.");
        }
        return session;
    }

    private MenuItem requireMenuItemForSession(Long itemId, OrderSession session) {
        MenuItem item = menuItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found with id=" + itemId));

        if (session.getRestaurant() != null
                && item.getRestaurant() != null
                && !session.getRestaurant().getId().equals(item.getRestaurant().getId())) {
            throw new BadRequestException("Selected menu item does not belong to this order session.");
        }
        return item;
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id=" + userId));
    }

    private UserOrder requireOrder(Long orderId) {
        return userOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id=" + orderId));
    }

    private void ensureUserHasNoOrder(Long sessionId, Long userId) {
        if (userOrderRepository.existsBySessionIdAndUserId(sessionId, userId)) {
            throw new BadRequestException("Each user can only have one order in this session.");
        }
    }

    private void ensureUsersHaveNoOrders(Long sessionId, List<Long> userIds) {
        List<UserOrder> existingOrders = userOrderRepository.findBySessionIdAndUserIdIn(sessionId, userIds);
        if (!existingOrders.isEmpty()) {
            throw new BadRequestException("Each user can only have one order in this session.");
        }
    }

    private void ensureOrderSessionOpen(UserOrder order) {
        OrderSession session = order.getSession();
        if (session.getStatus() == OrderSessionStatus.OPEN && isPastDeadline(session, LocalDateTime.now())) {
            orderSessionClosureService.closeExpiredOpenSession(session.getId());
            throw new BadRequestException(
                    "ORDER_SESSION_PAST_DEADLINE",
                    "Session is past deadline. This session has been automatically closed."
            );
        }
        if (session.getStatus() != OrderSessionStatus.OPEN) {
            throw new BadRequestException("Cannot change order because this session is not open.");
        }
    }

    private UserOrder buildOrderEntity(OrderSession session, User user, MenuItem item, Integer quantity, String note) {
        UserOrder order = new UserOrder();
        order.setSession(session);
        order.setUser(user);
        order.setItem(item);
        order.setQuantity(quantity);
        order.setNote(note);
        // Paid status is managed only via markPaid (admin-only path).
        order.setPaid(false);
        return order;
    }

    private void validateAndApplyReopenDeadline(OrderSession session, OrderSessionStatus status, LocalDateTime proposedDeadline) {
        if (status != OrderSessionStatus.OPEN) {
            if (proposedDeadline != null) {
                throw new BadRequestException("deadline can only be updated when reopening a session.");
            }
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (proposedDeadline != null && !proposedDeadline.isAfter(now)) {
            throw new BadRequestException("New deadline must be in the future.");
        }

        boolean expired = session.getDeadline() != null && !session.getDeadline().isAfter(now);
        if (expired && proposedDeadline == null) {
            throw new BadRequestException("Session is past deadline. Please provide a new deadline to reopen.");
        }

        if (proposedDeadline != null) {
            session.setDeadline(proposedDeadline);
            session.setDeadlineReminderSentAt(null);
        }
    }

    private boolean isPastDeadline(OrderSession session, LocalDateTime now) {
        return session.getDeadline() != null && !session.getDeadline().isAfter(now);
    }

    private void triggerOrderSessionCreatedNotification(OrderSession session) {
        ChatopsNotificationService notifier = chatopsNotificationService;
        if (notifier == null || session == null) {
            return;
        }

        Long sessionId = session.getId();
        String name = sessionName(session);
        String restaurantName = session.getRestaurant() != null ? session.getRestaurant().getName() : null;
        LocalDateTime deadline = session.getDeadline();
        OrderSessionStatus status = session.getStatus();
        dispatchAfterCommit(
                "Order session created ChatOps notification",
                () -> notifier.sendOrderSessionCreatedNotification(sessionId, name, restaurantName, deadline, status)
        );
    }

    private void dispatchAfterCommit(String taskName, Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(() -> runBestEffort(taskName, task));
                }
            });
            return;
        }

        runBestEffort(taskName, task);
    }

    private void runBestEffort(String taskName, Runnable task) {
        try {
            task.run();
        } catch (Exception ex) {
            log.warn("{} failed: {}", taskName, ex.getMessage());
        }
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
