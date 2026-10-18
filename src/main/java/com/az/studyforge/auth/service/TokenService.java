package com.az.studyforge.auth.service;


import com.az.studyforge.auth.entity.RefreshToken;

import java.util.Optional;

public interface RefreshTokenService {
    Optional<RefreshToken> refresh();
}
