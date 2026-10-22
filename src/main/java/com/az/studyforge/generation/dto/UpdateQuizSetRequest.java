package com.az.studyforge.generation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateQuizSetRequest(@NotBlank @Size(max = 200) String title) {}
