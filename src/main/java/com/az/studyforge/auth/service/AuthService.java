package com.az.studyforge.auth.service;

import com.az.studyforge.auth.dto.AuthResult;
import com.az.studyforge.auth.dto.GoogleLoginRequest;

public interface AuthService {
    AuthResult loginWithGoogle(GoogleLoginRequest request);
}
