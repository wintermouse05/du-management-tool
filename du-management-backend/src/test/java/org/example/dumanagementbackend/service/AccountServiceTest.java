package org.example.dumanagementbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import org.example.dumanagementbackend.dto.account.AccountPasswordChangeRequest;
import org.example.dumanagementbackend.dto.account.AccountProfileUpdateRequest;
import org.example.dumanagementbackend.dto.account.AccountResponse;
import org.example.dumanagementbackend.entity.Role;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.UserStatus;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountService accountService;

    @Test
    void updateProfile_updatesFullNameAndDates() {
        User user = buildUser();
        when(userRepository.findByUsernameAndDeletedAtIsNull("alice")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        LocalDate dob = LocalDate.of(1995, 3, 12);
        LocalDate joinDate = LocalDate.of(2024, 1, 8);

        AccountResponse response = accountService.updateProfile(
                "alice",
                new AccountProfileUpdateRequest(" Alice Nguyen ", dob, joinDate)
        );

        assertEquals("Alice Nguyen", user.getFullName());
        assertEquals(dob, user.getDob());
        assertEquals(joinDate, user.getJoinDate());
        assertEquals("Alice Nguyen", response.fullName());
        assertEquals(dob, response.dob());
        assertEquals(joinDate, response.joinDate());
        verify(userRepository).save(user);
    }

    @Test
    void changePassword_rejectsIncorrectCurrentPassword() {
        User user = buildUser();
        when(userRepository.findByUsernameAndDeletedAtIsNull("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed-current")).thenReturn(false);

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> accountService.changePassword(
                        "alice",
                        new AccountPasswordChangeRequest("wrong", "NewPass@123", "NewPass@123")
                )
        );

        assertEquals("ACCOUNT_CURRENT_PASSWORD_INVALID", ex.getErrorCode());
        verify(userRepository, never()).save(user);
    }

    @Test
    void changePassword_rejectsConfirmationMismatch() {
        User user = buildUser();
        when(userRepository.findByUsernameAndDeletedAtIsNull("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Current@123", "hashed-current")).thenReturn(true);

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> accountService.changePassword(
                        "alice",
                        new AccountPasswordChangeRequest("Current@123", "NewPass@123", "Different@123")
                )
        );

        assertEquals("ACCOUNT_PASSWORD_CONFIRMATION_MISMATCH", ex.getErrorCode());
        verify(userRepository, never()).save(user);
    }

    @Test
    void changePassword_rejectsSamePassword() {
        User user = buildUser();
        when(userRepository.findByUsernameAndDeletedAtIsNull("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Current@123", "hashed-current")).thenReturn(true);

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> accountService.changePassword(
                        "alice",
                        new AccountPasswordChangeRequest("Current@123", "Current@123", "Current@123")
                )
        );

        assertEquals("ACCOUNT_NEW_PASSWORD_SAME_AS_CURRENT", ex.getErrorCode());
        verify(userRepository, never()).save(user);
    }

    @Test
    void changePassword_savesEncodedNewPassword() {
        User user = buildUser();
        when(userRepository.findByUsernameAndDeletedAtIsNull("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Current@123", "hashed-current")).thenReturn(true);
        when(passwordEncoder.matches("NewPass@123", "hashed-current")).thenReturn(false);
        when(passwordEncoder.encode("NewPass@123")).thenReturn("hashed-new");

        accountService.changePassword(
                "alice",
                new AccountPasswordChangeRequest("Current@123", "NewPass@123", "NewPass@123")
        );

        assertEquals("hashed-new", user.getPassword());
        verify(userRepository).save(user);
    }

    private User buildUser() {
        Role role = new Role();
        role.setId(1L);
        role.setName("MEMBER");

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setFullName("Alice");
        user.setPassword("hashed-current");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setTotalPoints(0);
        return user;
    }
}
