package org.example.dumanagementbackend.repository;

import org.example.dumanagementbackend.entity.SeminarVote;
import org.example.dumanagementbackend.entity.SeminarVoteId;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeminarVoteRepository extends JpaRepository<SeminarVote, SeminarVoteId> {

    List<SeminarVote> findBySeminarId(Long seminarId);

    Page<SeminarVote> findBySeminarId(Long seminarId, Pageable pageable);
}
