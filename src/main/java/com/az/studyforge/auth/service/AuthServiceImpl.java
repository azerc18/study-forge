package com.az.studyforge.auth.service;

import com.az.studyforge.auth.dto.AuthResult;
import com.az.studyforge.auth.dto.GoogleLoginRequest;
import com.az.studyforge.auth.exception.LockedAccountException;
import com.az.studyforge.auth.google.GoogleTokenVerifier;
import com.az.studyforge.auth.google.GoogleUserInfo;
import com.az.studyforge.user.dto.GoogleProfile;
import com.az.studyforge.user.dto.UserResponse;
import com.az.studyforge.user.entity.User;
import com.az.studyforge.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService{
    private final GoogleTokenVerifier verifier;
    private final UserService userService;
    private final TokenService tokenService;

    @Override
    public AuthResult loginWithGoogle(GoogleLoginRequest request) {
        GoogleUserInfo  userInfo = verifier.verify(request.idToken());

        GoogleProfile profile = GoogleProfile.from(userInfo);
        User user = userService.upsertFromGoogle(profile);

        if(user.isLocked()){
            throw new LockedAccountException();
        }

        String accessToken = tokenService.generateAccessToken();
        String refreshToken = tokenService.generateRefreshToken();


        return new AuthResult(
                accessToken,
                0L,
                refreshToken,
                UserResponse.from(user)
        );
    }
}
