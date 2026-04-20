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
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        User user = userRepository.findByUsername(request.username())
                .or(() -> userRepository.findByEmail(request.username()))
                .orElseThrow(() -> new ResourceNotFoundException("User not found for username/email=" + request.username()));

        String token = jwtService.generateToken(user.getUsername(), user.getRole().getName());
        return new LoginResponse(token, "Bearer", user.getUsername(), user.getRole().getName());
    }

    public LoginResponse register(RegisterRequest request) {
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
        user.setJoinDate(LocalDate.now());
        user.setStatus(UserStatus.ACTIVE);
        user.setTotalPoints(0);

        User saved = userRepository.save(user);
        String token = jwtService.generateToken(saved.getUsername(), saved.getRole().getName());
        return new LoginResponse(token, "Bearer", saved.getUsername(), saved.getRole().getName());
    }

    public void logout() {
        // JWT is stateless, client removes token on logout.
    }
}
