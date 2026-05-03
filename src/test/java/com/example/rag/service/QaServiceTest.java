package com.example.rag.service;

import com.example.rag.integration.llm.ChatClient;
import com.example.rag.model.response.QuestionRetrievalResponse;
import com.example.rag.model.response.QaAnswerResponse;
import com.example.rag.model.response.RetrievedChunkResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QaServiceTest {

    @Mock
    private QuestionAnsweringService questionAnsweringService;

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private QaRecordService qaRecordService;

    @InjectMocks
    private QaService qaService;

    @Test
    void askShouldComposeRetrievalPromptAndAnswer() {
        QuestionRetrievalResponse retrievalResponse = new QuestionRetrievalResponse(
                "day6-kb",
                "这份文档主要讲了什么？",
                "bge-small-zh-v1.5",
                3,
                1,
                List.of(new RetrievedChunkResponse(
                        1L,
                        2L,
                        "DOC-1",
                        "Day6 Markdown Sample",
                        0,
                        "TEXT",
                        "This markdown file is used to verify the upload path.",
                        0,
                        57,
                        "bge-small-zh-v1.5",
                        0.48D
                ))
        );
        PromptBuilder.PromptPayload promptPayload = new PromptBuilder.PromptPayload(
                "system prompt",
                "user prompt"
        );

        when(questionAnsweringService.retrieve("day6-kb", "这份文档主要讲了什么？", 3))
                .thenReturn(retrievalResponse);
        when(promptBuilder.build(eq("这份文档主要讲了什么？"), eq(retrievalResponse.chunks())))
                .thenReturn(promptPayload);
        when(chatClient.chat("system prompt", "user prompt"))
                .thenReturn("这是基于检索内容生成的回答。");
        when(chatClient.getChatModel()).thenReturn("deepseek-v4-flash");

        QaAnswerResponse response = qaService.ask("day6-kb", "这份文档主要讲了什么？", 3);

        assertThat(response.question()).isEqualTo("这份文档主要讲了什么？");
        assertThat(response.answer()).isEqualTo("这是基于检索内容生成的回答。");
        assertThat(response.topK()).isEqualTo(3);
        assertThat(response.chatModel()).isEqualTo("deepseek-v4-flash");
        assertThat(response.retrievalResults()).hasSize(1);
        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().get(0).documentCode()).isEqualTo("DOC-1");
        assertThat(response.sources().get(0).chunkId()).isEqualTo(1L);
        verify(chatClient).chat(anyString(), anyString());
        verify(qaRecordService).persist(eq("day6-kb"), eq(response), org.mockito.ArgumentMatchers.anyLong());
    }
}
