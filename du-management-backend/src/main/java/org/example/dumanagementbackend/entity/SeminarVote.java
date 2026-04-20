package org.example.dumanagementbackend.entity;

import org.example.dumanagementbackend.entity.enums.VoteType;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "seminar_votes")
@Getter
@Setter
@NoArgsConstructor
public class SeminarVote {

    @EmbeddedId
    private SeminarVoteId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("seminarId")
    @JoinColumn(name = "seminar_id", nullable = false)
    private Seminar seminar;

    @Enumerated(EnumType.STRING)
    private VoteType voteType;
}
