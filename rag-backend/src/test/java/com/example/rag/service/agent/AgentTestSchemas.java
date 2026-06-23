package com.example.rag.service.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 测试用 MCP strict schema helper。 */
public final class AgentTestSchemas {
    private AgentTestSchemas() {
    }

    /** 构造 strict object schema。 */
    public static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }
}
