package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.dto.member.MemberRequest;
import org.example.dumanagementbackend.dto.member.MemberResponse;
import org.example.dumanagementbackend.entity.Role;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.UserStatus;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.RoleRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberResponse create(MemberRequest request) {
        User user = new User();
        apply(user, request);
        if (user.getTotalPoints() == null) {
            user.setTotalPoints(0);
        }
        return toResponse(userRepository.save(user));
    }

    public List<MemberResponse> getAll() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    public MemberResponse getById(Long id) {
        return toResponse(getEntityById(id));
    }

    @Transactional
    public MemberResponse update(Long id, MemberRequest request) {
        User user = getEntityById(id);
        apply(user, request);
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public MemberResponse deactivate(Long id) {
        User user = getEntityById(id);
        user.setStatus(UserStatus.INACTIVE);
        return toResponse(userRepository.save(user));
    }

    public User getEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id=" + id));
    }

    private void apply(User user, MemberRequest request) {
        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id=" + request.roleId()));
        user.setRole(role);
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setDob(request.dob());
        user.setJoinDate(request.joinDate());
        user.setStatus(request.status() != null ? request.status() : UserStatus.ACTIVE);
        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        } else if (user.getPassword() == null || user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode("ChangeMe@123"));
        }
    }

    private MemberResponse toResponse(User user) {
        return new MemberResponse(
                user.getId(),
                user.getRole().getId(),
                user.getRole().getName(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getDob(),
                user.getJoinDate(),
                user.getTotalPoints(),
                user.getStatus()
        );
    }
}
