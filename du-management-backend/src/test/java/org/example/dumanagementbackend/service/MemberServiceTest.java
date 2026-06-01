package org.example.dumanagementbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.example.dumanagementbackend.dto.member.MemberRequest;
import org.example.dumanagementbackend.dto.member.MemberResponse;
import org.example.dumanagementbackend.dto.member.MemberSkillRequest;
import org.example.dumanagementbackend.entity.Role;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.MemberSkillType;
import org.example.dumanagementbackend.entity.enums.UserStatus;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.RoleRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private MemberService memberService;

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void create_throwsNotFoundWhenRoleMissing() {
        MemberRequest req = new MemberRequest(99L, "user1", "user@b.com", "pass1234",
                "Full Name", null, null, UserStatus.ACTIVE);
        when(roleRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> memberService.create(req));
    }

    @Test
    void create_savesUserWithDefaultPoints() {
        Role role = buildRole(1L, "MEMBER");
        MemberRequest req = new MemberRequest(1L, "newuser", "new@b.com", "password1",
                "New User", null, LocalDate.now(), UserStatus.ACTIVE);

        when(roleRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("password1")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(5L);
            return u;
        });

        MemberResponse response = memberService.create(req);

        assertEquals(5L, response.id());
        assertEquals("newuser", response.username());
        assertEquals("MEMBER", response.roleName());
        assertEquals(0, response.totalPoints());
    }

    @Test
    void create_usesDefaultPasswordWhenNoneProvided() {
        Role role = buildRole(1L, "MEMBER");
        MemberRequest req = new MemberRequest(1L, "user2", "u2@b.com", null,
                "User Two", null, null, UserStatus.ACTIVE);

        when(roleRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("ChangeMe@123")).thenReturn("default-hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(6L);
            return u;
        });

        memberService.create(req);

        verify(passwordEncoder).encode("ChangeMe@123");
    }

    @Test
    void create_mapsMemberSkills() {
        Role role = buildRole(1L, "MEMBER");
        MemberRequest req = new MemberRequest(1L, "skilled", "skilled@b.com", "password1",
                "Skilled User", null, null, UserStatus.ACTIVE, List.of(
                new MemberSkillRequest(MemberSkillType.BACKEND_DEVELOPER, 4),
                new MemberSkillRequest(MemberSkillType.QA_ENGINEER, 2)
        ));

        when(roleRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("password1")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(7L);
            return u;
        });

        MemberResponse response = memberService.create(req);

        assertEquals(2, response.skills().size());
        assertEquals(MemberSkillType.BACKEND_DEVELOPER, response.skills().get(0).skill());
        assertEquals(4, response.skills().get(0).level());
    }

    @Test
    void create_throwsBadRequestWhenEmailAlreadyExists() {
        MemberRequest req = new MemberRequest(1L, "newuser", "existing@b.com", "password1",
                "New User", null, null, UserStatus.ACTIVE);
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("existing@b.com")).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> memberService.create(req));

        assertEquals("AUTH_EMAIL_EXISTS", ex.getErrorCode());
        assertEquals("Email already exists.", ex.getMessage());
    }

    // ── getAll ───────────────────────────────────────────────────────────────

    @Test
    void getAll_returnsMappedPage() {
        Role role = buildRole(1L, "MEMBER");
        User u1 = buildUser(1L, "alice", role);
        User u2 = buildUser(2L, "bob", role);
        Pageable pageable = PageRequest.of(0, 5);
        Page<User> page = new PageImpl<>(List.of(u1, u2), pageable, 2);

        when(userRepository.searchMembers(eq("%"), eq(UserStatus.ACTIVE), eq(false), any(Pageable.class))).thenReturn(page);

        Page<MemberResponse> result = memberService.getAll(pageable, false, false);

        assertEquals(2, result.getTotalElements());
        assertEquals("alice", result.getContent().get(0).username());
        verify(userRepository).searchMembers(eq("%"), eq(UserStatus.ACTIVE), eq(false), any(Pageable.class));
    }

    @Test
    void getAll_doesNotForceActiveStatusForAdminRole() {
        Role role = buildRole(1L, "MEMBER");
        User u1 = buildUser(1L, "alice", role);
        Pageable pageable = PageRequest.of(0, 5);
        Page<User> page = new PageImpl<>(List.of(u1), pageable, 1);

        when(userRepository.searchMembers(eq("%"), isNull(), eq(false), any(Pageable.class))).thenReturn(page);

        Page<MemberResponse> result = memberService.getAll(pageable, false, true);

        assertEquals(1, result.getTotalElements());
        verify(userRepository).searchMembers(eq("%"), isNull(), eq(false), any(Pageable.class));
    }

    @Test
    void search_returnsEmptyPageWhenNonAdminRequestsInactiveMembers() {
        Pageable pageable = PageRequest.of(0, 5);

        Page<MemberResponse> result = memberService.search(null, UserStatus.INACTIVE, pageable, false, false);

        assertEquals(0, result.getTotalElements());
        verify(userRepository, never()).searchMembers(any(), any(), anyBoolean(), any(Pageable.class));
    }

    // ── getById ──────────────────────────────────────────────────────────────

    @Test
    void getById_throwsNotFoundWhenUserMissing() {
        when(userRepository.findByIdAndDeletedAtIsNull(77L)).thenReturn(Optional.empty());
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> memberService.getById(77L));
        assertEquals("User not found with id=77", ex.getMessage());
    }

    @Test
    void getById_returnsMemberResponse() {
        Role role = buildRole(1L, "MEMBER");
        User user = buildUser(3L, "carol", role);
        when(userRepository.findByIdAndDeletedAtIsNull(3L)).thenReturn(Optional.of(user));

        MemberResponse response = memberService.getById(3L);
        assertEquals(3L, response.id());
        assertEquals("carol", response.username());
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void update_updatesAndReturnsResponse() {
        Role role = buildRole(1L, "MEMBER");
        User existing = buildUser(4L, "oldname", role);
        MemberRequest req = new MemberRequest(1L, "newname", "new@b.com", null,
                "New Full Name", null, null, UserStatus.ACTIVE);

        when(userRepository.findByIdAndDeletedAtIsNull(4L)).thenReturn(Optional.of(existing));
        when(roleRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(role));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        MemberResponse response = memberService.update(4L, req);

        assertEquals("newname", response.username());
        assertEquals("New Full Name", response.fullName());
    }

    @Test
    void update_throwsBadRequestWhenEmailAlreadyExistsOnAnotherUser() {
        Role role = buildRole(1L, "MEMBER");
        User existing = buildUser(4L, "oldname", role);
        MemberRequest req = new MemberRequest(1L, "newname", "existing@b.com", null,
                "New Full Name", null, null, UserStatus.ACTIVE);

        when(userRepository.findByIdAndDeletedAtIsNull(4L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByUsernameAndIdNot("newname", 4L)).thenReturn(false);
        when(userRepository.existsByEmailAndIdNot("existing@b.com", 4L)).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> memberService.update(4L, req));

        assertEquals("AUTH_EMAIL_EXISTS", ex.getErrorCode());
        assertEquals("Email already exists.", ex.getMessage());
    }

    // ── deactivate ────────────────────────────────────────────────────────────

    @Test
    void deactivate_setsStatusInactive() {
        Role role = buildRole(1L, "MEMBER");
        User user = buildUser(5L, "activemember", role);
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        MemberResponse response = memberService.deactivate(5L);

        assertEquals(UserStatus.INACTIVE, response.status());
    }

    @Test
    void deactivate_throwsNotFoundWhenUserMissing() {
        when(userRepository.findByIdAndDeletedAtIsNull(55L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> memberService.deactivate(55L));
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    void delete_archivesMemberAndRevokesRefreshTokens() {
        Role role = buildRole(1L, "MEMBER");
        User user = buildUser(6L, "deleteuser", role);

        when(userRepository.findByIdAndDeletedAtIsNull(6L)).thenReturn(Optional.of(user));

        memberService.delete(6L);

        assertEquals(UserStatus.INACTIVE, user.getStatus());
        assertTrue(user.isDeleted());
        verify(userRepository).save(user);
        verify(refreshTokenService).revokeActiveByUserId(6L, "USER_ARCHIVED");
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void delete_throwsBadRequestForSystemAdminAccount() {
        Role role = buildRole(1L, "ADMIN");
        User user = buildUser(1L, "admin", role);

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> memberService.delete(1L));

        assertEquals("MEMBER_SYSTEM_ACCOUNT_ARCHIVE_FORBIDDEN", ex.getErrorCode());
        assertEquals("The system admin account cannot be archived.", ex.getMessage());
        verify(userRepository, never()).delete(any(User.class));
        verify(userRepository, never()).save(any(User.class));
        verify(refreshTokenService, never()).revokeActiveByUserId(any(), any());
    }

    // ── exportCsv ─────────────────────────────────────────────────────────────

    @Test
    void exportCsv_includesAllColumns() {
        Role role = buildRole(1L, "MEMBER");
        User user = buildUser(1L, "exportuser", role);
        user.setEmail("export@test.com");
        user.setFullName("Export User");
        user.setJoinDate(LocalDate.of(2024, 1, 1));
        user.setStatus(UserStatus.ACTIVE);
        user.setTotalPoints(100);

        when(userRepository.searchMembersForExport(any(), eq(UserStatus.ACTIVE), anyBoolean())).thenReturn(List.of(user));

        byte[] csv = memberService.exportCsv(null, null, false, false);
        String content = new String(csv, java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(content.startsWith("id,username,email,fullName,role,status,joinDate,tenureMonths,totalPoints,skills\n"));
        assertTrue(content.contains("exportuser"));
        assertTrue(content.contains("export@test.com"));
        assertTrue(content.contains("MEMBER"));
        assertTrue(content.contains("100"));
    }

    @Test
    void exportCsv_returnsOnlyHeaderWhenNonAdminRequestsInactiveMembers() {
        byte[] csv = memberService.exportCsv(null, UserStatus.INACTIVE, false, false);
        String content = new String(csv, java.nio.charset.StandardCharsets.UTF_8);

        assertEquals("id,username,email,fullName,role,status,joinDate,tenureMonths,totalPoints,skills\n", content);
        verify(userRepository, never()).searchMembersForExport(any(), any(), anyBoolean());
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
        user.setEmail(username + "@test.com");
        user.setFullName(username + " FullName");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setTotalPoints(0);
        return user;
    }
}
