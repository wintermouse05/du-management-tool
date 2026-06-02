package org.example.dumanagementbackend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.example.dumanagementbackend.dto.project.AvailableProjectMemberResponse;
import org.example.dumanagementbackend.dto.project.ProjectMemberRequest;
import org.example.dumanagementbackend.dto.project.ProjectMemberResponse;
import org.example.dumanagementbackend.dto.project.ProjectRequest;
import org.example.dumanagementbackend.dto.project.ProjectAvailabilitySummaryResponse;
import org.example.dumanagementbackend.dto.project.ProjectResponse;
import org.example.dumanagementbackend.dto.project.ProjectTaskRequest;
import org.example.dumanagementbackend.dto.project.ProjectTaskResponse;
import org.example.dumanagementbackend.entity.Project;
import org.example.dumanagementbackend.entity.ProjectMember;
import org.example.dumanagementbackend.entity.ProjectMemberId;
import org.example.dumanagementbackend.entity.Task;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.ProjectStatus;
import org.example.dumanagementbackend.entity.enums.UserStatus;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.ProjectMemberRepository;
import org.example.dumanagementbackend.repository.ProjectRepository;
import org.example.dumanagementbackend.repository.TaskRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public Page<ProjectResponse> getAll(Pageable pageable) {
        Pageable resolvedPageable = PaginationUtils.toZeroBasedPageable(pageable);
        return projectRepository.findByDeletedAtIsNull(resolvedPageable).map(this::toProjectResponse);
    }

    public ProjectAvailabilitySummaryResponse getAvailabilitySummary() {
        LocalDateTime now = LocalDateTime.now();
        List<AvailableProjectMemberResponse> availableMembers = getAvailableMembers(now);
        return new ProjectAvailabilitySummaryResponse(
                projectRepository.countCurrentlyOpenProjects(now, ProjectStatus.ACTIVE),
                availableMembers.size(),
                now
        );
    }

    public List<AvailableProjectMemberResponse> getAvailableMembers() {
        return getAvailableMembers(LocalDateTime.now());
    }

    public ProjectResponse getById(Long id) {
        return toProjectResponse(getProject(id));
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request) {
        validateProjectRange(request.startTime(), request.endTime());
        String name = normalizeName(request.name());
        if (projectRepository.existsByNameAndDeletedAtIsNull(name)) {
            throw new BadRequestException("Project name already exists: " + name);
        }

        Project project = new Project();
        applyProject(project, request, name);
        return toProjectResponse(projectRepository.save(project));
    }

    @Transactional
    public ProjectResponse update(Long id, ProjectRequest request) {
        Project project = getProject(id);
        validateProjectRange(request.startTime(), request.endTime());
        String name = normalizeName(request.name());
        if (!project.getName().equals(name) && projectRepository.existsByNameAndIdNotAndDeletedAtIsNull(name, id)) {
            throw new BadRequestException("Project name already exists: " + name);
        }

        applyProject(project, request, name);
        return toProjectResponse(projectRepository.save(project));
    }

    @Transactional
    public void delete(Long id) {
        Project project = getProject(id);
        SoftDeleteUtils.markDeleted(project);
        projectRepository.save(project);
    }

    public List<ProjectMemberResponse> getMembers(Long projectId) {
        getProject(projectId);
        return projectMemberRepository.findByProjectId(projectId).stream()
                .filter(member -> !member.getUser().isDeleted())
                .filter(member -> !SystemAccountUtils.isAdminAccount(member.getUser()))
                .map(this::toProjectMemberResponse)
                .toList();
    }

    @Transactional
    public ProjectMemberResponse addMember(Long projectId, ProjectMemberRequest request) {
        Project project = getProject(projectId);
        validateMemberRange(request.participationStartTime(), request.expectedEndTime());
        User user = getAssignableUser(request.userId());

        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, request.userId())) {
            throw new BadRequestException("User is already a member of this project");
        }

        ProjectMember member = new ProjectMember();
        ProjectMemberId id = new ProjectMemberId();
        id.setProjectId(projectId);
        id.setUserId(request.userId());
        member.setId(id);
        member.setProject(project);
        member.setUser(user);
        applyProjectMember(member, request);
        return toProjectMemberResponse(projectMemberRepository.save(member));
    }

    @Transactional
    public ProjectMemberResponse updateMember(Long projectId, Long userId, ProjectMemberRequest request) {
        if (!userId.equals(request.userId())) {
            throw new BadRequestException("userId in path and request body must match");
        }
        getProject(projectId);
        validateMemberRange(request.participationStartTime(), request.expectedEndTime());
        getAssignableUser(userId);

        ProjectMember member = getProjectMember(projectId, userId);
        applyProjectMember(member, request);
        return toProjectMemberResponse(projectMemberRepository.save(member));
    }

    @Transactional
    public void removeMember(Long projectId, Long userId) {
        getProject(projectId);
        getProjectMember(projectId, userId);
        if (taskRepository.existsByProjectIdAndAssigneeIdAndDeletedAtIsNull(projectId, userId)) {
            throw new BadRequestException("Cannot remove a project member with assigned active tasks");
        }
        projectMemberRepository.deleteByProjectIdAndUserId(projectId, userId);
    }

    public List<ProjectTaskResponse> getTasks(Long projectId) {
        getProject(projectId);
        return taskRepository.findActiveByProjectId(projectId).stream()
                .map(this::toProjectTaskResponse)
                .toList();
    }

    @Transactional
    public ProjectTaskResponse createTask(Long projectId, ProjectTaskRequest request) {
        Project project = getProject(projectId);
        validateTaskRange(request.startTime(), request.deadline());
        User assignee = getTaskAssignee(projectId, request.assigneeId());

        Task task = new Task();
        task.setProject(project);
        task.setAssignee(assignee);
        applyTask(task, request);
        return toProjectTaskResponse(taskRepository.save(task));
    }

    @Transactional
    public ProjectTaskResponse updateTask(Long projectId, Long taskId, ProjectTaskRequest request) {
        getProject(projectId);
        validateTaskRange(request.startTime(), request.deadline());
        User assignee = getTaskAssignee(projectId, request.assigneeId());

        Task task = getTask(projectId, taskId);
        task.setAssignee(assignee);
        applyTask(task, request);
        return toProjectTaskResponse(taskRepository.save(task));
    }

    @Transactional
    public void deleteTask(Long projectId, Long taskId) {
        getProject(projectId);
        Task task = getTask(projectId, taskId);
        SoftDeleteUtils.markDeleted(task);
        taskRepository.save(task);
    }

    private Project getProject(Long id) {
        return projectRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id=" + id));
    }

    private List<AvailableProjectMemberResponse> getAvailableMembers(LocalDateTime now) {
        return userRepository.findAvailableProjectMembers(
                        now,
                        UserStatus.ACTIVE,
                        ProjectStatus.ACTIVE,
                        SystemAccountUtils.ADMIN_USERNAME
                ).stream()
                .map(this::toAvailableMemberResponse)
                .toList();
    }

    private ProjectMember getProjectMember(Long projectId, Long userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project member not found with projectId=" + projectId + ", userId=" + userId));
    }

    private Task getTask(Long projectId, Long taskId) {
        return taskRepository.findActiveByIdAndProjectId(taskId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Task not found with projectId=" + projectId + ", taskId=" + taskId));
    }

    private User getAssignableUser(Long userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id=" + userId));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Only active users can be assigned to projects");
        }
        if (SystemAccountUtils.isAdminAccount(user)) {
            throw new BadRequestException("Cannot add admin account to project member lists");
        }
        return user;
    }

    private User getTaskAssignee(Long projectId, Long assigneeId) {
        User user = getAssignableUser(assigneeId);
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, assigneeId)) {
            throw new BadRequestException("Task assignee must be a member of the project");
        }
        return user;
    }

    private void applyProject(Project project, ProjectRequest request, String name) {
        project.setName(name);
        project.setStatus(request.status());
        project.setStartTime(request.startTime());
        project.setEndTime(request.endTime());
    }

    private void applyProjectMember(ProjectMember member, ProjectMemberRequest request) {
        member.setProjectRole(request.projectRole());
        member.setParticipationStartTime(request.participationStartTime());
        member.setExpectedEndTime(request.expectedEndTime());
    }

    private void applyTask(Task task, ProjectTaskRequest request) {
        task.setName(normalizeName(request.name()));
        task.setStatus(request.status());
        task.setStartTime(request.startTime());
        task.setDeadline(request.deadline());
    }

    private void validateProjectRange(LocalDateTime startTime, LocalDateTime endTime) {
        validateRange(startTime, endTime, "Project start time must be before or equal to end time");
    }

    private void validateMemberRange(LocalDateTime startTime, LocalDateTime endTime) {
        validateRange(startTime, endTime, "Participation start time must be before or equal to expected end time");
    }

    private void validateTaskRange(LocalDateTime startTime, LocalDateTime deadline) {
        validateRange(startTime, deadline, "Task start time must be before or equal to deadline");
    }

    private void validateRange(LocalDateTime start, LocalDateTime end, String message) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new BadRequestException(message);
        }
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim();
    }

    private ProjectResponse toProjectResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getStatus(),
                project.getStatus().getLabel(),
                project.getStartTime(),
                project.getEndTime(),
                Math.toIntExact(projectMemberRepository.countByProjectId(project.getId())),
                Math.toIntExact(taskRepository.countByProjectIdAndDeletedAtIsNull(project.getId()))
        );
    }

    private ProjectMemberResponse toProjectMemberResponse(ProjectMember member) {
        User user = member.getUser();
        return new ProjectMemberResponse(
                member.getProject().getId(),
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                member.getProjectRole(),
                member.getProjectRole().getLabel(),
                member.getParticipationStartTime(),
                member.getExpectedEndTime()
        );
    }

    private ProjectTaskResponse toProjectTaskResponse(Task task) {
        User assignee = task.getAssignee();
        return new ProjectTaskResponse(
                task.getId(),
                task.getProject().getId(),
                task.getProject().getName(),
                task.getName(),
                task.getStatus(),
                task.getStatus().getLabel(),
                assignee.getId(),
                assignee.getUsername(),
                assignee.getFullName(),
                task.getStartTime(),
                task.getDeadline()
        );
    }

    private AvailableProjectMemberResponse toAvailableMemberResponse(User user) {
        return new AvailableProjectMemberResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole() != null ? user.getRole().getName() : null
        );
    }
}
