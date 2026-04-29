package org.example.dumanagementbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.example.dumanagementbackend.dto.luckydraw.LuckyDrawWinnerRequest;
import org.example.dumanagementbackend.dto.luckydraw.LuckyDrawWinnerResponse;
import org.example.dumanagementbackend.entity.Event;
import org.example.dumanagementbackend.entity.LuckyDrawPrize;
import org.example.dumanagementbackend.entity.LuckyDrawSession;
import org.example.dumanagementbackend.entity.LuckyDrawWinner;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.EventRepository;
import org.example.dumanagementbackend.repository.LuckyDrawPrizeRepository;
import org.example.dumanagementbackend.repository.LuckyDrawSessionRepository;
import org.example.dumanagementbackend.repository.LuckyDrawWinnerRepository;
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
class LuckyDrawServiceTest {

    @Mock
    private LuckyDrawSessionRepository sessionRepository;

    @Mock
    private LuckyDrawPrizeRepository prizeRepository;

    @Mock
    private LuckyDrawWinnerRepository winnerRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private GamificationService gamificationService;

    @InjectMocks
    private LuckyDrawService luckyDrawService;

    @Test
    void drawWinner_throwsBadRequestWhenPrizeSlotsAreFull() {
        LuckyDrawPrize prize = buildPrize(10L, 1, "Gift Card");
        User user = buildUser(20L, "Winner User");

        when(prizeRepository.findById(10L)).thenReturn(Optional.of(prize));
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));
        when(winnerRepository.countByPrizeId(10L)).thenReturn(1L);

        LuckyDrawWinnerRequest request = new LuckyDrawWinnerRequest(10L, 20L);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> luckyDrawService.drawWinner(request));

        assertEquals("All prize slots have already been assigned for prizeId=10", ex.getMessage());
        verify(winnerRepository, never()).save(any(LuckyDrawWinner.class));
    }

    @Test
    void drawWinner_returnsMappedWinnerWhenSuccess() {
        LuckyDrawPrize prize = buildPrize(10L, 2, "Headphone");
        User user = buildUser(20L, "Lucky User");

        when(prizeRepository.findById(10L)).thenReturn(Optional.of(prize));
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));
        when(winnerRepository.countByPrizeId(10L)).thenReturn(1L);
        when(winnerRepository.save(any(LuckyDrawWinner.class))).thenAnswer(invocation -> {
            LuckyDrawWinner winner = invocation.getArgument(0);
            winner.setId(99L);
            return winner;
        });

        LuckyDrawWinnerResponse response = luckyDrawService.drawWinner(new LuckyDrawWinnerRequest(10L, 20L));

        assertEquals(99L, response.id());
        assertEquals(10L, response.prizeId());
        assertEquals("Headphone", response.prizeName());
        assertEquals(20L, response.userId());
        assertEquals("Lucky User", response.fullName());
    }

    @Test
    void createSession_throwsNotFoundWhenEventMissing() {
        when(eventRepository.findById(7L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> luckyDrawService.createSession(new org.example.dumanagementbackend.dto.luckydraw.LuckyDrawSessionRequest(7L, "Session A"))
        );

        assertEquals("Event not found with id=7", ex.getMessage());
    }

    @Test
    void getWinnersByPrize_returnsMappedPage() {
        Pageable pageable = PageRequest.of(0, 10);

        LuckyDrawPrize prize = buildPrize(11L, 5, "Backpack");
        User user = buildUser(22L, "Paged Winner");

        LuckyDrawWinner winner = new LuckyDrawWinner();
        winner.setId(123L);
        winner.setPrize(prize);
        winner.setUser(user);

        Page<LuckyDrawWinner> winnerPage = new PageImpl<>(List.of(winner), pageable, 1);
        when(winnerRepository.findByPrizeId(11L, pageable)).thenReturn(winnerPage);

        Page<LuckyDrawWinnerResponse> result = luckyDrawService.getWinnersByPrize(11L, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("Backpack", result.getContent().get(0).prizeName());
        assertEquals("Paged Winner", result.getContent().get(0).fullName());
    }

    private LuckyDrawPrize buildPrize(Long prizeId, int quantity, String prizeName) {
        Event event = new Event();
        event.setId(1L);
        event.setName("Event A");

        LuckyDrawSession session = new LuckyDrawSession();
        session.setId(2L);
        session.setEvent(event);
        session.setName("Session 1");

        LuckyDrawPrize prize = new LuckyDrawPrize();
        prize.setId(prizeId);
        prize.setSession(session);
        prize.setPrizeName(prizeName);
        prize.setQuantity(quantity);
        return prize;
    }

    private User buildUser(Long userId, String fullName) {
        User user = new User();
        user.setId(userId);
        user.setFullName(fullName);
        return user;
    }
}
