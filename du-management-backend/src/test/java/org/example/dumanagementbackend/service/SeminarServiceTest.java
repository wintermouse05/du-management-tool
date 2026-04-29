package org.example.dumanagementbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.example.dumanagementbackend.dto.seminar.SeminarRequest;
import org.example.dumanagementbackend.dto.seminar.SeminarResponse;
import org.example.dumanagementbackend.dto.seminar.SeminarVoteRequest;
import org.example.dumanagementbackend.dto.seminar.SeminarVoteResponse;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class SeminarServiceTest {

    @Mock
    private SeminarRepository seminarRepository;

    @Mock
    private SeminarVoteRepository seminarVoteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GamificationService gamificationService;

    @InjectMocks
    private SeminarService seminarService;

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void create_returnsSeminarResponse() {
        SeminarRequest req = new SeminarRequest(null, "Clean Code Talk", "Best practices",
                LocalDateTime.now().plusDays(7), SeminarStatus.PROPOSED);
        Seminar saved = buildSeminar(1L, "Clean Code Talk", null, SeminarStatus.PROPOSED);

        when(seminarRepository.save(any(Seminar.class))).thenReturn(saved);

        SeminarResponse response = seminarService.create(req);

        assertEquals(1L, response.id());
        assertEquals("Clean Code Talk", response.title());
        assertEquals(SeminarStatus.PROPOSED, response.status());
    }

    // ── getAll ───────────────────────────────────────────────────────────────

    @Test
    void getAll_returnsMappedPage() {
        Pageable pageable = PageRequest.of(0, 5);
        Seminar s1 = buildSeminar(1L, "DDD Talk", null, SeminarStatus.PROPOSED);
        Seminar s2 = buildSeminar(2L, "TDD Talk", null, SeminarStatus.SCHEDULED);
        Page<Seminar> page = new PageImpl<>(List.of(s1, s2), pageable, 2);

        when(seminarRepository.findAll(pageable)).thenReturn(page);

        Page<SeminarResponse> result = seminarService.getAll(pageable);

        assertEquals(2, result.getTotalElements());
        assertEquals("DDD Talk", result.getContent().get(0).title());
    }

    // ── getById ──────────────────────────────────────────────────────────────

    @Test
    void getById_throwsNotFoundWhenSeminarMissing() {
        when(seminarRepository.findById(77L)).thenReturn(Optional.empty());
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> seminarService.getById(77L));
        assertEquals("Seminar not found with id=77", ex.getMessage());
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void update_grantsPointsToSpeakerWhenStatusChangesToDone() {
        User speaker = buildUser(10L, "Speaker Sam");
        Seminar existing = buildSeminar(5L, "ML Talk", speaker, SeminarStatus.SCHEDULED);

        SeminarRequest req = new SeminarRequest(10L, "ML Talk", "Machine Learning overview",
                LocalDateTime.now().plusDays(1), SeminarStatus.DONE);

        when(seminarRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(userRepository.findById(10L)).thenReturn(Optional.of(speaker));
        when(seminarRepository.save(any(Seminar.class))).thenAnswer(inv -> inv.getArgument(0));

        seminarService.update(5L, req);

        verify(gamificationService).applyActionPoints(10L, "SEMINAR_COMPLETION", "Completed seminar: ML Talk");
    }

    @Test
    void update_doesNotGrantPointsWhenStatusWasAlreadyDone() {
        User speaker = buildUser(10L, "Speaker Sam");
        Seminar existing = buildSeminar(5L, "ML Talk", speaker, SeminarStatus.DONE);

        SeminarRequest req = new SeminarRequest(10L, "ML Talk Updated", "Updated desc",
                LocalDateTime.now().plusDays(1), SeminarStatus.DONE);

        when(seminarRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(userRepository.findById(10L)).thenReturn(Optional.of(speaker));
        when(seminarRepository.save(any(Seminar.class))).thenAnswer(inv -> inv.getArgument(0));

        seminarService.update(5L, req);

        verify(gamificationService, never()).applyActionPoints(any(), any(), any());
    }

    @Test
    void update_doesNotGrantPointsWhenNoSpeaker() {
        Seminar existing = buildSeminar(6L, "No Speaker Talk", null, SeminarStatus.SCHEDULED);

        SeminarRequest req = new SeminarRequest(null, "No Speaker Talk", null,
                LocalDateTime.now().plusDays(1), SeminarStatus.DONE);

        when(seminarRepository.findById(6L)).thenReturn(Optional.of(existing));
        when(seminarRepository.save(any(Seminar.class))).thenAnswer(inv -> inv.getArgument(0));

        seminarService.update(6L, req);

        verify(gamificationService, never()).applyActionPoints(any(), any(), any());
    }

    // ── vote ─────────────────────────────────────────────────────────────────

    @Test
    void vote_throwsBadRequestWhenUserIdNull() {
        // userId and voteType null check fires BEFORE getEntityById (line 125 in service)
        SeminarVoteRequest req = new SeminarVoteRequest(null, null);

        assertThrows(BadRequestException.class, () -> seminarService.vote(3L, req));
    }

    @Test
    void vote_throwsBadRequestWhenSeminarNotProposed() {
        Seminar seminar = buildSeminar(4L, "Scheduled Talk", null, SeminarStatus.SCHEDULED);
        when(seminarRepository.findById(4L)).thenReturn(Optional.of(seminar));

        SeminarVoteRequest req = new SeminarVoteRequest(1L, VoteType.UPVOTE);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> seminarService.vote(4L, req));
        assertEquals("Cannot vote on a seminar that is already SCHEDULED", ex.getMessage());
    }

    @Test
    void vote_throwsNotFoundWhenUserMissing() {
        Seminar seminar = buildSeminar(4L, "Open Talk", null, SeminarStatus.PROPOSED);
        when(seminarRepository.findById(4L)).thenReturn(Optional.of(seminar));
        when(userRepository.findById(55L)).thenReturn(Optional.empty());

        SeminarVoteRequest req = new SeminarVoteRequest(55L, VoteType.UPVOTE);
        assertThrows(ResourceNotFoundException.class, () -> seminarService.vote(4L, req));
    }

    @Test
    void vote_savesVoteAndReturnsResponse() {
        Seminar seminar = buildSeminar(4L, "Open Talk", null, SeminarStatus.PROPOSED);
        User user = buildUser(9L, "Voter");

        SeminarVoteId voteId = new SeminarVoteId();
        voteId.setSeminarId(4L);
        voteId.setUserId(9L);

        when(seminarRepository.findById(4L)).thenReturn(Optional.of(seminar));
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(seminarVoteRepository.findById(any(SeminarVoteId.class))).thenReturn(Optional.empty());
        when(seminarVoteRepository.save(any(SeminarVote.class))).thenAnswer(inv -> {
            SeminarVote v = inv.getArgument(0);
            v.setId(voteId);
            v.setSeminar(seminar);
            v.setUser(user);
            return v;
        });

        SeminarVoteRequest req = new SeminarVoteRequest(9L, VoteType.UPVOTE);
        SeminarVoteResponse response = seminarService.vote(4L, req);

        assertEquals(4L, response.seminarId());
        assertEquals(9L, response.userId());
        assertEquals(VoteType.UPVOTE, response.voteType());
    }

    @Test
    void vote_updatesExistingVote() {
        Seminar seminar = buildSeminar(4L, "Open Talk", null, SeminarStatus.PROPOSED);
        User user = buildUser(9L, "Voter");

        SeminarVoteId voteId = new SeminarVoteId();
        voteId.setSeminarId(4L);
        voteId.setUserId(9L);

        SeminarVote existingVote = new SeminarVote();
        existingVote.setId(voteId);
        existingVote.setSeminar(seminar);
        existingVote.setUser(user);
        existingVote.setVoteType(VoteType.UPVOTE);

        when(seminarRepository.findById(4L)).thenReturn(Optional.of(seminar));
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(seminarVoteRepository.findById(any(SeminarVoteId.class))).thenReturn(Optional.of(existingVote));
        when(seminarVoteRepository.save(any(SeminarVote.class))).thenAnswer(inv -> inv.getArgument(0));

        SeminarVoteRequest req = new SeminarVoteRequest(9L, VoteType.DOWNVOTE);
        SeminarVoteResponse response = seminarService.vote(4L, req);

        assertEquals(VoteType.DOWNVOTE, response.voteType());
    }

    // ── uploadMaterials ───────────────────────────────────────────────────────

    @Test
    void uploadMaterials_throwsBadRequestWhenFileEmpty() {
        org.springframework.web.multipart.MultipartFile emptyFile =
                org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        when(emptyFile.isEmpty()).thenReturn(true);

        // file==null check and file.isEmpty() both happen before getEntityById is called
        assertThrows(BadRequestException.class,
                () -> seminarService.uploadMaterials(1L, emptyFile));
    }

    @Test
    void uploadMaterials_throwsBadRequestWhenFileNull() {
        // null file also triggers BadRequestException before repository lookup
        assertThrows(BadRequestException.class,
                () -> seminarService.uploadMaterials(1L, null));
    }

    // ── downloadMaterials ─────────────────────────────────────────────────────

    @Test
    void downloadMaterials_throwsNotFoundWhenNoMaterialsUrl() {
        Seminar seminar = buildSeminar(2L, "No Materials", null, SeminarStatus.PROPOSED);
        seminar.setMaterialsUrl(null);
        when(seminarRepository.findById(2L)).thenReturn(Optional.of(seminar));

        assertThrows(ResourceNotFoundException.class,
                () -> seminarService.downloadMaterials(2L));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Seminar buildSeminar(Long id, String title, User speaker, SeminarStatus status) {
        Seminar s = new Seminar();
        s.setId(id);
        s.setTitle(title);
        s.setSpeaker(speaker);
        s.setStatus(status);
        return s;
    }

    private User buildUser(Long id, String fullName) {
        User user = new User();
        user.setId(id);
        user.setFullName(fullName);
        return user;
    }
}
