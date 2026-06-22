package com.example.rag.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** Agent 内部工具执行请求。 */
public record AgentToolExecuteRequest(
        @NotBlank(message = "kbCode must not be blank")
        @Size(max = 128, message = "kbCode length must be <= 128")
        String kbCode,

        @Size(max = 128, message = "runCode length must be <= 128")
        String runCode,

        @Size(max = 2000, message = "question length must be <= 2000")
        String question,

        @Size(max = 128, message = "operator length must be <= 128")
        String operator,

        Map<String, Object> attributes
) {
}
