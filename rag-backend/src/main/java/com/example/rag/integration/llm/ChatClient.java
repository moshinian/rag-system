package com.example.rag.integration.llm;

import com.example.rag.config.RagLlmProperties;
import com.example.rag.integration.ai.AiGatewayClient;
import org.springframework.stereotype.Component;

/**
 * 基于 OpenAI-compatible 协议的聊天调用封装。
 *
 * 通过配置切换不同提供方，例如 OpenAI / DeepSeek / vLLM。
 */
@Component
public class ChatClient {
    private final RagLlmProperties ragLlmProperties;
    private final AiGatewayClient aiGatewayClient;

    /** 构造ChatClient。 */
    public ChatClient(RagLlmProperties ragLlmProperties,
                      AiGatewayClient aiGatewayClient) {
        this.ragLlmProperties = ragLlmProperties;
        this.aiGatewayClient = aiGatewayClient;
    }

    /** 调用 OpenAI-compatible chat completion 并返回回答。 */
    public String chat(String systemPrompt, String userPrompt) {
        RagLlmProperties.ChatProperties chat = ragLlmProperties.getChat();
        return aiGatewayClient.createChatCompletion(
                chat.getModel(),
                chat.getTemperature(),
                chat.getMaxOutputTokens(),
                systemPrompt,
                userPrompt
        );
    }

    /** 返回当前配置使用的 chat model。 */
    public String getChatModel() {
        return ragLlmProperties.getChat().getModel();
    }
}
