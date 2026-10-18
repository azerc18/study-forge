package com.az.studyforge.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleLoginRequest(
    @NotBlank
    @Size(max = 4069)
    String idToken
) {}
