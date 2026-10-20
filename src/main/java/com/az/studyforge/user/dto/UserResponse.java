package com.az.studyforge.user.dto;

import com.az.studyforge.user.entity.Role;
import com.az.studyforge.user.entity.User;

import java.util.UUID;

public record UserResponse (
        UUID id,
        String email,
        String fullName,
        String avatarUrl,
        Role role
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getRole()
        );
    }
}
