package org.example.dumanagementbackend.controller;

import org.example.dumanagementbackend.dto.chatops.ChatopsLeaveRequestSummaryResponse;
import org.example.dumanagementbackend.service.ChatopsLeaveRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/chatops/leave-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HR','MEMBER')")
public class ChatopsLeaveRequestController {

    private final ChatopsLeaveRequestService chatopsLeaveRequestService;

    @GetMapping("/today")
    public ResponseEntity<ChatopsLeaveRequestSummaryResponse> getTodayRequests() {
        return ResponseEntity.ok(chatopsLeaveRequestService.getTodayRequests());
    }

    @PostMapping("/today/refresh")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ChatopsLeaveRequestSummaryResponse> refreshTodayRequests() {
        return ResponseEntity.ok(chatopsLeaveRequestService.refreshToday());
    }
}
