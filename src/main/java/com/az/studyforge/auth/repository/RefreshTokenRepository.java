package com.az.studyforge.auth.repository;

import com.az.studyforge.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    @Query("""
           SELECT rt FROM RefreshToken rt
           JOIN FETCH rt.user
           WHERE rt.tokenHash = :hash
           """)
    Optional<RefreshToken> findByTokenHashWithUser(String hash);

    @Modifying
    @Query("""
           UPDATE RefreshToken rt SET rt.revokedAt = :now
           WHERE rt.user.id = :userId AND rt.revokedAt IS NULL
           """)
    int revokeAllByUserId(UUID userId, Instant now);
}
