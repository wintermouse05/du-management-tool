package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.dto.system.RoleRequest;
import org.example.dumanagementbackend.dto.system.RoleResponse;
import org.example.dumanagementbackend.entity.Role;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.RoleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    public RoleResponse create(RoleRequest request) {
        Role role = new Role();
        role.setName(request.name());
        role.setDescription(request.description());
        return toResponse(roleRepository.save(role));
    }

    public List<RoleResponse> getAll() {
        return roleRepository.findAll().stream().map(this::toResponse).toList();
    }

    public RoleResponse getById(Long id) {
        return toResponse(getEntityById(id));
    }

    public RoleResponse update(Long id, RoleRequest request) {
        Role role = getEntityById(id);
        role.setName(request.name());
        role.setDescription(request.description());
        return toResponse(roleRepository.save(role));
    }

    public void delete(Long id) {
        roleRepository.delete(getEntityById(id));
    }

    public Role getEntityById(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id=" + id));
    }

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getName(), role.getDescription());
    }
}
