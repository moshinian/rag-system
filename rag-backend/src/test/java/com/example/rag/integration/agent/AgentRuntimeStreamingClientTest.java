package com.example.rag.integration.agent;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.config.RagAiGatewayProperties;
import com.example.rag.model.dto.AgentRuntimeEvent;
import com.example.rag.model.dto.AgentRuntimeRequest;
import com.example.rag.model.enums.AgentRunMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Agent Runtime streaming client 测试。 */
class AgentRuntimeStreamingClientTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void runStreamShouldParseEventsIgnoreHeartbeatAndPropagateRequestId() throws Exception {
        String stream = """
                : heartbeat

                id: AR-1-000001
                event: RUN_STARTED
                data: {"eventId":"AR-1-000001","runCode":"AR-1","type":"RUN_STARTED","payload":{},"terminal":false,"createdAt":"2026-06-24T12:00:00Z"}

                id: AR-1-000002
                event: RUN_COMPLETED
                data: {"eventId":"AR-1-000002","runCode":"AR-1","type":"RUN_COMPLETED","status":"SUCCEEDED","payload":{"summary":"ok"},"terminal":true,"createdAt":"2026-06-24T12:00:01Z"}

                """;
        FakeConnection connection = new FakeConnection(200, stream);
        TestClient client = new TestClient(properties(), connection);
        MDC.put("requestId", "REQ-stream");
        List<AgentRuntimeEvent> events = new ArrayList<>();
        AtomicInteger heartbeatCount = new AtomicInteger();

        client.runStream(request(), events::add, heartbeatCount::incrementAndGet);

        assertThat(client.openedUrl).isEqualTo("http://runtime.test/v1/agent/runs/stream");
        assertThat(connection.getRequestProperty("Accept")).isEqualTo("text/event-stream");
        assertThat(connection.getRequestProperty("X-Request-Id")).isEqualTo("REQ-stream");
        assertThat(connection.getReadTimeout()).isEqualTo(60_000);
        assertThat(connection.requestBody()).contains("\"runCode\":\"AR-1\"");
        assertThat(events).extracting(event -> event.type().name())
                .containsExactly("RUN_STARTED", "RUN_COMPLETED");
        assertThat(heartbeatCount).hasValue(1);
    }

    @Test
    void runStreamShouldRejectEventNameMismatch() throws Exception {
        String stream = """
                id: AR-1-000001
                event: RUN_FAILED
                data: {"eventId":"AR-1-000001","runCode":"AR-1","type":"RUN_COMPLETED","payload":{},"terminal":true,"createdAt":"2026-06-24T12:00:00Z"}

                """;
        TestClient client = new TestClient(properties(), new FakeConnection(200, stream));

        assertThatThrownBy(() -> client.runStream(request(), ignored -> {
        }))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not match JSON type");
    }

    private RagAiGatewayProperties properties() {
        RagAiGatewayProperties properties = new RagAiGatewayProperties();
        properties.setBaseUrl("http://runtime.test");
        properties.setAgentRunsStreamPath("/v1/agent/runs/stream");
        properties.setConnectTimeoutMillis(1_000);
        properties.setAgentStreamReadTimeoutMillis(1_000);
        return properties;
    }

    private AgentRuntimeRequest request() {
        return new AgentRuntimeRequest(
                "AR-1",
                "kb-1",
                "诊断",
                null,
                AgentRunMode.DIAGNOSE_ONLY
        );
    }

    private static final class TestClient extends AgentRuntimeStreamingClient {
        private final FakeConnection connection;
        private String openedUrl;

        private TestClient(RagAiGatewayProperties properties, FakeConnection connection) {
            super(properties, new ObjectMapper().findAndRegisterModules());
            this.connection = connection;
        }

        @Override
        protected HttpURLConnection openConnection(String url) {
            openedUrl = url;
            return connection;
        }
    }

    private static final class FakeConnection extends HttpURLConnection {
        private final int status;
        private final byte[] response;
        private final ByteArrayOutputStream request = new ByteArrayOutputStream();

        private FakeConnection(int status, String response) throws Exception {
            super(new URL("http://runtime.test/v1/agent/runs/stream"));
            this.status = status;
            this.response = response.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }

        @Override
        public OutputStream getOutputStream() {
            return request;
        }

        @Override
        public int getResponseCode() {
            return status;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(response);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(response);
        }

        private String requestBody() {
            return request.toString(StandardCharsets.UTF_8);
        }
    }
}
