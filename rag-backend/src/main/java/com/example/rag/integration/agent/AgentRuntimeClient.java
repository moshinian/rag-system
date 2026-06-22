package com.example.rag.integration.agent;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.config.RagAiGatewayProperties;
import com.example.rag.model.dto.AgentRuntimeRequest;
import com.example.rag.model.dto.AgentRuntimeResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 调用 Python Agent Runtime 的客户端。
 */
@Component
public class AgentRuntimeClient {
    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeClient.class);
    private final RagAiGatewayProperties ragAiGatewayProperties;
    private final ObjectMapper objectMapper;

    /** 构造AgentRuntimeClient。 */
    public AgentRuntimeClient(RagAiGatewayProperties ragAiGatewayProperties,
                              ObjectMapper objectMapper) {
        this.ragAiGatewayProperties = ragAiGatewayProperties;
        this.objectMapper = objectMapper;
    }

    /** 调用 Python Agent Runtime。 */
    public AgentRuntimeResponse run(AgentRuntimeRequest request) {
        try {
            AgentRuntimeResponse response = postJson(
                    ragAiGatewayProperties.getAgentRunsPath(),
                    request,
                    AgentRuntimeResponse.class
            );
            if (response == null) {
                throw new BusinessException("Agent Runtime response is empty");
            }
            log.info(StructuredLogMessage.of("agent.runtime.completed")
                    .field("runCode", request.runCode())
                    .field("status", response.status())
                    .field("stepCount", response.steps() == null ? 0 : response.steps().size())
                    .field("actionCount", response.recommendedActions() == null ? 0 : response.recommendedActions().size())
                    .build());
            return response;
        } catch (IOException ex) {
            log.warn(StructuredLogMessage.of("agent.runtime.failed")
                    .field("runCode", request.runCode())
                    .field("message", ex.getMessage())
                    .build());
            throw new BusinessException("Failed to call Agent Runtime: " + ex.getMessage());
        }
    }

    private <T> T postJson(String path, Object payload, Class<T> responseType) throws IOException {
        byte[] jsonBytes = toJson(payload).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = openConnection(joinUrl(ragAiGatewayProperties.getBaseUrl(), path));
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(resolveConnectTimeoutMillis());
        connection.setReadTimeout(resolveReadTimeoutMillis());
        connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8");
        connection.setRequestProperty(HttpHeaders.ACCEPT, "application/json");
        connection.setFixedLengthStreamingMode(jsonBytes.length);
        String requestId = MDC.get("requestId");
        if (hasText(requestId)) {
            connection.setRequestProperty("X-Request-Id", requestId);
        }

        try {
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(jsonBytes);
                outputStream.flush();
            }
            int statusCode = connection.getResponseCode();
            String responseBody = readResponseBody(connection, statusCode);
            if (statusCode < 200 || statusCode >= 300) {
                throw new BusinessException(statusCode + " " + responseBody);
            }
            return objectMapper.readValue(responseBody, responseType);
        } finally {
            connection.disconnect();
        }
    }

    private String readResponseBody(HttpURLConnection connection, int statusCode) throws IOException {
        InputStream stream = statusCode >= 200 && statusCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (stream == null) {
            return "";
        }
        try (InputStream inputStream = stream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 打开 HTTP 连接，测试中可替换。 */
    protected HttpURLConnection openConnection(String url) throws IOException {
        return (HttpURLConnection) new URL(url).openConnection();
    }

    private int resolveConnectTimeoutMillis() {
        return ragAiGatewayProperties.getConnectTimeoutMillis() == null
                ? 5_000
                : ragAiGatewayProperties.getConnectTimeoutMillis();
    }

    private int resolveReadTimeoutMillis() {
        return ragAiGatewayProperties.getReadTimeoutMillis() == null
                ? 30_000
                : ragAiGatewayProperties.getReadTimeoutMillis();
    }

    private String joinUrl(String baseUrl, String path) {
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.trim();
        String normalizedPath = path == null ? "" : path.trim();
        if (normalizedBaseUrl.isEmpty()) {
            throw new BusinessException("AI gateway base URL must not be blank");
        }
        if (normalizedPath.isEmpty()) {
            throw new BusinessException("Agent Runtime path must not be blank");
        }
        if (normalizedBaseUrl.endsWith("/") && normalizedPath.startsWith("/")) {
            return normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1) + normalizedPath;
        }
        if (!normalizedBaseUrl.endsWith("/") && !normalizedPath.startsWith("/")) {
            return normalizedBaseUrl + "/" + normalizedPath;
        }
        return normalizedBaseUrl + normalizedPath;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to serialize Agent Runtime request payload: " + ex.getMessage());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
