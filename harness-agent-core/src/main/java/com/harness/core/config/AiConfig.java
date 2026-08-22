package com.harness.core.config;

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 模型配置
 * 只使用 OpenAI 兼容协议 (DeepSeek)
 */
@Configuration
public class AiConfig {

    @Value("${ai.openai.api-key}")
    private String openaiApiKey;

    @Value("${ai.openai.model-name}")
    private String openaiModelName;

    @Value("${ai.openai.base-url}")
    private String openaiBaseUrl;

    /**
     * 创建 OpenAI 流式聊天模型
     * 使用 DeepSeek 的 OpenAI 兼容接口
     */
    @Bean
    public OpenAiStreamingChatModel openaiStreamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(openaiApiKey)
                .modelName(openaiModelName)
                .baseUrl(openaiBaseUrl)
                .build();
    }
}