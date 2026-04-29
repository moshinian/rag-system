package com.example.rag.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateKnowledgeBaseRequest(
        @NotBlank(message = "kbCode must not be blank")
        @Size(max = 64, message = "kbCode length must be <= 64")
        String kbCode,

        @NotBlank(message = "name must not be blank")
        @Size(max = 128, message = "name length must be <= 128")
        String name,

        @Size(max = 1024, message = "description length must be <= 1024")
        String description,

        @Size(max = 64, message = "createdBy length must be <= 64")
        String createdBy
) {
}
