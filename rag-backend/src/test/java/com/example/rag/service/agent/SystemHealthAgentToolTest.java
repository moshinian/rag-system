package com.example.rag.service.agent;

import com.example.rag.model.dto.AgentToolContext;
import com.example.rag.model.dto.AgentToolResult;
import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentToolExecutionMode;
import com.example.rag.model.response.HealthComponentStatusResponse;
import com.example.rag.model.response.HealthStatusResponse;
import com.example.rag.service.SystemHealthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Agent 系统健康检查工具测试。 */
class SystemHealthAgentToolTest {

    @Test
    void executeShouldReturnSerializedHealthStatus() throws Exception {
        SystemHealthService systemHealthService = mock(SystemHealthService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SystemHealthAgentTool tool = new SystemHealthAgentTool(systemHealthService, objectMapper);
        when(systemHealthService.currentStatus()).thenReturn(new HealthStatusResponse(
                "UP",
                "rag-service",
                List.of("local"),
                Map.of("postgres", new HealthComponentStatusResponse(
                        "UP",
                        "infrastructure",
                        "jdbc:postgresql",
                        null,
                        null,
                        2L,
                        "SELECT 1 succeeded",
                        null,
                        Instant.parse("2026-06-16T10:00:00Z")
                )),
                Instant.parse("2026-06-16T10:00:01Z")
        ));

        AgentToolResult result = tool.execute(AgentToolContext.forKnowledgeBase("day20-cn-kb"));

        assertThat(result.success()).isTrue();
        assertThat(result.toolName()).isEqualTo(SystemHealthAgentTool.TOOL_NAME);
        assertThat(result.durationMs()).isNotNegative();
        JsonNode json = objectMapper.readTree(result.outputJson());
        assertThat(json.get("status").asText()).isEqualTo("UP");
        assertThat(json.get("serviceName").asText()).isEqualTo("rag-service");
        assertThat(json.get("components").get("postgres").get("status").asText()).isEqualTo("UP");
    }

    @Test
    void definitionShouldDeclareReadOnlyLowRiskTool() {
        SystemHealthAgentTool tool = new SystemHealthAgentTool(mock(SystemHealthService.class), new ObjectMapper());

        assertThat(tool.definition().toolName()).isEqualTo(SystemHealthAgentTool.TOOL_NAME);
        assertThat(tool.definition().executionMode()).isEqualTo(AgentToolExecutionMode.READ_ONLY);
        assertThat(tool.definition().maxRiskLevel()).isEqualTo(AgentActionRiskLevel.LOW);
    }
}
