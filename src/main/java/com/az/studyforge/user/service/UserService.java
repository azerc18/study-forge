package com.az.studyforge.user.service;

import com.az.studyforge.user.dto.GoogleProfile;
import com.az.studyforge.user.entity.User;

public interface UserService {
    User upsertFromGoogle(GoogleProfile userInfo);
}
