package com.harness.core.service;

import com.harness.core.entity.AgentTodoTask;
import com.harness.core.tool.ToolProvider;
import com.harness.core.tool.TodoWriteToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 业务服务
 * 封装 AI 交互的业务逻辑，集成 Agent Loop 和 Tool Use
 *
 * 核心功能：系统级任务完整性保障
 * - Todo 作为记忆锚点，存储在数据库中
 * - AI 每次响应后，系统自动检查未完成任务
 * - 如果有未完成任务，系统注入自然语言 prompt 继续对话
 */
@Service
public class AgentService {

    private static final Logger logger = LoggerFactory.getLogger(AgentService.class);
    private static final int MAX_TASK_LOOP = 5;

    private final AiServiceFactory aiServiceFactory;
    private final ToolProvider toolProvider;
    private final AgentTodoTaskService todoTaskService;

    public AgentService(AiServiceFactory aiServiceFactory,
                        ToolProvider toolProvider,
                        AgentTodoTaskService todoTaskService) {
        this.aiServiceFactory = aiServiceFactory;
        this.toolProvider = toolProvider;
        this.todoTaskService = todoTaskService;
    }

    /**
     * 执行 AI 对话（系统级任务完整性保障）
     */
    public ChatResult chat(String message, String modelType) {
        logger.info("收到用户消息: {}", message);

        try {
            AiChatService service = aiServiceFactory.getService(modelType);

            // 执行对话
            String response = service.chat(message);
            logger.info("AI 第一次响应完成");

            // 系统级任务完整性检查
            response = ensureTaskCompletion(service, response);

            return new ChatResult(true, response, modelType != null ? modelType : "openai");

        } catch (Exception e) {
            logger.error("Agent 执行失败: {}", e.getMessage(), e);
            return new ChatResult(false, "Agent 执行失败: " + e.getMessage(), modelType != null ? modelType : "openai");
        }
    }

    /**
     * 系统级任务完整性保障
     *
     * 关键：注入自然语言 prompt，让 AI 自然回复用户
     */
    private String ensureTaskCompletion(AiChatService service, String response) {
        int loopCount = 0;

        while (loopCount < MAX_TASK_LOOP) {
            String sessionId = TodoWriteToolProvider.getCurrentSessionId();
            if (sessionId == null) {
                logger.debug("无会话上下文，跳过检查");
                return response;
            }

            List<AgentTodoTask> pendingTasks = todoTaskService.getPendingTasks(sessionId);
            if (pendingTasks.isEmpty()) {
                logger.info("✅ 任务完整性检查通过");
                return response;
            }

            loopCount++;
            logger.warn("⚠️ 发现 {} 个未完成任务，强制继续 (loop={})", pendingTasks.size(), loopCount);

            // 注入自然语言 prompt（让 AI 继续对话，而不是回复系统提醒）
            String continuePrompt = buildContinuePrompt(pendingTasks);
            logger.info("注入继续 prompt: {}", continuePrompt);

            response = service.chat(continuePrompt);
            logger.info("AI 继续响应完成");
        }

        return response;
    }

    /**
     * 构造自然语言继续 prompt
     *
     * 关键：使用自然语言，让 AI 继续回复用户，而不是回复系统提醒
     */
    private String buildContinuePrompt(List<AgentTodoTask> pendingTasks) {
        String taskDesc = pendingTasks.stream()
                .map(t -> t.getTaskDescription())
                .collect(Collectors.joining("; "));

        // 自然语言，让 AI 继续执行并回复用户
        return String.format(
            "请继续完成以下内容：%s。完成后调用 finishTodo(taskId) 标记，然后回复用户结果。",
            taskDesc
        );
    }

    public record ChatResult(boolean success, String message, String modelType) {}
}