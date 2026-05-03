package com.example.rag.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Day 11 第一版问答请求。
 */
public record QaAskRequest(
        @NotBlank(message = "question must not be blank")
        @Size(max = 2000, message = "question length must be <= 2000")
        String question,

        @Min(value = 1, message = "topK must be >= 1")
        @Max(value = 50, message = "topK must be <= 50")
        Integer topK
) {
}
