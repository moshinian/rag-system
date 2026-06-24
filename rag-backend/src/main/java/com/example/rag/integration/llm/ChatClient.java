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

    /** 注入 LLM 默认配置和统一 AI Gateway 客户端。 */
    public ChatClient(RagLlmProperties ragLlmProperties,
                      AiGatewayClient aiGatewayClient) {
        this.ragLlmProperties = ragLlmProperties;
        this.aiGatewayClient = aiGatewayClient;
    }

    /** 调用 OpenAI-compatible chat completion 并返回回答。 */
    public ChatResult chat(String systemPrompt, String userPrompt) {
        RagLlmProperties.ChatProperties chat = ragLlmProperties.getChat();
        AiGatewayClient.GatewayHealthSnapshot gatewayHealth = aiGatewayClient.probeGatewayHealth();
        // Gateway 暴露的运行时默认模型优先，避免 Java 静态配置与 Python 实际 provider 漂移。
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

    /** 判断模型配置是否包含非空白文本。 */
    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    /** 返回模型回答及本次实际使用的模型名称。 */
    public record ChatResult(
            String answer,
            String model
    ) {
    }
}
