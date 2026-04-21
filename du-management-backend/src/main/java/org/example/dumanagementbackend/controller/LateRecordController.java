package org.example.dumanagementbackend.controller;

import org.example.dumanagementbackend.dto.late.LateRecordRequest;
import org.example.dumanagementbackend.dto.late.LateRecordResponse;
import org.example.dumanagementbackend.dto.late.LateSummaryResponse;
import org.example.dumanagementbackend.service.LateRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    public ResponseEntity<Page<LateRecordResponse>> getAll(Pageable pageable) {
        return ResponseEntity.ok(lateRecordService.getAll(pageable));
    }

    @GetMapping("/by-user")
    public ResponseEntity<Page<LateRecordResponse>> getByUser(@RequestParam Long userId, Pageable pageable) {
        return ResponseEntity.ok(lateRecordService.getByUser(userId, pageable));
    }

    @GetMapping("/monthly-summary")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<Page<LateSummaryResponse>> monthlySummary(
            @RequestParam int year,
            @RequestParam int month,
            Pageable pageable
    ) {
        return ResponseEntity.ok(lateRecordService.getMonthlySummary(year, month, pageable));
    }
}
