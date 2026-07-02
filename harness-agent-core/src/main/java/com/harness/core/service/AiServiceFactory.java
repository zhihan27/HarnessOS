package com.harness.core.service;

import dev.langchain4j.service.AiServices;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import org.springframework.stereotype.Service;

/**
 * AI 服务工厂
 * 提供不同模型类型的 AiServices 实例
 */
@Service
public class AiServiceFactory {

    private final OpenAiChatModel openaiChatModel;
    private final AnthropicChatModel anthropicChatModel;

    private final AiChatService openaiService;
    private final AiChatService anthropicService;

    public AiServiceFactory(OpenAiChatModel openaiChatModel, AnthropicChatModel anthropicChatModel) {
        this.openaiChatModel = openaiChatModel;
        this.anthropicChatModel = anthropicChatModel;

        // 使用 AiServices 构建 AI 服务实例
        this.openaiService = AiServices.create(AiChatService.class, openaiChatModel);
        this.anthropicService = AiServices.create(AiChatService.class, anthropicChatModel);
    }

    /**
     * 根据模型类型获取对应的 AI 服务
     *
     * @param modelType 模型类型 (openai/anthropic)
     * @return AI 聊天服务
     */
    public AiChatService getService(String modelType) {
        if ("anthropic".equalsIgnoreCase(modelType)) {
            return anthropicService;
        }
        return openaiService;
    }

    /**
     * 获取 OpenAI 服务
     */
    public AiChatService getOpenaiService() {
        return openaiService;
    }

    /**
     * 获取 Anthropic 服务
     */
    public AiChatService getAnthropicService() {
        return anthropicService;
    }
}