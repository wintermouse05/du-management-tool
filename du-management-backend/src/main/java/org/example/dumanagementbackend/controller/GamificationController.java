package org.example.dumanagementbackend.controller;

import org.example.dumanagementbackend.dto.gamification.LeaderboardEntryResponse;
import org.example.dumanagementbackend.dto.gamification.ManualPointRequest;
import org.example.dumanagementbackend.dto.gamification.PointHistoryResponse;
import org.example.dumanagementbackend.dto.gamification.PointRuleRequest;
import org.example.dumanagementbackend.dto.gamification.PointRuleResponse;
import org.example.dumanagementbackend.service.GamificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gamification")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HR','MEMBER')")
public class GamificationController {

    private final GamificationService gamificationService;

    @PostMapping("/rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PointRuleResponse> createRule(@Valid @RequestBody PointRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gamificationService.createRule(request));
    }

    @GetMapping("/rules")
    public ResponseEntity<Page<PointRuleResponse>> getRules(Pageable pageable) {
        return ResponseEntity.ok(gamificationService.getRules(pageable));
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PointRuleResponse> updateRule(@PathVariable Long id, @Valid @RequestBody PointRuleRequest request) {
        return ResponseEntity.ok(gamificationService.updateRule(id, request));
    }

    @PostMapping("/points/manual")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<PointHistoryResponse> manualAdjust(@Valid @RequestBody ManualPointRequest request) {
        return ResponseEntity.ok(gamificationService.adjustManual(request));
    }

    @GetMapping("/points/history/{userId}")
    public ResponseEntity<Page<PointHistoryResponse>> getUserHistory(@PathVariable Long userId, Pageable pageable) {
        return ResponseEntity.ok(gamificationService.getUserHistory(userId, pageable));
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<Page<LeaderboardEntryResponse>> leaderboard(Pageable pageable) {
        return ResponseEntity.ok(gamificationService.leaderboard(pageable));
    }
}
