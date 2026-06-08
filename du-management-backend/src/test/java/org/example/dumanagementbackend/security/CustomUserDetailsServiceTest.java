package org.example.dumanagementbackend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.example.dumanagementbackend.entity.Role;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.UserStatus;
import org.example.dumanagementbackend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    void loadUserByUsername_usesOnlyNonDeletedUsers() {
        when(userRepository.findByUsernameAndDeletedAtIsNull("alice")).thenReturn(Optional.of(buildUser()));

        UserDetails details = service.loadUserByUsername("alice");

        assertEquals("alice", details.getUsername());
        assertEquals("ROLE_MEMBER", details.getAuthorities().iterator().next().getAuthority());
        assertTrue(details.isEnabled());
    }

    @Test
    void loadUserByUsername_marksInactiveUserAsDisabled() {
        User user = buildUser();
        user.setStatus(UserStatus.INACTIVE);
        when(userRepository.findByUsernameAndDeletedAtIsNull("alice")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("alice");

        assertFalse(details.isEnabled());
    }

    @Test
    void loadUserByUsername_rejectsDeletedOrMissingUser() {
        when(userRepository.findByUsernameAndDeletedAtIsNull("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndDeletedAtIsNull("alice")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("alice"));
    }

    private User buildUser() {
        Role role = new Role();
        role.setName("MEMBER");

        User user = new User();
        user.setUsername("alice");
        user.setPassword("hashed");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
