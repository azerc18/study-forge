package com.az.studyforge.auth.service;

import com.az.studyforge.auth.dto.AuthResult;
import com.az.studyforge.auth.dto.ClientInfo;
import com.az.studyforge.auth.dto.GoogleLoginRequest;
import com.az.studyforge.auth.google.GoogleTokenVerifier;
import com.az.studyforge.auth.google.GoogleUserInfo;
import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.user.dto.GoogleProfile;
import com.az.studyforge.user.dto.UserResponse;
import com.az.studyforge.user.entity.User;
import com.az.studyforge.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final GoogleTokenVerifier verifier;
    private final UserService userService;
    private final TokenService tokenService;

    @Override
    public AuthResult loginWithGoogle(GoogleLoginRequest request, ClientInfo clientInfo) {
        GoogleUserInfo userInfo = verifier.verify(request.idToken());

        User user = userService.upsertFromGoogle(userInfo.toProfile());

        if (user.isLocked()) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }

        String accessToken = tokenService.generateAccessToken(user);
        String refreshToken = tokenService.generateRefreshToken(user, clientInfo);


        return new AuthResult(
                accessToken,
                tokenService.getAccessTokenTtl().toSeconds(),
                refreshToken,
                UserResponse.from(user)
        );
    }
}
