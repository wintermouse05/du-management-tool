package org.example.dumanagementbackend.controller;

import org.example.dumanagementbackend.dto.seminar.SeminarRequest;
import org.example.dumanagementbackend.dto.seminar.SeminarResponse;
import org.example.dumanagementbackend.dto.seminar.SeminarVoteRequest;
import org.example.dumanagementbackend.dto.seminar.SeminarVoteResponse;
import org.example.dumanagementbackend.service.SeminarService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seminars")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HR','MEMBER')")
public class SeminarController {

    private final SeminarService seminarService;

    @PostMapping
    public ResponseEntity<SeminarResponse> create(@RequestBody SeminarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(seminarService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<SeminarResponse>> getAll() {
        return ResponseEntity.ok(seminarService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SeminarResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(seminarService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<SeminarResponse> update(@PathVariable Long id, @RequestBody SeminarRequest request) {
        return ResponseEntity.ok(seminarService.update(id, request));
    }

    @PostMapping("/{id}/vote")
    public ResponseEntity<SeminarVoteResponse> vote(@PathVariable Long id, @RequestBody SeminarVoteRequest request) {
        return ResponseEntity.ok(seminarService.vote(id, request));
    }

    @GetMapping("/{id}/votes")
    public ResponseEntity<List<SeminarVoteResponse>> getVotes(@PathVariable Long id) {
        return ResponseEntity.ok(seminarService.getVotes(id));
    }
}
