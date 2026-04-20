package org.example.dumanagementbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.Data;

@Embeddable
@Data
public class SeminarVoteId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "seminar_id")
    private Long seminarId;
}
