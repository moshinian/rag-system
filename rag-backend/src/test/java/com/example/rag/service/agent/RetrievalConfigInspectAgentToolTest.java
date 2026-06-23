package com.example.rag.service.agent;

import com.example.rag.config.RagRetrievalProperties;
import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentToolExecutionMode;
import com.example.rag.model.enums.KeywordStrategy;
import com.example.rag.model.enums.RetrievalMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Agent 检索配置检查工具测试。 */
class RetrievalConfigInspectAgentToolTest {

    @Test
    void executeShouldReturnNonSensitiveRetrievalConfig() throws Exception {
        RagRetrievalProperties properties = new RagRetrievalProperties();
        properties.setDefaultMode(RetrievalMode.HYBRID);
        properties.setDenseCandidateLimit(20);
        properties.setKeywordCandidateLimit(30);
        properties.setFusionK(80);
        properties.setKeywordStrategy(KeywordStrategy.POSTGRES_FTS);
        properties.setKeywordMinTokenLength(2);
        properties.setKeywordMinHitThreshold(0.7D);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RetrievalConfigInspectAgentTool tool = new RetrievalConfigInspectAgentTool(properties);

        McpToolResult result = tool.call(McpToolContext.forKnowledgeBase("day20-cn-kb"));

        assertThat(!result.isError()).isTrue();
        assertThat(result.toolName()).isEqualTo(RetrievalConfigInspectAgentTool.TOOL_NAME);
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(result.structuredContent()));
        assertThat(json.get("defaultMode").asText()).isEqualTo("HYBRID");
        assertThat(json.get("denseCandidateLimit").asInt()).isEqualTo(20);
        assertThat(json.get("keywordCandidateLimit").asInt()).isEqualTo(30);
        assertThat(json.get("fusionK").asInt()).isEqualTo(80);
        assertThat(json.get("keywordStrategy").asText()).isEqualTo("POSTGRES_FTS");
        assertThat(json.get("keywordMinTokenLength").asInt()).isEqualTo(2);
        assertThat(objectMapper.writeValueAsString(result.structuredContent())).doesNotContain("apiKey", "password", "token");
    }

    @Test
    void definitionShouldDeclareReadOnlyLowRiskTool() {
        RetrievalConfigInspectAgentTool tool = new RetrievalConfigInspectAgentTool(
                new RagRetrievalProperties()
        );

        McpToolDefinition definition = tool.definition();
        assertThat(definition.name()).isEqualTo(RetrievalConfigInspectAgentTool.TOOL_NAME);
        assertThat(definition.executionMode()).isEqualTo(AgentToolExecutionMode.READ_ONLY);
        assertThat(definition.maxRiskLevel()).isEqualTo(AgentActionRiskLevel.LOW);
        assertThat(definition.requiresConfirmation()).isFalse();
    }
}
