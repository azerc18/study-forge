package com.az.studyforge.feedback.dto;

import java.util.List;

public record FeedbackDetail(String summary, List<String> corrections,
                             List<String> suggestions, String improvedAnswer) {}
