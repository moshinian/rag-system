package com.example.rag.service.agent;

import com.example.rag.common.exception.BusinessException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 校验 Java 内置 MCP tool definition 的 schema strict profile。
 */
final class McpToolContractValidator {
    private static final Set<String> JSON_SCHEMA_TYPES = Set.of(
            "string",
            "object",
            "integer",
            "number",
            "boolean",
            "array",
            "null"
    );

    private McpToolContractValidator() {
    }

    /** 校验 MCP tool definition schema。 */
    static void validate(String toolName,
                         Map<String, Object> inputSchema,
                         Map<String, Object> outputSchema) {
        validateObjectSchema(toolName, "inputSchema", inputSchema, true);
        if (outputSchema != null && !outputSchema.isEmpty()) {
            validateObjectSchema(toolName, "outputSchema", outputSchema, false);
        }
    }

    /** 校验顶层 object schema，并按 strict profile 要求 inputSchema 必须声明 properties。 */
    private static void validateObjectSchema(String toolName,
                                             String fieldPath,
                                             Map<String, Object> schema,
                                             boolean requireProperties) {
        if (schema == null) {
            throw invalid(toolName, fieldPath, "missing");
        }
        Object type = schema.get("type");
        if (type == null) {
            throw invalid(toolName, fieldPath + ".type", "missing");
        }
        if (!"object".equals(type)) {
            throw invalid(toolName, fieldPath + ".type", "type is not object");
        }
        Object propertiesObject = schema.get("properties");
        if (propertiesObject == null && requireProperties) {
            throw invalid(toolName, fieldPath + ".properties", "missing");
        }
        if (propertiesObject != null && !(propertiesObject instanceof Map<?, ?>)) {
            throw invalid(toolName, fieldPath + ".properties", "not object");
        }
        Map<?, ?> properties = propertiesObject instanceof Map<?, ?> propertiesMap
                ? propertiesMap
                : Map.of();
        validateRequired(toolName, fieldPath, schema.get("required"), properties);
        for (Map.Entry<?, ?> entry : properties.entrySet()) {
            if (!(entry.getKey() instanceof String propertyName)) {
                throw invalid(toolName, fieldPath + ".properties", "property name is not string");
            }
            if (!(entry.getValue() instanceof Map<?, ?> propertySchema)) {
                throw invalid(toolName, fieldPath + ".properties." + propertyName, "not object");
            }
            validatePropertySchema(toolName, fieldPath + ".properties." + propertyName, propertySchema);
        }
    }

    /** 校验 required 为字符串数组，且每个必填字段都存在于 properties。 */
    private static void validateRequired(String toolName,
                                         String fieldPath,
                                         Object requiredObject,
                                         Map<?, ?> properties) {
        if (requiredObject == null) {
            return;
        }
        if (!(requiredObject instanceof List<?> required)) {
            throw invalid(toolName, fieldPath + ".required", "not array");
        }
        for (Object item : required) {
            if (!(item instanceof String requiredName)) {
                throw invalid(toolName, fieldPath + ".required", "required item is not string");
            }
            if (!properties.containsKey(requiredName)) {
                throw invalid(
                        toolName,
                        fieldPath + ".required",
                        "required field " + requiredName + " missing from properties"
                );
            }
        }
    }

    /** 校验单个 property schema，并递归检查嵌套 object。 */
    private static void validatePropertySchema(String toolName, String fieldPath, Map<?, ?> schema) {
        Object type = schema.get("type");
        if (type != null && !JSON_SCHEMA_TYPES.contains(type)) {
            throw invalid(toolName, fieldPath + ".type", "unsupported type");
        }
        if ("object".equals(type)) {
            Object propertiesObject = schema.get("properties");
            if (propertiesObject == null) {
                throw invalid(toolName, fieldPath + ".properties", "missing");
            }
            if (!(propertiesObject instanceof Map<?, ?> properties)) {
                throw invalid(toolName, fieldPath + ".properties", "not object");
            }
            validateRequired(toolName, fieldPath, schema.get("required"), properties);
            for (Map.Entry<?, ?> entry : properties.entrySet()) {
                if (!(entry.getKey() instanceof String propertyName)) {
                    throw invalid(toolName, fieldPath + ".properties", "property name is not string");
                }
                if (!(entry.getValue() instanceof Map<?, ?> nestedSchema)) {
                    throw invalid(toolName, fieldPath + ".properties." + propertyName, "not object");
                }
                validatePropertySchema(toolName, fieldPath + ".properties." + propertyName, nestedSchema);
            }
        }
    }

    /** 构造包含工具名、定义字段和失败原因的契约异常。 */
    private static BusinessException invalid(String toolName, String fieldPath, String reason) {
        return new BusinessException(
                "MCP tool contract invalid: tool=" + toolName
                        + ", field=" + fieldPath
                        + ", reason=" + reason
        );
    }
}
