package org.example.dumanagementbackend.service;

import java.util.Set;
import org.example.dumanagementbackend.dto.system.RoleRequest;
import org.example.dumanagementbackend.dto.system.RoleResponse;
import org.example.dumanagementbackend.entity.Role;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.example.dumanagementbackend.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {

    private static final Set<String> SEEDED_ROLE_NAMES = Set.of("ADMIN", "HR", "MEMBER");

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    @Transactional
    @CacheEvict(cacheNames = {"rolesPage", "roleById"}, allEntries = true)
    public RoleResponse create(RoleRequest request) {
        if (roleRepository.existsByName(request.name())) {
            throw new BadRequestException("Role name already exists: " + request.name());
        }
        Role role = new Role();
        role.setName(request.name());
        role.setDescription(request.description());
        return toResponse(roleRepository.save(role));
    }

    @Cacheable(
            cacheNames = "rolesPage",
            key = "{#pageable.pageNumber,#pageable.pageSize,#pageable.sort.toString()}"
    )
    public Page<RoleResponse> getAll(Pageable pageable) {
        Pageable resolvedPageable = PaginationUtils.toZeroBasedPageable(pageable);
        return roleRepository.findByDeletedAtIsNull(resolvedPageable).map(this::toResponse);
    }

    @Cacheable(cacheNames = "roleById", key = "#id")
    public RoleResponse getById(Long id) {
        return toResponse(getEntityById(id));
    }

    @Transactional
    @CacheEvict(cacheNames = {"rolesPage", "roleById"}, allEntries = true)
    public RoleResponse update(Long id, RoleRequest request) {
        Role role = getEntityById(id);
        if (!role.getName().equals(request.name()) && roleRepository.existsByName(request.name())) {
            throw new BadRequestException("Role name already exists: " + request.name());
        }
        role.setName(request.name());
        role.setDescription(request.description());
        return toResponse(roleRepository.save(role));
    }

    @Transactional
    @CacheEvict(cacheNames = {"rolesPage", "roleById"}, allEntries = true)
    public void delete(Long id) {
        Role role = getEntityById(id);
        if (isSeededRole(role)) {
            throw new BadRequestException(
                    "ROLE_SYSTEM_ROLE_ARCHIVE_FORBIDDEN",
                    "Seeded roles ADMIN, HR, and MEMBER cannot be archived."
            );
        }
        if (userRepository.existsByRoleIdAndDeletedAtIsNull(id)) {
            throw new BadRequestException("Cannot archive role because active users are assigned to it.");
        }

        SoftDeleteUtils.markDeleted(role);
        roleRepository.save(role);
    }

    public Role getEntityById(Long id) {
        return roleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id=" + id));
    }

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getName(), role.getDescription());
    }

    private boolean isSeededRole(Role role) {
        return role.getName() != null && SEEDED_ROLE_NAMES.contains(role.getName().toUpperCase());
    }
}
