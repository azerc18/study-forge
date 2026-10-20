package com.az.studyforge.auth.service;

import com.az.studyforge.auth.config.JwtProperties;
import com.az.studyforge.auth.dto.ClientInfo;
import com.az.studyforge.auth.entity.RefreshToken;
import com.az.studyforge.auth.repository.RefreshTokenRepository;
import com.az.studyforge.common.utility.HashUtils;
import com.az.studyforge.user.entity.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@RequiredArgsConstructor
class TokenServiceImplTest {

    @Mock JwtEncoder jwtEncoder;
    @Mock RefreshTokenRepository refreshTokenRepository;

    private TokenService tokenService;

    private User user;
    private ClientInfo client = new ClientInfo("JUnit", "127.0.0.1");
    JwtProperties props;

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
}