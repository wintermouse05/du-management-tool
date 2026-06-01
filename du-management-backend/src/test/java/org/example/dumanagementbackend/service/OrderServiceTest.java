package org.example.dumanagementbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.example.dumanagementbackend.dto.order.RestaurantResponse;
import org.example.dumanagementbackend.dto.order.OrderSessionRequest;
import org.example.dumanagementbackend.dto.order.OrderSessionResponse;
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
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

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
    private RestaurantRepository restaurantRepository;

    @Mock
    private MenuScraperService menuScraperService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private OrderSessionClosureService orderSessionClosureService;

    @Mock
    private ChatopsNotificationService chatopsNotificationService;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void injectOptionalServices() {
        ReflectionTestUtils.setField(orderService, "chatopsNotificationService", chatopsNotificationService);
    }

    @Test
    void createSession_sendsChatopsNotification() {
        LocalDateTime deadline = LocalDateTime.now().plusHours(2);
        Restaurant restaurant = buildRestaurant(4L, "Test Cafe", "https://example.com/menu");
        OrderSession saved = buildSession(9L, OrderSessionStatus.OPEN);
        saved.setName("Team lunch");
        saved.setRestaurant(restaurant);
        saved.setDeadline(deadline);

        when(restaurantRepository.findByIdAndDeletedAtIsNull(4L)).thenReturn(Optional.of(restaurant));
        when(orderSessionRepository.save(any(OrderSession.class))).thenReturn(saved);
        when(userRepository.findByUsernameIn(any())).thenReturn(List.of());

        OrderSessionResponse response = orderService.createSession(
                new OrderSessionRequest(OrderSessionStatus.OPEN, "Team lunch", 4L, deadline)
        );

        assertEquals(9L, response.id());
        assertEquals("Team lunch", response.name());
        verify(chatopsNotificationService).sendOrderSessionCreatedNotification(
                eq(9L),
                eq("Team lunch"),
                eq("Test Cafe"),
                eq(deadline),
                eq(OrderSessionStatus.OPEN)
        );
        verify(messagingTemplate).convertAndSend("/topic/orders", "SESSION_CREATED");
    }

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
    void placeOrder_ignoresPaidFlagFromRequest() {
        OrderSession session = buildSession(1L, OrderSessionStatus.OPEN);
        User user = buildUser(2L, "Order User");
        MenuItem item = buildItem(3L, "Pho");

        when(orderSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(menuItemRepository.findById(3L)).thenReturn(Optional.of(item));
        when(userOrderRepository.save(any(UserOrder.class))).thenAnswer(invocation -> {
            UserOrder order = invocation.getArgument(0);
            order.setId(51L);
            return order;
        });

        UserOrderRequest request = new UserOrderRequest(1L, 2L, 3L, 1, null, true);

        UserOrderResponse response = orderService.placeOrder(request);

        assertEquals(51L, response.id());
        assertFalse(response.paid());
    }

    @Test
    void placeOrdersForUsers_createsOneOrderPerUser() {
        OrderSession session = buildSession(1L, OrderSessionStatus.OPEN);
        User firstUser = buildUser(2L, "First User");
        User secondUser = buildUser(3L, "Second User");
        MenuItem item = buildItem(4L, "Banh Mi");

        when(orderSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(menuItemRepository.findById(4L)).thenReturn(Optional.of(item));
        when(userRepository.findAllById(List.of(2L, 3L))).thenReturn(List.of(firstUser, secondUser));
        when(userOrderRepository.findBySessionIdAndUserIdIn(1L, List.of(2L, 3L))).thenReturn(List.of());
        final long[] sequence = {200L};
        when(userOrderRepository.save(any(UserOrder.class))).thenAnswer(invocation -> {
            UserOrder order = invocation.getArgument(0);
            order.setId(sequence[0]++);
            return order;
        });

        List<UserOrderResponse> responses = orderService.placeOrdersForUsers(
                new UserOrderBulkRequest(1L, List.of(2L, 3L), 4L, 1, "no onions", null)
        );

        assertEquals(2, responses.size());
        assertEquals(List.of(2L, 3L), responses.stream().map(UserOrderResponse::userId).toList());
        assertTrue(responses.stream().allMatch(response -> !response.paid()));
    }

    @Test
    void placeOrder_rejectsDuplicateOrderForUserInSameSession() {
        OrderSession session = buildSession(1L, OrderSessionStatus.OPEN);
        User user = buildUser(2L, "Order User");
        MenuItem item = buildItem(3L, "Pho");

        when(orderSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(menuItemRepository.findById(3L)).thenReturn(Optional.of(item));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userOrderRepository.existsBySessionIdAndUserId(1L, 2L)).thenReturn(true);

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> orderService.placeOrder(new UserOrderRequest(1L, 2L, 3L, 1, null, null))
        );

        assertEquals("Each user can only have one order in this session.", ex.getMessage());
        verify(userOrderRepository, never()).save(any(UserOrder.class));
    }

    @Test
    void placeOrdersForUsers_rejectsAnyUserWhoAlreadyHasAnOrder() {
        OrderSession session = buildSession(1L, OrderSessionStatus.OPEN);
        User firstUser = buildUser(2L, "First User");
        User secondUser = buildUser(3L, "Second User");
        MenuItem item = buildItem(4L, "Banh Mi");
        UserOrder existingOrder = buildOrder(90L, session, firstUser, item, "other");

        when(orderSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(menuItemRepository.findById(4L)).thenReturn(Optional.of(item));
        when(userRepository.findAllById(List.of(2L, 3L))).thenReturn(List.of(firstUser, secondUser));
        when(userOrderRepository.findBySessionIdAndUserIdIn(1L, List.of(2L, 3L))).thenReturn(List.of(existingOrder));

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> orderService.placeOrdersForUsers(new UserOrderBulkRequest(1L, List.of(2L, 3L), 4L, 1, null, null))
        );

        assertEquals("Each user can only have one order in this session.", ex.getMessage());
        verify(userOrderRepository, never()).save(any(UserOrder.class));
    }

    @Test
    void updateSessionStatus_throwsWhenSessionNotFound() {
        when(orderSessionRepository.findById(9L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> orderService.updateSessionStatus(9L, OrderSessionStatus.CLOSED, null)
        );

        assertEquals("Order session not found with id=9", ex.getMessage());
    }

    @Test
    void updateSessionStatus_reopenExpiredSession_requiresNewDeadline() {
        OrderSession session = buildSession(1L, OrderSessionStatus.CLOSED);
        session.setDeadline(LocalDateTime.now().minusMinutes(10));
        session.setCreatedBy(null);
        when(orderSessionRepository.findById(1L)).thenReturn(Optional.of(session));

        TestingAuthenticationToken managerAuth = new TestingAuthenticationToken("admin", "password", "ROLE_ADMIN");
        managerAuth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(managerAuth);

        BadRequestException ex;
        try {
            ex = assertThrows(
                    BadRequestException.class,
                    () -> orderService.updateSessionStatus(1L, OrderSessionStatus.OPEN, null)
            );
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertEquals("Session is past deadline. Please provide a new deadline to reopen.", ex.getMessage());
    }

    @Test
    void updateSessionStatus_reopenExpiredSession_updatesDeadlineWhenProvided() {
        OrderSession session = buildSession(1L, OrderSessionStatus.CLOSED);
        session.setDeadline(LocalDateTime.now().minusMinutes(10));
        session.setDeadlineReminderSentAt(LocalDateTime.now().minusMinutes(20));
        when(orderSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(orderSessionRepository.save(any(OrderSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findByUsernameIn(any())).thenReturn(List.of());

        LocalDateTime newDeadline = LocalDateTime.now().plusHours(2);
        TestingAuthenticationToken managerAuth = new TestingAuthenticationToken("admin", "password", "ROLE_ADMIN");
        managerAuth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(managerAuth);

        try {
            orderService.updateSessionStatus(1L, OrderSessionStatus.OPEN, newDeadline);
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertEquals(OrderSessionStatus.OPEN, session.getStatus());
        assertEquals(newDeadline, session.getDeadline());
        assertNull(session.getDeadlineReminderSentAt());
    }

    @Test
    void closeExpiredOpenSessions_closesOpenSessionsPastDeadline() {
        LocalDateTime now = LocalDateTime.now();
        OrderSession first = buildSession(1L, OrderSessionStatus.OPEN);
        first.setDeadline(now.minusMinutes(1));
        OrderSession second = buildSession(2L, OrderSessionStatus.OPEN);
        second.setDeadline(now);
        when(orderSessionRepository.findByStatusAndDeadlineLessThanEqual(OrderSessionStatus.OPEN, now))
                .thenReturn(List.of(first, second));
        when(orderSessionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        int closedCount = orderService.closeExpiredOpenSessions(now);

        assertEquals(2, closedCount);
        assertEquals(OrderSessionStatus.CLOSED, first.getStatus());
        assertEquals(OrderSessionStatus.CLOSED, second.getStatus());
        verify(orderSessionRepository).saveAll(List.of(first, second));
        verify(messagingTemplate).convertAndSend("/topic/orders", "SESSION_UPDATED");
    }

    @Test
    void getRestaurants_returnsOnlyRepositoryActiveRestaurants() {
        Restaurant restaurant = buildRestaurant(4L, "Test Cafe", "https://example.com/menu");
        when(restaurantRepository.findByDeletedAtIsNull()).thenReturn(List.of(restaurant));

        List<RestaurantResponse> responses = orderService.getRestaurants();

        assertEquals(1, responses.size());
        assertEquals("Test Cafe", responses.get(0).name());
        verify(restaurantRepository).findByDeletedAtIsNull();
    }

    @Test
    void deleteRestaurant_archivesRestaurantAndMenuItemsWhenNoOpenSessionExists() {
        Restaurant restaurant = buildRestaurant(4L, "Test Cafe", "https://example.com/menu");
        MenuItem firstItem = buildItem(11L, "Pho");
        MenuItem secondItem = buildItem(12L, "Banh Mi");
        firstItem.setRestaurant(restaurant);
        secondItem.setRestaurant(restaurant);

        when(restaurantRepository.findByIdAndDeletedAtIsNull(4L)).thenReturn(Optional.of(restaurant));
        when(orderSessionRepository.existsByRestaurant_IdAndStatus(4L, OrderSessionStatus.OPEN)).thenReturn(false);
        when(menuItemRepository.findByRestaurantIdAndDeletedAtIsNull(4L)).thenReturn(List.of(firstItem, secondItem));

        orderService.deleteRestaurant(4L);

        assertTrue(restaurant.isDeleted());
        assertTrue(firstItem.isDeleted());
        assertTrue(secondItem.isDeleted());
        verify(menuItemRepository).saveAll(List.of(firstItem, secondItem));
        verify(restaurantRepository).save(restaurant);
        verify(restaurantRepository, never()).delete(restaurant);
        verify(menuItemRepository, never()).deleteByRestaurantId(4L);
    }

    @Test
    void deleteRestaurant_rejectsRestaurantWithOpenSessions() {
        Restaurant restaurant = buildRestaurant(4L, "Test Cafe", "https://example.com/menu");
        when(restaurantRepository.findByIdAndDeletedAtIsNull(4L)).thenReturn(Optional.of(restaurant));
        when(orderSessionRepository.existsByRestaurant_IdAndStatus(4L, OrderSessionStatus.OPEN)).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> orderService.deleteRestaurant(4L));

        assertEquals("Cannot archive restaurant while it has open order sessions.", ex.getMessage());
        assertFalse(restaurant.isDeleted());
        verify(restaurantRepository, never()).save(any(Restaurant.class));
        verify(menuItemRepository, never()).saveAll(anyList());
    }

    @Test
    void placeOrder_closesExpiredOpenSessionAndRejectsOrder() {
        OrderSession session = buildSession(1L, OrderSessionStatus.OPEN);
        session.setDeadline(LocalDateTime.now().minusMinutes(1));
        when(orderSessionRepository.findById(1L)).thenReturn(Optional.of(session));

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> orderService.placeOrder(new UserOrderRequest(1L, 2L, 3L, 1, null, null))
        );

        assertEquals("ORDER_SESSION_PAST_DEADLINE", ex.getErrorCode());
        assertEquals(OrderSessionStatus.OPEN, session.getStatus());
        verify(orderSessionClosureService).closeExpiredOpenSession(1L);
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

        TestingAuthenticationToken adminAuth = new TestingAuthenticationToken("admin", "password", "ROLE_ADMIN");
        adminAuth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(adminAuth);

        UserOrderResponse response;
        try {
            response = orderService.markPaid(77L, true);
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertTrue(existing.isPaid());
        assertTrue(response.paid());
        assertEquals(77L, response.id());
    }

    @Test
    void updateOrder_allowsCreatorToEditOrderPlacedForSomeoneElse() {
        OrderSession session = buildSession(1L, OrderSessionStatus.OPEN);
        User targetUser = buildUser(2L, "Target User", "target");
        User actorUser = buildUser(5L, "Actor User", "actor");
        MenuItem oldItem = buildItem(3L, "Pho");
        MenuItem newItem = buildItem(4L, "Bun Bo");
        UserOrder order = buildOrder(77L, session, targetUser, oldItem, "actor");

        when(userOrderRepository.findById(77L)).thenReturn(Optional.of(order));
        when(menuItemRepository.findById(4L)).thenReturn(Optional.of(newItem));
        when(userOrderRepository.save(any(UserOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findByUsernameIn(any())).thenReturn(List.of(actorUser));

        TestingAuthenticationToken actorAuth = new TestingAuthenticationToken("actor", "password", "ROLE_MEMBER");
        actorAuth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(actorAuth);

        UserOrderResponse response;
        try {
            response = orderService.updateOrder(77L, new UserOrderUpdateRequest(4L, 2, "less spicy"));
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertEquals("Bun Bo", response.itemName());
        assertEquals(2, response.quantity());
        assertEquals("less spicy", response.note());
        assertTrue(response.canManage());
        verify(userOrderRepository).save(order);
        verify(messagingTemplate).convertAndSend("/topic/orders", "ORDER_UPDATED");
    }

    @Test
    void cancelOrder_allowsTargetUserToCancelOrderCreatedBySomeoneElse() {
        OrderSession session = buildSession(1L, OrderSessionStatus.OPEN);
        User targetUser = buildUser(2L, "Target User", "target");
        MenuItem item = buildItem(3L, "Pho");
        UserOrder order = buildOrder(77L, session, targetUser, item, "actor");

        when(userOrderRepository.findById(77L)).thenReturn(Optional.of(order));

        TestingAuthenticationToken targetAuth = new TestingAuthenticationToken("target", "password", "ROLE_MEMBER");
        targetAuth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(targetAuth);

        Long sessionId;
        try {
            sessionId = orderService.cancelOrder(77L);
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertEquals(1L, sessionId);
        verify(userOrderRepository).delete(order);
        verify(messagingTemplate).convertAndSend("/topic/orders", "ORDER_CANCELLED");
    }

    @Test
    void updateOrder_rejectsUnrelatedUser() {
        OrderSession session = buildSession(1L, OrderSessionStatus.OPEN);
        User targetUser = buildUser(2L, "Target User", "target");
        MenuItem item = buildItem(3L, "Pho");
        UserOrder order = buildOrder(77L, session, targetUser, item, "actor");

        when(userOrderRepository.findById(77L)).thenReturn(Optional.of(order));

        TestingAuthenticationToken unrelatedAuth = new TestingAuthenticationToken("other", "password", "ROLE_MEMBER");
        unrelatedAuth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(unrelatedAuth);

        try {
            assertThrows(
                    AccessDeniedException.class,
                    () -> orderService.updateOrder(77L, new UserOrderUpdateRequest(3L, 1, null))
            );
        } finally {
            SecurityContextHolder.clearContext();
        }
        verify(userOrderRepository, never()).save(any(UserOrder.class));
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
        when(userOrderRepository.findBySessionId(org.mockito.ArgumentMatchers.eq(1L), any(Pageable.class))).thenReturn(page);

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
        session.setCreatedBy("admin");
        return session;
    }

    private User buildUser(Long id, String fullName) {
        return buildUser(id, fullName, fullName.toLowerCase().replace(" ", "."));
    }

    private User buildUser(Long id, String fullName, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPassword("password");
        user.setFullName(fullName);
        return user;
    }

    private UserOrder buildOrder(Long id, OrderSession session, User user, MenuItem item, String createdBy) {
        UserOrder order = new UserOrder();
        order.setId(id);
        order.setSession(session);
        order.setUser(user);
        order.setItem(item);
        order.setQuantity(1);
        order.setNote("note");
        order.setPaid(false);
        order.setCreatedBy(createdBy);
        return order;
    }

    private Restaurant buildRestaurant(Long id, String name, String url) {
        Restaurant r = new Restaurant();
        r.setId(id);
        r.setName(name);
        r.setScrapeUrl(url);
        return r;
    }

    private MenuItem buildItem(Long id, String name) {
        Restaurant restaurant = buildRestaurant(1L, "Test Restaurant", "https://example.com");
        MenuItem item = new MenuItem();
        item.setId(id);
        item.setName(name);
        item.setPrice(BigDecimal.valueOf(30000));
        item.setRestaurant(restaurant);
        return item;
    }
}
