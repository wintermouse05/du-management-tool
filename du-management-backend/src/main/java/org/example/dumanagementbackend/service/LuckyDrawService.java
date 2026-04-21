package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.dto.luckydraw.LuckyDrawPrizeRequest;
import org.example.dumanagementbackend.dto.luckydraw.LuckyDrawPrizeResponse;
import org.example.dumanagementbackend.dto.luckydraw.LuckyDrawSessionRequest;
import org.example.dumanagementbackend.dto.luckydraw.LuckyDrawSessionResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LuckyDrawService {

    private final LuckyDrawSessionRepository sessionRepository;
    private final LuckyDrawPrizeRepository prizeRepository;
    private final LuckyDrawWinnerRepository winnerRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public LuckyDrawSessionResponse createSession(LuckyDrawSessionRequest request) {
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id=" + request.eventId()));
        LuckyDrawSession session = new LuckyDrawSession();
        session.setEvent(event);
        session.setName(request.name());
        return toSessionResponse(sessionRepository.save(session));
    }

    public Page<LuckyDrawSessionResponse> getSessionsByEvent(Long eventId, Pageable pageable) {
        return sessionRepository.findByEventId(eventId, pageable).map(this::toSessionResponse);
    }

    @Transactional
    public LuckyDrawPrizeResponse createPrize(LuckyDrawPrizeRequest request) {
        LuckyDrawSession session = sessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Lucky draw session not found with id=" + request.sessionId()));
        LuckyDrawPrize prize = new LuckyDrawPrize();
        prize.setSession(session);
        prize.setPrizeName(request.prizeName());
        prize.setQuantity(request.quantity());
        return toPrizeResponse(prizeRepository.save(prize));
    }

    public Page<LuckyDrawPrizeResponse> getPrizesBySession(Long sessionId, Pageable pageable) {
        return prizeRepository.findBySessionId(sessionId, pageable).map(this::toPrizeResponse);
    }

    @Transactional
    public LuckyDrawWinnerResponse drawWinner(LuckyDrawWinnerRequest request) {
        LuckyDrawPrize prize = prizeRepository.findById(request.prizeId())
                .orElseThrow(() -> new ResourceNotFoundException("Lucky draw prize not found with id=" + request.prizeId()));
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id=" + request.userId()));

        long assignedCount = winnerRepository.countByPrizeId(request.prizeId());
        if (assignedCount >= prize.getQuantity()) {
            throw new BadRequestException("All prize slots have already been assigned for prizeId=" + request.prizeId());
        }

        LuckyDrawWinner winner = new LuckyDrawWinner();
        winner.setPrize(prize);
        winner.setUser(user);
        
        LuckyDrawWinnerResponse response = toWinnerResponse(winnerRepository.save(winner));
        
        // Broadcast the winner
        messagingTemplate.convertAndSend("/topic/lucky-draw", response);
        
        return response;
    }

    public Page<LuckyDrawWinnerResponse> getWinnersByPrize(Long prizeId, Pageable pageable) {
        return winnerRepository.findByPrizeId(prizeId, pageable).map(this::toWinnerResponse);
    }

    private LuckyDrawSessionResponse toSessionResponse(LuckyDrawSession session) {
        return new LuckyDrawSessionResponse(
                session.getId(),
                session.getEvent().getId(),
                session.getEvent().getName(),
                session.getName()
        );
    }

    private LuckyDrawPrizeResponse toPrizeResponse(LuckyDrawPrize prize) {
        return new LuckyDrawPrizeResponse(
                prize.getId(),
                prize.getSession().getId(),
                prize.getSession().getName(),
                prize.getPrizeName(),
                prize.getQuantity()
        );
    }

    private LuckyDrawWinnerResponse toWinnerResponse(LuckyDrawWinner winner) {
        return new LuckyDrawWinnerResponse(
                winner.getId(),
                winner.getPrize().getId(),
                winner.getPrize().getPrizeName(),
                winner.getUser().getId(),
                winner.getUser().getFullName()
        );
    }
}
