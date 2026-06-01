package org.example.dumanagementbackend.repository;

import java.util.List;
import org.example.dumanagementbackend.entity.TeamBookmark;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamBookmarkRepository extends JpaRepository<TeamBookmark, Long> {

    List<TeamBookmark> findAllByOrderByPinnedDescTitleAsc();
}
