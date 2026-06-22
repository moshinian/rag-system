package com.example.rag.service.agent;

import com.example.rag.model.dto.AgentToolContext;
import com.example.rag.model.dto.AgentToolResult;
import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentToolExecutionMode;
import com.example.rag.model.enums.RetrievalMode;
import com.example.rag.model.response.QuestionRetrievalResponse;
import com.example.rag.model.response.RetrievedChunkResponse;
import com.example.rag.service.QuestionAnsweringService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Agent 检索探测工具测试。 */
class QaRetrieveProbeAgentToolTest {

    @Test
    void executeShouldCompareDenseAndHybridRetrievalResults() throws Exception {
        QuestionAnsweringService questionAnsweringService = mock(QuestionAnsweringService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        QaRetrieveProbeAgentTool tool = new QaRetrieveProbeAgentTool(questionAnsweringService, objectMapper);
        when(questionAnsweringService.retrieve("day20-cn-kb", "第二百三十八条是什么", 5, RetrievalMode.DENSE))
                .thenReturn(retrieval(RetrievalMode.DENSE, "NONE", 1, 0, 1, 12L, chunk("DOC-1", 1, 0.82D)));
        when(questionAnsweringService.retrieve("day20-cn-kb", "第二百三十八条是什么", 5, RetrievalMode.HYBRID))
                .thenReturn(retrieval(RetrievalMode.HYBRID, "RRF", 2, 1, 2, 18L, chunk("DOC-2", 2, 0.91D)));

        AgentToolResult result = tool.execute(new AgentToolContext(
                "day20-cn-kb",
                " 第二百三十八条是什么 ",
                "AR-test",
                "frontend",
                Map.of()
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.toolName()).isEqualTo(QaRetrieveProbeAgentTool.TOOL_NAME);
        JsonNode json = objectMapper.readTree(result.outputJson());
        assertThat(json.get("question").asText()).isEqualTo("第二百三十八条是什么");
        assertThat(json.get("topK").asInt()).isEqualTo(5);
        assertThat(json.get("dense").get("hitCount").asInt()).isEqualTo(1);
        assertThat(json.get("hybrid").get("keywordHitCount").asInt()).isEqualTo(1);
        assertThat(json.get("hybrid").get("sources").get(0).get("documentCode").asText()).isEqualTo("DOC-2");
        assertThat(json.get("hybrid").get("sources").get(0).has("content")).isFalse();
        assertThat(json.get("signals").get("keywordZeroHit").asBoolean()).isFalse();
        assertThat(json.get("signals").get("hybridNoGain").asBoolean()).isFalse();
        assertThat(json.get("signals").get("topSourceChanged").asBoolean()).isTrue();
        verify(questionAnsweringService).retrieve("day20-cn-kb", "第二百三十八条是什么", 5, RetrievalMode.DENSE);
        verify(questionAnsweringService).retrieve("day20-cn-kb", "第二百三十八条是什么", 5, RetrievalMode.HYBRID);
    }

    @Test
    void executeShouldReturnFailureWhenQuestionIsBlank() {
        QuestionAnsweringService questionAnsweringService = mock(QuestionAnsweringService.class);
        QaRetrieveProbeAgentTool tool = new QaRetrieveProbeAgentTool(questionAnsweringService, new ObjectMapper());

        AgentToolResult result = tool.execute(AgentToolContext.forKnowledgeBase("day20-cn-kb"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("question must not be blank");
        verifyNoInteractions(questionAnsweringService);
    }

    @Test
    void definitionShouldDeclareReadOnlyLowRiskTool() {
        QaRetrieveProbeAgentTool tool = new QaRetrieveProbeAgentTool(
                mock(QuestionAnsweringService.class),
                new ObjectMapper()
        );

        assertThat(tool.definition().toolName()).isEqualTo(QaRetrieveProbeAgentTool.TOOL_NAME);
        assertThat(tool.definition().executionMode()).isEqualTo(AgentToolExecutionMode.READ_ONLY);
        assertThat(tool.definition().maxRiskLevel()).isEqualTo(AgentActionRiskLevel.LOW);
    }

    private QuestionRetrievalResponse retrieval(RetrievalMode retrievalMode,
                                                String fusionStrategy,
                                                int denseHitCount,
                                                int keywordHitCount,
                                                int hitCount,
                                                long totalDurationMs,
                                                RetrievedChunkResponse chunk) {
        return new QuestionRetrievalResponse(
                "day20-cn-kb",
                "第二百三十八条是什么",
                "bge-small-zh-v1.5",
                5,
                retrievalMode,
                fusionStrategy,
                denseHitCount,
                keywordHitCount,
                hitCount,
                10L,
                retrievalMode == RetrievalMode.HYBRID ? 5L : 0L,
                retrievalMode == RetrievalMode.HYBRID ? 3L : 0L,
                totalDurationMs,
                List.of(chunk)
        );
    }

    private RetrievedChunkResponse chunk(String documentCode, int chunkIndex, double score) {
        return new RetrievedChunkResponse(
                (long) chunkIndex,
                100L + chunkIndex,
                documentCode,
                "示例文档.md",
                chunkIndex,
                "TEXT",
                "完整 chunk 内容不应该进入 probe source 摘要。",
                0,
                20,
                "bge-small-zh-v1.5",
                score
        );
    }
}
