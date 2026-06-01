package org.example.dumanagementbackend.controller;

import org.example.dumanagementbackend.dto.survey.SurveyCompletionRequest;
import org.example.dumanagementbackend.dto.survey.SurveyAssignmentUpdateRequest;
import org.example.dumanagementbackend.dto.survey.SurveyProgressResponse;
import org.example.dumanagementbackend.dto.survey.SurveyRequest;
import org.example.dumanagementbackend.dto.survey.SurveyResponse;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.service.SurveyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/surveys")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HR','MEMBER')")
public class SurveyController {

    private final SurveyService surveyService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<SurveyResponse> create(@Valid @RequestBody SurveyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(surveyService.create(request));
    }

    @GetMapping
    public ResponseEntity<Page<SurveyResponse>> getAll(Pageable pageable) {
        return ResponseEntity.ok(surveyService.getAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SurveyResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(surveyService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<SurveyResponse> update(@PathVariable Long id, @Valid @RequestBody SurveyRequest request) {
        return ResponseEntity.ok(surveyService.update(id, request));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<SurveyProgressResponse> assign(
            @PathVariable Long id,
            @RequestParam(required = false) Long userId
    ) {
        if (userId != null) {
            return ResponseEntity.ok(surveyService.assignToUser(id, userId));
        }
        throw new BadRequestException("userId is required");
    }

    @PutMapping("/{id}/assignments")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<SurveyProgressResponse> replaceAssignments(
            @PathVariable Long id,
            @RequestBody SurveyAssignmentUpdateRequest request
    ) {
        return ResponseEntity.ok(surveyService.replaceAssignments(id, request));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<SurveyProgressResponse> complete(@PathVariable Long id, @Valid @RequestBody SurveyCompletionRequest request) {
        return ResponseEntity.ok(surveyService.markCompletion(id, request));
    }

    @GetMapping("/{id}/progress")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<SurveyProgressResponse> progress(@PathVariable Long id) {
        return ResponseEntity.ok(surveyService.getProgress(id));
    }
}
