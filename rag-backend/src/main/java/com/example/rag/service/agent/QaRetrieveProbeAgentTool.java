package com.example.rag.service.agent;

import com.example.rag.model.enums.RetrievalMode;
import com.example.rag.model.response.QuestionRetrievalResponse;
import com.example.rag.model.response.RetrievedChunkResponse;
import com.example.rag.service.QuestionAnsweringService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent 只读 Dense / Hybrid 检索探测工具。
 */
@Component
public class QaRetrieveProbeAgentTool implements McpTool {
    public static final String TOOL_NAME = "qa.retrieve.probe";
    private static final int DEFAULT_TOP_K = 5;
    private final QuestionAnsweringService questionAnsweringService;

    /** 注入统一检索服务。 */
    public QaRetrieveProbeAgentTool(QuestionAnsweringService questionAnsweringService) {
        this.questionAnsweringService = questionAnsweringService;
    }

    /** 返回 MCP 工具唯一名称。 */
    @Override
    public String name() {
        return TOOL_NAME;
    }

    /** 返回工具展示标题。 */
    @Override
    public String title() {
        return TOOL_NAME;
    }

    /** 返回供 planner 理解双路检索探测用途的描述。 */
    @Override
    public String description() {
        return "对指定问题执行 Dense 与 Hybrid 检索探测，仅返回来源摘要和检索信号。";
    }

    /** 声明 kbCode、question 和可选 attributes.topK 的递归严格 schema。 */
    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> attributesProperties = new LinkedHashMap<>();
        attributesProperties.put("topK", AgentToolSupport.integerProperty("返回检索结果数量。", 1, 10));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("kbCode", AgentToolSupport.stringProperty("知识库编码。"));
        properties.put("question", AgentToolSupport.stringProperty("检索探测问题。"));
        properties.put(
                "attributes",
                AgentToolSupport.objectProperty("检索探测可选参数。", List.of(), attributesProperties)
        );
        return AgentToolSupport.objectSchema(List.of("kbCode", "question"), properties);
    }

    /** 分别执行 Dense 与 Hybrid 检索，并生成对比诊断信号。 */
    @Override
    public McpToolResult call(McpToolContext context) {
        long startedAt = System.nanoTime();
        String question = normalizeQuestion(context.question());
        if (question == null) {
            return McpToolResult.failure(
                    TOOL_NAME,
                    "question must not be blank for qa.retrieve.probe",
                    AgentToolSupport.elapsedMillis(startedAt)
            );
        }
        Integer topK = resolveTopK(context.attributes());
        if (topK == null) {
            return McpToolResult.failure(
                    TOOL_NAME,
                    "topK must be an integer between 1 and 10",
                    AgentToolSupport.elapsedMillis(startedAt)
            );
        }

        QuestionRetrievalResponse dense = questionAnsweringService.retrieve(
                context.kbCode(),
                question,
                topK,
                RetrievalMode.DENSE
        );
        QuestionRetrievalResponse hybrid = questionAnsweringService.retrieve(
                context.kbCode(),
                question,
                topK,
                RetrievalMode.HYBRID
        );

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("question", question);
        output.put("topK", topK);
        output.put("dense", toProbeBranch(dense));
        output.put("hybrid", toProbeBranch(hybrid));
        output.put("signals", signals(dense, hybrid));
        return McpToolResult.success(
                TOOL_NAME,
                output,
                AgentToolSupport.elapsedMillis(startedAt)
        );
    }

    /** 清理问题文本，空白问题返回 null。 */
    private String normalizeQuestion(String question) {
        if (question == null || question.trim().isBlank()) {
            return null;
        }
        return question.trim();
    }

    /** 解析可选 topK；缺省使用 5，并限制在 schema 声明的 1 到 10。 */
    private Integer resolveTopK(Map<String, Object> attributes) {
        if (attributes == null || !attributes.containsKey("topK")) {
            return DEFAULT_TOP_K;
        }
        Object rawTopK = attributes.get("topK");
        Integer topK = null;
        if (rawTopK instanceof Number number) {
            if (number.doubleValue() != number.intValue()) {
                return null;
            }
            topK = number.intValue();
        } else if (rawTopK instanceof String text && !text.trim().isBlank()) {
            try {
                topK = Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (topK == null || topK < 1 || topK > 10) {
            return null;
        }
        return topK;
    }

    /** 把完整检索响应裁剪成探测分支摘要。 */
    private Map<String, Object> toProbeBranch(QuestionRetrievalResponse response) {
        Map<String, Object> branch = new LinkedHashMap<>();
        branch.put("retrievalMode", response.retrievalMode());
        branch.put("hitCount", response.hitCount());
        branch.put("denseHitCount", response.denseHitCount());
        branch.put("keywordHitCount", response.keywordHitCount());
        branch.put("fusionStrategy", response.fusionStrategy());
        branch.put("denseDurationMs", response.denseDurationMs());
        branch.put("keywordDurationMs", response.keywordDurationMs());
        branch.put("fusionDurationMs", response.fusionDurationMs());
        branch.put("rerankStatus", response.rerankStatus());
        branch.put("rerankModel", response.rerankModel());
        branch.put("rerankCandidateCount", response.rerankCandidateCount());
        branch.put("rerankDurationMs", response.rerankDurationMs());
        branch.put("totalDurationMs", response.totalDurationMs());
        branch.put("sources", response.chunks().stream().map(this::toSource).toList());
        return branch;
    }

    /** 只保留定位来源所需字段，避免把完整 chunk 正文送回 planner。 */
    private Map<String, Object> toSource(RetrievedChunkResponse chunk) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("documentCode", chunk.documentCode());
        source.put("documentName", chunk.documentName());
        source.put("chunkId", chunk.chunkId());
        source.put("chunkIndex", chunk.chunkIndex());
        source.put("score", chunk.score());
        source.put("rerankScore", chunk.rerankScore());
        return source;
    }

    /** 比较 Dense 与 Hybrid 结果，生成可直接用于诊断的布尔信号。 */
    private Map<String, Object> signals(QuestionRetrievalResponse dense, QuestionRetrievalResponse hybrid) {
        boolean denseEmpty = dense.hitCount() == 0;
        boolean hybridEmpty = hybrid.hitCount() == 0;
        boolean keywordZeroHit = hybrid.keywordHitCount() == 0;
        boolean hybridNoGain = hybrid.hitCount() <= dense.hitCount() && sameTopSourceSet(dense.chunks(), hybrid.chunks());
        boolean topSourceChanged = !Objects.equals(topSourceKey(dense.chunks()), topSourceKey(hybrid.chunks()));

        Map<String, Object> signals = new LinkedHashMap<>();
        signals.put("denseEmpty", denseEmpty);
        signals.put("hybridEmpty", hybridEmpty);
        signals.put("keywordZeroHit", keywordZeroHit);
        signals.put("hybridNoGain", hybridNoGain);
        signals.put("topSourceChanged", topSourceChanged);
        return signals;
    }

    /** 判断两条检索分支是否返回相同的来源集合。 */
    private boolean sameTopSourceSet(List<RetrievedChunkResponse> left, List<RetrievedChunkResponse> right) {
        Set<String> leftKeys = left.stream().map(this::sourceKey).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Set<String> rightKeys = right.stream().map(this::sourceKey).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return leftKeys.equals(rightKeys);
    }

    /** 返回首条来源的稳定标识，无命中时返回 null。 */
    private String topSourceKey(List<RetrievedChunkResponse> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return null;
        }
        return sourceKey(chunks.get(0));
    }

    /** 用文档编码和 chunk 序号构造来源稳定标识。 */
    private String sourceKey(RetrievedChunkResponse chunk) {
        return chunk.documentCode() + "#" + chunk.chunkIndex();
    }
}
