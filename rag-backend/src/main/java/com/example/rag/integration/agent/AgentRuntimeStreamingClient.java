package com.example.rag.integration.agent;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.config.RagAiGatewayProperties;
import com.example.rag.model.dto.AgentRuntimeEvent;
import com.example.rag.model.dto.AgentRuntimeRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * 阻塞式消费 Python Agent Runtime SSE 的内部客户端。
 */
@Component
public class AgentRuntimeStreamingClient {
    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeStreamingClient.class);
    private static final int MIN_STREAM_READ_TIMEOUT_MILLIS = 60_000;

    private final RagAiGatewayProperties properties;
    private final ObjectMapper objectMapper;

    /** 构造 AgentRuntimeStreamingClient。 */
    public AgentRuntimeStreamingClient(RagAiGatewayProperties properties,
                                       ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 调用 Python streaming endpoint，并逐条回调 Runtime event。 */
    public void runStream(AgentRuntimeRequest request,
                          Consumer<AgentRuntimeEvent> onEvent) {
        runStream(request, onEvent, () -> {
        });
    }

    /** 调用 Python streaming endpoint，并逐条回调 Runtime event 与 heartbeat。 */
    public void runStream(AgentRuntimeRequest request,
                          Consumer<AgentRuntimeEvent> onEvent,
                          Runnable onHeartbeat) {
        HttpURLConnection connection = null;
        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(request);
            connection = openConnection(joinUrl(properties.getBaseUrl(), properties.getAgentRunsStreamPath()));
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(resolveConnectTimeoutMillis());
            connection.setReadTimeout(resolveStreamReadTimeoutMillis());
            connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8");
            connection.setRequestProperty(HttpHeaders.ACCEPT, "text/event-stream");
            connection.setFixedLengthStreamingMode(jsonBytes.length);
            String requestId = MDC.get("requestId");
            if (hasText(requestId)) {
                connection.setRequestProperty("X-Request-Id", requestId);
            }

            log.info(StructuredLogMessage.of("agent.runtime.stream.started")
                    .field("runCode", request.runCode())
                    .build());
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(jsonBytes);
                outputStream.flush();
            }

            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new BusinessException(statusCode + " " + readBody(connection.getErrorStream()));
            }
            consumeStream(request, connection.getInputStream(), onEvent, onHeartbeat);
            log.info(StructuredLogMessage.of("agent.runtime.stream.completed")
                    .field("runCode", request.runCode())
                    .build());
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to serialize Agent Runtime streaming request: " + ex.getMessage());
        } catch (IOException ex) {
            throw new BusinessException("Failed to consume Agent Runtime stream: " + ex.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** 逐行解析 SSE，并校验 frame 与 JSON 协议一致。 */
    private void consumeStream(AgentRuntimeRequest request,
                               InputStream inputStream,
                               Consumer<AgentRuntimeEvent> onEvent,
                               Runnable onHeartbeat) throws IOException {
        SseEventParser parser = new SseEventParser();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                parser.accept(line, ignored -> onHeartbeat.run())
                        .ifPresent(frame -> consumeFrame(request, frame, onEvent));
            }
            parser.finish().ifPresent(frame -> consumeFrame(request, frame, onEvent));
        }
    }

    /** 反序列化并校验一条业务 frame。 */
    private void consumeFrame(AgentRuntimeRequest request,
                              SseEventParser.SseFrame frame,
                              Consumer<AgentRuntimeEvent> onEvent) {
        if (!hasText(frame.id()) || !hasText(frame.event()) || !hasText(frame.data())) {
            throw new BusinessException("Agent Runtime SSE frame is incomplete");
        }
        try {
            AgentRuntimeEvent event = objectMapper.readValue(frame.data(), AgentRuntimeEvent.class);
            if (!frame.id().equals(event.eventId())) {
                throw new BusinessException("Agent Runtime SSE id does not match eventId");
            }
            if (!frame.event().equals(event.type().name())) {
                throw new BusinessException("Agent Runtime SSE event name does not match JSON type");
            }
            if (!request.runCode().equals(event.runCode())) {
                throw new BusinessException("Agent Runtime SSE runCode does not match request");
            }
            log.info(StructuredLogMessage.of("agent.runtime.stream.event.received")
                    .field("runCode", event.runCode())
                    .field("eventId", event.eventId())
                    .field("eventType", event.type())
                    .build());
            onEvent.accept(event);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to parse Agent Runtime SSE event: " + ex.getMessage());
        }
    }

    /** 打开 HTTP 连接，测试中可替换。 */
    protected HttpURLConnection openConnection(String url) throws IOException {
        return (HttpURLConnection) new URL(url).openConnection();
    }

    /** 读取连接超时。 */
    private int resolveConnectTimeoutMillis() {
        Integer configured = properties.getConnectTimeoutMillis();
        return configured == null || configured < 1 ? 5_000 : configured;
    }

    /** streaming 读取超时不得低于 60 秒，以覆盖 Python 10 秒 heartbeat。 */
    private int resolveStreamReadTimeoutMillis() {
        Integer configured = properties.getAgentStreamReadTimeoutMillis();
        return Math.max(
                MIN_STREAM_READ_TIMEOUT_MILLIS,
                configured == null ? 120_000 : configured
        );
    }

    /** 拼接 base URL 与 path。 */
    private String joinUrl(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        String endpoint = path == null ? "" : path.trim();
        if (base.isEmpty() || endpoint.isEmpty()) {
            throw new BusinessException("Agent Runtime streaming URL must not be blank");
        }
        if (base.endsWith("/") && endpoint.startsWith("/")) {
            return base.substring(0, base.length() - 1) + endpoint;
        }
        if (!base.endsWith("/") && !endpoint.startsWith("/")) {
            return base + "/" + endpoint;
        }
        return base + endpoint;
    }

    /** 读取错误响应体。 */
    private String readBody(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        try (InputStream stream = inputStream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 判断文本是否非空白。 */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
