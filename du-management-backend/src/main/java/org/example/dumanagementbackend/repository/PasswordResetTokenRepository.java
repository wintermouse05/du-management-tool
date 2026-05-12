package org.example.dumanagementbackend.repository;

import java.time.Instant;
import java.util.Optional;
import org.example.dumanagementbackend.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
