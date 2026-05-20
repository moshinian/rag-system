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
    public ChatResult chat(String systemPrompt, String userPrompt) {
        RagLlmProperties.ChatProperties chat = ragLlmProperties.getChat();
        AiGatewayClient.GatewayHealthSnapshot gatewayHealth = aiGatewayClient.probeGatewayHealth();
        String effectiveModel = hasText(gatewayHealth.chatDefaultModel())
                ? gatewayHealth.chatDefaultModel()
                : chat.getModel();
        return new ChatResult(
                aiGatewayClient.createChatCompletion(
                        effectiveModel,
                        chat.getTemperature(),
                        chat.getMaxOutputTokens(),
                        systemPrompt,
                        userPrompt
                ),
                effectiveModel
        );
    }

    /** 返回当前配置使用的 chat model。 */
    public String getChatModel() {
        AiGatewayClient.GatewayHealthSnapshot gatewayHealth = aiGatewayClient.probeGatewayHealth();
        return hasText(gatewayHealth.chatDefaultModel())
                ? gatewayHealth.chatDefaultModel()
                : ragLlmProperties.getChat().getModel();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    public record ChatResult(
            String answer,
            String model
    ) {
    }
}
