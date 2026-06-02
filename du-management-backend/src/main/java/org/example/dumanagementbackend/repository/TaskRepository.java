package org.example.dumanagementbackend.repository;

import java.util.List;
import java.util.Optional;

import org.example.dumanagementbackend.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("""
            select t
              from Task t
              join fetch t.assignee
             where t.project.id = :projectId
               and t.deletedAt is null
             order by t.deadline asc
            """)
    List<Task> findActiveByProjectId(@Param("projectId") Long projectId);

    @Query("""
            select t
              from Task t
              join fetch t.assignee
              join fetch t.project
             where t.id = :taskId
               and t.project.id = :projectId
               and t.deletedAt is null
            """)
    Optional<Task> findActiveByIdAndProjectId(
            @Param("taskId") Long taskId,
            @Param("projectId") Long projectId
    );

    long countByProjectIdAndDeletedAtIsNull(Long projectId);

    boolean existsByProjectIdAndAssigneeIdAndDeletedAtIsNull(Long projectId, Long assigneeId);
}
