package com.az.studyforge.auth.service;


import com.az.studyforge.auth.entity.RefreshToken;

import java.util.Optional;

public interface TokenService {
    Optional<RefreshToken> refresh();
    String generateAccessToken();
    String generateRefreshToken();
}
