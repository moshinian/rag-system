package com.example.rag.model.request;

/**
 * Agent 推荐动作拒绝请求。
 */
public record AgentActionRejectRequest(
        String operator,
        String reason
) {
}
