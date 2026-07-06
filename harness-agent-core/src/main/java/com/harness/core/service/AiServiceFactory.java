package com.harness.core.service;

import com.harness.core.memory.DatabaseChatMemoryStore;
import com.harness.core.tool.BashToolProvider;
import com.harness.core.tool.SubAgentToolProvider;
import com.harness.core.tool.ToolProvider;
import com.harness.core.tool.TodoWriteToolProvider;
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
 * 提供不同模型类型的 AiServices 实例，集成 Tool Use 和持久化 ChatMemory
 * 使用 ChatMemoryProvider 实现多会话隔离
 */
@Service
public class AiServiceFactory {

    private static final Logger logger = LoggerFactory.getLogger(AiServiceFactory.class);

    private final OpenAiChatModel openaiChatModel;
    private final AnthropicChatModel anthropicChatModel;
    private final ToolProvider toolProvider;
    private final BashToolProvider bashToolProvider;
    private final TodoWriteToolProvider todoWriteToolProvider;
    private final SubAgentToolProvider subAgentToolProvider;
    private final DatabaseChatMemoryStore memoryStore;

    // 最大消息数量（用于MessageWindowChatMemory）
    private static final int MAX_MESSAGES = 100;

    // AI服务单例（用于所有会话，memoryId动态传递）
    private AiChatService sharedService;

    public AiServiceFactory(OpenAiChatModel openaiChatModel,
                           AnthropicChatModel anthropicChatModel,
                           ToolProvider toolProvider,
                           BashToolProvider bashToolProvider,
                           TodoWriteToolProvider todoWriteToolProvider,
                           SubAgentToolProvider subAgentToolProvider,
                           DatabaseChatMemoryStore memoryStore) {
        this.openaiChatModel = openaiChatModel;
        this.anthropicChatModel = anthropicChatModel;
        this.toolProvider = toolProvider;
        this.bashToolProvider = bashToolProvider;
        this.todoWriteToolProvider = todoWriteToolProvider;
        this.subAgentToolProvider = subAgentToolProvider;
        this.memoryStore = memoryStore;

        logger.info("初始化 AI 服务工厂，注册工具...");

        // 初始化共享的AI服务实例（memoryId通过chat方法参数传递）
        initSharedService();

        logger.info("AI 服务工厂初始化完成，已注册 {} 个工具", getToolCount());
    }

    /**
     * 初始化共享的AI服务实例
     * ChatMemoryProvider根据memoryId动态创建/获取会话记忆
     */
    private void initSharedService() {
        // 获取原始对象（去除 CGLIB 代理）
        Object rawToolProvider = AopProxyUtils.getSingletonTarget(toolProvider);
        Object rawBashToolProvider = AopProxyUtils.getSingletonTarget(bashToolProvider);
        Object rawTodoWriteToolProvider = AopProxyUtils.getSingletonTarget(todoWriteToolProvider);
        Object rawSubAgentToolProvider = AopProxyUtils.getSingletonTarget(subAgentToolProvider);

        if (rawToolProvider == null) rawToolProvider = toolProvider;
        if (rawBashToolProvider == null) rawBashToolProvider = bashToolProvider;
        if (rawTodoWriteToolProvider == null) rawTodoWriteToolProvider = todoWriteToolProvider;
        if (rawSubAgentToolProvider == null) rawSubAgentToolProvider = subAgentToolProvider;

        // 构建共享的AI服务实例，使用ChatMemoryProvider实现多会话隔离
        // memoryId通过AiChatService.chat(@MemoryId sessionId, message)传递
        this.sharedService = AiServices.builder(AiChatService.class)
                .chatModel(openaiChatModel)
                .tools(rawToolProvider)
                .tools(rawBashToolProvider)
                .tools(rawTodoWriteToolProvider)
                .tools(rawSubAgentToolProvider)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(MAX_MESSAGES)
                        .chatMemoryStore(memoryStore)
                        .build())
                .build();

        logger.debug("共享AI服务实例初始化完成");
    }

    /**
     * 根据模型类型获取对应的 AI 服务
     * 返回共享的服务实例，memoryId通过chat方法参数传递
     *
     * @param modelType 模型类型 (openai/anthropic) - 当前仅使用openai
     * @param sessionId 会话ID（仅用于日志，实际通过@MemoryId传递）
     * @return AI 聊天服务
     */
    public AiChatService getService(String modelType, String sessionId) {
        logger.debug("获取AI服务: modelType={}, sessionId={}", modelType, sessionId);
        // 返回共享服务，memoryId在调用chat时通过@MemoryId传递
        return sharedService;
    }

    /**
     * 获取默认的 AI 服务（无会话隔离）
     * 用于简单的独立任务执行
     */
    public AiChatService getDefaultService(String modelType) {
        Object rawToolProvider = AopProxyUtils.getSingletonTarget(toolProvider);
        Object rawBashToolProvider = AopProxyUtils.getSingletonTarget(bashToolProvider);
        Object rawTodoWriteToolProvider = AopProxyUtils.getSingletonTarget(todoWriteToolProvider);
        Object rawSubAgentToolProvider = AopProxyUtils.getSingletonTarget(subAgentToolProvider);

        if (rawToolProvider == null) rawToolProvider = toolProvider;
        if (rawBashToolProvider == null) rawBashToolProvider = bashToolProvider;
        if (rawTodoWriteToolProvider == null) rawTodoWriteToolProvider = todoWriteToolProvider;
        if (rawSubAgentToolProvider == null) rawSubAgentToolProvider = subAgentToolProvider;

        return AiServices.builder(AiChatService.class)
                .chatModel(openaiChatModel)
                .tools(rawToolProvider)
                .tools(rawBashToolProvider)
                .tools(rawTodoWriteToolProvider)
                .tools(rawSubAgentToolProvider)
                .build();  // 无ChatMemory，每次独立执行
    }

    /**
     * 清除会话记忆缓存
     */
    public void clearSessionCache(String sessionId) {
        memoryStore.clearCache(sessionId);
        logger.debug("清除会话记忆缓存: sessionId={}", sessionId);
    }

    /**
     * 获取已注册的工具数量
     */
    public int getToolCount() {
        int count = 0;

        Object rawToolProvider = AopProxyUtils.getSingletonTarget(toolProvider);
        Object rawBashToolProvider = AopProxyUtils.getSingletonTarget(bashToolProvider);
        Object rawTodoWriteToolProvider = AopProxyUtils.getSingletonTarget(todoWriteToolProvider);
        Object rawSubAgentToolProvider = AopProxyUtils.getSingletonTarget(subAgentToolProvider);

        Class<?> toolProviderClass = rawToolProvider != null ?
            rawToolProvider.getClass() : toolProvider.getClass();
        Class<?> bashToolProviderClass = rawBashToolProvider != null ?
            rawBashToolProvider.getClass() : bashToolProvider.getClass();
        Class<?> todoWriteToolProviderClass = rawTodoWriteToolProvider != null ?
            rawTodoWriteToolProvider.getClass() : todoWriteToolProvider.getClass();
        Class<?> subAgentToolProviderClass = rawSubAgentToolProvider != null ?
            rawSubAgentToolProvider.getClass() : subAgentToolProvider.getClass();

        for (Method method : toolProviderClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) count++;
        }
        for (Method method : bashToolProviderClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) count++;
        }
        for (Method method : todoWriteToolProviderClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) count++;
        }
        for (Method method : subAgentToolProviderClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) count++;
        }

        return count;
    }
}