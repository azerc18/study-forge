package com.az.studyforge.auth.service;


import com.az.studyforge.auth.dto.AuthResult;
import com.az.studyforge.auth.dto.ClientInfo;
import com.az.studyforge.auth.dto.RotatedToken;
import com.az.studyforge.auth.entity.RefreshToken;
import com.az.studyforge.user.entity.User;

import java.time.Duration;
import java.util.Optional;

public interface TokenService {
    String generateAccessToken(User user);
    String generateRefreshToken(User user, ClientInfo clientInfo);
    Duration getAccessTokenTtl();
    RotatedToken rotateRefreshToken(String rawToken, ClientInfo clientInfo);
    void revokeRefreshToken(String rawToken);
}
