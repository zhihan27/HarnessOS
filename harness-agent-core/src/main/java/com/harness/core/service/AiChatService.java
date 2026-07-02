package com.harness.core.service;

/**
 * AI 聊天服务接口
 * 使用 LangChain4j AiServices 自动实现
 */
public interface AiChatService {

    /**
     * 与 AI 进行对话
     *
     * @param userMessage 用户消息
     * @return AI 响应
     */
    String chat(String userMessage);
}