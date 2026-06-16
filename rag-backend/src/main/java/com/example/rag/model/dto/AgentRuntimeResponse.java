package com.example.rag.model.dto;

import java.util.List;

/**
 * Python Agent Runtime 返回的诊断结果契约。
 */
public record AgentRuntimeResponse(
        String status,
        String summary,
        List<AgentRuntimeStepResult> steps,
        List<AgentRuntimeActionDraft> recommendedActions,
        String errorMessage
) {
}
