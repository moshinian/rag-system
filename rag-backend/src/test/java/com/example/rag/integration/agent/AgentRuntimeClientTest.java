package com.example.rag.integration.agent;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.config.RagAiGatewayProperties;
import com.example.rag.model.dto.AgentRuntimeRequest;
import com.example.rag.model.dto.AgentRuntimeResponse;
import com.example.rag.model.enums.AgentRunMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Agent Runtime 客户端测试。 */
class AgentRuntimeClientTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void runShouldPostCamelCasePayloadAndReadRuntimeResponse() throws Exception {
        FakeHttpURLConnection connection = new FakeHttpURLConnection(200, """
                {
                  "status": "SUCCEEDED",
                  "summary": "ok",
                  "steps": [
                    {
                      "nodeName": "parse_goal",
                      "stepType": "NODE",
                      "status": "SUCCEEDED",
                      "outputJson": "{}"
                    }
                  ],
                  "recommendedActions": [
                    {
                      "toolName": "embedding.rebuild.submit",
                      "title": "提交知识库重嵌入任务",
                      "reason": "readiness 显示需要重嵌入",
                      "riskLevel": "MEDIUM",
                      "requiresConfirmation": true,
                      "actionPayload": "{\\"kbCode\\":\\"day20-cn-kb\\"}"
                    }
                  ]
                }
                """);
        TestAgentRuntimeClient client = new TestAgentRuntimeClient(properties(), connection);
        MDC.put("requestId", "REQ-123");

        AgentRuntimeResponse response = client.run(new AgentRuntimeRequest(
                "AR-100",
                "day20-cn-kb",
                "诊断这个知识库为什么不能问答",
                null,
                AgentRunMode.DIAGNOSE_AND_RECOMMEND
        ));

        assertThat(client.openedUrl).isEqualTo("http://agent-runtime.test/v1/agent/runs");
        assertThat(connection.getRequestProperty("X-Request-Id")).isEqualTo("REQ-123");
        assertThat(connection.requestBody()).contains("\"runCode\":\"AR-100\"");
        assertThat(connection.requestBody()).contains("\"kbCode\":\"day20-cn-kb\"");
        assertThat(connection.requestBody()).contains("\"runMode\":\"DIAGNOSE_AND_RECOMMEND\"");
        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.steps()).hasSize(1);
        assertThat(response.recommendedActions()).hasSize(1);
        assertThat(response.recommendedActions().get(0).toolName()).isEqualTo("embedding.rebuild.submit");
    }

    @Test
    void runShouldThrowBusinessExceptionForNon2xxResponse() throws Exception {
        FakeHttpURLConnection connection = new FakeHttpURLConnection(500, "{\"error\":{\"message\":\"runtime down\"}}");
        TestAgentRuntimeClient client = new TestAgentRuntimeClient(properties(), connection);

        assertThatThrownBy(() -> client.run(new AgentRuntimeRequest(
                "AR-100",
                "day20-cn-kb",
                "诊断",
                null,
                AgentRunMode.DIAGNOSE_AND_RECOMMEND
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("500");
    }

    private RagAiGatewayProperties properties() {
        RagAiGatewayProperties properties = new RagAiGatewayProperties();
        properties.setBaseUrl("http://agent-runtime.test");
        properties.setAgentRunsPath("/v1/agent/runs");
        properties.setConnectTimeoutMillis(1_000);
        properties.setReadTimeoutMillis(1_000);
        return properties;
    }

    private static final class TestAgentRuntimeClient extends AgentRuntimeClient {
        private final FakeHttpURLConnection connection;
        private String openedUrl;

        private TestAgentRuntimeClient(RagAiGatewayProperties properties, FakeHttpURLConnection connection) {
            super(properties, new ObjectMapper());
            this.connection = connection;
        }

        @Override
        protected HttpURLConnection openConnection(String url) {
            this.openedUrl = url;
            return connection;
        }
    }

    private static final class FakeHttpURLConnection extends HttpURLConnection {
        private final int statusCode;
        private final byte[] responseBody;
        private final ByteArrayOutputStream requestBody = new ByteArrayOutputStream();

        private FakeHttpURLConnection(int statusCode, String responseBody) throws IOException {
            super(new URL("http://agent-runtime.test/v1/agent/runs"));
            this.statusCode = statusCode;
            this.responseBody = responseBody.getBytes(StandardCharsets.UTF_8);
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
            return requestBody;
        }

        @Override
        public int getResponseCode() {
            return statusCode;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(responseBody);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(responseBody);
        }

        private String requestBody() {
            return requestBody.toString(StandardCharsets.UTF_8);
        }
    }
}
