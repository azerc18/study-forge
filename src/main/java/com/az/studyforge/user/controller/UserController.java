package com.az.studyforge.user.controller;

import com.az.studyforge.common.dto.ApiResponse;
import com.az.studyforge.common.security.CurrentUser;
import com.az.studyforge.user.dto.MeResponse;
import com.az.studyforge.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;


@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(MeResponse.from(userService.getActiveUser(CurrentUser.id(jwt))));
    }
}
