package com.example.rag.service.agent;

import com.example.rag.common.exception.BusinessException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * 校验 MCP tools/call arguments 是否符合当前 tool inputSchema。
 */
public final class McpToolArgumentsValidator {
    private McpToolArgumentsValidator() {
    }

    /** 校验一次工具调用参数。 */
    public static void validate(String toolName, Map<String, Object> inputSchema, Map<String, Object> arguments) {
        validateObject(toolName, "arguments", inputSchema, arguments);
    }

    /** 递归校验 object 的 required、properties 与 additionalProperties 约束。 */
    private static void validateObject(String toolName,
                                       String fieldPath,
                                       Map<?, ?> schema,
                                       Map<?, ?> value) {
        Object requiredObject = schema.get("required");
        if (requiredObject instanceof List<?> required) {
            for (Object item : required) {
                if (item instanceof String requiredName && !value.containsKey(requiredName)) {
                    throw invalid(toolName, fieldPath + "." + requiredName, "missing");
                }
            }
        }

        Map<?, ?> properties = schema.get("properties") instanceof Map<?, ?> propertiesMap
                ? propertiesMap
                : Map.of();
        boolean additionalProperties = !Boolean.FALSE.equals(schema.get("additionalProperties"));
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            if (!(entry.getKey() instanceof String propertyName)) {
                throw invalid(toolName, fieldPath, "property name is not string");
            }
            Object propertySchema = properties.get(propertyName);
            if (!(propertySchema instanceof Map<?, ?> propertySchemaMap)) {
                if (!additionalProperties) {
                    throw invalid(toolName, fieldPath + "." + propertyName, "additional property is not allowed");
                }
                continue;
            }
            validateValue(toolName, fieldPath + "." + propertyName, propertySchemaMap, entry.getValue());
        }
    }

    /** 根据 schema type 分派单值校验，并继续处理嵌套 object。 */
    private static void validateValue(String toolName, String fieldPath, Map<?, ?> schema, Object value) {
        Object type = schema.get("type");
        if ("string".equals(type) && !(value instanceof String)) {
            throw invalid(toolName, fieldPath, "must be string");
        }
        if ("integer".equals(type)) {
            BigInteger integer = toInteger(value);
            if (integer == null) {
                throw invalid(toolName, fieldPath, "must be integer");
            }
            validateNumberRange(toolName, fieldPath, schema, new BigDecimal(integer));
        }
        if ("number".equals(type)) {
            BigDecimal number = toNumber(value);
            if (number == null) {
                throw invalid(toolName, fieldPath, "must be number");
            }
            validateNumberRange(toolName, fieldPath, schema, number);
        }
        if ("boolean".equals(type) && !(value instanceof Boolean)) {
            throw invalid(toolName, fieldPath, "must be boolean");
        }
        if ("object".equals(type)) {
            if (!(value instanceof Map<?, ?> valueMap)) {
                throw invalid(toolName, fieldPath, "must be object");
            }
            validateObject(toolName, fieldPath, schema, valueMap);
        }
        if ("array".equals(type) && !(value instanceof List<?>)) {
            throw invalid(toolName, fieldPath, "must be array");
        }
    }

    /** 校验 number/integer 的 minimum 和 maximum 边界。 */
    private static void validateNumberRange(String toolName,
                                            String fieldPath,
                                            Map<?, ?> schema,
                                            BigDecimal value) {
        BigDecimal minimum = toNumber(schema.get("minimum"));
        BigDecimal maximum = toNumber(schema.get("maximum"));
        if (minimum != null && value.compareTo(minimum) < 0) {
            throw invalid(toolName, fieldPath, "must be >= " + minimum.stripTrailingZeros().toPlainString());
        }
        if (maximum != null && value.compareTo(maximum) > 0) {
            throw invalid(toolName, fieldPath, "must be <= " + maximum.stripTrailingZeros().toPlainString());
        }
    }

    /** 把 Jackson 可能产生的各类整数 Number 无损转换为 BigInteger。 */
    private static BigInteger toInteger(Object value) {
        if (value instanceof Boolean || !(value instanceof Number number)) {
            return null;
        }
        if (number instanceof BigInteger bigInteger) {
            return bigInteger;
        }
        if (number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long) {
            return BigInteger.valueOf(number.longValue());
        }
        BigDecimal decimal = toNumber(number);
        if (decimal == null || decimal.stripTrailingZeros().scale() > 0) {
            return null;
        }
        return decimal.toBigIntegerExact();
    }

    /** 把有限 Number 统一转换为 BigDecimal，便于精确比较 schema 数值边界。 */
    private static BigDecimal toNumber(Object value) {
        if (value instanceof Boolean || !(value instanceof Number number)) {
            return null;
        }
        if (number instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (number instanceof BigInteger bigInteger) {
            return new BigDecimal(bigInteger);
        }
        if (number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long) {
            return BigDecimal.valueOf(number.longValue());
        }
        double doubleValue = number.doubleValue();
        if (!Double.isFinite(doubleValue)) {
            return null;
        }
        return BigDecimal.valueOf(doubleValue);
    }

    /** 构造包含工具名、字段路径和失败原因的参数异常。 */
    private static BusinessException invalid(String toolName, String fieldPath, String reason) {
        return new BusinessException(
                "MCP tool arguments invalid: tool=" + toolName
                        + ", field=" + fieldPath
                        + ", reason=" + reason
        );
    }
}
