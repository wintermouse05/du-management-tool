package org.example.dumanagementbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.example.dumanagementbackend.dto.bookmark.BookmarkRequest;
import org.example.dumanagementbackend.dto.bookmark.BookmarkResponse;
import org.example.dumanagementbackend.entity.TeamBookmark;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.TeamBookmarkRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    @Mock
    private TeamBookmarkRepository bookmarkRepository;

    @InjectMocks
    private BookmarkService bookmarkService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAll_returnsMappedBookmarks() {
        TeamBookmark first = buildBookmark(1L, "Wiki", "https://wiki.example.com", true);
        TeamBookmark second = buildBookmark(2L, "Jira", "https://jira.example.com", false);
        when(bookmarkRepository.findAllByOrderByPinnedDescTitleAsc()).thenReturn(List.of(first, second));

        List<BookmarkResponse> responses = bookmarkService.getAll();

        assertEquals(2, responses.size());
        assertEquals("Wiki", responses.get(0).title());
        assertEquals("Jira", responses.get(1).title());
    }

    @Test
    void create_trimsInputAndReturnsResponse() {
        BookmarkRequest request = new BookmarkRequest(
                "  Team Portal  ",
                "  https://portal.example.com  ",
                "  Main company portal  ",
                "  Internal  ",
                true
        );
        when(bookmarkRepository.save(any(TeamBookmark.class))).thenAnswer(invocation -> {
            TeamBookmark saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        BookmarkResponse response = bookmarkService.create(request);

        assertEquals(10L, response.id());
        assertEquals("Team Portal", response.title());
        assertEquals("https://portal.example.com", response.url());
        assertEquals("Main company portal", response.description());
        assertEquals("Internal", response.category());
        assertEquals(true, response.pinned());
    }

    @Test
    void update_throwsNotFoundWhenBookmarkMissing() {
        setAuthentication("owner", "ROLE_MEMBER");
        when(bookmarkRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bookmarkService.update(
                99L,
                new BookmarkRequest("Link", "https://example.com", null, null, false)
        ));
    }

    @Test
    void update_throwsAccessDeniedWhenMemberIsNotCreator() {
        TeamBookmark bookmark = buildBookmark(12L, "Portal", "https://portal.example.com", false);
        bookmark.setCreatedBy("another-member");
        when(bookmarkRepository.findById(12L)).thenReturn(Optional.of(bookmark));
        setAuthentication("member-a", "ROLE_MEMBER");

        assertThrows(AccessDeniedException.class, () -> bookmarkService.update(
                12L,
                new BookmarkRequest("Portal", "https://portal.example.com", null, null, false)
        ));
    }

    @Test
    void update_allowsAdminToModifyAnyBookmark() {
        TeamBookmark bookmark = buildBookmark(13L, "Portal", "https://portal.example.com", false);
        bookmark.setCreatedBy("member-b");
        when(bookmarkRepository.findById(13L)).thenReturn(Optional.of(bookmark));
        when(bookmarkRepository.save(any(TeamBookmark.class))).thenAnswer(invocation -> invocation.getArgument(0));
        setAuthentication("admin-user", "ROLE_ADMIN");

        BookmarkResponse response = bookmarkService.update(
                13L,
                new BookmarkRequest("Portal Updated", "https://portal.example.com", null, null, true)
        );

        assertEquals("Portal Updated", response.title());
        assertEquals(true, response.pinned());
    }

    @Test
    void delete_deletesBookmark() {
        TeamBookmark bookmark = buildBookmark(3L, "Docs", "https://docs.example.com", false);
        bookmark.setCreatedBy("member-user");
        when(bookmarkRepository.findById(3L)).thenReturn(Optional.of(bookmark));
        setAuthentication("member-user", "ROLE_MEMBER");

        bookmarkService.delete(3L);

        verify(bookmarkRepository).delete(bookmark);
    }

    private TeamBookmark buildBookmark(Long id, String title, String url, boolean pinned) {
        TeamBookmark bookmark = new TeamBookmark();
        bookmark.setId(id);
        bookmark.setTitle(title);
        bookmark.setUrl(url);
        bookmark.setPinned(pinned);
        return bookmark;
    }

    private void setAuthentication(String username, String... authorities) {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(username, "password", authorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
