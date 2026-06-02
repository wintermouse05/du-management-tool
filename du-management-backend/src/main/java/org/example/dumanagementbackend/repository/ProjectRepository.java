package org.example.dumanagementbackend.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.example.dumanagementbackend.entity.Project;
import org.example.dumanagementbackend.entity.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByIdAndDeletedAtIsNull(Long id);

    Page<Project> findByDeletedAtIsNull(Pageable pageable);

    boolean existsByNameAndDeletedAtIsNull(String name);

    boolean existsByNameAndIdNotAndDeletedAtIsNull(String name, Long id);

    @Query("""
            select count(p)
              from Project p
             where p.deletedAt is null
               and p.status = :status
               and p.startTime <= :now
               and p.endTime >= :now
            """)
    long countCurrentlyOpenProjects(
            @Param("now") LocalDateTime now,
            @Param("status") ProjectStatus status
    );
}
