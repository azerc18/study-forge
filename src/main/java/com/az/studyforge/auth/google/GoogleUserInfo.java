package com.az.studyforge.auth.google;

import com.az.studyforge.user.dto.GoogleProfile;

public record GoogleUserInfo(String sub, String email, String name, String avatarUrl) {
    public GoogleProfile toProfile() {
        return new GoogleProfile(sub, email, name, avatarUrl);
    }
}
