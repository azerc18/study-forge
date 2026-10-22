package com.az.studyforge.common.security;

import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public final class CurrentUser {
    private CurrentUser() {}

    public static UUID id(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }
}
