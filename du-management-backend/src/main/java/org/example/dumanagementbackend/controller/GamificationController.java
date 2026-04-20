package org.example.dumanagementbackend.controller;

import org.example.dumanagementbackend.dto.gamification.LeaderboardEntryResponse;
import org.example.dumanagementbackend.dto.gamification.ManualPointRequest;
import org.example.dumanagementbackend.dto.gamification.PointHistoryResponse;
import org.example.dumanagementbackend.dto.gamification.PointRuleRequest;
import org.example.dumanagementbackend.dto.gamification.PointRuleResponse;
import org.example.dumanagementbackend.service.GamificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
public class GamificationController {

    private final GamificationService gamificationService;

    @PostMapping("/rules")
    public ResponseEntity<PointRuleResponse> createRule(@RequestBody PointRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gamificationService.createRule(request));
    }

    @GetMapping("/rules")
    public ResponseEntity<List<PointRuleResponse>> getRules() {
        return ResponseEntity.ok(gamificationService.getRules());
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<PointRuleResponse> updateRule(@PathVariable Long id, @RequestBody PointRuleRequest request) {
        return ResponseEntity.ok(gamificationService.updateRule(id, request));
    }

    @PostMapping("/points/manual")
    public ResponseEntity<PointHistoryResponse> manualAdjust(@RequestBody ManualPointRequest request) {
        return ResponseEntity.ok(gamificationService.adjustManual(request));
    }

    @GetMapping("/points/history/{userId}")
    public ResponseEntity<List<PointHistoryResponse>> getUserHistory(@PathVariable Long userId) {
        return ResponseEntity.ok(gamificationService.getUserHistory(userId));
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<LeaderboardEntryResponse>> leaderboard() {
        return ResponseEntity.ok(gamificationService.leaderboard());
    }
}
