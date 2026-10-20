package com.az.studyforge.auth.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigTest {

    private static final String SECRET = "c3R1ZHlmb3JnZS10ZXN0LXNlY3JldC0zMmJ5dGVzISE=";
    private static final String OTHER_SECRET = "b3RoZXItc2VjcmV0LWZvci10ZXN0aW5nLTMyYnl0ZXM=";
    private static final String SHORT_SECRET = "c2hvcnQtc2VjcmV0LTE2Yg==";

    private JwtEncoder encoder;
    private JwtDecoder decoder;

    private static JwtProperties props(String secret) {
        return new JwtProperties(secret, "study-forge", Duration.ofMinutes(15), Duration.ofDays(7), true);
    }

    @BeforeEach
    void setUp() {
        JwtConfig config = new JwtConfig(props(SECRET));
        SecretKey key = config.jwtSecretKey();
        encoder = config.jwtEncoder(key);
        decoder = config.jwtDecoder(key);
    }

    private static String encode(JwtEncoder enc, String issuer, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("user-1")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("role", "USER")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return enc.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }


    @Test
    void encodeThenDecode_roundTrip_succeeds() {
        Instant now = Instant.now();
        String token = encode(encoder, "study-forge", now, now.plus(Duration.ofMinutes(15)));

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("user-1");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
    }


    @Test
    void decode_wrongIssuer_throws() {
        Instant now = Instant.now();
        String token = encode(encoder, "other-issuer", now, now.plus(Duration.ofMinutes(15)));

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void decode_expiredToken_throws() {
        Instant now = Instant.now();
        String token = encode(encoder, "study-forge",
                now.minus(Duration.ofMinutes(10)), now.minus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void decode_tokenSignedWithOtherSecret_throws() {
        JwtConfig otherConfig = new JwtConfig(props(OTHER_SECRET));
        JwtEncoder otherEncoder = otherConfig.jwtEncoder(otherConfig.jwtSecretKey());
        Instant now = Instant.now();
        String token = encode(otherEncoder, "study-forge", now, now.plus(Duration.ofMinutes(15)));

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void jwtSecretKey_secretShorterThan32Bytes_throws() {
        JwtConfig config = new JwtConfig(props(SHORT_SECRET));

        assertThatThrownBy(config::jwtSecretKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
