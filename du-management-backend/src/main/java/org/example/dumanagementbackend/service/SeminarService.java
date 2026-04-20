package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.dto.seminar.SeminarRequest;
import org.example.dumanagementbackend.dto.seminar.SeminarResponse;
import org.example.dumanagementbackend.dto.seminar.SeminarVoteRequest;
import org.example.dumanagementbackend.dto.seminar.SeminarVoteResponse;
import org.example.dumanagementbackend.entity.Seminar;
import org.example.dumanagementbackend.entity.SeminarVote;
import org.example.dumanagementbackend.entity.SeminarVoteId;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.SeminarStatus;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.SeminarRepository;
import org.example.dumanagementbackend.repository.SeminarVoteRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SeminarService {

    private final SeminarRepository seminarRepository;
    private final SeminarVoteRepository seminarVoteRepository;
    private final UserRepository userRepository;

    @Transactional
    public SeminarResponse create(SeminarRequest request) {
        Seminar seminar = new Seminar();
        apply(seminar, request);
        return toResponse(seminarRepository.save(seminar));
    }

    public List<SeminarResponse> getAll() {
        return seminarRepository.findAll().stream().map(this::toResponse).toList();
    }

    public SeminarResponse getById(Long id) {
        return toResponse(getEntityById(id));
    }

    @Transactional
    public SeminarResponse update(Long id, SeminarRequest request) {
        Seminar seminar = getEntityById(id);
        apply(seminar, request);
        return toResponse(seminarRepository.save(seminar));
    }

    @Transactional
    public SeminarVoteResponse vote(Long seminarId, SeminarVoteRequest request) {
        if (request.userId() == null || request.voteType() == null) {
            throw new BadRequestException("userId and voteType are required");
        }
        Seminar seminar = getEntityById(seminarId);
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id=" + request.userId()));

        SeminarVoteId id = new SeminarVoteId();
        id.setSeminarId(seminarId);
        id.setUserId(request.userId());

        SeminarVote vote = seminarVoteRepository.findById(id).orElseGet(SeminarVote::new);
        vote.setId(id);
        vote.setSeminar(seminar);
        vote.setUser(user);
        vote.setVoteType(request.voteType());

        SeminarVote saved = seminarVoteRepository.save(vote);
        return new SeminarVoteResponse(saved.getSeminar().getId(), saved.getUser().getId(), saved.getVoteType());
    }

    public List<SeminarVoteResponse> getVotes(Long seminarId) {
        return seminarVoteRepository.findBySeminarId(seminarId).stream()
                .map(v -> new SeminarVoteResponse(v.getSeminar().getId(), v.getUser().getId(), v.getVoteType()))
                .toList();
    }

    public Seminar getEntityById(Long id) {
        return seminarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Seminar not found with id=" + id));
    }

    private void apply(Seminar seminar, SeminarRequest request) {
        seminar.setTitle(request.title());
        seminar.setDescription(request.description());
        seminar.setScheduledAt(request.scheduledAt());
        seminar.setStatus(request.status() != null ? request.status() : SeminarStatus.PROPOSED);

        if (request.speakerId() != null) {
            User speaker = userRepository.findById(request.speakerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Speaker not found with id=" + request.speakerId()));
            seminar.setSpeaker(speaker);
        } else {
            seminar.setSpeaker(null);
        }
    }

    private SeminarResponse toResponse(Seminar seminar) {
        Long speakerId = seminar.getSpeaker() != null ? seminar.getSpeaker().getId() : null;
        String speakerName = seminar.getSpeaker() != null ? seminar.getSpeaker().getFullName() : null;
        return new SeminarResponse(
                seminar.getId(),
                speakerId,
                speakerName,
                seminar.getTitle(),
                seminar.getDescription(),
                seminar.getScheduledAt(),
                seminar.getStatus()
        );
    }
}
