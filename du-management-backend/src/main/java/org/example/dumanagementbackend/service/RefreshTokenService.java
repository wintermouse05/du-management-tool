package org.example.dumanagementbackend.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.example.dumanagementbackend.entity.RefreshToken;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.exception.UnauthorizedException;
import org.example.dumanagementbackend.repository.RefreshTokenRepository;
import org.example.dumanagementbackend.security.AccountStatusPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.auth.refresh-token.expiration-ms:1209600000}")
    private long refreshTokenExpirationMs;

    @Value("${app.auth.refresh-token.cookie-name:refresh_token}")
    private String refreshTokenCookieName;

    @Value("${app.auth.refresh-token.cookie-path:/api/auth}")
    private String refreshTokenCookiePath;

    @Value("${app.auth.refresh-token.cookie-secure:false}")
    private boolean refreshTokenCookieSecure;

    @Value("${app.auth.refresh-token.cookie-same-site:Lax}")
    private String refreshTokenCookieSameSite;

    @Value("${app.auth.refresh-token.cookie-domain:}")
    private String refreshTokenCookieDomain;

    public record RotationResult(User user, String rawRefreshToken) {}

    @Transactional
    public void issueNewRefreshToken(User user, HttpServletRequest request, HttpServletResponse response) {
        if (!AccountStatusPolicy.isActive(user)) {
            throw new UnauthorizedException(
                    AccountStatusPolicy.ACCOUNT_UNAVAILABLE_CODE,
                    AccountStatusPolicy.ACCOUNT_UNAVAILABLE_MESSAGE
            );
        }

        Instant now = Instant.now();
        String rawToken = generateRawToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hashToken(rawToken));
        refreshToken.setFamilyId(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(now.plusMillis(refreshTokenExpirationMs));
        refreshToken.setCreatedAt(now);
        refreshToken.setIpAddress(extractClientIp(request));
        refreshToken.setUserAgent(extractUserAgent(request));

        refreshTokenRepository.save(refreshToken);
        attachRefreshTokenCookie(response, rawToken);
    }

    @Transactional
    public RotationResult rotateRefreshToken(String rawToken, HttpServletRequest request) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException("Missing refresh token");
        }

        RefreshToken presentedToken = refreshTokenRepository.findByTokenHash(hashToken(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid"));

        Instant now = Instant.now();
        if (presentedToken.getRevokedAt() != null) {
            if ("ROTATED".equals(presentedToken.getRevokeReason())) {
                revokeTokenFamily(presentedToken.getFamilyId(), "REUSE_DETECTED");
            }
            throw new UnauthorizedException("Refresh token is invalid");
        }

        if (presentedToken.getExpiresAt().isBefore(now)) {
            presentedToken.setRevokedAt(now);
            presentedToken.setRevokeReason("EXPIRED");
            refreshTokenRepository.save(presentedToken);
            throw new UnauthorizedException("Refresh token has expired");
        }

        if (!AccountStatusPolicy.isActive(presentedToken.getUser())) {
            revokeTokenFamily(presentedToken.getFamilyId(), "USER_INACTIVE");
            throw new UnauthorizedException(
                    AccountStatusPolicy.ACCOUNT_UNAVAILABLE_CODE,
                    AccountStatusPolicy.ACCOUNT_UNAVAILABLE_MESSAGE
            );
        }

        String replacementRawToken = generateRawToken();
        String replacementTokenHash = hashToken(replacementRawToken);

        presentedToken.setLastUsedAt(now);
        presentedToken.setRotatedAt(now);
        presentedToken.setRevokedAt(now);
        presentedToken.setRevokeReason("ROTATED");
        presentedToken.setReplacedByTokenHash(replacementTokenHash);

        RefreshToken replacementToken = new RefreshToken();
        replacementToken.setUser(presentedToken.getUser());
        replacementToken.setTokenHash(replacementTokenHash);
        replacementToken.setFamilyId(presentedToken.getFamilyId());
        replacementToken.setParentTokenId(presentedToken.getId());
        replacementToken.setExpiresAt(now.plusMillis(refreshTokenExpirationMs));
        replacementToken.setCreatedAt(now);
        replacementToken.setIpAddress(extractClientIp(request));
        replacementToken.setUserAgent(extractUserAgent(request));

        refreshTokenRepository.save(presentedToken);
        refreshTokenRepository.save(replacementToken);

        return new RotationResult(presentedToken.getUser(), replacementRawToken);
    }

    @Transactional
    public void revokeByRawToken(String rawToken, String reason) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByTokenHash(hashToken(rawToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
                token.setRevokeReason(reason);
                refreshTokenRepository.save(token);
            }
        });
    }

    @Transactional
    public void revokeTokenFamily(String familyId, String reason) {
        if (familyId == null || familyId.isBlank()) {
            return;
        }
        refreshTokenRepository.revokeActiveByFamilyId(familyId, Instant.now(), reason);
    }

    @Transactional
    public void revokeActiveByUserId(Long userId, String reason) {
        if (userId == null) {
            return;
        }
        refreshTokenRepository.revokeActiveByUserId(userId, Instant.now(), reason);
    }

    public String extractRefreshToken(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return null;
        }

        for (Cookie cookie : request.getCookies()) {
            if (refreshTokenCookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public void attachRefreshTokenCookie(HttpServletResponse response, String rawToken) {
        if (response == null) {
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(rawToken, false).toString());
    }

    public void clearRefreshTokenCookie(HttpServletResponse response) {
        if (response == null) {
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", true).toString());
    }

    private ResponseCookie buildCookie(String value, boolean clear) {
        long maxAgeSeconds = clear ? 0 : Math.max(1L, refreshTokenExpirationMs / 1000);
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(refreshTokenCookieName, value)
                .httpOnly(true)
                .secure(refreshTokenCookieSecure)
                .sameSite(refreshTokenCookieSameSite)
                .path(refreshTokenCookiePath)
                .maxAge(Duration.ofSeconds(maxAgeSeconds));

        String cookieDomain = refreshTokenCookieDomain == null ? "" : refreshTokenCookieDomain.trim();
        if (!cookieDomain.isEmpty()) {
            builder.domain(cookieDomain);
        }

        return builder.build();
    }

    private String generateRawToken() {
        byte[] tokenBytes = new byte[64];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    private String extractUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }

        return userAgent.length() > 512 ? userAgent.substring(0, 512) : userAgent;
    }
}
