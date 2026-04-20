package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.dto.auth.LoginRequest;
import org.example.dumanagementbackend.dto.auth.LoginResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    public LoginResponse login(LoginRequest request) {
        String token = UUID.randomUUID().toString();
        String username = request.username() != null ? request.username() : "anonymous";
        return new LoginResponse(token, "Bearer", username, "MEMBER");
    }

    public void logout() {
        // Stateless placeholder for local development.
    }
}
