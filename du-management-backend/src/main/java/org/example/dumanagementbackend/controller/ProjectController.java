package org.example.dumanagementbackend.controller;

import java.util.List;

import org.example.dumanagementbackend.dto.project.ProjectAvailabilitySummaryResponse;
import org.example.dumanagementbackend.dto.project.ProjectMemberRequest;
import org.example.dumanagementbackend.dto.project.ProjectMemberResponse;
import org.example.dumanagementbackend.dto.project.ProjectRequest;
import org.example.dumanagementbackend.dto.project.ProjectResponse;
import org.example.dumanagementbackend.dto.project.ProjectTaskRequest;
import org.example.dumanagementbackend.dto.project.ProjectTaskResponse;
import org.example.dumanagementbackend.service.ProjectService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HR','MEMBER')")
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ResponseEntity<Page<ProjectResponse>> getAll(Pageable pageable) {
        return ResponseEntity.ok(projectService.getAll(pageable));
    }

    @GetMapping("/availability-summary")
    public ResponseEntity<ProjectAvailabilitySummaryResponse> getAvailabilitySummary() {
        return ResponseEntity.ok(projectService.getAvailabilitySummary());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ProjectResponse> update(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.ok(projectService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<ProjectMemberResponse>> getMembers(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getMembers(id));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ProjectMemberResponse> addMember(
            @PathVariable Long id,
            @Valid @RequestBody ProjectMemberRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.addMember(id, request));
    }

    @PutMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ProjectMemberResponse> updateMember(
            @PathVariable Long id,
            @PathVariable Long userId,
            @Valid @RequestBody ProjectMemberRequest request
    ) {
        return ResponseEntity.ok(projectService.updateMember(id, userId, request));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        projectService.removeMember(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/tasks")
    public ResponseEntity<List<ProjectTaskResponse>> getTasks(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getTasks(id));
    }

    @PostMapping("/{id}/tasks")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ProjectTaskResponse> createTask(
            @PathVariable Long id,
            @Valid @RequestBody ProjectTaskRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.createTask(id, request));
    }

    @PutMapping("/{id}/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ProjectTaskResponse> updateTask(
            @PathVariable Long id,
            @PathVariable Long taskId,
            @Valid @RequestBody ProjectTaskRequest request
    ) {
        return ResponseEntity.ok(projectService.updateTask(id, taskId, request));
    }

    @DeleteMapping("/{id}/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id, @PathVariable Long taskId) {
        projectService.deleteTask(id, taskId);
        return ResponseEntity.noContent().build();
    }
}
