package org.example.dumanagementbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.example.dumanagementbackend.dto.system.RoleRequest;
import org.example.dumanagementbackend.dto.system.RoleResponse;
import org.example.dumanagementbackend.entity.Role;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private RoleService roleService;

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void create_savesAndReturnsRoleResponse() {
        RoleRequest req = new RoleRequest("MANAGER", "Manages projects");
        Role saved = buildRole(1L, "MANAGER", "Manages projects");

        when(roleRepository.save(any(Role.class))).thenReturn(saved);

        RoleResponse response = roleService.create(req);

        assertEquals(1L, response.id());
        assertEquals("MANAGER", response.name());
        assertEquals("Manages projects", response.description());
    }

    // ── getAll ───────────────────────────────────────────────────────────────

    @Test
    void getAll_returnsMappedPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Role r1 = buildRole(1L, "ADMIN", "Administrator");
        Role r2 = buildRole(2L, "MEMBER", "Regular member");
        Page<Role> page = new PageImpl<>(List.of(r1, r2), pageable, 2);

        when(roleRepository.findAll(pageable)).thenReturn(page);

        Page<RoleResponse> result = roleService.getAll(pageable);

        assertEquals(2, result.getTotalElements());
        assertEquals("ADMIN", result.getContent().get(0).name());
        assertEquals("MEMBER", result.getContent().get(1).name());
    }

    // ── getById ──────────────────────────────────────────────────────────────

    @Test
    void getById_throwsNotFoundWhenRoleMissing() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> roleService.getById(99L));
        assertEquals("Role not found with id=99", ex.getMessage());
    }

    @Test
    void getById_returnsRoleWhenFound() {
        Role role = buildRole(3L, "HR", "Human Resources");
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));

        RoleResponse response = roleService.getById(3L);

        assertEquals(3L, response.id());
        assertEquals("HR", response.name());
        assertEquals("Human Resources", response.description());
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void update_throwsNotFoundWhenRoleMissing() {
        when(roleRepository.findById(88L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> roleService.update(88L, new RoleRequest("NEW", "desc")));
    }

    @Test
    void update_updatesAndReturnsResponse() {
        Role role = buildRole(2L, "OLD", "Old description");
        when(roleRepository.findById(2L)).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleResponse response = roleService.update(2L, new RoleRequest("UPDATED", "New description"));

        assertEquals("UPDATED", response.name());
        assertEquals("New description", response.description());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_throwsNotFoundWhenRoleMissing() {
        when(roleRepository.findById(77L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> roleService.delete(77L));
    }

    @Test
    void delete_deletesRole() {
        Role role = buildRole(4L, "TEMP", "Temporary role");
        when(roleRepository.findById(4L)).thenReturn(Optional.of(role));

        roleService.delete(4L);

        verify(roleRepository).delete(role);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Role buildRole(Long id, String name, String description) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        role.setDescription(description);
        return role;
    }
}
