package com.example.rag.service.agent;

import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentToolExecutionMode;
import com.example.rag.model.response.QuestionAnsweringReadinessResponse;
import com.example.rag.service.QuestionAnsweringService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Agent 问答 readiness 工具测试。 */
class QaReadinessAgentToolTest {

    @Test
    void executeShouldReturnSerializedReadinessStatus() throws Exception {
        QuestionAnsweringService questionAnsweringService = mock(QuestionAnsweringService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        QaReadinessAgentTool tool = new QaReadinessAgentTool(questionAnsweringService, objectMapper);
        when(questionAnsweringService.getReadiness("day20-cn-kb"))
                .thenReturn(new QuestionAnsweringReadinessResponse(
                        "day20-cn-kb",
                        "ACTIVE",
                        false,
                        "aliyun-bailian-openai-compatible",
                        "text-embedding-v4",
                        "text-embedding-v3",
                        1024,
                        "pgvector",
                        3,
                        12L,
                        0L,
                        true,
                        false,
                        123L,
                        "Submit embedding rebuild"
                ));

        McpToolResult result = tool.call(McpToolContext.forKnowledgeBase("day20-cn-kb"));

        assertThat(!result.isError()).isTrue();
        assertThat(result.toolName()).isEqualTo(QaReadinessAgentTool.TOOL_NAME);
        assertThat(result.durationMs()).isNotNegative();
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(result.structuredContent()));
        assertThat(json.get("knowledgeBaseCode").asText()).isEqualTo("day20-cn-kb");
        assertThat(json.get("questionAnsweringReady").asBoolean()).isFalse();
        assertThat(json.get("reembedRequired").asBoolean()).isTrue();
        assertThat(json.get("nextStep").asText()).isEqualTo("Submit embedding rebuild");
    }

    @Test
    void definitionShouldDeclareReadOnlyLowRiskTool() {
        QaReadinessAgentTool tool = new QaReadinessAgentTool(mock(QuestionAnsweringService.class), new ObjectMapper());

        assertThat(tool.definition().name()).isEqualTo(QaReadinessAgentTool.TOOL_NAME);
        assertThat(tool.definition().executionMode()).isEqualTo(AgentToolExecutionMode.READ_ONLY);
        assertThat(tool.definition().maxRiskLevel()).isEqualTo(AgentActionRiskLevel.LOW);
    }
}
