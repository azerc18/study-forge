package com.az.studyforge.generation.controller;

import com.az.studyforge.common.dto.ApiResponse;
import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.common.security.CurrentUser;
import com.az.studyforge.document.dto.DocumentResponse;
import com.az.studyforge.document.entity.DocumentStatus;
import com.az.studyforge.document.service.DocumentService;
import com.az.studyforge.generation.domain.OutputLanguage;
import com.az.studyforge.generation.dto.GenerationOptionsResponse;
import com.az.studyforge.generation.policy.QuestionLimitPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class GenerationOptionsController {

    private final DocumentService documentService;
    private final QuestionLimitPolicy limitPolicy;

    @GetMapping("/api/documents/{id}/options")
    public ApiResponse<GenerationOptionsResponse> options(@AuthenticationPrincipal Jwt jwt,
                                                          @PathVariable UUID id) {
        DocumentResponse doc = documentService.get(CurrentUser.id(jwt), id);

        if (doc.status() != DocumentStatus.READY) {
            throw new AppException(ErrorCode.DOCUMENT_NOT_READY);
        }

        int words = doc.wordCount();
        return ApiResponse.success(new GenerationOptionsResponse(
                doc.id(), words, limitPolicy.isShort(words),
                limitPolicy.allowedCounts(words),
                List.of(OutputLanguage.values())));
    }
}
