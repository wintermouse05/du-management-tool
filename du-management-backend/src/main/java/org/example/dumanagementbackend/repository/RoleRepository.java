package org.example.dumanagementbackend.repository;

import org.example.dumanagementbackend.entity.Role;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    Optional<Role> findByNameAndDeletedAtIsNull(String name);

    Optional<Role> findByIdAndDeletedAtIsNull(Long id);

    Page<Role> findByDeletedAtIsNull(Pageable pageable);

    boolean existsByName(String name);
}
