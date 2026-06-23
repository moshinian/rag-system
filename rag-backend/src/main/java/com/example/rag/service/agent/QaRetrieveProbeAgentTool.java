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

    /** 构造QaRetrieveProbeAgentTool。 */
    public QaRetrieveProbeAgentTool(QuestionAnsweringService questionAnsweringService) {
        this.questionAnsweringService = questionAnsweringService;
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String title() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "对指定问题执行 Dense 与 Hybrid 检索探测，仅返回来源摘要和检索信号。";
    }

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

    private String normalizeQuestion(String question) {
        if (question == null || question.trim().isBlank()) {
            return null;
        }
        return question.trim();
    }

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
        branch.put("totalDurationMs", response.totalDurationMs());
        branch.put("sources", response.chunks().stream().map(this::toSource).toList());
        return branch;
    }

    private Map<String, Object> toSource(RetrievedChunkResponse chunk) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("documentCode", chunk.documentCode());
        source.put("documentName", chunk.documentName());
        source.put("chunkId", chunk.chunkId());
        source.put("chunkIndex", chunk.chunkIndex());
        source.put("score", chunk.score());
        return source;
    }

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

    private boolean sameTopSourceSet(List<RetrievedChunkResponse> left, List<RetrievedChunkResponse> right) {
        Set<String> leftKeys = left.stream().map(this::sourceKey).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Set<String> rightKeys = right.stream().map(this::sourceKey).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return leftKeys.equals(rightKeys);
    }

    private String topSourceKey(List<RetrievedChunkResponse> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return null;
        }
        return sourceKey(chunks.get(0));
    }

    private String sourceKey(RetrievedChunkResponse chunk) {
        return chunk.documentCode() + "#" + chunk.chunkIndex();
    }
}
