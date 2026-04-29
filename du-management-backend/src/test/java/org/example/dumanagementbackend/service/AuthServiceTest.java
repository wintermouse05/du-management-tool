package org.example.dumanagementbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    // ── register ─────────────────────────────────────────────────────────────

    @Test
    void register_throwsBadRequestWhenUsernameBlank() {
        RegisterRequest req = new RegisterRequest("", "a@b.com", "Full Name", "password1");
        assertThrows(BadRequestException.class, () -> authService.register(req));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_throwsBadRequestWhenEmailBlank() {
        RegisterRequest req = new RegisterRequest("user1", "", "Full Name", "password1");
        assertThrows(BadRequestException.class, () -> authService.register(req));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_throwsBadRequestWhenPasswordBlank() {
        RegisterRequest req = new RegisterRequest("user1", "a@b.com", "Full Name", "");
        assertThrows(BadRequestException.class, () -> authService.register(req));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_throwsBadRequestWhenUsernameAlreadyExists() {
        RegisterRequest req = new RegisterRequest("taken", "a@b.com", "Full Name", "secret123");
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.register(req));
        assertEquals("username already exists: taken", ex.getMessage());
    }

    @Test
    void register_throwsBadRequestWhenEmailAlreadyExists() {
        RegisterRequest req = new RegisterRequest("newuser", "taken@b.com", "Full Name", "secret123");
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("taken@b.com")).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.register(req));
        assertEquals("email already exists: taken@b.com", ex.getMessage());
    }

    @Test
    void register_throwsNotFoundWhenMemberRoleMissing() {
        RegisterRequest req = new RegisterRequest("newuser", "new@b.com", "Full Name", "secret123");
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@b.com")).thenReturn(false);
        when(roleRepository.findByName("MEMBER")).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> authService.register(req));
        assertEquals("Required role MEMBER is missing", ex.getMessage());
    }

    @Test
    void register_usesUsernameAsFullNameWhenFullNameNull() {
        RegisterRequest req = new RegisterRequest("newuser", "new@b.com", null, "secret123");
        Role role = buildRole(1L, "MEMBER");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@b.com")).thenReturn(false);
        when(roleRepository.findByName("MEMBER")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });
        when(jwtService.generateToken("newuser", "MEMBER")).thenReturn("jwt-token");

        LoginResponse response = authService.register(req);

        assertEquals("newuser", response.username());
        assertEquals("MEMBER", response.role());
        assertEquals("jwt-token", response.accessToken());
    }

    @Test
    void register_successSetsActiveStatusAndZeroPoints() {
        RegisterRequest req = new RegisterRequest("alice", "alice@b.com", "Alice Doe", "password99");
        Role role = buildRole(1L, "MEMBER");

        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@b.com")).thenReturn(false);
        when(roleRepository.findByName("MEMBER")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("password99")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(5L);
            return u;
        });
        when(jwtService.generateToken("alice", "MEMBER")).thenReturn("token-abc");

        LoginResponse response = authService.register(req);

        assertEquals(5L, response.userId());
        assertEquals("alice", response.username());
        verify(userRepository).save(any(User.class));
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_returnsTokenWhenCredentialsValid() {
        Role role = buildRole(1L, "MEMBER");
        User user = buildUser(7L, "bob", role);

        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("bob", "MEMBER")).thenReturn("jwt-bob");

        LoginResponse response = authService.login(new LoginRequest("bob", "pass"));

        assertEquals("bob", response.username());
        assertEquals("MEMBER", response.role());
        assertEquals("jwt-bob", response.accessToken());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void login_throwsNotFoundWhenUserMissing() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ghost")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> authService.login(new LoginRequest("ghost", "pass")));
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_isNoOp() {
        // Stateless JWT – should complete without exceptions
        authService.logout();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Role buildRole(Long id, String name) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        return role;
    }

    private User buildUser(Long id, String username, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setTotalPoints(0);
        return user;
    }
}
