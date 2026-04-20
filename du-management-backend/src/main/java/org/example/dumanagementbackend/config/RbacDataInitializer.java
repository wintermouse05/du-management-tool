package org.example.dumanagementbackend.config;

import org.example.dumanagementbackend.entity.Role;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.UserStatus;
import org.example.dumanagementbackend.repository.RoleRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RbacDataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.rbac.seed-default-users:true}")
    private boolean seedDefaultUsers;

    @Value("${app.rbac.default-admin-password:Admin@123}")
    private String adminPassword;

    @Value("${app.rbac.default-hr-password:Hr@123}")
    private String hrPassword;

    @Value("${app.rbac.default-member-password:Member@123}")
    private String memberPassword;

    @Override
    public void run(String... args) {
        Role adminRole = getOrCreateRole("ADMIN", "System administrator");
        Role hrRole = getOrCreateRole("HR", "HR/manager role");
        Role memberRole = getOrCreateRole("MEMBER", "Normal member role");

        if (!seedDefaultUsers) {
            return;
        }

        createUserIfMissing("admin", "admin@du.local", "System Admin", adminPassword, adminRole);
        createUserIfMissing("hr", "hr@du.local", "HR User", hrPassword, hrRole);
        createUserIfMissing("member", "member@du.local", "Default Member", memberPassword, memberRole);
    }

    private Role getOrCreateRole(String name, String description) {
        return roleRepository.findByName(name).orElseGet(() -> {
            Role role = new Role();
            role.setName(name);
            role.setDescription(description);
            return roleRepository.save(role);
        });
    }

    private void createUserIfMissing(String username, String email, String fullName, String rawPassword, Role role) {
        if (userRepository.existsByUsername(username)) {
            return;
        }

        User user = new User();
        user.setRole(role);
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setJoinDate(LocalDate.now());
        user.setTotalPoints(0);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }
}
