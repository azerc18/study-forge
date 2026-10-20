package com.az.studyforge.auth.service;

import com.az.studyforge.auth.dto.AuthResult;
import com.az.studyforge.auth.dto.ClientInfo;
import com.az.studyforge.auth.dto.GoogleLoginRequest;
import com.az.studyforge.auth.google.GoogleTokenVerifier;
import com.az.studyforge.auth.google.GoogleUserInfo;
import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.user.dto.GoogleProfile;
import com.az.studyforge.user.entity.User;
import com.az.studyforge.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String ID_TOKEN = "google-id-token";

    @Mock GoogleTokenVerifier verifier;
    @Mock UserService userService;
    @Mock TokenService tokenService;

    private AuthServiceImpl authService;
    private GoogleLoginRequest request;
    private GoogleUserInfo googleUser;
    private User user;
    private final ClientInfo client = new ClientInfo("JUnit", "127.0.0.1");

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(verifier, userService, tokenService);
        request = new GoogleLoginRequest(ID_TOKEN);
        googleUser = new GoogleUserInfo("sub-1", "a@test.com", "Toan", "https://img.test/avatar.png");
        user = User.builder()
                .id(UUID.randomUUID())
                .googleSub("sub-1")
                .email("a@test.com")
                .fullName("Toan")
                .avatarUrl("https://img.test/avatar.png")
                .build();
    }

    private void givenSuccessfulLogin() {
        when(verifier.verify(ID_TOKEN)).thenReturn(googleUser);
        when(userService.upsertFromGoogle(any(GoogleProfile.class))).thenReturn(user);
        when(tokenService.generateAccessToken(user)).thenReturn("access-token");
        when(tokenService.generateRefreshToken(user, client)).thenReturn("refresh-token");
        when(tokenService.getAccessTokenTtl()).thenReturn(Duration.ofMinutes(15));
    }

    // A1
    @Test
    void loginWithGoogle_validToken_returnsAuthResult() {
        givenSuccessfulLogin();

        AuthResult result = authService.loginWithGoogle(request, client);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.accessTokenExpiresIn()).isEqualTo(900L);
        assertThat(result.user().id()).isEqualTo(user.getId());
        assertThat(result.user().email()).isEqualTo("a@test.com");
    }

    // A2
    @Test
    void loginWithGoogle_passesMappedProfileToUserService() {
        givenSuccessfulLogin();

        authService.loginWithGoogle(request, client);

        ArgumentCaptor<GoogleProfile> captor = ArgumentCaptor.forClass(GoogleProfile.class);
        verify(userService).upsertFromGoogle(captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new GoogleProfile("sub-1", "a@test.com", "Toan", "https://img.test/avatar.png"));
    }

    // A3
    @Test
    void loginWithGoogle_lockedUser_throwsAccountLocked() {
        User lockedUser = User.builder().id(UUID.randomUUID()).googleSub("sub-1").isLocked(true).build();
        when(verifier.verify(ID_TOKEN)).thenReturn(googleUser);
        when(userService.upsertFromGoogle(any(GoogleProfile.class))).thenReturn(lockedUser);

        assertThatThrownBy(() -> authService.loginWithGoogle(request, client))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED));

        verifyNoInteractions(tokenService);
    }

    // A4
    @Test
    void loginWithGoogle_invalidGoogleToken_propagatesError() {
        when(verifier.verify(ID_TOKEN)).thenThrow(new AppException(ErrorCode.INVALID_GOOGLE_TOKEN));

        assertThatThrownBy(() -> authService.loginWithGoogle(request, client))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_GOOGLE_TOKEN));

        verifyNoInteractions(userService, tokenService);
    }

    // A5
    @Test
    void loginWithGoogle_googleUnavailable_propagatesError() {
        when(verifier.verify(ID_TOKEN)).thenThrow(new AppException(ErrorCode.GOOGLE_AUTH_UNAVAILABLE));

        assertThatThrownBy(() -> authService.loginWithGoogle(request, client))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOOGLE_AUTH_UNAVAILABLE));

        verifyNoInteractions(userService, tokenService);
    }

    // A6
    @Test
    void loginWithGoogle_passesClientInfoToTokenService() {
        givenSuccessfulLogin();

        authService.loginWithGoogle(request, client);

        verify(tokenService).generateRefreshToken(same(user), same(client));
    }
}
