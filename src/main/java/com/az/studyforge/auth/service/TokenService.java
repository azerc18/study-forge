package com.az.studyforge.auth.service;


import com.az.studyforge.auth.dto.AuthResult;
import com.az.studyforge.auth.dto.ClientInfo;
import com.az.studyforge.auth.entity.RefreshToken;
import com.az.studyforge.user.entity.User;

import java.time.Duration;
import java.util.Optional;

public interface TokenService {
    AuthResult rotate(String rawToken, ClientInfo clientInfo);
    String generateAccessToken(User user);
    String generateRefreshToken(User user, ClientInfo clientInfo);
    Duration getAccessTokenTtl();
}
