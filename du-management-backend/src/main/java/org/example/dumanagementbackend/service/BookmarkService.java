package org.example.dumanagementbackend.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.dumanagementbackend.dto.bookmark.BookmarkRequest;
import org.example.dumanagementbackend.dto.bookmark.BookmarkResponse;
import org.example.dumanagementbackend.entity.TeamBookmark;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.TeamBookmarkRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookmarkService {

    private final TeamBookmarkRepository bookmarkRepository;

    public List<BookmarkResponse> getAll() {
        return bookmarkRepository.findAllByOrderByPinnedDescTitleAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    public BookmarkResponse getById(Long id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public BookmarkResponse create(BookmarkRequest request) {
        TeamBookmark bookmark = new TeamBookmark();
        apply(bookmark, request);
        return toResponse(bookmarkRepository.save(bookmark));
    }

    @Transactional
    public BookmarkResponse update(Long id, BookmarkRequest request) {
        TeamBookmark bookmark = getEntity(id);
        assertCanModify(bookmark);
        apply(bookmark, request);
        return toResponse(bookmarkRepository.save(bookmark));
    }

    @Transactional
    public void delete(Long id) {
        TeamBookmark bookmark = getEntity(id);
        assertCanModify(bookmark);
        bookmarkRepository.delete(bookmark);
    }

    private TeamBookmark getEntity(Long id) {
        return bookmarkRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bookmark not found with id=" + id));
    }

    private void apply(TeamBookmark bookmark, BookmarkRequest request) {
        bookmark.setTitle(request.title().trim());
        bookmark.setUrl(request.url().trim());
        bookmark.setDescription(normalizeNullableText(request.description()));
        bookmark.setCategory(normalizeNullableText(request.category()));
        bookmark.setPinned(request.pinned());
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private void assertCanModify(TeamBookmark bookmark) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new AccessDeniedException("You do not have permission to modify this bookmark.");
        }

        if (hasAdminOrHrAuthority(authentication)) {
            return;
        }

        String createdBy = bookmark.getCreatedBy();
        if (createdBy != null && createdBy.equalsIgnoreCase(authentication.getName())) {
            return;
        }

        throw new AccessDeniedException("You do not have permission to modify this bookmark.");
    }

    private boolean hasAdminOrHrAuthority(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority) || "ROLE_HR".equals(authority));
    }

    private BookmarkResponse toResponse(TeamBookmark bookmark) {
        return new BookmarkResponse(
                bookmark.getId(),
                bookmark.getTitle(),
                bookmark.getUrl(),
                bookmark.getDescription(),
                bookmark.getCategory(),
                bookmark.isPinned(),
                bookmark.getCreatedBy(),
                bookmark.getUpdatedAt(),
                bookmark.getUpdatedBy()
        );
    }
}
