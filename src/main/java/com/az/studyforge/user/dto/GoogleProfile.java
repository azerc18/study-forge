package com.az.studyforge.user.dto;

import com.az.studyforge.auth.google.GoogleUserInfo;

public record GoogleProfile(String sub, String email, String name, String avatarUrl) {
    public static GoogleProfile from(GoogleUserInfo userInfo){
        return new GoogleProfile(
                userInfo.sub(),
                userInfo.email(),
                userInfo.name(),
                userInfo.avatarUrl()
        );
    }
}
