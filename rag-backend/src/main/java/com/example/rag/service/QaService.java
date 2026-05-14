package com.example.rag.service;

import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.integration.llm.ChatClient;
import com.example.rag.model.enums.RetrievalMode;
import com.example.rag.model.response.QuestionRetrievalResponse;
import com.example.rag.model.response.QaAnswerResponse;
import com.example.rag.model.response.QaSourceResponse;
import com.example.rag.model.response.RetrievedChunkResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 问答流程编排服务。
 *
 * 串联检索、Prompt 构造、模型调用和问答记录持久化，不直接处理底层向量检索细节。
 */
@Service
public class QaService {
    private static final Logger log = LoggerFactory.getLogger(QaService.class);
    private final QuestionAnsweringService questionAnsweringService;
    private final PromptBuilder promptBuilder;
    private final ChatClient chatClient;
    private final QaRecordService qaRecordService;

    /** 构造QaService。 */
    public QaService(QuestionAnsweringService questionAnsweringService,
                     PromptBuilder promptBuilder,
                     ChatClient chatClient,
                     QaRecordService qaRecordService) {
        this.questionAnsweringService = questionAnsweringService;
        this.promptBuilder = promptBuilder;
        this.chatClient = chatClient;
        this.qaRecordService = qaRecordService;
    }

    /** 执行一次完整的问答闭环。 */
    @Transactional
    public QaAnswerResponse ask(String kbCode, String question, Integer topK) {
        return ask(kbCode, question, topK, null);
    }

    /** 执行一次完整的问答闭环。 */
    @Transactional
    public QaAnswerResponse ask(String kbCode, String question, Integer topK, RetrievalMode retrievalMode) {
        long startedAt = System.currentTimeMillis();
        log.info(StructuredLogMessage.of("qa.ask.started")
                .field("kbCode", kbCode)
                .field("topK", topK)
                .field("requestedRetrievalMode", retrievalMode == null ? "AUTO" : retrievalMode.name())
                .field("questionLength", question == null ? 0 : question.trim().length())
                .build());
        // 问答编排始终复用 retrieval 结果，不再单独构造另一份 sources 之外的证据来源。
        QuestionRetrievalResponse retrievalResponse = questionAnsweringService.retrieve(kbCode, question, topK, retrievalMode);
        PromptBuilder.PromptPayload promptPayload = promptBuilder.build(
                retrievalResponse.question(),
                retrievalResponse.chunks()
        );
        long llmStartedAt = System.currentTimeMillis();
        String answer = chatClient.chat(promptPayload.systemPrompt(), promptPayload.userPrompt());
        long llmDurationMs = System.currentTimeMillis() - llmStartedAt;
        log.info(StructuredLogMessage.of("qa.ask.llm.completed")
                .field("kbCode", kbCode)
                .field("retrievalMode", retrievalResponse.retrievalMode().name())
                .field("fusionStrategy", retrievalResponse.fusionStrategy())
                .field("chatModel", chatClient.getChatModel())
                .field("llmDurationMs", llmDurationMs)
                .field("answerLength", answer.length())
                .build());
        // sources 只保留回答展示和追溯所需字段，避免直接暴露完整检索对象。
        List<QaSourceResponse> sources = retrievalResponse.chunks().stream()
                .map(this::toQaSourceResponse)
                .toList();
        long totalDurationMs = System.currentTimeMillis() - startedAt;
        QaAnswerResponse answerResponse = new QaAnswerResponse(
                retrievalResponse.question(),
                answer,
                retrievalResponse.topK(),
                chatClient.getChatModel(),
                retrievalResponse.retrievalMode(),
                retrievalResponse.fusionStrategy(),
                retrievalResponse.denseHitCount(),
                retrievalResponse.keywordHitCount(),
                retrievalResponse.hitCount(),
                retrievalResponse.denseDurationMs(),
                retrievalResponse.keywordDurationMs(),
                retrievalResponse.fusionDurationMs(),
                llmDurationMs,
                totalDurationMs,
                retrievalResponse.chunks(),
                sources
        );
        qaRecordService.persist(kbCode, answerResponse, totalDurationMs);
        log.info(StructuredLogMessage.of("qa.ask.completed")
                .field("kbCode", kbCode)
                .field("topK", retrievalResponse.topK())
                .field("retrievalMode", retrievalResponse.retrievalMode().name())
                .field("fusionStrategy", retrievalResponse.fusionStrategy())
                .field("denseCandidateCount", retrievalResponse.denseHitCount())
                .field("keywordCandidateCount", retrievalResponse.keywordHitCount())
                .field("finalHitCount", retrievalResponse.hitCount())
                .field("denseDurationMs", retrievalResponse.denseDurationMs())
                .field("keywordDurationMs", retrievalResponse.keywordDurationMs())
                .field("fusionDurationMs", retrievalResponse.fusionDurationMs())
                .field("llmDurationMs", llmDurationMs)
                .field("chatModel", chatClient.getChatModel())
                .field("answerLength", answer.length())
                .field("totalDurationMs", totalDurationMs)
                .build());
        return answerResponse;
    }

    /** 将检索结果映射为更适合前端展示的来源结构。 */
    private QaSourceResponse toQaSourceResponse(RetrievedChunkResponse chunk) {
        return new QaSourceResponse(
                chunk.documentCode(),
                chunk.documentName(),
                chunk.chunkId(),
                chunk.chunkIndex(),
                chunk.content(),
                chunk.score(),
                chunk.startOffset(),
                chunk.endOffset()
        );
    }
}
