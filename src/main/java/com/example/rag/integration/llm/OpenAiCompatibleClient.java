package com.example.rag.integration.llm;

import com.example.rag.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * OpenAI 兼容接口客户端。
 *
 * Day 8 先把 embedding / chat 所需的最小调用封装好，
 * 后续检索和问答服务直接复用这里的接口能力。
 */
@Component
public class OpenAiCompatibleClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 调用 embedding 接口，返回第一条向量结果。 */
    public List<Double> createEmbedding(String baseUrl,
                                        String apiKey,
                                        String path,
                                        String model,
                                        String input) {
        List<List<Double>> embeddings = createEmbeddings(baseUrl, apiKey, path, model, List.of(input));
        return embeddings.get(0);
    }

    /** 调用 embedding 接口，返回全部向量结果。 */
    public List<List<Double>> createEmbeddings(String baseUrl,
                                               String apiKey,
                                               String path,
                                               String model,
                                               List<String> inputs) {
        try {
            EmbeddingResponse response = postJson(
                    normalizeUrl(baseUrl, path),
                    apiKey,
                    new EmbeddingRequest(model, inputs),
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
            log.warn("Embedding request failed: {}", ex.getMessage());
            throw new BusinessException("Failed to call embedding model: " + ex.getMessage());
        }
    }

    /** 调用 chat completion 接口，返回第一条回答。 */
    public String createChatCompletion(String baseUrl,
                                       String apiKey,
                                       String path,
                                       String model,
                                       Double temperature,
                                       Integer maxOutputTokens,
                                       String systemPrompt,
                                       String userPrompt) {
        try {
            ChatCompletionResponse response = postJson(
                    normalizeUrl(baseUrl, path),
                    apiKey,
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
            log.warn("Chat completion request failed: {}", ex.getMessage());
            throw new BusinessException("Failed to call chat model: " + ex.getMessage());
        }
    }

    private <T> T postJson(String url,
                           String apiKey,
                           Object payload,
                           Class<T> responseType) throws IOException {
        byte[] jsonBytes = toJson(payload).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8");
        connection.setRequestProperty(HttpHeaders.ACCEPT, "application/json");
        connection.setFixedLengthStreamingMode(jsonBytes.length);
        if (hasText(apiKey)) {
            connection.setRequestProperty(HttpHeaders.AUTHORIZATION, bearerToken(apiKey));
        }

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

    private String normalizeUrl(String baseUrl, String path) {
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.trim();
        String normalizedPath = path == null ? "" : path.trim();
        if (normalizedBaseUrl.endsWith("/") && normalizedPath.startsWith("/")) {
            return normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1) + normalizedPath;
        }
        if (!normalizedBaseUrl.endsWith("/") && !normalizedPath.startsWith("/")) {
            return normalizedBaseUrl + "/" + normalizedPath;
        }
        return normalizedBaseUrl + normalizedPath;
    }

    private String bearerToken(String apiKey) {
        return "Bearer " + (apiKey == null ? "" : apiKey.trim());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to serialize request payload: " + ex.getMessage());
        }
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
}
