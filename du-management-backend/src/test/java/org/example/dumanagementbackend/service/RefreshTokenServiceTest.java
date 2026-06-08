package org.example.dumanagementbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.example.dumanagementbackend.entity.RefreshToken;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.UserStatus;
import org.example.dumanagementbackend.exception.UnauthorizedException;
import org.example.dumanagementbackend.repository.RefreshTokenRepository;
import org.example.dumanagementbackend.security.AccountStatusPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Test
    void issueNewRefreshToken_rejectsInactiveAccount() {
        User user = new User();
        user.setStatus(UserStatus.INACTIVE);

        UnauthorizedException ex = assertThrows(
                UnauthorizedException.class,
                () -> refreshTokenService.issueNewRefreshToken(user, null, null)
        );

        assertEquals(AccountStatusPolicy.ACCOUNT_UNAVAILABLE_CODE, ex.getErrorCode());
        assertEquals(AccountStatusPolicy.ACCOUNT_UNAVAILABLE_MESSAGE, ex.getMessage());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotateRefreshToken_rejectsInactiveAccountAndRevokesTokenFamily() {
        User user = new User();
        user.setStatus(UserStatus.INACTIVE);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setFamilyId("family-1");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        UnauthorizedException ex = assertThrows(
                UnauthorizedException.class,
                () -> refreshTokenService.rotateRefreshToken("raw-token", null)
        );

        assertEquals(AccountStatusPolicy.ACCOUNT_UNAVAILABLE_CODE, ex.getErrorCode());
        assertEquals(AccountStatusPolicy.ACCOUNT_UNAVAILABLE_MESSAGE, ex.getMessage());
        verify(refreshTokenRepository).revokeActiveByFamilyId(
                eq("family-1"),
                any(Instant.class),
                eq("USER_INACTIVE")
        );
    }
}
