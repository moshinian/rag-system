package com.example.rag.integration.ai;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.config.RagAiGatewayProperties;
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
import java.util.List;

/**
 * 调用 Python AI Gateway 的客户端。
 */
@Component
public class AiGatewayClient {
    private static final Logger log = LoggerFactory.getLogger(AiGatewayClient.class);
    private final RagAiGatewayProperties ragAiGatewayProperties;
    private final ObjectMapper objectMapper;

    public AiGatewayClient(RagAiGatewayProperties ragAiGatewayProperties,
                           ObjectMapper objectMapper) {
        this.ragAiGatewayProperties = ragAiGatewayProperties;
        this.objectMapper = objectMapper;
    }

    /** 调用 AI Gateway 单条 embedding。 */
    public List<Double> createEmbedding(String model, String input) {
        return createEmbeddings(model, List.of(input)).get(0);
    }

    /** 调用 AI Gateway 批量 embeddings。 */
    public List<List<Double>> createEmbeddings(String model, List<String> inputs) {
        validateModel(model);
        if (inputs == null || inputs.isEmpty()) {
            throw new BusinessException("Embedding inputs must not be empty");
        }
        try {
            EmbeddingResponse response = postJson(
                    ragAiGatewayProperties.getEmbeddingsPath(),
                    new EmbeddingRequest(model, inputs.size() == 1 ? inputs.get(0) : inputs),
                    EmbeddingResponse.class
            );
            if (response == null || response.data() == null || response.data().isEmpty()) {
                throw new BusinessException("Embedding response is empty");
            }
            return response.data().stream()
                    .sorted((left, right) -> Integer.compare(left.index(), right.index()))
                    .map(EmbeddingData::embedding)
                    .toList();
        } catch (IOException ex) {
            log.warn(StructuredLogMessage.of("ai.gateway.embedding.failed")
                    .field("model", model)
                    .field("inputCount", inputs.size())
                    .field("message", ex.getMessage())
                    .build());
            throw new BusinessException("Failed to call AI gateway embeddings: " + ex.getMessage());
        }
    }

    /** 调用 AI Gateway chat completion。 */
    public String createChatCompletion(String model,
                                       Double temperature,
                                       Integer maxOutputTokens,
                                       String systemPrompt,
                                       String userPrompt) {
        validateModel(model);
        try {
            ChatCompletionResponse response = postJson(
                    ragAiGatewayProperties.getChatCompletionsPath(),
                    new ChatCompletionRequest(
                            model,
                            List.of(
                                    new ChatMessage("system", systemPrompt),
                                    new ChatMessage("user", userPrompt)
                            ),
                            temperature,
                            maxOutputTokens
                    ),
                    ChatCompletionResponse.class
            );
            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new BusinessException("Chat completion response is empty");
            }
            ChatMessage message = response.choices().get(0).message();
            if (message == null || message.content() == null || message.content().isBlank()) {
                throw new BusinessException("Chat completion content is empty");
            }
            return message.content();
        } catch (IOException ex) {
            log.warn(StructuredLogMessage.of("ai.gateway.chat.failed")
                    .field("model", model)
                    .field("message", ex.getMessage())
                    .build());
            throw new BusinessException("Failed to call AI gateway chat completions: " + ex.getMessage());
        }
    }

    /** 通过 AI Gateway 做 embedding 健康探针。 */
    public void probeEmbedding(String model, String input) {
        createEmbedding(model, input);
    }

    /** 通过 AI Gateway 做 chat completion 健康探针。 */
    public void probeChatCompletion(String model,
                                    Double temperature,
                                    Integer maxOutputTokens,
                                    String systemPrompt,
                                    String userPrompt) {
        createChatCompletion(model, temperature, maxOutputTokens, systemPrompt, userPrompt);
    }

    /** 返回已配置的 embeddings endpoint。 */
    public String embeddingsEndpoint() {
        return joinUrl(ragAiGatewayProperties.getBaseUrl(), ragAiGatewayProperties.getEmbeddingsPath());
    }

    /** 返回已配置的 chat completions endpoint。 */
    public String chatCompletionsEndpoint() {
        return joinUrl(ragAiGatewayProperties.getBaseUrl(), ragAiGatewayProperties.getChatCompletionsPath());
    }

    private <T> T postJson(String path, Object payload, Class<T> responseType) throws IOException {
        byte[] jsonBytes = toJson(payload).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(joinUrl(ragAiGatewayProperties.getBaseUrl(), path)).openConnection();
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
                String message = extractErrorMessage(responseBody);
                throw new BusinessException(statusCode + " " + message);
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

    private String extractErrorMessage(String responseBody) {
        if (!hasText(responseBody)) {
            return "";
        }
        try {
            ErrorResponse response = objectMapper.readValue(responseBody, ErrorResponse.class);
            if (response != null && response.error() != null && hasText(response.error().message())) {
                return response.error().message();
            }
        } catch (IOException ignored) {
            // Ignore parse failure and fall back to raw body.
        }
        return responseBody;
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
            throw new BusinessException("AI gateway request path must not be blank");
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
            throw new BusinessException("Failed to serialize AI gateway request payload: " + ex.getMessage());
        }
    }

    private void validateModel(String model) {
        if (!hasText(model)) {
            throw new BusinessException("Model must not be blank");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private record EmbeddingRequest(
            String model,
            Object input
    ) {
    }

    private record EmbeddingResponse(
            List<EmbeddingData> data
    ) {
    }

    private record EmbeddingData(
            Integer index,
            List<Double> embedding
    ) {
    }

    private record ChatCompletionRequest(
            String model,
            List<ChatMessage> messages,
            Double temperature,
            Integer max_tokens
    ) {
    }

    private record ChatCompletionResponse(
            List<ChatChoice> choices
    ) {
    }

    private record ChatChoice(
            ChatMessage message
    ) {
    }

    private record ChatMessage(
            String role,
            String content
    ) {
    }

    private record ErrorResponse(
            ErrorDetail error
    ) {
    }

    private record ErrorDetail(
            String message
    ) {
    }
}
