package com.harness.core.service;

import com.harness.core.tool.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Agent 业务服务
 * 封装 AI 交互的业务逻辑，集成 Agent Loop 和 Tool Use
 */
@Service
public class AgentService {

    private static final Logger logger = LoggerFactory.getLogger(AgentService.class);
    private static final int MAX_ITERATIONS = 10;

    private final AiServiceFactory aiServiceFactory;
    private final ToolProvider toolProvider;

    public AgentService(AiServiceFactory aiServiceFactory, ToolProvider toolProvider) {
        this.aiServiceFactory = aiServiceFactory;
        this.toolProvider = toolProvider;
    }

    /**
     * 执行 AI 对话（支持 Agent Loop 和 Tool Use）
     *
     * LangChain4j 自动处理 Agent Loop：
     * 1. 用户发送消息
     * 2. AI 决定是否需要调用工具
     * 3. 如果需要，执行工具并返回结果给 AI
     * 4. AI 根据工具结果继续思考
     * 5. 循环直到 AI 不再需要调用工具，返回最终答案
     *
     * @param message   用户消息
     * @param modelType 模型类型
     * @return AI 响应结果
     */
    public ChatResult chat(String message, String modelType) {
        logger.info("收到用户消息: {}, 模型类型: {}", message, modelType);
        logger.info("已注册工具数量: {}, 最大迭代次数: {}",
                    aiServiceFactory.getToolCount(), MAX_ITERATIONS);

        try {
            // 获取 AI 服务（已集成 Tool Use 和 ChatMemory）
            AiChatService service = aiServiceFactory.getService(modelType);

            // LangChain4j 自动执行 Agent Loop
            logger.debug("开始 Agent Loop 执行...");
            String response = service.chat(message);
            logger.debug("Agent Loop 执行完成");

            logger.info("AI 响应成功: {}", response);

            return new ChatResult(
                    true,
                    response,
                    modelType != null ? modelType : "openai"
            );

        } catch (Exception e) {
            logger.error("Agent 执行失败: {}", e.getMessage(), e);

            return new ChatResult(
                    false,
                    "Agent 执行失败: " + e.getMessage(),
                    modelType != null ? modelType : "openai"
            );
        }
    }

    /**
     * 获取已注册的工具信息
     */
    public ToolInfo getToolInfo() {
        return new ToolInfo(
                aiServiceFactory.getToolCount(),
                MAX_ITERATIONS
        );
    }

    /**
     * 聊天结果 DTO
     */
    public record ChatResult(boolean success, String message, String modelType) {}

    /**
     * 工具信息 DTO
     */
    public record ToolInfo(int toolCount, int maxIterations) {}
}