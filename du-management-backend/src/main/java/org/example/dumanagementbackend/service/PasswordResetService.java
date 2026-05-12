package org.example.dumanagementbackend.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dumanagementbackend.dto.auth.LoginResponse;
import org.example.dumanagementbackend.entity.PasswordResetToken;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.repository.PasswordResetTokenRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import org.example.dumanagementbackend.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final NotificationEmailService notificationEmailService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.password-reset.token-expiration-minutes:15}")
    private int tokenExpirationMinutes;

    @Value("${app.password-reset.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresentOrElse(
                user -> {
                    String token = UUID.randomUUID().toString();
                    Instant now = Instant.now();

                    PasswordResetToken resetToken = new PasswordResetToken();
                    resetToken.setToken(token);
                    resetToken.setUser(user);
                    resetToken.setExpiresAt(now.plus(tokenExpirationMinutes, ChronoUnit.MINUTES));
                    resetToken.setCreatedAt(now);
                    resetToken.setUsed(false);
                    tokenRepository.save(resetToken);

                    String resetLink = frontendUrl + "/reset-password/" + token;
                    String body = "Hello " + user.getFullName() + ",\n\n"
                            + "A password reset was requested for your DU Manager account.\n\n"
                            + "Click the link below to reset your password (expires in "
                            + tokenExpirationMinutes + " minutes):\n\n"
                            + resetLink + "\n\n"
                            + "If you did not request this, please ignore this email.\n";
                    notificationEmailService.sendEmail(user.getEmail(), "DU Manager - Password Reset", body);
                },
                () -> log.info("Password reset requested for unknown email: {}", email)
        );
    }

    @Transactional
    public LoginResponse resetPassword(String token, String newPassword) {
        return resetPassword(token, newPassword, null, null);
    }

    @Transactional
    public LoginResponse resetPassword(
            String token,
            String newPassword,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        if (resetToken.isUsed()) {
            throw new BadRequestException("This reset token has already been used");
        }
        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("This reset token has expired");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        String jwt = jwtService.generateToken(user.getUsername(), user.getRole().getName());
        refreshTokenService.issueNewRefreshToken(user, httpRequest, httpResponse);
        return new LoginResponse(jwt, "Bearer", user.getUsername(), user.getRole().getName(), user.getId());
    }
}
