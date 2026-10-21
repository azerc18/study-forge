package com.az.studyforge.auth.service;

import com.az.studyforge.TestcontainersConfiguration;
import com.az.studyforge.auth.dto.ClientInfo;
import com.az.studyforge.auth.dto.RotatedToken;
import com.az.studyforge.auth.entity.RefreshToken;
import com.az.studyforge.auth.repository.RefreshTokenRepository;
import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.common.utility.HashUtils;
import com.az.studyforge.user.entity.User;
import com.az.studyforge.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RefreshTokenRotationIntegrationTest {

    @Autowired TokenService tokenService;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired UserRepository userRepository;

    private User user;
    private final ClientInfo client = new ClientInfo("JUnit", "127.0.0.1");

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .googleSub("it-" + UUID.randomUUID())
                .email("it@test.com")
                .fullName("Integration")
                .build());
    }

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    private RefreshToken load(String rawToken) {
        return refreshTokenRepository.findByTokenHash(HashUtils.sha256Hex(rawToken)).orElseThrow();
    }

    private static void assertInvalidRefresh(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    void reusingOldToken_revokesAllSessionsPersistently() {
        String r1 = tokenService.generateRefreshToken(user, client);
        String r2 = tokenService.rotateRefreshToken(r1, client).refreshToken();

        assertInvalidRefresh(() -> tokenService.rotateRefreshToken(r1, client));   // dùng lại R1

        assertThat(load(r2).getRevokedAt())
                .as("R2 phải bị revoke trong DB sau khi phát hiện R1 bị dùng lại")
                .isNotNull();
        assertInvalidRefresh(() -> tokenService.rotateRefreshToken(r2, client));
    }

    @Test
    void rotation_persistsOldRevokedAndNewActive() {
        String r1 = tokenService.generateRefreshToken(user, client);

        RotatedToken rotated = tokenService.rotateRefreshToken(r1, client);

        assertThat(rotated.refreshToken()).isNotEqualTo(r1);
        assertThat(load(r1).getRevokedAt()).isNotNull();
        assertThat(load(rotated.refreshToken()).getRevokedAt()).isNull();
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void chainedRotation_eachNewTokenWorksOnce() {
        String r1 = tokenService.generateRefreshToken(user, client);
        String r2 = tokenService.rotateRefreshToken(r1, client).refreshToken();
        String r3 = tokenService.rotateRefreshToken(r2, client).refreshToken();

        assertThat(load(r3).getRevokedAt()).isNull();
        assertThat(refreshTokenRepository.count()).isEqualTo(3);
    }

    @Test
    void revokedByLogout_cannotBeUsedToRefresh() {
        String r1 = tokenService.generateRefreshToken(user, client);

        tokenService.revokeRefreshToken(r1);

        assertThat(load(r1).getRevokedAt()).isNotNull();
        assertInvalidRefresh(() -> tokenService.rotateRefreshToken(r1, client));
    }

    @Test
    void expiredToken_isRejectedAndLeftUnchanged() {
        String raw = "expired-raw-token";
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(HashUtils.sha256Hex(raw))
                .expiresAt(Instant.now().minus(Duration.ofMinutes(1)))
                .build());

        assertInvalidRefresh(() -> tokenService.rotateRefreshToken(raw, client));

        assertThat(load(raw).getRevokedAt()).isNull();
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }
}
