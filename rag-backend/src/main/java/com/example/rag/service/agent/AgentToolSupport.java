package com.example.rag.service.agent;

import com.example.rag.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /** 构造严格 object JSON Schema。 */
    static Map<String, Object> objectSchema(List<String> required, Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties == null ? Map.of() : properties);
        schema.put("required", required == null ? List.of() : required);
        schema.put("additionalProperties", false);
        return schema;
    }

    /** 构造 string property schema。 */
    static Map<String, Object> stringProperty(String description) {
        return typedProperty("string", description);
    }

    /** 构造 integer property schema。 */
    static Map<String, Object> integerProperty(String description, Integer minimum, Integer maximum) {
        Map<String, Object> property = typedProperty("integer", description);
        if (minimum != null) {
            property.put("minimum", minimum);
        }
        if (maximum != null) {
            property.put("maximum", maximum);
        }
        return property;
    }

    /** 构造 boolean property schema。 */
    static Map<String, Object> booleanProperty(String description) {
        return typedProperty("boolean", description);
    }

    /** 构造 object property schema。 */
    static Map<String, Object> objectProperty(String description,
                                              List<String> required,
                                              Map<String, Object> properties) {
        Map<String, Object> property = objectSchema(required, properties);
        if (description != null && !description.isBlank()) {
            property.put("description", description);
        }
        return property;
    }

    /** 构造 array property schema。 */
    static Map<String, Object> arrayProperty(String description, Map<String, Object> items) {
        Map<String, Object> property = typedProperty("array", description);
        property.put("items", items == null ? Map.of() : items);
        return property;
    }

    private static Map<String, Object> typedProperty(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        if (description != null && !description.isBlank()) {
            property.put("description", description);
        }
        return property;
    }
}
