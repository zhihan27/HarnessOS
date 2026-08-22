package com.harness.core.service;

import com.harness.core.memory.DatabaseChatMemoryStore;
import com.harness.core.model.AiChatModel;
import com.harness.core.tool.*;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.stereotype.Service;

/**
 * AI 服务工厂
 * 统一管理 OpenAI 模型 (DeepSeek OpenAI 兼容协议)
 */
@Service
public class AiServiceFactory {

    private static final Logger logger = LoggerFactory.getLogger(AiServiceFactory.class);

    private final OpenAiStreamingChatModel openaiStreamingModel;

    private final ToolProvider toolProvider;
    private final BashToolProvider bashToolProvider;
    private final FileToolProvider fileToolProvider;
    private final TodoWriteToolProvider todoWriteToolProvider;
    private final SubAgentToolProvider subAgentToolProvider;
    private final DagTaskToolProvider dagTaskToolProvider;
    private final DatabaseChatMemoryStore memoryStore;

    private static final int MAX_MESSAGES = 100;

    private AiChatModel openaiModel;

    public AiServiceFactory(
            OpenAiStreamingChatModel openaiStreamingModel,
            ToolProvider toolProvider,
            BashToolProvider bashToolProvider,
            FileToolProvider fileToolProvider,
            TodoWriteToolProvider todoWriteToolProvider,
            SubAgentToolProvider subAgentToolProvider,
            DagTaskToolProvider dagTaskToolProvider,
            DatabaseChatMemoryStore memoryStore) {
        this.openaiStreamingModel = openaiStreamingModel;
        this.toolProvider = toolProvider;
        this.bashToolProvider = bashToolProvider;
        this.fileToolProvider = fileToolProvider;
        this.todoWriteToolProvider = todoWriteToolProvider;
        this.subAgentToolProvider = subAgentToolProvider;
        this.dagTaskToolProvider = dagTaskToolProvider;
        this.memoryStore = memoryStore;

        logger.info("初始化 AI 服务工厂（OpenAI 单模型版本）...");
        initModels();
        logger.info("AI 服务工厂初始化完成，已注册 {} 个工具", getToolCount());
    }

    private void initModels() {
        // 解析 AOP 代理对象，获取真实的 Tool 实例
        // 如果代理解析失败，直接使用注入的 Bean
        ToolProvider rawToolProvider = resolveProxy(toolProvider, ToolProvider.class);
        BashToolProvider rawBashToolProvider = resolveProxy(bashToolProvider, BashToolProvider.class);
        FileToolProvider rawFileToolProvider = resolveProxy(fileToolProvider, FileToolProvider.class);
        TodoWriteToolProvider rawTodoWriteToolProvider = resolveProxy(todoWriteToolProvider, TodoWriteToolProvider.class);
        SubAgentToolProvider rawSubAgentToolProvider = resolveProxy(subAgentToolProvider, SubAgentToolProvider.class);
        DagTaskToolProvider rawDagTaskToolProvider = resolveProxy(dagTaskToolProvider, DagTaskToolProvider.class);

        // 初始化 OpenAI 模型
        this.openaiModel = AiServices.builder(AiChatModel.class)
                .streamingChatModel(openaiStreamingModel)
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
    }

    /**
     * 解析 AOP 代理，如果失败则返回原始 Bean
     */
    @SuppressWarnings("unchecked")
    private <T> T resolveProxy(T proxy, Class<T> type) {
        if (proxy == null) {
            logger.warn("代理对象为 null: {}", type.getSimpleName());
            return null;
        }
        try {
            Object target = AopProxyUtils.getSingletonTarget(proxy);
            if (target != null) {
                logger.debug("成功解析 AOP 代理: {} -> {}", type.getSimpleName(), target.getClass().getSimpleName());
                return (T) target;
            } else {
                logger.debug("AOP 代理解析返回 null，使用原始 Bean: {}", type.getSimpleName());
                return proxy;
            }
        } catch (Exception e) {
            logger.warn("解析 AOP 代理失败，使用原始 Bean: {} - {}", type.getSimpleName(), e.getMessage());
            return proxy;
        }
    }

    /**
     * 获取模型（兼容旧代码，modelType 参数被忽略）
     */
    public AiChatModel getModel(String modelType) {
        // 统一返回 OpenAI 模型
        return openaiModel;
    }

    /**
     * 获取默认模型（OpenAI）
     */
    public AiChatModel getDefaultModel() {
        return openaiModel;
    }

    /**
     * 获取工具数量
     */
    private int getToolCount() {
        int count = 0;
        if (toolProvider != null) count++;
        if (bashToolProvider != null) count++;
        if (fileToolProvider != null) count++;
        if (todoWriteToolProvider != null) count++;
        if (subAgentToolProvider != null) count++;
        if (dagTaskToolProvider != null) count++;
        return count;
    }
}