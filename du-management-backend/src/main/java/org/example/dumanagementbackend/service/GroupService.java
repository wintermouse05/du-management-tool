package org.example.dumanagementbackend.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.dumanagementbackend.dto.group.GroupMemberRequest;
import org.example.dumanagementbackend.dto.group.GroupRequest;
import org.example.dumanagementbackend.dto.group.GroupResponse;
import org.example.dumanagementbackend.entity.GroupMember;
import org.example.dumanagementbackend.entity.GroupMemberId;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.UserGroup;
import org.example.dumanagementbackend.entity.enums.UserStatus;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.GroupMemberRepository;
import org.example.dumanagementbackend.repository.UserGroupRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupService {

    private final UserGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final UserRepository userRepository;

    public List<GroupResponse> getAll() {
        return groupRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    public GroupResponse getById(Long id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public GroupResponse create(GroupRequest request) {
        if (groupRepository.existsByName(request.name())) {
            throw new BadRequestException("Group name already exists: " + request.name());
        }
        UserGroup group = new UserGroup();
        apply(group, request);
        return toResponse(groupRepository.save(group));
    }

    @Transactional
    public GroupResponse update(Long id, GroupRequest request) {
        UserGroup group = getEntity(id);
        if (!group.getName().equals(request.name()) && groupRepository.existsByName(request.name())) {
            throw new BadRequestException("Group name already exists: " + request.name());
        }
        apply(group, request);
        if (group.isAllGroup()) {
            memberRepository.deleteByGroupId(id);
        }
        return toResponse(groupRepository.save(group));
    }

    @Transactional
    public void delete(Long id) {
        UserGroup group = getEntity(id);
        if (group.isAllGroup()) {
            throw new BadRequestException("Cannot archive the All Users group.");
        }
        SoftDeleteUtils.markDeleted(group);
        groupRepository.save(group);
    }

    @Transactional
    public GroupResponse addMember(Long groupId, GroupMemberRequest request) {
        UserGroup group = getEntity(groupId);
        if (group.isAllGroup()) {
            throw new BadRequestException("Cannot manually add members to an All Users group");
        }
        User user = userRepository.findByIdAndDeletedAtIsNull(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id=" + request.userId()));
        if (isAdminAccount(user)) {
            throw new BadRequestException("Cannot add admin account to member lists");
        }

        if (!memberRepository.existsByGroupIdAndUserId(groupId, request.userId())) {
            GroupMember member = new GroupMember();
            GroupMemberId id = new GroupMemberId();
            id.setGroupId(groupId);
            id.setUserId(request.userId());
            member.setId(id);
            member.setGroup(group);
            member.setUser(user);
            memberRepository.save(member);
        }
        return toResponse(group);
    }

    @Transactional
    public GroupResponse removeMember(Long groupId, Long userId) {
        UserGroup group = getEntity(groupId);
        if (group.isAllGroup()) {
            throw new BadRequestException("Cannot manually remove members from an All Users group");
        }
        memberRepository.deleteByGroupIdAndUserId(groupId, userId);
        return toResponse(group);
    }

    public List<User> getMembers(Long groupId) {
        UserGroup group = getEntity(groupId);
        if (group.isAllGroup()) {
            return userRepository.findByStatusAndUsernameIgnoreCaseNotOrderByTotalPointsDesc(
                    UserStatus.ACTIVE,
                    SystemAccountUtils.ADMIN_USERNAME
            );
        }
        return memberRepository.findByGroupId(groupId).stream()
                .map(GroupMember::getUser)
                .filter(user -> !user.isDeleted())
                .filter(user -> !isAdminAccount(user))
                .toList();
    }

    public List<User> getResolvedMembers(Long groupId) {
        return getMembers(groupId);
    }

    public List<org.example.dumanagementbackend.dto.group.GroupMemberResponse> getMemberResponses(Long groupId) {
        return getMembers(groupId).stream()
                .map(u -> new org.example.dumanagementbackend.dto.group.GroupMemberResponse(
                        u.getId(), u.getUsername(), u.getFullName(), u.getEmail()))
                .toList();
    }

    private UserGroup getEntity(Long id) {
        return groupRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with id=" + id));
    }

    private void apply(UserGroup group, GroupRequest request) {
        group.setName(request.name());
        group.setDescription(request.description());
        group.setAllGroup(request.allGroup());
    }

    private GroupResponse toResponse(UserGroup group) {
        int count;
        if (group.isAllGroup()) {
            count = (int) userRepository.countByStatusAndUsernameIgnoreCaseNot(
                    UserStatus.ACTIVE,
                    SystemAccountUtils.ADMIN_USERNAME
            );
        } else {
            count = getMembers(group.getId()).size();
        }
        return new GroupResponse(group.getId(), group.getName(), group.getDescription(), group.isAllGroup(), count);
    }

    private boolean isAdminAccount(User user) {
        return SystemAccountUtils.isAdminAccount(user);
    }
}
