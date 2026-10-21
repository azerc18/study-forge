package com.az.studyforge.auth.service;

import com.az.studyforge.auth.config.JwtProperties;
import com.az.studyforge.auth.dto.ClientInfo;
import com.az.studyforge.auth.dto.RotatedToken;
import com.az.studyforge.auth.entity.RefreshToken;
import com.az.studyforge.auth.repository.RefreshTokenRepository;
import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.common.utility.HashUtils;
import com.az.studyforge.user.entity.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@RequiredArgsConstructor
class TokenServiceImplTest {

    @Mock JwtEncoder jwtEncoder;
    @Mock RefreshTokenRepository refreshTokenRepository;

    private TokenService tokenService;

    private User user;
    private ClientInfo client = new ClientInfo("JUnit", "127.0.0.1");
    JwtProperties props;

    private static final String RAW = "old-raw-refresh-token";
    private static final String HASH = HashUtils.sha256Hex(RAW);

    @BeforeEach
    void setUp() {
         props = new JwtProperties(
                "c3R1ZHlmb3JnZS10ZXN0LXNlY3JldC0zMmJ5dGVzISE=", "study-forge",
                Duration.ofMinutes(15), Duration.ofDays(7), true);
        tokenService = new TokenServiceImpl(jwtEncoder, props, refreshTokenRepository);
        user = User.builder().id(UUID.randomUUID()).googleSub("sub-1").email("a@test.com").build();
    }

    @Test
    void generateRefreshToken_twoCalls_returnDifferentTokens() {
        String first = tokenService.generateRefreshToken(user, client);
        String second = tokenService.generateRefreshToken(user, client);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void generateRefreshToken_storesHashNotRawToken() {
        String raw = tokenService.generateRefreshToken(user, client);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        assertThat(saved.getTokenHash())
                .hasSize(64)
                .isEqualTo(HashUtils.sha256Hex(raw))
                .isNotEqualTo(raw);
        assertThat(saved.getUser()).isSameAs(user);
    }

    @Test
    void generateRefreshToken_setsExpiryFromTtl() {
        String rawToken = tokenService.generateRefreshToken(user,client);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        assertThat(saved.getExpiresAt())
                .isCloseTo(Instant.now().plus(Duration.ofDays(7)), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void generateRefreshToken_longUserAgent_truncatedTo255() {
        ClientInfo c = new ClientInfo("a".repeat(300), "127.0.0.1");

        String rawToken = tokenService.generateRefreshToken(user,c);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        assertThat(saved.getUserAgent()).hasSize(255);
    }

    @Test
    void generateRefreshToken_nullClientInfoFields_savedAsNull() {
        tokenService.generateRefreshToken(user, new ClientInfo(null, null));

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        assertThat(saved.getUserAgent()).isNull();
        assertThat(saved.getIpAddress()).isNull();
    }

    @Test
    void generateRefreshToken_returnsUrlSafeString() {
        String rawToken = tokenService.generateRefreshToken(user, client);

        assertThat(rawToken)
                .hasSize(43)
                .matches("^[A-Za-z0-9_-]+$")
                .doesNotContain("+", "/", "=");
    }

    @Test
    void generateAccessToken_containsExpectedClaims() {
        SecretKey key = new SecretKeySpec(Base64.getDecoder().decode(props.secret()), "HmacSHA256");
        JwtEncoder realEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        TokenServiceImpl service = new TokenServiceImpl(realEncoder, props, refreshTokenRepository);

        String token = service.generateAccessToken(user);
        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
        assertThat(jwt.getClaimAsString(JwtClaimNames.ISS)).isEqualTo("study-forge");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
                .isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void getAccessTokenTtl_returnsConfiguredValue() {
        assertThat(tokenService.getAccessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
        verifyNoInteractions(jwtEncoder, refreshTokenRepository);
    }

    private RefreshToken storedToken(User owner, Instant expiresAt, Instant revokedAt) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(owner)
                .tokenHash(HASH)
                .expiresAt(expiresAt)
                .revokedAt(revokedAt)
                .build();
    }

    private void givenStored(RefreshToken token) {
        when(refreshTokenRepository.findByTokenHashWithUser(HASH)).thenReturn(Optional.of(token));
    }

    private static void assertFailsWith(ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(AppException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    // ===== rotateRefreshToken =====

    // R1
    @Test
    void rotate_validToken_revokesOldAndIssuesNew() {
        RefreshToken current = storedToken(user, Instant.now().plus(Duration.ofDays(1)), null);
        givenStored(current);

        RotatedToken result = tokenService.rotateRefreshToken(RAW, client);

        assertThat(result.user()).isSameAs(user);
        assertThat(result.refreshToken()).isNotEqualTo(RAW).hasSize(43);
        assertThat(current.getRevokedAt()).isNotNull();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getTokenHash()).isEqualTo(HashUtils.sha256Hex(result.refreshToken()));
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getRevokedAt()).isNull();

        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any());
    }

    // R2
    @Test
    void rotate_unknownToken_throwsInvalidRefreshToken() {
        when(refreshTokenRepository.findByTokenHashWithUser(HASH)).thenReturn(Optional.empty());

        assertFailsWith(() -> tokenService.rotateRefreshToken(RAW, client), ErrorCode.INVALID_REFRESH_TOKEN);
        verify(refreshTokenRepository, never()).save(any());
    }

    // R3
    @Test
    void rotate_alreadyRevokedToken_revokesAllSessionsAndThrows() {
        RefreshToken reused = storedToken(user,
                Instant.now().plus(Duration.ofDays(1)), Instant.now().minus(Duration.ofMinutes(5)));
        givenStored(reused);

        assertFailsWith(() -> tokenService.rotateRefreshToken(RAW, client), ErrorCode.INVALID_REFRESH_TOKEN);
        verify(refreshTokenRepository).revokeAllByUserId(eq(user.getId()), any(Instant.class));
        verify(refreshTokenRepository, never()).save(any());
    }

    // R4
    @Test
    void rotate_expiredToken_throwsWithoutRevokingAll() {
        RefreshToken expired = storedToken(user, Instant.now().minus(Duration.ofMinutes(1)), null);
        givenStored(expired);

        assertFailsWith(() -> tokenService.rotateRefreshToken(RAW, client), ErrorCode.INVALID_REFRESH_TOKEN);
        assertThat(expired.getRevokedAt()).isNull();
        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    // R5
    @Test
    void rotate_lockedUser_throwsAccountLockedAndDoesNotIssueToken() {
        User locked = User.builder().id(UUID.randomUUID()).googleSub("sub-2")
                .email("b@test.com").isLocked(true).build();
        RefreshToken current = storedToken(locked, Instant.now().plus(Duration.ofDays(1)), null);
        givenStored(current);

        assertFailsWith(() -> tokenService.rotateRefreshToken(RAW, client), ErrorCode.ACCOUNT_LOCKED);
        verify(refreshTokenRepository, never()).save(any());
    }

    // R6
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rotate_missingToken_throwsWithoutQueryingDatabase(String rawToken) {
        assertFailsWith(() -> tokenService.rotateRefreshToken(rawToken, client), ErrorCode.INVALID_REFRESH_TOKEN);
        verifyNoInteractions(refreshTokenRepository);
    }

    // ===== revokeRefreshToken =====

    // R7
    @Test
    void revoke_activeToken_setsRevokedAt() {
        RefreshToken active = storedToken(user, Instant.now().plus(Duration.ofDays(1)), null);
        when(refreshTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(active));

        tokenService.revokeRefreshToken(RAW);

        assertThat(active.getRevokedAt()).isNotNull();
    }

    // R8
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void revoke_missingToken_doesNothing(String rawToken) {
        tokenService.revokeRefreshToken(rawToken);

        verifyNoInteractions(refreshTokenRepository);
    }

    // R8b
    @Test
    void revoke_unknownToken_doesNotThrow() {
        when(refreshTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.empty());

        tokenService.revokeRefreshToken(RAW);   // không ném exception là đạt
    }

    // R9
    @Test
    void revoke_alreadyRevokedToken_keepsOriginalRevokedAt() {
        Instant originalRevokedAt = Instant.now().minus(Duration.ofHours(1));
        RefreshToken revoked = storedToken(user, Instant.now().plus(Duration.ofDays(1)), originalRevokedAt);
        when(refreshTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(revoked));

        tokenService.revokeRefreshToken(RAW);

        assertThat(revoked.getRevokedAt()).isEqualTo(originalRevokedAt);
    }
}