package com.harness.core.service;

import com.harness.core.tool.BashToolProvider;
import com.harness.core.tool.ToolProvider;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;

/**
 * AI 服务工厂
 * 提供不同模型类型的 AiServices 实例，集成 Tool Use 和 ChatMemory
 */
@Service
public class AiServiceFactory {

    private static final Logger logger = LoggerFactory.getLogger(AiServiceFactory.class);

    private final OpenAiChatModel openaiChatModel;
    private final AnthropicChatModel anthropicChatModel;
    private final ToolProvider toolProvider;
    private final BashToolProvider bashToolProvider;

    private final AiChatService openaiService;
    private final AiChatService anthropicService;

    public AiServiceFactory(OpenAiChatModel openaiChatModel,
                           AnthropicChatModel anthropicChatModel,
                           ToolProvider toolProvider,
                           BashToolProvider bashToolProvider) {
        this.openaiChatModel = openaiChatModel;
        this.anthropicChatModel = anthropicChatModel;
        this.toolProvider = toolProvider;
        this.bashToolProvider = bashToolProvider;

        logger.info("初始化 AI 服务工厂，注册工具...");

        // 获取原始对象（去除 CGLIB 代理），避免 LangChain4j 无法识别 @Tool 注解
        Object rawToolProvider = AopProxyUtils.getSingletonTarget(toolProvider);
        Object rawBashToolProvider = AopProxyUtils.getSingletonTarget(bashToolProvider);

        if (rawToolProvider == null) rawToolProvider = toolProvider;
        if (rawBashToolProvider == null) rawBashToolProvider = bashToolProvider;

        // 构建 AI 服务实例，集成 Tool Use 和 ChatMemory
        this.openaiService = AiServices.builder(AiChatService.class)
                .chatModel(openaiChatModel)
                .tools(rawToolProvider)
                .tools(rawBashToolProvider)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        this.anthropicService = AiServices.builder(AiChatService.class)
                .chatModel(anthropicChatModel)
                .tools(rawToolProvider)
                .tools(rawBashToolProvider)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        logger.info("AI 服务工厂初始化完成，已注册 {} 个工具", getToolCount());
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
     * 获取已注册的工具数量
     * 通过反射获取所有 ToolProvider 类中标注了 @Tool 注解的方法数量
     * 使用原始对象避免 CGLIB 代理影响
     */
    public int getToolCount() {
        int count = 0;

        Object rawToolProvider = AopProxyUtils.getSingletonTarget(toolProvider);
        Object rawBashToolProvider = AopProxyUtils.getSingletonTarget(bashToolProvider);

        Class<?> toolProviderClass = rawToolProvider != null ?
            rawToolProvider.getClass() : toolProvider.getClass();
        Class<?> bashToolProviderClass = rawBashToolProvider != null ?
            rawBashToolProvider.getClass() : bashToolProvider.getClass();

        // 统计基础工具数量（去除 CGLIB 代理类的影响）
        for (Method method : toolProviderClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                count++;
            }
        }

        // 统计 Bash 工具数量
        for (Method method : bashToolProviderClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                count++;
            }
        }

        return count;
    }
}