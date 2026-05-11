package org.example.dumanagementbackend.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.example.dumanagementbackend.dto.late.LateRecordRequest;
import org.example.dumanagementbackend.dto.late.LateRecordResponse;
import org.example.dumanagementbackend.dto.late.LateSummaryResponse;
import org.example.dumanagementbackend.service.LateRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public ResponseEntity<Page<LateRecordResponse>> getAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Pageable pageable) {
        if (fromDate != null && toDate != null) {
            return ResponseEntity.ok(lateRecordService.getByDateRange(fromDate, toDate, pageable));
        }
        return ResponseEntity.ok(lateRecordService.getAll(pageable));
    }

    @GetMapping("/by-user")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
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

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        byte[] content = lateRecordService.exportCsv(year, month);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=late-records.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(content);
    }

    @PostMapping("/check-now")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<String> checkNow(@RequestParam(required = false) String channelId) {
        int saved = lateRecordService.checkNow(channelId);
        return ResponseEntity.ok("Late check-in fetch completed. " + saved + " record(s) saved.");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        lateRecordService.deleteLateRecord(id);
        return ResponseEntity.noContent().build();
    }
}
