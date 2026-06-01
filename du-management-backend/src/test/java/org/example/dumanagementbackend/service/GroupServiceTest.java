package org.example.dumanagementbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.example.dumanagementbackend.dto.group.GroupResponse;
import org.example.dumanagementbackend.entity.GroupMember;
import org.example.dumanagementbackend.entity.GroupMemberId;
import org.example.dumanagementbackend.entity.Role;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.UserGroup;
import org.example.dumanagementbackend.entity.enums.UserStatus;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.repository.GroupMemberRepository;
import org.example.dumanagementbackend.repository.UserGroupRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private UserGroupRepository groupRepository;

    @Mock
    private GroupMemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GroupService groupService;

    @Test
    void getAll_returnsOnlyActiveGroupsFromRepository() {
        UserGroup group = buildGroup(1L, "Engineering", false);
        when(groupRepository.findByDeletedAtIsNullOrderByNameAsc()).thenReturn(List.of(group));
        when(groupRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(group));
        when(memberRepository.findByGroupId(1L)).thenReturn(List.of());

        List<GroupResponse> responses = groupService.getAll();

        assertEquals(1, responses.size());
        assertEquals("Engineering", responses.get(0).name());
        verify(groupRepository).findByDeletedAtIsNullOrderByNameAsc();
    }

    @Test
    void delete_archivesCustomGroup() {
        UserGroup group = buildGroup(1L, "Engineering", false);
        when(groupRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(group));

        groupService.delete(1L);

        assertTrue(group.isDeleted());
        verify(groupRepository).save(group);
        verify(groupRepository, never()).delete(group);
        verify(memberRepository, never()).deleteByGroupId(1L);
    }

    @Test
    void delete_rejectsAllUsersGroup() {
        UserGroup group = buildGroup(1L, "All", true);
        when(groupRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(group));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> groupService.delete(1L));

        assertEquals("Cannot archive the All Users group.", ex.getMessage());
        assertFalse(group.isDeleted());
        verify(groupRepository, never()).save(any(UserGroup.class));
    }

    @Test
    void getMembers_filtersArchivedUsersFromCustomGroup() {
        UserGroup group = buildGroup(1L, "Engineering", false);
        User activeUser = buildUser(2L, "member", "MEMBER");
        User archivedUser = buildUser(3L, "archived", "MEMBER");
        archivedUser.markDeleted("admin");

        when(groupRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(group));
        when(memberRepository.findByGroupId(1L)).thenReturn(List.of(
                buildMembership(group, activeUser),
                buildMembership(group, archivedUser)
        ));

        List<User> members = groupService.getMembers(1L);

        assertEquals(1, members.size());
        assertEquals("member", members.get(0).getUsername());
    }

    private UserGroup buildGroup(Long id, String name, boolean allGroup) {
        UserGroup group = new UserGroup();
        group.setId(id);
        group.setName(name);
        group.setDescription(name + " group");
        group.setAllGroup(allGroup);
        return group;
    }

    private User buildUser(Long id, String username, String roleName) {
        Role role = new Role();
        role.setId(id);
        role.setName(roleName);

        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFullName(username + " User");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private GroupMember buildMembership(UserGroup group, User user) {
        GroupMember member = new GroupMember();
        GroupMemberId id = new GroupMemberId();
        id.setGroupId(group.getId());
        id.setUserId(user.getId());
        member.setId(id);
        member.setGroup(group);
        member.setUser(user);
        return member;
    }
}
