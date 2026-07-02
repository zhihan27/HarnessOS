package com.harness.core.config;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 模型配置类，提供全局可注入的 AI 客户端
 */
@Configuration
public class AiConfig {

    @Value("${ai.openai.api-key}")
    private String openaiApiKey;

    @Value("${ai.openai.model-name}")
    private String openaiModelName;

    @Value("${ai.openai.base-url}")
    private String openaiBaseUrl;

    @Value("${ai.anthropic.api-key}")
    private String anthropicApiKey;

    @Value("${ai.anthropic.model-name}")
    private String anthropicModelName;

    @Value("${ai.anthropic.base-url}")
    private String anthropicBaseUrl;

    /**
     * OpenAI ChatModel Bean
     */
    @Bean
    public OpenAiChatModel openaiChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(openaiApiKey)
                .modelName(openaiModelName)
                .baseUrl(openaiBaseUrl)
                .build();
    }

    /**
     * Anthropic ChatModel Bean
     */
    @Bean
    public AnthropicChatModel anthropicChatModel() {
        return AnthropicChatModel.builder()
                .apiKey(anthropicApiKey)
                .modelName(anthropicModelName)
                .baseUrl(anthropicBaseUrl)
                .build();
    }
}