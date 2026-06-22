package com.example.rag.model.request;

import com.example.rag.model.enums.AgentRunMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建 Agent 诊断运行请求。 */
public record AgentRunCreateRequest(
        @NotBlank(message = "goal must not be blank")
        @Size(max = 2000, message = "goal length must be <= 2000")
        String goal,

        @Size(max = 2000, message = "question length must be <= 2000")
        String question,

        AgentRunMode runMode,

        @Size(max = 128, message = "createdBy length must be <= 128")
        String createdBy
) {
}
