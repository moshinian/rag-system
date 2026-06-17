package com.example.rag.integration.llm;

import com.example.rag.config.RagLlmProperties;
import com.example.rag.integration.ai.AiGatewayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatClientTest {

    private RagLlmProperties ragLlmProperties;
    private AiGatewayClient aiGatewayClient;
    private ChatClient chatClient;

    @BeforeEach
    void setUp() {
        ragLlmProperties = new RagLlmProperties();
        ragLlmProperties.getChat().setModel("deepseek-v4-pro");
        ragLlmProperties.getChat().setTemperature(0.2D);
        ragLlmProperties.getChat().setMaxOutputTokens(1200);
        aiGatewayClient = mock(AiGatewayClient.class);
        chatClient = new ChatClient(ragLlmProperties, aiGatewayClient);
    }

    @Test
    void chatShouldPreferGatewayDefaultModel() {
        when(aiGatewayClient.probeGatewayHealth()).thenReturn(new AiGatewayClient.GatewayHealthSnapshot(
                "UP",
                "aliyun-bailian-openai-compatible",
                "text-embedding-v4",
                "aliyun-bailian-openai-compatible",
                "qwen-plus"
        ));
        when(aiGatewayClient.createChatCompletion(
                "qwen-plus",
                0.2D,
                1200,
                "system",
                "user"
        )).thenReturn("pong");

        ChatClient.ChatResult response = chatClient.chat("system", "user");

        assertThat(response.answer()).isEqualTo("pong");
        assertThat(response.model()).isEqualTo("qwen-plus");
        verify(aiGatewayClient).createChatCompletion("qwen-plus", 0.2D, 1200, "system", "user");
        assertThat(chatClient.getChatModel()).isEqualTo("qwen-plus");
    }

    @Test
    void chatShouldFallbackToLocalConfiguredModelWhenGatewayModelMissing() {
        when(aiGatewayClient.probeGatewayHealth()).thenReturn(new AiGatewayClient.GatewayHealthSnapshot(
                "UP",
                "aliyun-bailian-openai-compatible",
                "text-embedding-v4",
                "aliyun-bailian-openai-compatible",
                null
        ));
        when(aiGatewayClient.createChatCompletion(
                "deepseek-v4-pro",
                0.2D,
                1200,
                "system",
                "user"
        )).thenReturn("pong");

        ChatClient.ChatResult response = chatClient.chat("system", "user");

        assertThat(response.answer()).isEqualTo("pong");
        assertThat(response.model()).isEqualTo("deepseek-v4-pro");
        verify(aiGatewayClient).createChatCompletion("deepseek-v4-pro", 0.2D, 1200, "system", "user");
        assertThat(chatClient.getChatModel()).isEqualTo("deepseek-v4-pro");
    }
}
