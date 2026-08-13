package com.example.rag.integration.ai;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.config.RagAiGatewayProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    /** 注入 AI Gateway 地址配置和 JSON 序列化器。 */
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
            // 按上游 index 恢复输入顺序，避免 provider 返回顺序变化导致向量与文本错配。
            return response.data().stream()
                    .sorted((left, right) -> Integer.compare(left.index(), right.index()))
                    .map(data -> data.embedding())
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

    /** 调用 AI Gateway 文本重排序。 */
    public RerankGatewayResponse createRerank(String model,
                                              String query,
                                              List<String> documents,
                                              int topN,
                                              String instruct) {
        validateModel(model);
        if (!hasText(query)) {
            throw new BusinessException("Rerank query must not be blank");
        }
        if (documents == null || documents.isEmpty()) {
            throw new BusinessException("Rerank documents must not be empty");
        }
        try {
            RerankGatewayResponse response = postJson(
                    ragAiGatewayProperties.getRerankPath(),
                    new RerankRequest(model, query, documents, topN, hasText(instruct) ? instruct.trim() : null),
                    RerankGatewayResponse.class,
                    resolveRerankReadTimeoutMillis()
            );
            if (response == null || response.results() == null) {
                throw new BusinessException("Rerank response is empty");
            }
            return response;
        } catch (IOException ex) {
            log.warn(StructuredLogMessage.of("ai.gateway.rerank.failed")
                    .field("model", model)
                    .field("candidateCount", documents.size())
                    .field("message", ex.getMessage())
                    .build());
            throw new BusinessException("Failed to call AI gateway rerank: " + ex.getMessage());
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

    /** 直接探测 AI Gateway 自身健康接口。 */
    public GatewayHealthSnapshot probeGatewayHealth() {
        try {
            GatewayHealthResponse response = getJson("/health", GatewayHealthResponse.class);
            if (response == null || !hasText(response.status())) {
                throw new BusinessException("AI gateway health response is empty");
            }
            return new GatewayHealthSnapshot(
                    response.status(),
                    response.embeddingProvider(),
                    response.embeddingDefaultModel(),
                    response.chatProvider(),
                    response.chatDefaultModel(),
                    response.rerankProvider(),
                    response.rerankDefaultModel()
            );
        } catch (IOException ex) {
            log.warn(StructuredLogMessage.of("ai.gateway.health.failed")
                    .field("message", ex.getMessage())
                    .build());
            throw new BusinessException("Failed to call AI gateway health endpoint: " + ex.getMessage());
        }
    }

    /** 返回已配置的 embeddings endpoint。 */
    public String embeddingsEndpoint() {
        return joinUrl(ragAiGatewayProperties.getBaseUrl(), ragAiGatewayProperties.getEmbeddingsPath());
    }

    /** 返回已配置的 chat completions endpoint。 */
    public String chatCompletionsEndpoint() {
        return joinUrl(ragAiGatewayProperties.getBaseUrl(), ragAiGatewayProperties.getChatCompletionsPath());
    }

    /** 返回已配置的 rerank endpoint。 */
    public String rerankEndpoint() {
        return joinUrl(ragAiGatewayProperties.getBaseUrl(), ragAiGatewayProperties.getRerankPath());
    }

    /** 返回 AI Gateway 健康检查 endpoint。 */
    public String gatewayHealthEndpoint() {
        return joinUrl(ragAiGatewayProperties.getBaseUrl(), "/health");
    }

    /** 发送 JSON POST 请求，并统一处理 requestId、超时、状态码和反序列化。 */
    private <T> T postJson(String path, Object payload, Class<T> responseType) throws IOException {
        return postJson(path, payload, responseType, resolveReadTimeoutMillis());
    }

    /** 发送使用指定读超时的 JSON POST 请求。 */
    private <T> T postJson(String path, Object payload, Class<T> responseType, int readTimeoutMillis) throws IOException {
        byte[] jsonBytes = toJson(payload).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(joinUrl(ragAiGatewayProperties.getBaseUrl(), path)).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(resolveConnectTimeoutMillis());
        connection.setReadTimeout(readTimeoutMillis);
        connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8");
        connection.setRequestProperty(HttpHeaders.ACCEPT, "application/json");
        connection.setFixedLengthStreamingMode(jsonBytes.length);
        String requestId = MDC.get("requestId");
        if (hasText(requestId)) {
            // 保留 Java 请求链路标识，便于关联 Python Gateway 与上游模型日志。
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

    /** 发送 JSON GET 请求，主要用于 AI Gateway 自身健康探针。 */
    private <T> T getJson(String path, Class<T> responseType) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(joinUrl(ragAiGatewayProperties.getBaseUrl(), path)).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(resolveConnectTimeoutMillis());
        connection.setReadTimeout(resolveReadTimeoutMillis());
        connection.setRequestProperty(HttpHeaders.ACCEPT, "application/json");
        String requestId = MDC.get("requestId");
        if (hasText(requestId)) {
            connection.setRequestProperty("X-Request-Id", requestId);
        }

        try {
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

    /** 根据状态码选择响应流，并以 UTF-8 读取完整响应体。 */
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

    /** 优先提取网关标准 error.message，无法解析时保留原始响应体。 */
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
            // 非标准错误体仍有排障价值，因此解析失败后直接回退原文。
        }
        return responseBody;
    }

    /** 读取连接超时，未配置时使用 5 秒默认值。 */
    private int resolveConnectTimeoutMillis() {
        return ragAiGatewayProperties.getConnectTimeoutMillis() == null
                ? 5_000
                : ragAiGatewayProperties.getConnectTimeoutMillis();
    }

    /** 读取响应超时，未配置时使用 30 秒默认值。 */
    private int resolveReadTimeoutMillis() {
        return ragAiGatewayProperties.getReadTimeoutMillis() == null
                ? 30_000
                : ragAiGatewayProperties.getReadTimeoutMillis();
    }

    /** 读取 rerank 专用超时，避免降级前长时间阻塞在线问答。 */
    private int resolveRerankReadTimeoutMillis() {
        return ragAiGatewayProperties.getRerankReadTimeoutMillis() == null
                ? 10_000
                : ragAiGatewayProperties.getRerankReadTimeoutMillis();
    }

    /** 规范拼接 Gateway 地址与接口路径，并拒绝空地址。 */
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

    /** 序列化 Gateway 请求，失败时转换为业务异常。 */
    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to serialize AI gateway request payload: " + ex.getMessage());
        }
    }

    /** 在发起网络请求前校验模型名称。 */
    private void validateModel(String model) {
        if (!hasText(model)) {
            throw new BusinessException("Model must not be blank");
        }
    }

    /** 判断字符串是否包含非空白内容。 */
    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    /** OpenAI-compatible embedding 请求体。 */
    private record EmbeddingRequest(
            String model,
            Object input
    ) {
    }

    /** OpenAI-compatible embedding 响应体。 */
    private record EmbeddingResponse(
            List<EmbeddingData> data
    ) {
    }

    /** 单条 embedding 数据及其原输入序号。 */
    private record EmbeddingData(
            Integer index,
            List<Double> embedding
    ) {
    }

    /** OpenAI-compatible chat completion 请求体。 */
    private record ChatCompletionRequest(
            String model,
            List<ChatMessage> messages,
            Double temperature,
            Integer max_tokens
    ) {
    }

    /** OpenAI-compatible chat completion 响应体。 */
    private record ChatCompletionResponse(
            List<ChatChoice> choices
    ) {
    }

    /** Gateway 文本重排序请求。 */
    private record RerankRequest(
            String model,
            String query,
            List<String> documents,
            @JsonProperty("top_n") int topN,
            String instruct
    ) {
    }

    /** Gateway 文本重排序响应。 */
    public record RerankGatewayResponse(
            String model,
            List<RerankGatewayResult> results
    ) {
    }

    /** Gateway 单条重排序结果。 */
    public record RerankGatewayResult(
            Integer index,
            @JsonProperty("relevance_score") Double relevanceScore
    ) {
    }

    /** 单个 chat completion 候选项。 */
    private record ChatChoice(
            ChatMessage message
    ) {
    }

    /** Chat 协议中的角色消息。 */
    private record ChatMessage(
            String role,
            String content
    ) {
    }

    /** AI Gateway 统一错误响应。 */
    private record ErrorResponse(
            ErrorDetail error
    ) {
    }

    /** AI Gateway 错误详情。 */
    private record ErrorDetail(
            String message
    ) {
    }

    /** Python 健康接口的原始 snake_case 响应映射。 */
    private record GatewayHealthResponse(
            String status,
            String embedding_provider,
            String embedding_default_model,
            String chat_provider,
            String chat_default_model,
            String rerank_provider,
            String rerank_default_model
    ) {
        /** 返回 embedding provider。 */
        String embeddingProvider() {
            return embedding_provider;
        }

        /** 返回 embedding 默认模型。 */
        String embeddingDefaultModel() {
            return embedding_default_model;
        }

        /** 返回 chat provider。 */
        String chatProvider() {
            return chat_provider;
        }

        /** 返回 chat 默认模型。 */
        String chatDefaultModel() {
            return chat_default_model;
        }

        String rerankProvider() {
            return rerank_provider;
        }

        String rerankDefaultModel() {
            return rerank_default_model;
        }
    }

    /** Java 业务层使用的 AI Gateway 健康快照。 */
    public record GatewayHealthSnapshot(
            String status,
            String embeddingProvider,
            String embeddingDefaultModel,
            String chatProvider,
            String chatDefaultModel,
            String rerankProvider,
            String rerankDefaultModel
    ) {
        /** 兼容旧测试及仅关心 embedding/chat 的调用方。 */
        public GatewayHealthSnapshot(String status,
                                     String embeddingProvider,
                                     String embeddingDefaultModel,
                                     String chatProvider,
                                     String chatDefaultModel) {
            this(status, embeddingProvider, embeddingDefaultModel, chatProvider, chatDefaultModel, null, null);
        }
    }
}
