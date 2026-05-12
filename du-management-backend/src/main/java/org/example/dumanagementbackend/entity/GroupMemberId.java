package org.example.dumanagementbackend.entity;

import java.io.Serializable;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

@Embeddable
@Data
public class GroupMemberId implements Serializable {

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "user_id")
    private Long userId;
}
