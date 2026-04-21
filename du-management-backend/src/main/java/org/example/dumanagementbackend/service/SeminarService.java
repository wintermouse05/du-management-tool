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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeminarService {

    private final SeminarRepository seminarRepository;
    private final SeminarVoteRepository seminarVoteRepository;
    private final UserRepository userRepository;
    private final GamificationService gamificationService;

    @Value("${app.upload.seminar-dir:uploads/seminars}")
    private String seminarUploadDir;

    @Transactional
    public SeminarResponse create(SeminarRequest request) {
        Seminar seminar = new Seminar();
        apply(seminar, request);
        return toResponse(seminarRepository.save(seminar));
    }

    public Page<SeminarResponse> getAll(Pageable pageable) {
        return seminarRepository.findAll(pageable).map(this::toResponse);
    }

    public SeminarResponse getById(Long id) {
        return toResponse(getEntityById(id));
    }

    @Transactional
    public SeminarResponse update(Long id, SeminarRequest request) {
        Seminar seminar = getEntityById(id);
        SeminarStatus previousStatus = seminar.getStatus();
        apply(seminar, request);
        Seminar saved = seminarRepository.save(seminar);

        if (previousStatus != SeminarStatus.DONE
            && saved.getStatus() == SeminarStatus.DONE
                && saved.getSpeaker() != null) {
            gamificationService.applyActionPoints(
                    saved.getSpeaker().getId(),
                    "SEMINAR_COMPLETION",
                    "Completed seminar: " + saved.getTitle()
            );
        }

        return toResponse(saved);
    }

    @Transactional
    public SeminarResponse uploadMaterials(Long seminarId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("file is required");
        }

        Seminar seminar = getEntityById(seminarId);
        String originalName = file.getOriginalFilename() == null ? "material.bin" : file.getOriginalFilename();
        String extension = extractExtension(originalName);
        String storedFileName = "seminar-" + seminarId + "-" + System.currentTimeMillis() + extension;

        Path uploadPath = Paths.get(seminarUploadDir).toAbsolutePath().normalize();
        Path target = uploadPath.resolve(storedFileName);

        try {
            Files.createDirectories(uploadPath);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BadRequestException("Cannot store file: " + ex.getMessage());
        }

        seminar.setMaterialsUrl(storedFileName);
        return toResponse(seminarRepository.save(seminar));
    }

    public Resource downloadMaterials(Long seminarId) {
        Seminar seminar = getEntityById(seminarId);
        if (seminar.getMaterialsUrl() == null || seminar.getMaterialsUrl().isBlank()) {
            throw new ResourceNotFoundException("No materials found for seminar id=" + seminarId);
        }

        try {
            Path filePath = Paths.get(seminarUploadDir).toAbsolutePath().normalize().resolve(seminar.getMaterialsUrl()).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("Materials file is unavailable for seminar id=" + seminarId);
            }
            return resource;
        } catch (IOException ex) {
            throw new ResourceNotFoundException("Materials file is unavailable for seminar id=" + seminarId);
        }
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

    public Page<SeminarVoteResponse> getVotes(Long seminarId, Pageable pageable) {
        return seminarVoteRepository.findBySeminarId(seminarId, pageable)
                .map(v -> new SeminarVoteResponse(v.getSeminar().getId(), v.getUser().getId(), v.getVoteType()));
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
                seminar.getMaterialsUrl() != null ? "/api/seminars/" + seminar.getId() + "/materials" : null,
                seminar.getStatus()
        );
    }

    private String extractExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index);
    }
}
