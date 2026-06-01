package org.example.dumanagementbackend.repository;

import org.example.dumanagementbackend.entity.SeminarVote;
import org.example.dumanagementbackend.entity.SeminarVoteId;
import org.example.dumanagementbackend.entity.enums.VoteType;
import java.util.List;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeminarVoteRepository extends JpaRepository<SeminarVote, SeminarVoteId> {

    List<SeminarVote> findBySeminarId(Long seminarId);

    Page<SeminarVote> findBySeminarId(Long seminarId, Pageable pageable);

    List<SeminarVote> findByIdUserIdAndIdSeminarIdIn(Long userId, Collection<Long> seminarIds);

    long countBySeminarIdAndVoteType(Long seminarId, VoteType voteType);
}
