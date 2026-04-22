package org.example.dumanagementbackend.repository;

import java.util.List;
import java.util.Optional;

import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<User> findByStatusOrderByTotalPointsDesc(UserStatus status);

    Page<User> findByStatusOrderByTotalPointsDesc(UserStatus status, Pageable pageable);

        @Query("""
                        select u
                            from User u
                         where (:status is null or u.status = :status)
                           and (
                                        lower(u.username) like :q escape '\\'
                                        or lower(u.email) like :q escape '\\'
                                        or lower(u.fullName) like :q escape '\\'
                           )
                        """)
        Page<User> searchMembers(@Param("q") String q, @Param("status") UserStatus status, Pageable pageable);

        @Query("""
                        select u
                            from User u
                         where (:status is null or u.status = :status)
                           and (
                                        lower(u.username) like :q escape '\\'
                                        or lower(u.email) like :q escape '\\'
                                        or lower(u.fullName) like :q escape '\\'
                           )
                         order by u.fullName asc
                        """)
        List<User> searchMembersForExport(@Param("q") String q, @Param("status") UserStatus status);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("""
                        update User u
                             set u.totalPoints = u.totalPoints + :delta
                         where u.id = :userId
                        """)
        int incrementTotalPoints(@Param("userId") Long userId, @Param("delta") int delta);
}
