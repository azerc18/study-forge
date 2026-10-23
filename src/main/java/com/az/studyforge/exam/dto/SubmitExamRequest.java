package com.az.studyforge.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record SubmitExamRequest(@NotNull @Size(max = 50) List<@Valid @NotNull Answer> answers) {

    /** label = null → bỏ trống câu đó. */
    public record Answer(@NotNull UUID questionId, @Pattern(regexp = "[A-D]") String label) {}
}
