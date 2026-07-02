package com.harness.core.service;

import org.springframework.stereotype.Service;

/**
 * Agent 业务服务
 * 封装 AI 交互的业务逻辑
 */
@Service
public class AgentService {

    private final AiServiceFactory aiServiceFactory;

    public AgentService(AiServiceFactory aiServiceFactory) {
        this.aiServiceFactory = aiServiceFactory;
    }

    /**
     * 执行 AI 对话
     *
     * @param message   用户消息
     * @param modelType 模型类型
     * @return AI 响应结果
     */
    public ChatResult chat(String message, String modelType) {
        AiChatService service = aiServiceFactory.getService(modelType);
        String response = service.chat(message);

        return new ChatResult(
                true,
                response,
                modelType != null ? modelType : "openai"
        );
    }

    /**
     * 聊天结果 DTO
     */
    public record ChatResult(boolean success, String message, String modelType) {}
}