package com.az.studyforge.user.service;

import com.az.studyforge.user.dto.GoogleProfile;
import com.az.studyforge.user.entity.User;

import java.util.UUID;

public interface UserService {
    User upsertFromGoogle(GoogleProfile userInfo);
    User getActiveUser(UUID id);
    User lockActiveUser(UUID userId);
}
