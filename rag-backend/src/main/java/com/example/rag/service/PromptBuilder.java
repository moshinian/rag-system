package com.example.rag.service;

import com.example.rag.config.RagRetrievalProperties;
import com.example.rag.model.response.RetrievedChunkResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 负责将检索结果拼装为第一版问答 prompt。
 */
@Component
public class PromptBuilder {

    private final RagRetrievalProperties ragRetrievalProperties;

    public PromptBuilder(RagRetrievalProperties ragRetrievalProperties) {
        this.ragRetrievalProperties = ragRetrievalProperties;
    }

    /** 基于问题和召回结果构造 system/user prompt。 */
    public PromptPayload build(String question, List<RetrievedChunkResponse> retrievalResults) {
        return new PromptPayload(
                """
                你是企业知识库问答助手。
                你只能基于提供的检索内容回答问题。
                如果检索内容不足以回答问题，直接明确回答“根据当前检索内容，无法确定答案”。
                不要使用检索内容之外的常识补全，不要编造事实，不要输出引用格式。
                """.strip(),
                buildUserPrompt(question, retrievalResults)
        );
    }

    private String buildUserPrompt(String question, List<RetrievedChunkResponse> retrievalResults) {
        StringBuilder contextBuilder = new StringBuilder();
        int maxContextChars = resolveMaxContextChars();
        for (RetrievedChunkResponse result : retrievalResults) {
            String block = """
                    [DocumentCode] %s
                    [DocumentName] %s
                    [ChunkIndex] %s
                    [Score] %s
                    [Content]
                    %s

                    """.formatted(
                    nullToEmpty(result.documentCode()),
                    nullToEmpty(result.documentName()),
                    result.chunkIndex(),
                    result.score(),
                    nullToEmpty(result.content())
            );
            if (contextBuilder.length() + block.length() > maxContextChars) {
                break;
            }
            contextBuilder.append(block);
        }

        String context = contextBuilder.isEmpty()
                ? "No retrieval context available."
                : contextBuilder.toString().trim();

        return """
                问题：
                %s

                检索内容：
                %s

                请严格基于以上检索内容回答问题。
                """.formatted(question, context);
    }

    private int resolveMaxContextChars() {
        Integer configured = ragRetrievalProperties.getMaxContextChars();
        return configured == null || configured < 200 ? 6000 : configured;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record PromptPayload(
            String systemPrompt,
            String userPrompt
    ) {
    }
}
