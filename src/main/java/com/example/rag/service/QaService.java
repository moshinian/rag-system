package com.example.rag.service;

import com.example.rag.integration.llm.ChatClient;
import com.example.rag.model.response.QuestionRetrievalResponse;
import com.example.rag.model.response.QaAnswerResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public QaService(QuestionAnsweringService questionAnsweringService,
                     PromptBuilder promptBuilder,
                     ChatClient chatClient) {
        this.questionAnsweringService = questionAnsweringService;
        this.promptBuilder = promptBuilder;
        this.chatClient = chatClient;
    }

    /** 执行 Day 11 第一版最小问答闭环。 */
    @Transactional(readOnly = true)
    public QaAnswerResponse ask(String kbCode, String question, Integer topK) {
        QuestionRetrievalResponse retrievalResponse = questionAnsweringService.retrieve(kbCode, question, topK);
        PromptBuilder.PromptPayload promptPayload = promptBuilder.build(
                retrievalResponse.question(),
                retrievalResponse.chunks()
        );
        String answer = chatClient.chat(promptPayload.systemPrompt(), promptPayload.userPrompt());
        return new QaAnswerResponse(
                retrievalResponse.question(),
                answer,
                retrievalResponse.topK(),
                chatClient.getChatModel(),
                retrievalResponse.chunks()
        );
    }
}
