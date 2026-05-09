package com.example.rag.integration.llm;

import com.example.rag.config.RagLlmProperties;
import org.springframework.stereotype.Component;

/**
 * 基于 OpenAI-compatible 协议的聊天调用封装。
 *
 * 通过配置切换不同提供方，例如 OpenAI / DeepSeek / vLLM。
 */
@Component
public class ChatClient {

    private final RagLlmProperties ragLlmProperties;
    private final OpenAiCompatibleClient openAiCompatibleClient;

    public ChatClient(RagLlmProperties ragLlmProperties,
                      OpenAiCompatibleClient openAiCompatibleClient) {
        this.ragLlmProperties = ragLlmProperties;
        this.openAiCompatibleClient = openAiCompatibleClient;
    }

    /** 调用 OpenAI-compatible chat completion 并返回回答。 */
    public String chat(String systemPrompt, String userPrompt) {
        RagLlmProperties.ChatProperties chat = ragLlmProperties.getChat();
        return openAiCompatibleClient.createChatCompletion(
                chat.getBaseUrl(),
                chat.getApiKey(),
                chat.getChatPath(),
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
