package org.example.dumanagementbackend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.example.dumanagementbackend.entity.Task;
import org.example.dumanagementbackend.entity.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("""
            select distinct t
              from Task t
              join fetch t.project
              left join fetch t.assignees
             where t.project.id = :projectId
               and t.deletedAt is null
             order by t.deadline asc
            """)
    List<Task> findActiveByProjectId(@Param("projectId") Long projectId);

    @Query("""
            select distinct t
              from Task t
              join fetch t.project
              left join fetch t.assignees
             where t.id = :taskId
               and t.project.id = :projectId
               and t.deletedAt is null
            """)
    Optional<Task> findActiveByIdAndProjectId(
            @Param("taskId") Long taskId,
            @Param("projectId") Long projectId
    );

    long countByProjectIdAndDeletedAtIsNull(Long projectId);

    @Query("""
            select t
              from Task t
              join fetch t.project
             where t.deletedAt is null
               and t.project.deletedAt is null
               and t.deadline <= :now
               and t.status <> :doneStatus
             order by t.deadline asc
            """)
    List<Task> findOverdueActiveTasks(
            @Param("now") LocalDateTime now,
            @Param("doneStatus") TaskStatus doneStatus
    );

    @Query("""
            select case when count(t) > 0 then true else false end
              from Task t
              join t.assignees assignee
             where t.project.id = :projectId
               and assignee.id = :assigneeId
               and t.deletedAt is null
            """)
    boolean existsActiveByProjectIdAndAssigneeId(
            @Param("projectId") Long projectId,
            @Param("assigneeId") Long assigneeId
    );
}
