package com.az.studyforge.auth.google;

import com.az.studyforge.auth.exception.InvalidGoogleTokenException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Component
public class GoogleApiTokenVerifier implements GoogleTokenVerifier{

    private final GoogleIdTokenVerifier verifier;

    public GoogleApiTokenVerifier(@Value("${app.google.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(clientId))
                .build();
    }

    @Override
    public GoogleUserInfo verify(String idToken) {
        try{
            GoogleIdToken token = verifier.verify(idToken);
            if(token == null) {
                throw new InvalidGoogleTokenException("Invalid Google ID token");
            }

            GoogleIdToken.Payload payload = token.getPayload();
            if(!Boolean.TRUE.equals(payload.getEmailVerified())){
                throw new InvalidGoogleTokenException("Email not verified");
            }

            return new GoogleUserInfo(
                    payload.getSubject(),
                    payload.getEmail(),
                    (String) payload.get("name"),
                    (String) payload.get("picture")
            );
        } catch (GeneralSecurityException | IOException e) {
            throw new InvalidGoogleTokenException("Cannot verify Google ID token");
        }
    }
}
