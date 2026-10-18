package com.az.studyforge.auth.service;

import com.az.studyforge.auth.entity.RefreshToken;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TokenServiceImpl implements TokenService {
    @Override
    public Optional<RefreshToken> refresh() {
        return Optional.empty();
    }

    @Override
    public String generateAccessToken() {
        return "";
    }

    @Override
    public String generateRefreshToken() {
        return "";
    }
}
