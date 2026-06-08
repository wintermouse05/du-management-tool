package org.example.dumanagementbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.example.dumanagementbackend.entity.PasswordResetToken;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.UserStatus;
import org.example.dumanagementbackend.exception.UnauthorizedException;
import org.example.dumanagementbackend.repository.PasswordResetTokenRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import org.example.dumanagementbackend.security.AccountStatusPolicy;
import org.example.dumanagementbackend.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private NotificationEmailService notificationEmailService;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private PasswordResetService passwordResetService;

    @Test
    void resetPassword_rejectsInactiveAccountWithoutIssuingSession() {
        User user = new User();
        user.setStatus(UserStatus.INACTIVE);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setUsed(false);
        resetToken.setExpiresAt(Instant.now().plusSeconds(300));
        when(tokenRepository.findByToken("reset-token")).thenReturn(Optional.of(resetToken));

        UnauthorizedException ex = assertThrows(
                UnauthorizedException.class,
                () -> passwordResetService.resetPassword("reset-token", "NewPassword@123")
        );

        assertEquals(AccountStatusPolicy.ACCOUNT_UNAVAILABLE_CODE, ex.getErrorCode());
        assertEquals(AccountStatusPolicy.ACCOUNT_UNAVAILABLE_MESSAGE, ex.getMessage());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(jwtService, never()).generateToken(anyString(), anyString());
        verify(refreshTokenService, never()).issueNewRefreshToken(any(), any(), any());
    }
}
