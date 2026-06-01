package org.example.dumanagementbackend.repository;

import java.util.List;
import java.util.Optional;
import org.example.dumanagementbackend.entity.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGroupRepository extends JpaRepository<UserGroup, Long> {

    Optional<UserGroup> findByName(String name);

    Optional<UserGroup> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByName(String name);

    List<UserGroup> findByDeletedAtIsNullOrderByNameAsc();

    List<UserGroup> findByAllGroupTrue();
}
