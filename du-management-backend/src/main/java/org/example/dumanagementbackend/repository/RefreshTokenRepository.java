package org.example.dumanagementbackend.repository;

import java.time.Instant;
import java.util.Optional;
import org.example.dumanagementbackend.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update RefreshToken t
               set t.revokedAt = :revokedAt,
                   t.revokeReason = :reason
             where t.familyId = :familyId
               and t.revokedAt is null
               and t.expiresAt > :revokedAt
            """)
    int revokeActiveByFamilyId(
            @Param("familyId") String familyId,
            @Param("revokedAt") Instant revokedAt,
            @Param("reason") String reason
    );

    @Modifying
    @Query("""
            update RefreshToken t
               set t.revokedAt = :revokedAt,
                   t.revokeReason = :reason
             where t.user.id = :userId
               and t.revokedAt is null
               and t.expiresAt > :revokedAt
            """)
    int revokeActiveByUserId(
            @Param("userId") Long userId,
            @Param("revokedAt") Instant revokedAt,
            @Param("reason") String reason
    );
}
