package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.dto.auth.LoginRequest;
import org.example.dumanagementbackend.dto.auth.LoginResponse;
import org.example.dumanagementbackend.dto.auth.RegisterRequest;
import org.example.dumanagementbackend.entity.Role;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.UserStatus;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.RoleRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import org.example.dumanagementbackend.security.JwtService;
import java.time.LocalDate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        return login(request, null, null);
    }

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()) 
        );

        User user = userRepository.findByUsername(request.username())
                .or(() -> userRepository.findByEmail(request.username()))
                .orElseThrow(() -> new ResourceNotFoundException("User not found for username/email=" + request.username()));

        return createAuthenticatedSession(user, httpRequest, httpResponse);
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        return register(request, null, null);
    }

    @Transactional
    public LoginResponse register(RegisterRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        if (request.username() == null || request.username().isBlank()) {
            throw new BadRequestException("username is required");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new BadRequestException("email is required");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new BadRequestException("password is required");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new BadRequestException("username already exists: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("email already exists: " + request.email());
        }

        Role memberRole = roleRepository.findByName("MEMBER")
                .orElseThrow(() -> new ResourceNotFoundException("Required role MEMBER is missing"));

        User user = new User();
        user.setRole(memberRole);
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFullName(request.fullName() != null && !request.fullName().isBlank() ? request.fullName() : request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setDob(request.dob());
        user.setJoinDate(LocalDate.now());
        user.setStatus(UserStatus.ACTIVE);
        user.setTotalPoints(0);

        User saved = userRepository.save(user);
        return createAuthenticatedSession(saved, httpRequest, httpResponse);
    }

    @Transactional
    public LoginResponse refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String refreshToken = refreshTokenService.extractRefreshToken(httpRequest);
        RefreshTokenService.RotationResult rotated = refreshTokenService.rotateRefreshToken(refreshToken, httpRequest);
        refreshTokenService.attachRefreshTokenCookie(httpResponse, rotated.rawRefreshToken());
        return buildAccessTokenResponse(rotated.user());
    }

    @Transactional
    public void logout() {
        logout(null, null);
    }

    @Transactional
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String refreshToken = refreshTokenService.extractRefreshToken(httpRequest);
        refreshTokenService.revokeByRawToken(refreshToken, "LOGOUT");
        refreshTokenService.clearRefreshTokenCookie(httpResponse);
    }

    private LoginResponse createAuthenticatedSession(
            User user,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        refreshTokenService.issueNewRefreshToken(user, httpRequest, httpResponse);
        return buildAccessTokenResponse(user);
    }

    private LoginResponse buildAccessTokenResponse(User user) {
        String token = jwtService.generateToken(user.getUsername(), user.getRole().getName());
        return new LoginResponse(token, "Bearer", user.getUsername(), user.getRole().getName(), user.getId());
    }
}
