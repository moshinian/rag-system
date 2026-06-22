package com.example.rag.controller;

import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.common.exception.GlobalExceptionHandler;
import com.example.rag.config.RagAgentProperties;
import com.example.rag.config.RequestIdFilter;
import com.example.rag.model.dto.AgentToolContext;
import com.example.rag.model.dto.AgentToolDefinition;
import com.example.rag.model.dto.AgentToolResult;
import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentToolExecutionMode;
import com.example.rag.service.agent.AgentTool;
import com.example.rag.service.agent.AgentToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.rag.controller.AgentInternalToolController.INTERNAL_TOOL_TOKEN_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Agent 内部工具执行接口测试。 */
class AgentInternalToolControllerTest {
    private static final String TOKEN = "test-agent-token";
    private MockMvc mockMvc;
    private SnowflakeIdGenerator snowflakeIdGenerator;
    private AtomicReference<AgentToolContext> executedContext;

    @BeforeEach
    void setUp() {
        snowflakeIdGenerator = mock(SnowflakeIdGenerator.class);
        executedContext = new AtomicReference<>();
        RagAgentProperties properties = new RagAgentProperties();
        properties.setInternalToolToken(TOKEN);
        AgentToolRegistry registry = new AgentToolRegistry(List.of(
                stubTool("kb.readiness.check", AgentToolExecutionMode.READ_ONLY),
                stubTool("document.indexing_task.retry", AgentToolExecutionMode.REQUIRES_CONFIRMATION)
        ));
        mockMvc = MockMvcBuilders.standaloneSetup(new AgentInternalToolController(registry, properties))
                .addFilters(new RequestIdFilter(snowflakeIdGenerator))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void definitionsShouldReturnToolDefinitionV2() throws Exception {
        when(snowflakeIdGenerator.nextId("REQ-")).thenReturn("REQ-test");

        mockMvc.perform(get("/api/internal/agent/tools")
                        .header(INTERNAL_TOOL_TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "REQ-test"))
                .andExpect(jsonPath("$.data[?(@.toolName == 'kb.readiness.check')].schemaVersion").value("v2"))
                .andExpect(jsonPath("$.data[?(@.toolName == 'kb.readiness.check')].sourceType").value("JAVA"))
                .andExpect(jsonPath("$.data[?(@.toolName == 'kb.readiness.check')].requiresConfirmation").value(false))
                .andExpect(jsonPath("$.data[?(@.toolName == 'kb.readiness.check')].timeoutMs").value(5000));
    }

    @Test
    void executeShouldRunReadOnlyToolWithContext() throws Exception {
        when(snowflakeIdGenerator.nextId("REQ-")).thenReturn("REQ-test");

        mockMvc.perform(post("/api/internal/agent/tools/kb.readiness.check/execute")
                        .header(INTERNAL_TOOL_TOKEN_HEADER, TOKEN)
                        .contentType("application/json")
                        .content("""
                                {
                                  "kbCode": "day20-cn-kb",
                                  "runCode": " AR-test ",
                                  "question": " 第二百三十八条是什么 ",
                                  "operator": " agent-runtime ",
                                  "attributes": {"source": "python"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "REQ-test"))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.toolName").value("kb.readiness.check"))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.outputJson").value("{\"ok\":true}"));

        AgentToolContext context = executedContext.get();
        assertThat(context.kbCode()).isEqualTo("day20-cn-kb");
        assertThat(context.runCode()).isEqualTo("AR-test");
        assertThat(context.question()).isEqualTo("第二百三十八条是什么");
        assertThat(context.operator()).isEqualTo("agent-runtime");
        assertThat(context.attributes()).containsEntry("source", "python");
    }

    @Test
    void executeShouldRejectInvalidToken() throws Exception {
        when(snowflakeIdGenerator.nextId("REQ-")).thenReturn("REQ-test");

        mockMvc.perform(post("/api/internal/agent/tools/kb.readiness.check/execute")
                        .header(INTERNAL_TOOL_TOKEN_HEADER, "wrong")
                        .contentType("application/json")
                        .content("""
                                {
                                  "kbCode": "day20-cn-kb"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("Invalid agent internal tool token"));
    }

    @Test
    void executeShouldRejectNonReadOnlyTool() throws Exception {
        when(snowflakeIdGenerator.nextId("REQ-")).thenReturn("REQ-test");

        mockMvc.perform(post("/api/internal/agent/tools/document.indexing_task.retry/execute")
                        .header(INTERNAL_TOOL_TOKEN_HEADER, TOKEN)
                        .contentType("application/json")
                        .content("""
                                {
                                  "kbCode": "day20-cn-kb"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("Only READ_ONLY agent tools can be executed by Agent Runtime: document.indexing_task.retry"));
    }

    private AgentTool stubTool(String toolName, AgentToolExecutionMode executionMode) {
        return new AgentTool() {
            @Override
            public String toolName() {
                return toolName;
            }

            @Override
            public AgentToolDefinition definition() {
                return new AgentToolDefinition(toolName, executionMode, AgentActionRiskLevel.LOW);
            }

            @Override
            public AgentToolResult execute(AgentToolContext context) {
                executedContext.set(context);
                return AgentToolResult.success(toolName, "{\"ok\":true}", 3L);
            }
        };
    }
}
