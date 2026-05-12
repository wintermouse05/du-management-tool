package org.example.dumanagementbackend.repository;

import java.util.List;
import org.example.dumanagementbackend.entity.GroupMember;
import org.example.dumanagementbackend.entity.GroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {

    List<GroupMember> findByGroupId(Long groupId);

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    @Modifying
    void deleteByGroupIdAndUserId(Long groupId, Long userId);

    @Modifying
    void deleteByGroupId(Long groupId);
}
