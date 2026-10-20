package com.az.studyforge.auth.google;

public interface GoogleTokenVerifier {
    GoogleUserInfo verify(String idToken);
}
