package com.az.studyforge.auth.dto;

import com.az.studyforge.user.dto.UserResponse;

public record AuthResult (
        String accessToken,
        long accessTokenExpiresIn,
        String refreshToken,
        UserResponse user
) {
    @Override
    public String toString() {
        return "AuthResult[user=" + user.id() + ", accessToken=***, refreshToken=***]";
    }
}
