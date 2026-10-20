package com.az.studyforge.auth.dto;

import com.az.studyforge.user.dto.UserResponse;

public record LoginResponse (
   String accessToken,
   String tokenType,
   long expiresIn,
   UserResponse user
) {
    public static LoginResponse of(AuthResult result) {
        return new LoginResponse(
                result.accessToken(), "Bearer", result.accessTokenExpiresIn(), result.user());
    }
}
