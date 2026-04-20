package org.example.dumanagementbackend.controller;

import org.example.dumanagementbackend.dto.survey.SurveyCompletionRequest;
import org.example.dumanagementbackend.dto.survey.SurveyProgressResponse;
import org.example.dumanagementbackend.dto.survey.SurveyRequest;
import org.example.dumanagementbackend.dto.survey.SurveyResponse;
import org.example.dumanagementbackend.service.SurveyService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
public class SurveyController {

    private final SurveyService surveyService;

    @PostMapping
    public ResponseEntity<SurveyResponse> create(@RequestBody SurveyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(surveyService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<SurveyResponse>> getAll() {
        return ResponseEntity.ok(surveyService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SurveyResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(surveyService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SurveyResponse> update(@PathVariable Long id, @RequestBody SurveyRequest request) {
        return ResponseEntity.ok(surveyService.update(id, request));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<SurveyProgressResponse> assign(@PathVariable Long id, @RequestParam Long userId) {
        return ResponseEntity.ok(surveyService.assignToUser(id, userId));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<SurveyProgressResponse> complete(@PathVariable Long id, @RequestBody SurveyCompletionRequest request) {
        return ResponseEntity.ok(surveyService.markCompletion(id, request));
    }

    @GetMapping("/{id}/progress")
    public ResponseEntity<SurveyProgressResponse> progress(@PathVariable Long id) {
        return ResponseEntity.ok(surveyService.getProgress(id));
    }
}
