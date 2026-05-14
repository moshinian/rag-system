package com.example.rag.model.request;

import com.example.rag.model.enums.RetrievalMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 问题检索请求。 */
public record QuestionRetrievalRequest(
        @NotBlank(message = "question must not be blank")
        @Size(max = 2000, message = "question length must be <= 2000")
        String question,

        @Min(value = 1, message = "topK must be >= 1")
        @Max(value = 10, message = "topK must be <= 10")
        Integer topK,

        RetrievalMode retrievalMode
) {
}
