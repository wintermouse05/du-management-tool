package org.example.dumanagementbackend.controller;

import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dumanagementbackend.dto.group.GroupMemberRequest;
import org.example.dumanagementbackend.dto.group.GroupMemberResponse;
import org.example.dumanagementbackend.dto.group.GroupRequest;
import org.example.dumanagementbackend.dto.group.GroupResponse;
import org.example.dumanagementbackend.service.GroupService;
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
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HR')")
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    public ResponseEntity<GroupResponse> create(@Valid @RequestBody GroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<GroupResponse>> getAll() {
        return ResponseEntity.ok(groupService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(groupService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GroupResponse> update(@PathVariable Long id, @Valid @RequestBody GroupRequest request) {
        return ResponseEntity.ok(groupService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        groupService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<GroupMemberResponse>> getMembers(@PathVariable Long id) {
        return ResponseEntity.ok(groupService.getMemberResponses(id));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<GroupResponse> addMember(@PathVariable Long id, @Valid @RequestBody GroupMemberRequest request) {
        return ResponseEntity.ok(groupService.addMember(id, request));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<GroupResponse> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        return ResponseEntity.ok(groupService.removeMember(id, userId));
    }
}
