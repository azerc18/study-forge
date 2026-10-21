package com.az.studyforge.user.dto;

import com.az.studyforge.user.entity.User;

public record MeResponse(
        UserResponse user,
        int dailyQuota
        // remainingQuota: thêm ở M3, khi đã có bảng quiz_sets để đếm số đề đã tạo trong ngày
) {
    public static MeResponse from(User user) {
        return new MeResponse(UserResponse.from(user), user.getDailyQuota());
    }
}