package org.example.dumanagementbackend.repository;

import java.util.List;
import java.util.Optional;

import org.example.dumanagementbackend.entity.ProjectMember;
import org.example.dumanagementbackend.entity.ProjectMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberId> {

    @Query("""
            select pm
              from ProjectMember pm
              join fetch pm.user
             where pm.project.id = :projectId
             order by pm.user.fullName asc
            """)
    List<ProjectMember> findByProjectId(@Param("projectId") Long projectId);

    @Query("""
            select pm
              from ProjectMember pm
              join fetch pm.user
              join fetch pm.project
             where pm.project.id = :projectId
               and pm.user.id = :userId
            """)
    Optional<ProjectMember> findByProjectIdAndUserId(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId
    );

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    long countByProjectId(Long projectId);

    @Modifying
    void deleteByProjectIdAndUserId(Long projectId, Long userId);
}
