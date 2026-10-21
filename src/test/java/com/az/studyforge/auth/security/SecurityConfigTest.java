package com.az.studyforge.auth.security;

import com.az.studyforge.TestcontainersConfiguration;
import com.az.studyforge.auth.service.TokenService;
import com.az.studyforge.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SecurityConfigTest {

    @Autowired MockMvc mockMvc;
    @Autowired TokenService tokenService;

    // S1
    @Test
    void actuatorHealth_noToken_returns200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    // S2: permitAll có tác dụng → tới controller → lỗi validation 400, không phải 401
    @Test
    void authEndpoint_noToken_isNotBlocked() throws Exception {
        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // S3
    @Test
    void protectedEndpoint_noToken_returns401Json() throws Exception {
        mockMvc.perform(get("/api/anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    // S4
    @Test
    void protectedEndpoint_invalidToken_returns401Json() throws Exception {
        mockMvc.perform(get("/api/anything")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        org.hamcrest.Matchers.containsString("invalid_token")))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    // S5: token hợp lệ → qua được security → endpoint chưa tồn tại nên 404
    @Test
    void protectedEndpoint_validToken_passesSecurity() throws Exception {
        User user = User.builder().id(UUID.randomUUID()).googleSub("s1").email("a@test.com").build();
        String token = tokenService.generateAccessToken(user);

        mockMvc.perform(get("/api/anything")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
