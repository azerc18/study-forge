package com.az.studyforge.auth.dto;

import com.az.studyforge.user.entity.User;

public record RotatedToken(User user, String refreshToken) {
    @Override
    public String toString() {
        return "RotatedToken[user=" + user.getId() + ", refreshToken=***]";
    }
}