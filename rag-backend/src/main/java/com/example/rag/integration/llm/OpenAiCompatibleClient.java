package com.example.rag.integration.llm;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.logging.StructuredLogMessage;
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
 * 统一封装 embeddings 和 chat completions 的 HTTP 调用细节，供检索和问答服务复用。
 */
@Component
public class OpenAiCompatibleClient {

    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int READ_TIMEOUT_MILLIS = 30_000;
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
        validateModel(model);
        if (inputs == null || inputs.isEmpty()) {
            throw new BusinessException("Embedding inputs must not be empty");
        }
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
            log.warn(StructuredLogMessage.of("llm.embedding.failed")
                    .field("url", normalizeUrl(baseUrl, path))
                    .field("model", model)
                    .field("inputCount", inputs == null ? 0 : inputs.size())
                    .field("message", ex.getMessage())
                    .build());
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
        validateModel(model);
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
            log.warn(StructuredLogMessage.of("llm.chat.failed")
                    .field("url", normalizeUrl(baseUrl, path))
                    .field("model", model)
                    .field("message", ex.getMessage())
                    .build());
            throw new BusinessException("Failed to call chat model: " + ex.getMessage());
        }
    }

    /** 调用 chat completion 接口做健康探针，只要求返回合法 completion 响应。 */
    public void probeChatCompletion(String baseUrl,
                                    String apiKey,
                                    String path,
                                    String model,
                                    Double temperature,
                                    Integer maxOutputTokens,
                                    String systemPrompt,
                                    String userPrompt) {
        validateModel(model);
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
        } catch (IOException ex) {
            log.warn(StructuredLogMessage.of("llm.chat.probe.failed")
                    .field("url", normalizeUrl(baseUrl, path))
                    .field("model", model)
                    .field("message", ex.getMessage())
                    .build());
            throw new BusinessException("Failed to probe chat model: " + ex.getMessage());
        }
    }

    /** 发送 JSON POST 请求，并把响应反序列化成指定类型。 */
    private <T> T postJson(String url,
                           String apiKey,
                           Object payload,
                           Class<T> responseType) throws IOException {
        byte[] jsonBytes = toJson(payload).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8");
        connection.setRequestProperty(HttpHeaders.ACCEPT, "application/json");
        connection.setFixedLengthStreamingMode(jsonBytes.length);
        if (hasText(apiKey)) {
            connection.setRequestProperty(HttpHeaders.AUTHORIZATION, bearerToken(apiKey));
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

    /** 兼容成功流和错误流，统一读取 HTTP 返回体。 */
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

    /** 规范化 baseUrl 和 path 之间的斜杠，避免拼接出重复或缺失分隔符。 */
    private String normalizeUrl(String baseUrl, String path) {
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.trim();
        String normalizedPath = path == null ? "" : path.trim();
        if (normalizedBaseUrl.isEmpty()) {
            throw new BusinessException("Base URL must not be blank");
        }
        if (normalizedPath.isEmpty()) {
            throw new BusinessException("Request path must not be blank");
        }
        if (normalizedBaseUrl.endsWith("/") && normalizedPath.startsWith("/")) {
            return normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1) + normalizedPath;
        }
        if (!normalizedBaseUrl.endsWith("/") && !normalizedPath.startsWith("/")) {
            return normalizedBaseUrl + "/" + normalizedPath;
        }
        return normalizedBaseUrl + normalizedPath;
    }

    /** 组装 Bearer Token 请求头。 */
    private String bearerToken(String apiKey) {
        return "Bearer " + (apiKey == null ? "" : apiKey.trim());
    }

    /** 判断字符串是否包含非空白内容。 */
    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    /** 把请求对象序列化成 JSON 字符串。 */
    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to serialize request payload: " + ex.getMessage());
        }
    }

    /** 模型名为空时直接快速失败，避免把明显配置错误拖到远端。 */
    private void validateModel(String model) {
        if (!hasText(model)) {
            throw new BusinessException("Model must not be blank");
        }
    }

    /** Embeddings 接口请求体。 */
    private record EmbeddingRequest(
            String model,
            Object input
    ) {
    }

    /** Embeddings 接口返回体中的最小字段集合。 */
    private record EmbeddingResponse(
            List<EmbeddingData> data
    ) {
    }

    /** 单条 embedding 结果。 */
    private record EmbeddingData(
            Integer index,
            List<Double> embedding
    ) {
    }

    /** Chat Completions 接口请求体。 */
    private record ChatCompletionRequest(
            String model,
            List<ChatMessage> messages,
            Double temperature,
            Integer max_tokens
    ) {
    }

    /** Chat Completions 接口返回体中的最小字段集合。 */
    private record ChatCompletionResponse(
            List<ChatChoice> choices
    ) {
    }

    /** 单条候选回答。 */
    private record ChatChoice(
            ChatMessage message
    ) {
    }

    /** Chat 消息结构，兼容 system/user 角色。 */
    private record ChatMessage(
            String role,
            String content
    ) {
    }
}
