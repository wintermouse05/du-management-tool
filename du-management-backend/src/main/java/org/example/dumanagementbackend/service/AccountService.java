package org.example.dumanagementbackend.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.example.dumanagementbackend.dto.account.AccountPasswordChangeRequest;
import org.example.dumanagementbackend.dto.account.AccountProfileUpdateRequest;
import org.example.dumanagementbackend.dto.account.AccountResponse;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountResponse getAccount(String username) {
        return toResponse(getCurrentUser(username));
    }

    @Transactional
    public AccountResponse updateProfile(String username, AccountProfileUpdateRequest request) {
        User user = getCurrentUser(username);
        user.setFullName(request.fullName().trim());
        user.setDob(request.dob());
        user.setJoinDate(request.joinDate());
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void changePassword(String username, AccountPasswordChangeRequest request) {
        User user = getCurrentUser(username);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadRequestException("ACCOUNT_CURRENT_PASSWORD_INVALID", "Current password is incorrect.");
        }
        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw new BadRequestException(
                    "ACCOUNT_PASSWORD_CONFIRMATION_MISMATCH",
                    "New password and confirmation do not match."
            );
        }
        if (request.newPassword().equals(request.currentPassword())
                || passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BadRequestException(
                    "ACCOUNT_NEW_PASSWORD_SAME_AS_CURRENT",
                    "New password must be different from the current password."
            );
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private User getCurrentUser(String username) {
        return userRepository.findByUsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username=" + username));
    }

    private AccountResponse toResponse(User user) {
        return new AccountResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().getName(),
                user.getDob(),
                user.getJoinDate(),
                calculateTenureMonths(user.getJoinDate()),
                user.getTotalPoints(),
                user.getStatus()
        );
    }

    private Long calculateTenureMonths(LocalDate joinDate) {
        if (joinDate == null) {
            return null;
        }
        long months = ChronoUnit.MONTHS.between(joinDate, LocalDate.now());
        return Math.max(months, 0);
    }
}
