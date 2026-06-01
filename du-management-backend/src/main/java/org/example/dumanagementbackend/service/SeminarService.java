package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.dto.seminar.SeminarRequest;
import org.example.dumanagementbackend.dto.seminar.SeminarResponse;
import org.example.dumanagementbackend.dto.seminar.SeminarVoteRequest;
import org.example.dumanagementbackend.dto.seminar.SeminarVoteResponse;
import org.example.dumanagementbackend.dto.seminar.SeminarVoteSummaryResponse;
import org.example.dumanagementbackend.entity.Seminar;
import org.example.dumanagementbackend.entity.SeminarVote;
import org.example.dumanagementbackend.entity.SeminarVoteId;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.SeminarStatus;
import org.example.dumanagementbackend.entity.enums.VoteType;
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
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeminarService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeminarService.class);

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
        Seminar saved = seminarRepository.save(seminar);
        return toResponse(saved, resolveCurrentUserVote(saved));
    }

    public Page<SeminarResponse> getAll(Pageable pageable) {
        Pageable resolvedPageable = PaginationUtils.toZeroBasedPageable(pageable);
        User currentUser = getCurrentAuthenticatedUser();
        Page<Seminar> page = resolveVisibleSeminars(resolvedPageable, currentUser);
        Map<Long, VoteType> voteBySeminarId = resolveCurrentUserVotes(page.getContent());
        List<SeminarResponse> content = page.getContent().stream()
                .map(seminar -> toResponse(seminar, voteBySeminarId.get(seminar.getId())))
                .toList();
        return new PageImpl<>(content, resolvedPageable, page.getTotalElements());
    }

    public SeminarResponse getById(Long id) {
        Seminar seminar = getEntityById(id);
        return toResponse(seminar, resolveCurrentUserVote(seminar));
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

        return toResponse(saved, resolveCurrentUserVote(saved));
    }

    @Transactional
    public SeminarResponse uploadMaterials(Long seminarId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("file is required");
        }

        Seminar seminar = getEntityById(seminarId);
        if (isSeminarOccurred(seminar)) {
            throw new BadRequestException("Cannot upload materials for a seminar that has already occurred.");
        }
        String originalName = file.getOriginalFilename() == null ? "material.bin" : file.getOriginalFilename();
        String extension = extractExtension(originalName);
        String storedFileName = "seminar-" + seminarId + "-" + System.currentTimeMillis() + extension;

        Path uploadPath = Paths.get(seminarUploadDir).toAbsolutePath().normalize();
        Path target = uploadPath.resolve(storedFileName);

        try {
            Files.createDirectories(uploadPath);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            LOGGER.error("Cannot store seminar materials. seminarId={}, target={}", seminarId, target, ex);
            throw new BadRequestException("Cannot store file. Please try again.");
        }

        seminar.setMaterialsUrl(storedFileName);
        Seminar saved = seminarRepository.save(seminar);
        return toResponse(saved, resolveCurrentUserVote(saved));
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
        return new SeminarVoteResponse(
                saved.getSeminar().getId(),
                saved.getUser().getId(),
                saved.getUser().getFullName(),
                saved.getVoteType()
        );
    }

    public Page<SeminarVoteResponse> getVotes(Long seminarId, Pageable pageable) {
        Pageable resolvedPageable = PaginationUtils.toZeroBasedPageable(pageable);
        return seminarVoteRepository.findBySeminarId(seminarId, resolvedPageable)
                .map(v -> new SeminarVoteResponse(
                        v.getSeminar().getId(),
                        v.getUser().getId(),
                        v.getUser().getFullName(),
                        v.getVoteType()
                ));
    }

    public SeminarVoteSummaryResponse getVoteSummary(Long seminarId) {
        getEntityById(seminarId);
        long upvotes = seminarVoteRepository.countBySeminarIdAndVoteType(seminarId, VoteType.UPVOTE);
        long downvotes = seminarVoteRepository.countBySeminarIdAndVoteType(seminarId, VoteType.DOWNVOTE);
        return new SeminarVoteSummaryResponse(upvotes, downvotes);
    }

    @Transactional
    public int approveSeminars(List<Long> seminarIds) {
        if (seminarIds == null || seminarIds.isEmpty()) {
            throw new BadRequestException("seminarIds is required");
        }

        List<Seminar> seminars = seminarRepository.findAllById(seminarIds);
        Set<Long> foundIds = seminars.stream().map(Seminar::getId).collect(Collectors.toSet());
        List<Long> missingIds = seminarIds.stream().filter(id -> !foundIds.contains(id)).toList();
        if (!missingIds.isEmpty()) {
            throw new ResourceNotFoundException("Seminar not found with id=" + missingIds.get(0));
        }

        int updated = 0;
        for (Seminar seminar : seminars) {
            SeminarStatus currentStatus = normalizeStatus(seminar.getStatus());
            if (currentStatus == SeminarStatus.APPROVED || currentStatus == SeminarStatus.DONE) {
                continue;
            }
            seminar.setStatus(SeminarStatus.APPROVED);
            updated++;
        }

        if (updated > 0) {
            seminarRepository.saveAll(seminars);
        }
        return updated;
    }

    public Seminar getEntityById(Long id) {
        return seminarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Seminar not found with id=" + id));
    }

    private void apply(Seminar seminar, SeminarRequest request) {
        seminar.setTitle(request.title());
        seminar.setDescription(request.description());
        seminar.setScheduledAt(request.scheduledAt());
        seminar.setStatus(normalizeStatus(request.status()));

        if (request.speakerId() != null) {
            User speaker = userRepository.findById(request.speakerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Speaker not found with id=" + request.speakerId()));
            seminar.setSpeaker(speaker);
        } else {
            seminar.setSpeaker(null);
        }
    }

    private SeminarResponse toResponse(Seminar seminar, VoteType currentUserVote) {
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
                normalizeStatus(seminar.getStatus()),
                currentUserVote
        );
    }

    private String extractExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index);
    }

    private VoteType resolveCurrentUserVote(Seminar seminar) {
        if (seminar == null || seminar.getId() == null) {
            return null;
        }
        Map<Long, VoteType> voteBySeminarId = resolveCurrentUserVotes(List.of(seminar));
        return voteBySeminarId.get(seminar.getId());
    }

    private Map<Long, VoteType> resolveCurrentUserVotes(Collection<Seminar> seminars) {
        if (seminars == null || seminars.isEmpty()) {
            return Map.of();
        }
        Long currentUserId = getCurrentAuthenticatedUserId();
        if (currentUserId == null) {
            return Map.of();
        }

        List<Long> seminarIds = seminars.stream()
                .map(Seminar::getId)
                .filter(id -> id != null)
                .toList();
        if (seminarIds.isEmpty()) {
            return Map.of();
        }

        return seminarVoteRepository.findByIdUserIdAndIdSeminarIdIn(currentUserId, seminarIds).stream()
                .collect(Collectors.toMap(
                        vote -> vote.getSeminar().getId(),
                        SeminarVote::getVoteType,
                        (first, second) -> second
                ));
    }

    private Long getCurrentAuthenticatedUserId() {
        User currentUser = getCurrentAuthenticatedUser();
        return currentUser != null ? currentUser.getId() : null;
    }

    private User getCurrentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().isBlank()
                || "anonymousUser".equalsIgnoreCase(authentication.getName())) {
            return null;
        }

        return userRepository.findByUsername(authentication.getName())
                .or(() -> userRepository.findByEmail(authentication.getName()))
                .orElse(null);
    }

    private Page<Seminar> resolveVisibleSeminars(Pageable pageable, User currentUser) {
        if (currentUser == null || isAdminOrHr(currentUser)) {
            return seminarRepository.findAllOrdered(pageable);
        }

        String username = currentUser.getUsername() == null ? "" : currentUser.getUsername();
        String email = currentUser.getEmail() == null ? "" : currentUser.getEmail();
        return seminarRepository.findVisibleForMember(username, email, pageable);
    }

    private boolean isAdminOrHr(User user) {
        if (user == null || user.getRole() == null || user.getRole().getName() == null) {
            return false;
        }
        String roleName = user.getRole().getName();
        return "ADMIN".equalsIgnoreCase(roleName) || "HR".equalsIgnoreCase(roleName);
    }

    private SeminarStatus normalizeStatus(SeminarStatus status) {
        if (status == null || status == SeminarStatus.PROPOSED) {
            return SeminarStatus.PENDING;
        }
        if (status == SeminarStatus.SCHEDULED) {
            return SeminarStatus.APPROVED;
        }
        return status;
    }

    private boolean isSeminarOccurred(Seminar seminar) {
        if (seminar == null || seminar.getScheduledAt() == null) {
            return false;
        }
        return !seminar.getScheduledAt().isAfter(LocalDateTime.now());
    }
}
