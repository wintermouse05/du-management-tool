package org.example.dumanagementbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import org.example.dumanagementbackend.entity.Role;
import org.example.dumanagementbackend.entity.Task;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.ProjectRole;
import org.example.dumanagementbackend.entity.enums.ProjectStatus;
import org.example.dumanagementbackend.entity.enums.TaskStatus;
import org.example.dumanagementbackend.entity.enums.UserStatus;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.repository.ProjectMemberRepository;
import org.example.dumanagementbackend.repository.ProjectRepository;
import org.example.dumanagementbackend.repository.TaskRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class ProjectServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 6, 1, 9, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 7, 1, 18, 0);

    @Test
    void create_savesProjectWithLifecycleStatus() {
        Harness harness = new Harness();

        ProjectResponse response = harness.service.create(new ProjectRequest(
                " Apollo ",
                ProjectStatus.ACTIVE,
                START,
                END
        ));

        assertEquals(1L, response.id());
        assertEquals("Apollo", response.name());
        assertEquals(ProjectStatus.ACTIVE, response.status());
        assertEquals(1, harness.projects.size());
    }

    @Test
    void getAll_normalizesOneBasedPageNumberLikeOtherFeatures() {
        Harness harness = new Harness();
        harness.addProject(buildProject(1L));

        harness.service.getAll(PageRequest.of(2, 10));

        assertEquals(1, harness.lastProjectPageable.getPageNumber());
        assertEquals(10, harness.lastProjectPageable.getPageSize());
    }

    @Test
    void getAvailabilitySummary_countsOpenProjectsAndAvailableMembers() {
        Harness harness = new Harness();
        LocalDateTime now = LocalDateTime.now();
        Project openProject = buildProjectWithRange(1L, ProjectStatus.ACTIVE, now.minusDays(1), now.plusDays(1));
        Project onHoldProject = buildProjectWithRange(2L, ProjectStatus.ON_HOLD, now.minusDays(1), now.plusDays(1));
        Project endedProject = buildProjectWithRange(3L, ProjectStatus.ACTIVE, now.minusDays(10), now.minusDays(5));
        harness.addProject(openProject);
        harness.addProject(onHoldProject);
        harness.addProject(endedProject);

        User busyUser = buildUser(2L, "busy", UserStatus.ACTIVE);
        User noProjectUser = buildUser(3L, "free", UserStatus.ACTIVE);
        User endedParticipationUser = buildUser(4L, "ended", UserStatus.ACTIVE);
        User onHoldProjectUser = buildUser(5L, "onhold", UserStatus.ACTIVE);
        User endedProjectUser = buildUser(6L, "endedproject", UserStatus.ACTIVE);
        User adminUser = buildUser(7L, "admin", UserStatus.ACTIVE);
        User inactiveUser = buildUser(8L, "inactive", UserStatus.INACTIVE);
        List.of(busyUser, noProjectUser, endedParticipationUser, onHoldProjectUser, endedProjectUser, adminUser, inactiveUser)
                .forEach(harness::addUser);

        harness.addProjectMember(buildMembership(openProject, busyUser, ProjectRole.BACKEND_DEVELOPER,
                now.minusHours(1), now.plusHours(1)));
        harness.addProjectMember(buildMembership(openProject, endedParticipationUser, ProjectRole.FRONTEND_DEVELOPER,
                now.minusDays(2), now.minusDays(1)));
        harness.addProjectMember(buildMembership(onHoldProject, onHoldProjectUser, ProjectRole.QA_ENGINEER,
                now.minusHours(1), now.plusHours(1)));
        harness.addProjectMember(buildMembership(endedProject, endedProjectUser, ProjectRole.TECH_LEAD,
                now.minusHours(1), now.plusHours(1)));

        ProjectAvailabilitySummaryResponse summary = harness.service.getAvailabilitySummary();

        assertEquals(1, summary.openProjectCount());
        assertEquals(4, summary.availableMemberCount());
        assertEquals(List.of(4L, 6L, 3L, 5L), harness.service.getAvailableMembers().stream()
                .map(member -> member.id())
                .toList());
    }

    @Test
    void create_rejectsDuplicateActiveProjectName() {
        Harness harness = new Harness();
        harness.addProject(buildProject(1L));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> harness.service.create(
                new ProjectRequest("Apollo", ProjectStatus.ACTIVE, START, END)
        ));

        assertEquals("Project name already exists: Apollo", ex.getMessage());
        assertEquals(1, harness.projects.size());
    }

    @Test
    void create_rejectsProjectDateRangeWhenStartIsAfterEnd() {
        Harness harness = new Harness();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> harness.service.create(
                new ProjectRequest("Apollo", ProjectStatus.ACTIVE, END, START)
        ));

        assertEquals("Project start time must be before or equal to end time", ex.getMessage());
        assertEquals(0, harness.projects.size());
    }

    @Test
    void delete_archivesProject() {
        Harness harness = new Harness();
        Project project = buildProject(1L);
        harness.addProject(project);

        harness.service.delete(1L);

        assertTrue(project.isDeleted());
        assertEquals(project, harness.projects.get(1L));
    }

    @Test
    void addMember_savesProjectMembershipWithRoleAndDates() {
        Harness harness = new Harness();
        Project project = buildProject(1L);
        User user = buildUser(2L, "member", UserStatus.ACTIVE);
        harness.addProject(project);
        harness.addUser(user);

        ProjectMemberResponse response = harness.service.addMember(1L, new ProjectMemberRequest(
                2L,
                ProjectRole.BACKEND_DEVELOPER,
                START,
                END
        ));

        assertEquals(1L, response.projectId());
        assertEquals(2L, response.userId());
        assertEquals(ProjectRole.BACKEND_DEVELOPER, response.projectRole());
        assertEquals(1, harness.projectMembers.size());
    }

    @Test
    void addMember_rejectsDuplicateMembership() {
        Harness harness = new Harness();
        Project project = buildProject(1L);
        User user = buildUser(2L, "member", UserStatus.ACTIVE);
        harness.addProject(project);
        harness.addUser(user);
        harness.addProjectMember(buildMembership(project, user, ProjectRole.BACKEND_DEVELOPER));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> harness.service.addMember(
                1L,
                new ProjectMemberRequest(2L, ProjectRole.BACKEND_DEVELOPER, START, END)
        ));

        assertEquals("User is already a member of this project", ex.getMessage());
        assertEquals(1, harness.projectMembers.size());
    }

    @Test
    void addMember_rejectsInactiveUser() {
        Harness harness = new Harness();
        Project project = buildProject(1L);
        User user = buildUser(2L, "member", UserStatus.INACTIVE);
        harness.addProject(project);
        harness.addUser(user);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> harness.service.addMember(
                1L,
                new ProjectMemberRequest(2L, ProjectRole.BACKEND_DEVELOPER, START, END)
        ));

        assertEquals("Only active users can be assigned to projects", ex.getMessage());
        assertEquals(0, harness.projectMembers.size());
    }

    @Test
    void updateMember_rejectsMismatchedRequestUserId() {
        Harness harness = new Harness();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> harness.service.updateMember(
                1L,
                2L,
                new ProjectMemberRequest(3L, ProjectRole.TECH_LEAD, START, END)
        ));

        assertEquals("userId in path and request body must match", ex.getMessage());
    }

    @Test
    void removeMember_rejectsMemberWithAssignedActiveTasks() {
        Harness harness = new Harness();
        Project project = buildProject(1L);
        User user = buildUser(2L, "member", UserStatus.ACTIVE);
        harness.addProject(project);
        harness.addUser(user);
        harness.addProjectMember(buildMembership(project, user, ProjectRole.FRONTEND_DEVELOPER));
        harness.addTask(buildTask(10L, project, user));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> harness.service.removeMember(1L, 2L));

        assertEquals("Cannot remove a project member with assigned active tasks", ex.getMessage());
        assertEquals(1, harness.projectMembers.size());
    }

    @Test
    void createTask_savesProjectScopedTaskForProjectMember() {
        Harness harness = new Harness();
        Project project = buildProject(1L);
        User assignee = buildUser(2L, "member", UserStatus.ACTIVE);
        harness.addProject(project);
        harness.addUser(assignee);
        harness.addProjectMember(buildMembership(project, assignee, ProjectRole.BACKEND_DEVELOPER));

        ProjectTaskResponse response = harness.service.createTask(1L, new ProjectTaskRequest(
                "API work",
                TaskStatus.IN_PROGRESS,
                2L,
                START,
                END
        ));

        assertEquals(10L, response.id());
        assertEquals(1L, response.projectId());
        assertEquals(2L, response.assigneeId());
        assertEquals(TaskStatus.IN_PROGRESS, response.status());
        assertEquals(1, harness.tasks.size());
    }

    @Test
    void createTask_rejectsAssigneeWhoIsNotProjectMember() {
        Harness harness = new Harness();
        Project project = buildProject(1L);
        User assignee = buildUser(2L, "member", UserStatus.ACTIVE);
        harness.addProject(project);
        harness.addUser(assignee);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> harness.service.createTask(
                1L,
                new ProjectTaskRequest("API work", TaskStatus.TODO, 2L, START, END)
        ));

        assertEquals("Task assignee must be a member of the project", ex.getMessage());
        assertEquals(0, harness.tasks.size());
    }

    @Test
    void createTask_rejectsTaskDateRangeWhenStartIsAfterDeadline() {
        Harness harness = new Harness();
        harness.addProject(buildProject(1L));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> harness.service.createTask(
                1L,
                new ProjectTaskRequest("API work", TaskStatus.TODO, 2L, END, START)
        ));

        assertEquals("Task start time must be before or equal to deadline", ex.getMessage());
        assertEquals(0, harness.tasks.size());
    }

    private static Project buildProject(Long id) {
        return buildProjectWithRange(id, ProjectStatus.ACTIVE, START, END);
    }

    private static Project buildProjectWithRange(Long id, ProjectStatus status, LocalDateTime startTime, LocalDateTime endTime) {
        Project project = new Project();
        project.setId(id);
        project.setName("Apollo");
        project.setStatus(status);
        project.setStartTime(startTime);
        project.setEndTime(endTime);
        return project;
    }

    private static User buildUser(Long id, String username, UserStatus status) {
        Role role = new Role();
        role.setId(id);
        role.setName("MEMBER");

        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFullName(username + " User");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }

    private static ProjectMember buildMembership(Project project, User user, ProjectRole role) {
        return buildMembership(project, user, role, START, END);
    }

    private static ProjectMember buildMembership(
            Project project,
            User user,
            ProjectRole role,
            LocalDateTime participationStartTime,
            LocalDateTime expectedEndTime
    ) {
        ProjectMember member = new ProjectMember();
        ProjectMemberId id = new ProjectMemberId();
        id.setProjectId(project.getId());
        id.setUserId(user.getId());
        member.setId(id);
        member.setProject(project);
        member.setUser(user);
        member.setProjectRole(role);
        member.setParticipationStartTime(participationStartTime);
        member.setExpectedEndTime(expectedEndTime);
        return member;
    }

    private static Task buildTask(Long id, Project project, User assignee) {
        Task task = new Task();
        task.setId(id);
        task.setProject(project);
        task.setName("API work");
        task.setStatus(TaskStatus.TODO);
        task.setAssignee(assignee);
        task.setStartTime(START);
        task.setDeadline(END);
        return task;
    }

    private static final class Harness {

        private final Map<Long, Project> projects = new HashMap<>();
        private final Map<Long, User> users = new HashMap<>();
        private final Map<String, ProjectMember> projectMembers = new HashMap<>();
        private final Map<Long, Task> tasks = new HashMap<>();
        private long nextProjectId = 1L;
        private long nextTaskId = 10L;
        private Pageable lastProjectPageable;
        private final ProjectService service = new ProjectService(
                projectRepository(),
                projectMemberRepository(),
                taskRepository(),
                userRepository()
        );

        private void addProject(Project project) {
            projects.put(project.getId(), project);
            nextProjectId = Math.max(nextProjectId, project.getId() + 1);
        }

        private void addUser(User user) {
            users.put(user.getId(), user);
        }

        private void addProjectMember(ProjectMember member) {
            projectMembers.put(memberKey(member.getProject().getId(), member.getUser().getId()), member);
        }

        private void addTask(Task task) {
            tasks.put(task.getId(), task);
            nextTaskId = Math.max(nextTaskId, task.getId() + 1);
        }

        private ProjectRepository projectRepository() {
            return proxy(ProjectRepository.class, (method, args) -> switch (method.getName()) {
                case "findByIdAndDeletedAtIsNull" -> Optional.ofNullable(projects.get((Long) args[0]))
                        .filter(project -> !project.isDeleted());
                case "findByDeletedAtIsNull" -> new PageImpl<>(
                        projects.values().stream().filter(project -> !project.isDeleted()).toList(),
                        captureProjectPageable((Pageable) args[0]),
                        projects.values().stream().filter(project -> !project.isDeleted()).count()
                );
                case "existsByNameAndDeletedAtIsNull" -> projects.values().stream()
                        .anyMatch(project -> !project.isDeleted() && project.getName().equals(args[0]));
                case "existsByNameAndIdNotAndDeletedAtIsNull" -> projects.values().stream()
                        .anyMatch(project -> !project.isDeleted()
                                && project.getName().equals(args[0])
                                && !project.getId().equals(args[1]));
                case "countCurrentlyOpenProjects" -> projects.values().stream()
                        .filter(project -> isCurrentlyOpen(project, (LocalDateTime) args[0], (ProjectStatus) args[1]))
                        .count();
                case "save" -> {
                    Project project = (Project) args[0];
                    if (project.getId() == null) {
                        project.setId(nextProjectId++);
                    }
                    projects.put(project.getId(), project);
                    yield project;
                }
                default -> defaultValue(method);
            });
        }

        private ProjectMemberRepository projectMemberRepository() {
            return proxy(ProjectMemberRepository.class, (method, args) -> switch (method.getName()) {
                case "findByProjectId" -> projectMembers.values().stream()
                        .filter(member -> member.getProject().getId().equals(args[0]))
                        .toList();
                case "findByProjectIdAndUserId" -> Optional.ofNullable(projectMembers.get(memberKey((Long) args[0], (Long) args[1])));
                case "existsByProjectIdAndUserId" -> projectMembers.containsKey(memberKey((Long) args[0], (Long) args[1]));
                case "countByProjectId" -> projectMembers.values().stream()
                        .filter(member -> member.getProject().getId().equals(args[0]))
                        .count();
                case "deleteByProjectIdAndUserId" -> {
                    projectMembers.remove(memberKey((Long) args[0], (Long) args[1]));
                    yield null;
                }
                case "save" -> {
                    ProjectMember member = (ProjectMember) args[0];
                    addProjectMember(member);
                    yield member;
                }
                default -> defaultValue(method);
            });
        }

        private TaskRepository taskRepository() {
            return proxy(TaskRepository.class, (method, args) -> switch (method.getName()) {
                case "findActiveByProjectId" -> tasks.values().stream()
                        .filter(task -> task.getProject().getId().equals(args[0]) && !task.isDeleted())
                        .toList();
                case "findActiveByIdAndProjectId" -> Optional.ofNullable(tasks.get((Long) args[0]))
                        .filter(task -> task.getProject().getId().equals(args[1]))
                        .filter(task -> !task.isDeleted());
                case "countByProjectIdAndDeletedAtIsNull" -> tasks.values().stream()
                        .filter(task -> task.getProject().getId().equals(args[0]) && !task.isDeleted())
                        .count();
                case "existsByProjectIdAndAssigneeIdAndDeletedAtIsNull" -> tasks.values().stream()
                        .anyMatch(task -> task.getProject().getId().equals(args[0])
                                && task.getAssignee().getId().equals(args[1])
                                && !task.isDeleted());
                case "save" -> {
                    Task task = (Task) args[0];
                    if (task.getId() == null) {
                        task.setId(nextTaskId++);
                    }
                    tasks.put(task.getId(), task);
                    yield task;
                }
                default -> defaultValue(method);
            });
        }

        private UserRepository userRepository() {
            return proxy(UserRepository.class, (method, args) -> switch (method.getName()) {
                case "findByIdAndDeletedAtIsNull" -> Optional.ofNullable(users.get((Long) args[0]))
                        .filter(user -> !user.isDeleted());
                case "findAvailableProjectMembers" -> users.values().stream()
                        .filter(user -> !user.isDeleted())
                        .filter(user -> user.getStatus() == args[1])
                        .filter(user -> !user.getUsername().equalsIgnoreCase((String) args[3]))
                        .filter(user -> !hasCurrentOpenParticipation(user, (LocalDateTime) args[0], (ProjectStatus) args[2]))
                        .sorted((left, right) -> {
                            int byName = left.getFullName().compareToIgnoreCase(right.getFullName());
                            return byName != 0 ? byName : left.getUsername().compareToIgnoreCase(right.getUsername());
                        })
                        .toList();
                default -> defaultValue(method);
            });
        }

        private boolean hasCurrentOpenParticipation(User user, LocalDateTime now, ProjectStatus openProjectStatus) {
            return projectMembers.values().stream()
                    .filter(member -> member.getUser().getId().equals(user.getId()))
                    .anyMatch(member -> isCurrentlyOpen(member.getProject(), now, openProjectStatus)
                            && !member.getParticipationStartTime().isAfter(now)
                            && !member.getExpectedEndTime().isBefore(now));
        }

        private boolean isCurrentlyOpen(Project project, LocalDateTime now, ProjectStatus openProjectStatus) {
            return !project.isDeleted()
                    && project.getStatus() == openProjectStatus
                    && !project.getStartTime().isAfter(now)
                    && !project.getEndTime().isBefore(now);
        }

        private Pageable captureProjectPageable(Pageable pageable) {
            lastProjectPageable = pageable;
            return pageable;
        }

        private String memberKey(Long projectId, Long userId) {
            return projectId + ":" + userId;
        }

        private Object defaultValue(Method method) {
            Class<?> type = method.getReturnType();
            if (type == boolean.class) return false;
            if (type == long.class) return 0L;
            if (type == int.class) return 0;
            if (Optional.class.equals(type)) return Optional.empty();
            if (List.class.equals(type)) return List.of();
            return null;
        }

        @SuppressWarnings("unchecked")
        private <T> T proxy(Class<T> type, RepositoryHandler handler) {
            InvocationHandler invocationHandler = (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" -> type.getSimpleName() + " proxy";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    };
                }
                return handler.invoke(method, args == null ? new Object[0] : args);
            };
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, invocationHandler);
        }
    }

    @FunctionalInterface
    private interface RepositoryHandler {
        Object invoke(Method method, Object[] args);
    }
}
