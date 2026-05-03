package com.example.rag.service;

import com.example.rag.integration.llm.ChatClient;
import com.example.rag.model.response.QuestionRetrievalResponse;
import com.example.rag.model.response.QaAnswerResponse;
import com.example.rag.model.response.QaSourceResponse;
import com.example.rag.model.response.RetrievedChunkResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Day 11 问答流程编排服务。
 *
 * 只负责问题输入、检索、prompt 组装和 chat completion 调用。
 */
@Service
public class QaService {

    private final QuestionAnsweringService questionAnsweringService;
    private final PromptBuilder promptBuilder;
    private final ChatClient chatClient;
    private final QaRecordService qaRecordService;

    public QaService(QuestionAnsweringService questionAnsweringService,
                     PromptBuilder promptBuilder,
                     ChatClient chatClient,
                     QaRecordService qaRecordService) {
        this.questionAnsweringService = questionAnsweringService;
        this.promptBuilder = promptBuilder;
        this.chatClient = chatClient;
        this.qaRecordService = qaRecordService;
    }

    /** 执行 Day 11 第一版最小问答闭环。 */
    @Transactional
    public QaAnswerResponse ask(String kbCode, String question, Integer topK) {
        long startedAt = System.currentTimeMillis();
        QuestionRetrievalResponse retrievalResponse = questionAnsweringService.retrieve(kbCode, question, topK);
        PromptBuilder.PromptPayload promptPayload = promptBuilder.build(
                retrievalResponse.question(),
                retrievalResponse.chunks()
        );
        String answer = chatClient.chat(promptPayload.systemPrompt(), promptPayload.userPrompt());
        List<QaSourceResponse> sources = retrievalResponse.chunks().stream()
                .map(this::toQaSourceResponse)
                .toList();
        QaAnswerResponse answerResponse = new QaAnswerResponse(
                retrievalResponse.question(),
                answer,
                retrievalResponse.topK(),
                chatClient.getChatModel(),
                retrievalResponse.chunks(),
                sources
        );
        qaRecordService.persist(kbCode, answerResponse, System.currentTimeMillis() - startedAt);
        return answerResponse;
    }

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
