package org.example.dumanagementbackend.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.example.dumanagementbackend.entity.Seminar;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeminarRepository extends JpaRepository<Seminar, Long> {

	List<Seminar> findByScheduledAtBetween(LocalDateTime from, LocalDateTime to);

    @Query("""
            select s
              from Seminar s
             order by
               case
                 when s.status in (org.example.dumanagementbackend.entity.enums.SeminarStatus.PENDING,
                                   org.example.dumanagementbackend.entity.enums.SeminarStatus.PROPOSED) then 0
                 when s.status in (org.example.dumanagementbackend.entity.enums.SeminarStatus.APPROVED,
                                   org.example.dumanagementbackend.entity.enums.SeminarStatus.SCHEDULED) then 1
                 when s.status = org.example.dumanagementbackend.entity.enums.SeminarStatus.DONE then 2
                 else 3
               end,
               coalesce(s.scheduledAt, s.createdAt) asc,
               s.id desc
            """)
    Page<Seminar> findAllOrdered(Pageable pageable);

    @Query("""
            select s
              from Seminar s
             where s.status in (org.example.dumanagementbackend.entity.enums.SeminarStatus.APPROVED,
                                org.example.dumanagementbackend.entity.enums.SeminarStatus.DONE,
                                org.example.dumanagementbackend.entity.enums.SeminarStatus.SCHEDULED)
                or (
                    s.status in (org.example.dumanagementbackend.entity.enums.SeminarStatus.PENDING,
                                 org.example.dumanagementbackend.entity.enums.SeminarStatus.PROPOSED)
                    and (
                        lower(coalesce(s.createdBy, '')) = lower(:username)
                        or lower(coalesce(s.createdBy, '')) = lower(:email)
                    )
                )
             order by
               case
                 when s.status in (org.example.dumanagementbackend.entity.enums.SeminarStatus.PENDING,
                                   org.example.dumanagementbackend.entity.enums.SeminarStatus.PROPOSED) then 0
                 when s.status in (org.example.dumanagementbackend.entity.enums.SeminarStatus.APPROVED,
                                   org.example.dumanagementbackend.entity.enums.SeminarStatus.SCHEDULED) then 1
                 when s.status = org.example.dumanagementbackend.entity.enums.SeminarStatus.DONE then 2
                 else 3
               end,
               coalesce(s.scheduledAt, s.createdAt) asc,
               s.id desc
            """)
    Page<Seminar> findVisibleForMember(
            @Param("username") String username,
            @Param("email") String email,
            Pageable pageable
    );
}
