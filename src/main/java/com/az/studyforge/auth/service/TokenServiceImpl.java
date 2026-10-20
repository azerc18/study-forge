package com.az.studyforge.auth.service;

import com.az.studyforge.auth.config.JwtProperties;
import com.az.studyforge.auth.dto.AuthResult;
import com.az.studyforge.auth.dto.ClientInfo;
import com.az.studyforge.auth.entity.RefreshToken;
import com.az.studyforge.auth.repository.RefreshTokenRepository;
import com.az.studyforge.common.utility.HashUtils;
import com.az.studyforge.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTE = 32;

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    public String generateAccessToken(User user) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("role", user.getRole().name())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Override
    @Transactional
    public String generateRefreshToken(User user, ClientInfo clientInfo) {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTE];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(HashUtils.sha256Hex(rawToken))
                .expiresAt(Instant.now().plus(jwtProperties.refreshTokenTtl()))
                .userAgent(truncate(clientInfo.userAgent(),255))
                .ipAddress(truncate(clientInfo.ipAddress(),45))
                .build();
        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    @Override
    public Duration getAccessTokenTtl() {
        return jwtProperties.accessTokenTtl();
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    @Override
    public AuthResult rotate(String rawToken, ClientInfo clientInfo) {



        return null;
    }
}
