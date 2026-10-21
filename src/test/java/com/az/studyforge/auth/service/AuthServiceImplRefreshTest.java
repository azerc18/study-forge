package com.az.studyforge.auth.service;

import com.az.studyforge.auth.dto.AuthResult;
import com.az.studyforge.auth.dto.ClientInfo;
import com.az.studyforge.auth.dto.RotatedToken;
import com.az.studyforge.auth.google.GoogleTokenVerifier;
import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.user.entity.User;
import com.az.studyforge.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplRefreshTest {

    @Mock GoogleTokenVerifier verifier;
    @Mock UserService userService;
    @Mock TokenService tokenService;

    private AuthServiceImpl authService;
    private User user;
    private final ClientInfo client = new ClientInfo("JUnit", "127.0.0.1");

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(verifier, userService, tokenService);
        user = User.builder().id(UUID.randomUUID()).googleSub("sub-1").email("a@test.com").fullName("Toan").build();
    }

    // AR1
    @Test
    void refresh_validToken_returnsNewTokenPair() {
        when(tokenService.rotateRefreshToken("old-refresh", client))
                .thenReturn(new RotatedToken(user, "new-refresh"));
        when(tokenService.generateAccessToken(user)).thenReturn("new-access");
        when(tokenService.getAccessTokenTtl()).thenReturn(Duration.ofMinutes(15));

        AuthResult result = authService.refresh("old-refresh", client);

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        assertThat(result.accessTokenExpiresIn()).isEqualTo(900L);
        assertThat(result.user().id()).isEqualTo(user.getId());
    }

    // AR2
    @Test
    void refresh_rotationFails_propagatesErrorAndIssuesNoAccessToken() {
        when(tokenService.rotateRefreshToken("bad", client))
                .thenThrow(new AppException(ErrorCode.INVALID_REFRESH_TOKEN));

        assertThatThrownBy(() -> authService.refresh("bad", client))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));

        verify(tokenService, never()).generateAccessToken(any());
    }

    // AR3
    @Test
    void refresh_doesNotTouchGoogleOrUserService() {
        when(tokenService.rotateRefreshToken("old-refresh", client))
                .thenReturn(new RotatedToken(user, "new-refresh"));
        when(tokenService.generateAccessToken(user)).thenReturn("new-access");
        when(tokenService.getAccessTokenTtl()).thenReturn(Duration.ofMinutes(15));

        authService.refresh("old-refresh", client);

        verifyNoInteractions(verifier, userService);
    }

    // AL1
    @Test
    void logout_delegatesToTokenService() {
        authService.logout("old-refresh");

        verify(tokenService).revokeRefreshToken("old-refresh");
    }

    // AL2
    @Test
    void logout_nullToken_stillDelegatesWithoutError() {
        authService.logout(null);

        verify(tokenService).revokeRefreshToken(null);
    }
}
