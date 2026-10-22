package com.az.studyforge.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitEssayRequest(@NotBlank @Size(max = 3000) String answer) {}
