package org.example.dumanagementbackend.controller;

import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dumanagementbackend.dto.bookmark.BookmarkRequest;
import org.example.dumanagementbackend.dto.bookmark.BookmarkResponse;
import org.example.dumanagementbackend.service.BookmarkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HR','MEMBER')")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @GetMapping
    public ResponseEntity<List<BookmarkResponse>> getAll() {
        return ResponseEntity.ok(bookmarkService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookmarkResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(bookmarkService.getById(id));
    }

    @PostMapping
    public ResponseEntity<BookmarkResponse> create(@Valid @RequestBody BookmarkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookmarkService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BookmarkResponse> update(@PathVariable Long id, @Valid @RequestBody BookmarkRequest request) {
        return ResponseEntity.ok(bookmarkService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bookmarkService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
