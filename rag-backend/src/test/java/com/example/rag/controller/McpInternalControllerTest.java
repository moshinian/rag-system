package com.example.rag.controller;

import com.example.rag.config.RagAgentProperties;
import com.example.rag.service.agent.AgentTestSchemas;
import com.example.rag.service.agent.McpTool;
import com.example.rag.service.agent.McpToolContext;
import com.example.rag.service.agent.McpToolRegistry;
import com.example.rag.service.agent.McpToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.jspecify.annotations.NonNull;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.rag.controller.McpInternalController.INTERNAL_TOOL_TOKEN_HEADER;
import static com.example.rag.controller.McpInternalController.PROTOCOL_VERSION;
import static com.example.rag.controller.McpInternalController.PROTOCOL_VERSION_HEADER;
import static com.example.rag.controller.McpInternalController.SESSION_ID_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MCP 内部 endpoint 合约测试。 */
class McpInternalControllerTest {
    private static final String TOKEN = "test-agent-token";
    private static final String ORIGIN = "http://127.0.0.1:8001";

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private AtomicReference<McpToolContext> executedContext;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        executedContext = new AtomicReference<>();
        RagAgentProperties properties = new RagAgentProperties();
        properties.setInternalToolToken(TOKEN);
        properties.setMcpAllowedOrigins(List.of(ORIGIN));
        McpToolRegistry registry = new McpToolRegistry(List.of(stubTool("kb.readiness.check")));
        mockMvc = MockMvcBuilders.standaloneSetup(new McpInternalController(registry, properties, objectMapper))
                .build();
    }

    @Test
    void getShouldReturnMethodNotAllowedWhenSseUnsupported() throws Exception {
        mockMvc.perform(get("/api/internal/mcp"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void deleteShouldCleanupSessionWithoutServerError() throws Exception {
        String sessionId = initializedSession();

        mockMvc.perform(delete("/api/internal/mcp")
                        .headers(mcpHeaders(sessionId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/internal/mcp")
                        .headers(mcpHeaders(sessionId))
                        .contentType(jsonMediaType())
                        .content("""
                                {"jsonrpc":"2.0","id":"list-1","method":"tools/list","params":{}}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void oldAgentToolEndpointShouldNotExist() throws Exception {
        mockMvc.perform(get("/api/internal/agent/tools"))
                .andExpect(status().isNotFound());
    }

    @Test
    void initializeShouldReturnRawJsonRpcAndSessionId() throws Exception {
        MvcResult result = initialize();

        assertThat(requiredHeader(result, SESSION_ID_HEADER)).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("SUCCESS", "data", "ApiResponse");
        mockMvc.perform(post("/api/internal/mcp")
                        .headers(mcpHeaders(requiredHeader(result, SESSION_ID_HEADER)))
                        .contentType(jsonMediaType())
                        .content("""
                                {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$").doesNotExist());
    }

    @Test
    void toolsListShouldRequireInitializedSession() throws Exception {
        MvcResult init = initialize();
        String sessionId = requiredHeader(init, SESSION_ID_HEADER);

        mockMvc.perform(post("/api/internal/mcp")
                        .headers(mcpHeaders(sessionId))
                        .contentType(jsonMediaType())
                        .content("""
                                {"jsonrpc":"2.0","id":"list-1","method":"tools/list","params":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32002));

        initialized(sessionId);

        mockMvc.perform(post("/api/internal/mcp")
                        .headers(mcpHeaders(sessionId))
                        .contentType(jsonMediaType())
                        .content("""
                                {"jsonrpc":"2.0","id":"list-2","method":"tools/list","params":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools[0].name").value("kb.readiness.check"))
                .andExpect(jsonPath("$.result.tools[0].inputSchema.type").value("object"))
                .andExpect(jsonPath("$.result.tools[0].outputSchema").doesNotExist())
                .andExpect(jsonPath("$.result.tools[0].annotations['x-rag.executionMode']").value("READ_ONLY"));
    }

    @Test
    void toolsCallShouldExecuteToolAndReturnStructuredContent() throws Exception {
        String sessionId = initializedSession();

        mockMvc.perform(post("/api/internal/mcp")
                        .headers(mcpHeaders(sessionId))
                        .contentType(jsonMediaType())
                        .content("""
                                {
                                  "jsonrpc": "2.0",
                                  "id": "call-1",
                                  "method": "tools/call",
                                  "params": {
                                    "name": "kb.readiness.check",
                                    "arguments": {
                                      "kbCode": "day20-cn-kb",
                                      "question": " 第二百三十八条是什么 ",
                                      "attributes": {"source": "python"}
                                    },
                                    "_meta": {
                                      "x-rag.runCode": "AR-test",
                                      "x-rag.operator": "agent-runtime"
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(jsonPath("$.result.structuredContent.ok").value(true))
                .andExpect(jsonPath("$.result.content[0].type").value("text"))
                .andExpect(jsonPath("$.result.content[0].text").value("{\"ok\":true}"));

        McpToolContext context = executedContext.get();
        assertThat(context.kbCode()).isEqualTo("day20-cn-kb");
        assertThat(context.question()).isEqualTo("第二百三十八条是什么");
        assertThat(context.attributes()).containsEntry("source", "python");
        assertThat(context.meta()).containsEntry("x-rag.runCode", "AR-test");
        assertThat(context.meta()).containsEntry("x-rag.operator", "agent-runtime");
    }

    @Test
    void toolsCallShouldMergeRuntimeHeadersIntoToolContextMeta() throws Exception {
        String sessionId = initializedSession();

        HttpHeaders headers = mcpHeaders(sessionId);
        headers.add(McpInternalController.RUNTIME_RUN_CODE_HEADER, "AR-header");
        headers.add(McpInternalController.RUNTIME_OPERATOR_HEADER, "agent-runtime");

        mockMvc.perform(post("/api/internal/mcp")
                        .headers(headers)
                        .contentType(jsonMediaType())
                        .content("""
                                {
                                  "jsonrpc": "2.0",
                                  "id": "call-headers",
                                  "method": "tools/call",
                                  "params": {
                                    "name": "kb.readiness.check",
                                    "arguments": {
                                      "kbCode": "day20-cn-kb"
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(false));

        McpToolContext context = executedContext.get();
        assertThat(context.meta()).containsEntry("x-rag.runCode", "AR-header");
        assertThat(context.meta()).containsEntry("x-rag.operator", "agent-runtime");
    }

    @Test
    void unknownToolShouldReturnJsonRpcError() throws Exception {
        String sessionId = initializedSession();

        mockMvc.perform(post("/api/internal/mcp")
                        .headers(mcpHeaders(sessionId))
                        .contentType(jsonMediaType())
                        .content("""
                                {"jsonrpc":"2.0","id":"call-1","method":"tools/call","params":{"name":"missing.tool","arguments":{"kbCode":"day20-cn-kb"}}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32602))
                .andExpect(jsonPath("$.error.message").value("MCP tool not found: missing.tool"));
    }

    @Test
    void toolsCallShouldValidateArgumentsAgainstToolSchema() throws Exception {
        String sessionId = initializedSession();

        MvcResult result = mockMvc.perform(post("/api/internal/mcp")
                        .headers(mcpHeaders(sessionId))
                        .contentType(jsonMediaType())
                        .content("""
                                {"jsonrpc":"2.0","id":"call-1","method":"tools/call","params":{"name":"kb.readiness.check","arguments":{"runCode":"AR-test"}}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32602))
                .andReturn();
        assertThat(result.getResponse().getContentAsString())
                .contains("field=arguments.kbCode")
                .contains("reason=missing");
    }

    @Test
    void toolsCallShouldAllowMissingArgumentsForNoArgumentTool() throws Exception {
        RagAgentProperties properties = new RagAgentProperties();
        properties.setInternalToolToken(TOKEN);
        properties.setMcpAllowedOrigins(List.of(ORIGIN));
        McpToolRegistry registry = new McpToolRegistry(List.of(
                stubTool("system.health.check", AgentTestSchemas.objectSchema(Map.of(), List.of()))
        ));
        MockMvc noArgMockMvc = MockMvcBuilders.standaloneSetup(new McpInternalController(registry, properties, objectMapper))
                .build();
        MvcResult init = noArgMockMvc.perform(post("/api/internal/mcp")
                        .header("Accept", "application/json, text/event-stream")
                        .header("Origin", ORIGIN)
                        .header(INTERNAL_TOOL_TOKEN_HEADER, TOKEN)
                        .contentType(jsonMediaType())
                        .content("""
                                {"jsonrpc":"2.0","id":"init-1","method":"initialize","params":{"protocolVersion":"2025-06-18"}}
                                """))
                .andReturn();
        String sessionId = requiredHeader(init, SESSION_ID_HEADER);
        noArgMockMvc.perform(post("/api/internal/mcp")
                        .headers(mcpHeaders(sessionId))
                        .contentType(jsonMediaType())
                        .content("""
                                {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}
                                """))
                .andExpect(status().isAccepted());

        noArgMockMvc.perform(post("/api/internal/mcp")
                        .headers(mcpHeaders(sessionId))
                        .contentType(jsonMediaType())
                        .content("""
                                {"jsonrpc":"2.0","id":"call-1","method":"tools/call","params":{"name":"system.health.check"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(false));
    }

    @Test
    void transportSecurityFailuresShouldReturnHttp4xx() throws Exception {
        mockMvc.perform(post("/api/internal/mcp")
                        .header("Accept", "application/json, text/event-stream")
                        .header("Origin", ORIGIN)
                        .header(INTERNAL_TOOL_TOKEN_HEADER, "wrong")
                        .contentType(jsonMediaType())
                        .content("""
                                {"jsonrpc":"2.0","id":"init-1","method":"initialize","params":{}}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/internal/mcp")
                        .header("Accept", "application/json")
                        .header("Origin", ORIGIN)
                        .header(INTERNAL_TOOL_TOKEN_HEADER, TOKEN)
                        .contentType(jsonMediaType())
                        .content("""
                                {"jsonrpc":"2.0","id":"init-1","method":"initialize","params":{}}
                                """))
                .andExpect(status().isNotAcceptable());

        mockMvc.perform(post("/api/internal/mcp")
                        .header("Accept", "application/json, text/event-stream")
                        .header("Origin", "http://evil.example")
                        .header(INTERNAL_TOOL_TOKEN_HEADER, TOKEN)
                        .contentType(jsonMediaType())
                        .content("""
                                {"jsonrpc":"2.0","id":"init-1","method":"initialize","params":{}}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/internal/mcp")
                        .header("Accept", "application/json, text/event-stream")
                        .header("Origin", ORIGIN)
                        .header(INTERNAL_TOOL_TOKEN_HEADER, TOKEN)
                        .contentType(jsonMediaType())
                        .content("""
                                [{"jsonrpc":"2.0","id":"init-1","method":"initialize","params":{}}]
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingSessionShouldReturnHttp404() throws Exception {
        mockMvc.perform(post("/api/internal/mcp")
                        .headers(mcpHeaders("missing-session"))
                        .contentType(jsonMediaType())
                        .content("""
                                {"jsonrpc":"2.0","id":"list-1","method":"tools/list","params":{}}
                                """))
                .andExpect(status().isNotFound());
    }

    private MvcResult initialize() throws Exception {
        return mockMvc.perform(post("/api/internal/mcp")
                        .header("Accept", "application/json, text/event-stream")
                        .header("Origin", ORIGIN)
                        .header(INTERNAL_TOOL_TOKEN_HEADER, TOKEN)
                        .contentType(jsonMediaType())
                        .content("""
                                {"jsonrpc":"2.0","id":"init-1","method":"initialize","params":{"protocolVersion":"2025-06-18"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().exists(SESSION_ID_HEADER))
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.result.protocolVersion").value(PROTOCOL_VERSION))
                .andExpect(jsonPath("$.result.capabilities.tools.listChanged").value(false))
                .andReturn();
    }

    private @NonNull String initializedSession() throws Exception {
        String sessionId = requiredHeader(initialize(), SESSION_ID_HEADER);
        initialized(sessionId);
        return sessionId;
    }

    private void initialized(String sessionId) throws Exception {
        mockMvc.perform(post("/api/internal/mcp")
                        .headers(mcpHeaders(sessionId))
                        .contentType(jsonMediaType())
                        .content("""
                                {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}
                                """))
                .andExpect(status().isAccepted());
    }

    private @NonNull HttpHeaders mcpHeaders(String sessionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Accept", "application/json, text/event-stream");
        headers.add("Origin", ORIGIN);
        headers.add(INTERNAL_TOOL_TOKEN_HEADER, TOKEN);
        headers.add(PROTOCOL_VERSION_HEADER, PROTOCOL_VERSION);
        headers.add(SESSION_ID_HEADER, sessionId);
        return headers;
    }

    private @NonNull MediaType jsonMediaType() {
        return Objects.requireNonNull(MediaType.APPLICATION_JSON);
    }

    private @NonNull String requiredHeader(MvcResult result, String headerName) {
        return Objects.requireNonNull(result.getResponse().getHeader(headerName));
    }

    private McpTool stubTool(String toolName) {
        Map<String, Object> attributes = AgentTestSchemas.objectSchema(
                Map.of("source", Map.of("type", "string")),
                List.of()
        );
        return stubTool(
                toolName,
                AgentTestSchemas.objectSchema(
                        Map.of(
                                "kbCode", Map.of("type", "string"),
                                "question", Map.of("type", "string"),
                                "attributes", attributes
                        ),
                        List.of("kbCode")
                )
        );
    }

    private McpTool stubTool(String toolName, Map<String, Object> inputSchema) {
        return new McpTool() {
            @Override
            public String name() {
                return toolName;
            }

            @Override
            public String title() {
                return toolName;
            }

            @Override
            public String description() {
                return "stub tool";
            }

            @Override
            public Map<String, Object> inputSchema() {
                return inputSchema;
            }

            @Override
            public McpToolResult call(McpToolContext context) {
                executedContext.set(context);
                Map<String, Object> output = new LinkedHashMap<>();
                output.put("ok", true);
                return McpToolResult.success(toolName, output, 3L);
            }
        };
    }
}
