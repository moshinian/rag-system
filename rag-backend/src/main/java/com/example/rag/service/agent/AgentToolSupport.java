package com.example.rag.service.agent;

import com.example.rag.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Agent 工具公共辅助逻辑。
 */
final class AgentToolSupport {
    private AgentToolSupport() {
    }

    /** 把工具输出序列化成 JSON。 */
    static String toJson(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Failed to serialize agent tool output: " + exception.getMessage());
        }
    }

    /** 计算工具耗时。 */
    static long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
