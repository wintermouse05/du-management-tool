package org.example.dumanagementbackend.repository;

import java.time.LocalDate;
import java.util.Collection;
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

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    List<User> findByUsernameIn(Collection<String> usernames);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsernameAndIdNot(String username, Long id);

    boolean existsByEmailAndIdNot(String email, Long id);

    @Query("""
            select u
              from User u
             where u.deletedAt is null
               and u.status = :status
             order by u.totalPoints desc
            """)
    List<User> findByStatusOrderByTotalPointsDesc(@Param("status") UserStatus status);

    @Query("""
            select count(u)
              from User u
             where u.deletedAt is null
               and u.status = :status
            """)
    long countByStatus(@Param("status") UserStatus status);

    @Query("""
            select u
              from User u
             where u.deletedAt is null
               and u.status = :status
             order by u.totalPoints desc
            """)
    Page<User> findByStatusOrderByTotalPointsDesc(@Param("status") UserStatus status, Pageable pageable);

    @Query("""
            select u
              from User u
             where u.deletedAt is null
               and u.status = :status
               and lower(u.username) <> lower(:username)
             order by u.totalPoints desc
            """)
    List<User> findByStatusAndUsernameIgnoreCaseNotOrderByTotalPointsDesc(
            @Param("status") UserStatus status,
            @Param("username") String username
    );

    @Query("""
            select count(u)
              from User u
             where u.deletedAt is null
               and u.status = :status
               and lower(u.username) <> lower(:username)
            """)
    long countByStatusAndUsernameIgnoreCaseNot(@Param("status") UserStatus status, @Param("username") String username);

    @Query("""
            select u
              from User u
             where u.deletedAt is null
               and u.status = :status
               and lower(u.username) <> lower(:username)
             order by u.totalPoints desc
            """)
    Page<User> findByStatusAndUsernameIgnoreCaseNotOrderByTotalPointsDesc(
            @Param("status") UserStatus status,
            @Param("username") String username,
            Pageable pageable
    );

        @Query("""
                        select u
                            from User u
                         where u.deletedAt is null
                           and (:includeAdmins = true or lower(u.username) <> 'admin')
                           and (:status is null or u.status = :status)
                           and (
                                        lower(u.username) like :q escape '\\'
                                        or lower(u.email) like :q escape '\\'
                                        or lower(u.fullName) like :q escape '\\'
                           )
                        """)
        Page<User> searchMembers(
                @Param("q") String q,
                @Param("status") UserStatus status,
                @Param("includeAdmins") boolean includeAdmins,
                Pageable pageable
        );

        @Query("""
                        select u
                            from User u
                         where u.deletedAt is null
                           and (:includeAdmins = true or lower(u.username) <> 'admin')
                           and (:status is null or u.status = :status)
                           and (
                                        lower(u.username) like :q escape '\\'
                                        or lower(u.email) like :q escape '\\'
                                        or lower(u.fullName) like :q escape '\\'
                           )
                         order by u.fullName asc
                        """)
        List<User> searchMembersForExport(
                @Param("q") String q,
                @Param("status") UserStatus status,
                @Param("includeAdmins") boolean includeAdmins
        );

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("""
                        update User u
                             set u.totalPoints = u.totalPoints + :delta
                         where u.id = :userId
                           and u.deletedAt is null
                        """)
        int incrementTotalPoints(@Param("userId") Long userId, @Param("delta") int delta);

    Optional<User> findByFullNameAndDeletedAtIsNull(String fullName);

    Optional<User> findByFullNameIgnoreCaseAndDeletedAtIsNull(String fullName);

    @Query("""
            select u
              from User u
             where u.deletedAt is null
               and u.fullName = :fullName
            """)
    Optional<User> findByFullName(@Param("fullName") String fullName);

    @Query("""
            select u
              from User u
             where u.deletedAt is null
               and lower(u.fullName) = lower(:fullName)
            """)
    Optional<User> findByFullNameIgnoreCase(@Param("fullName") String fullName);

    @Query("""
            select case when count(u) > 0 then true else false end
              from User u
             where u.deletedAt is null
               and u.role.id = :roleId
            """)
    boolean existsByRoleIdAndDeletedAtIsNull(@Param("roleId") Long roleId);

    @Query("""
            select u.fullName, count(lr) as lateCount
              from LateRecord lr
              join lr.user u
             where lr.recordDate >= :monthStart
               and lr.recordDate <= :monthEnd
               and u.status = 'ACTIVE'
               and u.deletedAt is null
             group by u.fullName
            having count(lr) >= 2
             order by lateCount desc
            """)
    List<Object[]> findRepeatLateOffendersInMonth(@Param("monthStart") LocalDate monthStart, @Param("monthEnd") LocalDate monthEnd);
}
