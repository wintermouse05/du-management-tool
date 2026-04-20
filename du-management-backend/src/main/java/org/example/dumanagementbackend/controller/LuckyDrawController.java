package org.example.dumanagementbackend.controller;

import org.example.dumanagementbackend.dto.luckydraw.LuckyDrawPrizeRequest;
import org.example.dumanagementbackend.dto.luckydraw.LuckyDrawPrizeResponse;
import org.example.dumanagementbackend.dto.luckydraw.LuckyDrawSessionRequest;
import org.example.dumanagementbackend.dto.luckydraw.LuckyDrawSessionResponse;
import org.example.dumanagementbackend.dto.luckydraw.LuckyDrawWinnerRequest;
import org.example.dumanagementbackend.dto.luckydraw.LuckyDrawWinnerResponse;
import org.example.dumanagementbackend.service.LuckyDrawService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lucky-draw")
@RequiredArgsConstructor
public class LuckyDrawController {

    private final LuckyDrawService luckyDrawService;

    @PostMapping("/sessions")
    public ResponseEntity<LuckyDrawSessionResponse> createSession(@RequestBody LuckyDrawSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(luckyDrawService.createSession(request));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<LuckyDrawSessionResponse>> getSessionsByEvent(@RequestParam Long eventId) {
        return ResponseEntity.ok(luckyDrawService.getSessionsByEvent(eventId));
    }

    @PostMapping("/prizes")
    public ResponseEntity<LuckyDrawPrizeResponse> createPrize(@RequestBody LuckyDrawPrizeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(luckyDrawService.createPrize(request));
    }

    @GetMapping("/prizes")
    public ResponseEntity<List<LuckyDrawPrizeResponse>> getPrizesBySession(@RequestParam Long sessionId) {
        return ResponseEntity.ok(luckyDrawService.getPrizesBySession(sessionId));
    }

    @PostMapping("/winners")
    public ResponseEntity<LuckyDrawWinnerResponse> drawWinner(@RequestBody LuckyDrawWinnerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(luckyDrawService.drawWinner(request));
    }

    @GetMapping("/winners")
    public ResponseEntity<List<LuckyDrawWinnerResponse>> getWinnersByPrize(@RequestParam Long prizeId) {
        return ResponseEntity.ok(luckyDrawService.getWinnersByPrize(prizeId));
    }
}
