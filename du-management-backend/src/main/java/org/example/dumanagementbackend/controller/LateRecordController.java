package org.example.dumanagementbackend.controller;

import org.example.dumanagementbackend.dto.late.LateRecordRequest;
import org.example.dumanagementbackend.dto.late.LateRecordResponse;
import org.example.dumanagementbackend.dto.late.LateSummaryResponse;
import org.example.dumanagementbackend.service.LateRecordService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/late-records")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HR','MEMBER')")
public class LateRecordController {

    private final LateRecordService lateRecordService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<LateRecordResponse> create(@Valid @RequestBody LateRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lateRecordService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<List<LateRecordResponse>> getAll() {
        return ResponseEntity.ok(lateRecordService.getAll());
    }

    @GetMapping("/by-user")
    public ResponseEntity<List<LateRecordResponse>> getByUser(@RequestParam Long userId) {
        return ResponseEntity.ok(lateRecordService.getByUser(userId));
    }

    @GetMapping("/monthly-summary")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<List<LateSummaryResponse>> monthlySummary(@RequestParam int year, @RequestParam int month) {
        return ResponseEntity.ok(lateRecordService.getMonthlySummary(year, month));
    }
}
