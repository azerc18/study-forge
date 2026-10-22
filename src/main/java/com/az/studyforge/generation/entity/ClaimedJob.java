package com.az.studyforge.generation.entity;

import java.util.UUID;

public record ClaimedJob(long id, UUID quizSetId, int attempts, int maxAttempts) {}
