package org.example.dumanagementbackend.controller;

import org.example.dumanagementbackend.dto.seminar.SeminarRequest;
import org.example.dumanagementbackend.dto.seminar.SeminarResponse;
import org.example.dumanagementbackend.dto.seminar.SeminarVoteRequest;
import org.example.dumanagementbackend.dto.seminar.SeminarVoteResponse;
import org.example.dumanagementbackend.service.SeminarService;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/seminars")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HR','MEMBER')")
public class SeminarController {

    private final SeminarService seminarService;

    @PostMapping
    public ResponseEntity<SeminarResponse> create(@Valid @RequestBody SeminarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(seminarService.create(request));
    }

    @GetMapping
    public ResponseEntity<Page<SeminarResponse>> getAll(Pageable pageable) {
        return ResponseEntity.ok(seminarService.getAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SeminarResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(seminarService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<SeminarResponse> update(@PathVariable Long id, @Valid @RequestBody SeminarRequest request) {
        return ResponseEntity.ok(seminarService.update(id, request));
    }

    @PostMapping(value = "/{id}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<SeminarResponse> uploadMaterials(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(seminarService.uploadMaterials(id, file));
    }

    @GetMapping("/{id}/materials")
    public ResponseEntity<Resource> downloadMaterials(@PathVariable Long id) throws IOException {
        Resource resource = seminarService.downloadMaterials(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @PostMapping("/{id}/vote")
    public ResponseEntity<SeminarVoteResponse> vote(@PathVariable Long id, @Valid @RequestBody SeminarVoteRequest request) {
        return ResponseEntity.ok(seminarService.vote(id, request));
    }

    @GetMapping("/{id}/votes")
    public ResponseEntity<Page<SeminarVoteResponse>> getVotes(@PathVariable Long id, Pageable pageable) {
        return ResponseEntity.ok(seminarService.getVotes(id, pageable));
    }
}
