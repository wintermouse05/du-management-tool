package org.example.dumanagementbackend.controller;

import org.example.dumanagementbackend.dto.account.AccountPasswordChangeRequest;
import org.example.dumanagementbackend.dto.account.AccountProfileUpdateRequest;
import org.example.dumanagementbackend.dto.account.AccountResponse;
import org.example.dumanagementbackend.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HR','MEMBER')")
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<AccountResponse> getAccount(Authentication authentication) {
        return ResponseEntity.ok(accountService.getAccount(authentication.getName()));
    }

    @PutMapping("/profile")
    public ResponseEntity<AccountResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody AccountProfileUpdateRequest request
    ) {
        return ResponseEntity.ok(accountService.updateProfile(authentication.getName(), request));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody AccountPasswordChangeRequest request
    ) {
        accountService.changePassword(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }
}
