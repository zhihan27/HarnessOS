package com.harness.core.service;

import com.harness.core.memory.DatabaseChatMemoryStore;
import com.harness.core.model.AiChatModel;
import com.harness.core.tool.BashToolProvider;
import com.harness.core.tool.DagTaskToolProvider;
import com.harness.core.tool.FileToolProvider;
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
    private final FileToolProvider fileToolProvider;
    private final TodoWriteToolProvider todoWriteToolProvider;
    private final SubAgentToolProvider subAgentToolProvider;
    private final DagTaskToolProvider dagTaskToolProvider;
    private final DatabaseChatMemoryStore memoryStore;

    // 最大消息数量（用于MessageWindowChatMemory）
    private static final int MAX_MESSAGES = 100;

    // AI模型单例（用于所有会话，memoryId动态传递）
    private AiChatModel sharedModel;

    public AiServiceFactory(OpenAiChatModel openaiChatModel,
                           AnthropicChatModel anthropicChatModel,
                           ToolProvider toolProvider,
                           BashToolProvider bashToolProvider,
                           FileToolProvider fileToolProvider,
                           TodoWriteToolProvider todoWriteToolProvider,
                           SubAgentToolProvider subAgentToolProvider,
                           DagTaskToolProvider dagTaskToolProvider,
                           DatabaseChatMemoryStore memoryStore) {
        this.openaiChatModel = openaiChatModel;
        this.anthropicChatModel = anthropicChatModel;
        this.toolProvider = toolProvider;
        this.bashToolProvider = bashToolProvider;
        this.fileToolProvider = fileToolProvider;
        this.todoWriteToolProvider = todoWriteToolProvider;
        this.subAgentToolProvider = subAgentToolProvider;
        this.dagTaskToolProvider = dagTaskToolProvider;
        this.memoryStore = memoryStore;

        logger.info("初始化 AI 服务工厂，注册工具...");

        // 初始化共享的AI模型实例（memoryId通过chat方法参数传递）
        initSharedModel();

        logger.info("AI 服务工厂初始化完成，已注册 {} 个工具", getToolCount());
    }

    /**
     * 初始化共享的AI模型实例
     * ChatMemoryProvider根据memoryId动态创建/获取会话记忆
     */
    private void initSharedModel() {
        // 获取原始对象（去除 CGLIB 代理）
        Object rawToolProvider = AopProxyUtils.getSingletonTarget(toolProvider);
        Object rawBashToolProvider = AopProxyUtils.getSingletonTarget(bashToolProvider);
        Object rawFileToolProvider = AopProxyUtils.getSingletonTarget(fileToolProvider);
        Object rawTodoWriteToolProvider = AopProxyUtils.getSingletonTarget(todoWriteToolProvider);
        Object rawSubAgentToolProvider = AopProxyUtils.getSingletonTarget(subAgentToolProvider);
        Object rawDagTaskToolProvider = AopProxyUtils.getSingletonTarget(dagTaskToolProvider);

        if (rawToolProvider == null) rawToolProvider = toolProvider;
        if (rawBashToolProvider == null) rawBashToolProvider = bashToolProvider;
        if (rawFileToolProvider == null) rawFileToolProvider = fileToolProvider;
        if (rawTodoWriteToolProvider == null) rawTodoWriteToolProvider = todoWriteToolProvider;
        if (rawSubAgentToolProvider == null) rawSubAgentToolProvider = subAgentToolProvider;
        if (rawDagTaskToolProvider == null) rawDagTaskToolProvider = dagTaskToolProvider;

        // 构建共享的AI模型实例，使用ChatMemoryProvider实现多会话隔离
        // memoryId通过AiChatModel.chat(@MemoryId sessionId, message)传递
        // 使用 OpenAI 兼容模式（阿里云 DashScope）
        this.sharedModel = AiServices.builder(AiChatModel.class)
                .chatModel(openaiChatModel)
                .tools(rawToolProvider)
                .tools(rawBashToolProvider)
                .tools(rawFileToolProvider)
                .tools(rawTodoWriteToolProvider)
                .tools(rawSubAgentToolProvider)
                .tools(rawDagTaskToolProvider)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(MAX_MESSAGES)
                        .chatMemoryStore(memoryStore)
                        .build())
                .build();

        logger.debug("共享AI模型实例初始化完成");
    }

    /**
     * 根据模型类型获取对应的 AI 模型
     * 返回共享的模型实例，memoryId通过chat方法参数传递
     *
     * @param modelType 模型类型 (openai/anthropic) - 当前仅使用openai
     * @param sessionId 会话ID（仅用于日志，实际通过@MemoryId传递）
     * @return AI 聊天模型
     */
    public AiChatModel getModel(String modelType, String sessionId) {
        logger.debug("获取AI模型: modelType={}, sessionId={}", modelType, sessionId);
        // 返回共享模型，memoryId在调用chat时通过@MemoryId传递
        return sharedModel;
    }

    /**
     * 获取默认的 AI 模型（无会话隔离）
     * 用于简单的独立任务执行
     */
    public AiChatModel getDefaultModel(String modelType) {
        Object rawToolProvider = AopProxyUtils.getSingletonTarget(toolProvider);
        Object rawBashToolProvider = AopProxyUtils.getSingletonTarget(bashToolProvider);
        Object rawFileToolProvider = AopProxyUtils.getSingletonTarget(fileToolProvider);
        Object rawTodoWriteToolProvider = AopProxyUtils.getSingletonTarget(todoWriteToolProvider);
        Object rawSubAgentToolProvider = AopProxyUtils.getSingletonTarget(subAgentToolProvider);
        Object rawDagTaskToolProvider = AopProxyUtils.getSingletonTarget(dagTaskToolProvider);

        if (rawToolProvider == null) rawToolProvider = toolProvider;
        if (rawBashToolProvider == null) rawBashToolProvider = bashToolProvider;
        if (rawFileToolProvider == null) rawFileToolProvider = fileToolProvider;
        if (rawTodoWriteToolProvider == null) rawTodoWriteToolProvider = todoWriteToolProvider;
        if (rawSubAgentToolProvider == null) rawSubAgentToolProvider = subAgentToolProvider;
        if (rawDagTaskToolProvider == null) rawDagTaskToolProvider = dagTaskToolProvider;

        return AiServices.builder(AiChatModel.class)
                .chatModel(openaiChatModel)
                .tools(rawToolProvider)
                .tools(rawBashToolProvider)
                .tools(rawFileToolProvider)
                .tools(rawTodoWriteToolProvider)
                .tools(rawSubAgentToolProvider)
                .tools(rawDagTaskToolProvider)
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
        Object rawFileToolProvider = AopProxyUtils.getSingletonTarget(fileToolProvider);
        Object rawTodoWriteToolProvider = AopProxyUtils.getSingletonTarget(todoWriteToolProvider);
        Object rawSubAgentToolProvider = AopProxyUtils.getSingletonTarget(subAgentToolProvider);
        Object rawDagTaskToolProvider = AopProxyUtils.getSingletonTarget(dagTaskToolProvider);

        Class<?> toolProviderClass = rawToolProvider != null ?
            rawToolProvider.getClass() : toolProvider.getClass();
        Class<?> bashToolProviderClass = rawBashToolProvider != null ?
            rawBashToolProvider.getClass() : bashToolProvider.getClass();
        Class<?> fileToolProviderClass = rawFileToolProvider != null ?
            rawFileToolProvider.getClass() : fileToolProvider.getClass();
        Class<?> todoWriteToolProviderClass = rawTodoWriteToolProvider != null ?
            rawTodoWriteToolProvider.getClass() : todoWriteToolProvider.getClass();
        Class<?> subAgentToolProviderClass = rawSubAgentToolProvider != null ?
            rawSubAgentToolProvider.getClass() : subAgentToolProvider.getClass();
        Class<?> dagTaskToolProviderClass = rawDagTaskToolProvider != null ?
            rawDagTaskToolProvider.getClass() : dagTaskToolProvider.getClass();

        for (Method method : toolProviderClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) count++;
        }
        for (Method method : bashToolProviderClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) count++;
        }
        for (Method method : fileToolProviderClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) count++;
        }
        for (Method method : todoWriteToolProviderClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) count++;
        }
        for (Method method : subAgentToolProviderClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) count++;
        }
        for (Method method : dagTaskToolProviderClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) count++;
        }

        return count;
    }
}