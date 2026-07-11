package com.harness.core.service;

import com.harness.core.entity.AgentTodoTask;
import com.harness.core.hook.ChatContext;
import com.harness.core.hook.ChatHookExecutor;
import com.harness.core.tool.DagTaskToolProvider;
import com.harness.core.tool.SubAgentToolProvider;
import com.harness.core.tool.TodoWriteToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Agent 服务
 * 封装完整的对话流程：会话管理 + 任务跟踪 + AI调用
 */
@Service
public class AgentService {

    private static final Logger logger = LoggerFactory.getLogger(AgentService.class);

    private final ChatHookExecutor hookExecutor;
    private final AiServiceFactory aiServiceFactory;
    private final ChatSessionService chatSessionService;
    private final AgentTodoTaskService todoTaskService;

    public AgentService(ChatHookExecutor hookExecutor,
                        AiServiceFactory aiServiceFactory,
                        ChatSessionService chatSessionService,
                        AgentTodoTaskService todoTaskService) {
        this.hookExecutor = hookExecutor;
        this.aiServiceFactory = aiServiceFactory;
        this.chatSessionService = chatSessionService;
        this.todoTaskService = todoTaskService;
    }

    /**
     * 完整的对话流程
     * 包含：会话管理、任务跟踪、AI调用
     */
    public ChatResult chat(String tenantId, String userId, String sessionId, String message) {
        // 1. 会话管理：获取或创建会话
        SessionInfo sessionInfo = prepareSession(sessionId, message);

        // 2. 设置工具上下文
        TodoWriteToolProvider.setSessionContext(tenantId, userId, sessionInfo.sessionId);
        SubAgentToolProvider.setSessionContext(tenantId, userId, sessionInfo.sessionId);
        DagTaskToolProvider.setSessionContext(tenantId, userId, sessionInfo.sessionId);

        try {
            // 3. 创建跟踪任务
            AgentTodoTask trackingTask = todoTaskService.createTask(
                    tenantId, userId, sessionInfo.sessionId, message, null
            );
            logger.info("创建跟踪任务: id={}", trackingTask.getId());

            // 4. 前置hook执行
            ChatContext context = new ChatContext(tenantId, userId, sessionInfo.sessionId, message);
            hookExecutor.executeBefore(context);

            if (!context.isSuccess()) {
                return new ChatResult(false, context.getResult(), sessionInfo.sessionId);
            }

            String aiResponse = callAI(sessionInfo.sessionId, message);
            context.setResult(aiResponse);

            // 5.后置hook执行
            hookExecutor.executeAfter(context);

            // 6. 标记跟踪任务完成
            todoTaskService.markCompleted(trackingTask.getId());

            logger.info("Chat完成: success={}", context.isSuccess());
            return new ChatResult(context.isSuccess(), context.getResult(), sessionInfo.sessionId);

        } finally {
            // 6. 清理工具上下文
            TodoWriteToolProvider.clearSessionContext();
            SubAgentToolProvider.clearSessionContext();
            DagTaskToolProvider.clearSessionContext();
        }
    }

    /**
     * 准备会话：获取或创建，并设置标题
     */
    private SessionInfo prepareSession(String sessionId, String message) {
        boolean isNewSession = false;

        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = generateSessionId();
            chatSessionService.createSessionWithId(sessionId, "default-tenant", "default-user");
            isNewSession = true;
            logger.info("创建新会话: sessionId={}", sessionId);
        } else if (!chatSessionService.existsSession(sessionId)) {
            chatSessionService.createSessionWithId(sessionId, "default-tenant", "default-user");
            isNewSession = true;
            logger.info("会话不存在，创建新会话: sessionId={}", sessionId);
        }

        // 新会话设置标题
        if (isNewSession) {
            String title = generateTitle(message);
            chatSessionService.updateSessionTitle(sessionId, title);
            logger.info("设置会话标题: sessionId={}, title={}", sessionId, title);
        }

        return new SessionInfo(sessionId, isNewSession);
    }

    /**
     * 调用AI服务
     */
    private String callAI(String sessionId, String message) {
        logger.info("调用AI: sessionId={}", sessionId);
        AiChatService aiService = aiServiceFactory.getService("openai", sessionId);
        return aiService.chat(sessionId, message);
    }

    /**
     * 生成会话ID
     */
    private String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 生成会话标题
     */
    private String generateTitle(String message) {
        if (message == null || message.isEmpty()) {
            return "新对话";
        }
        String title = message.replaceAll("[\\r\\n\\t]", " ").trim();
        if (title.length() > 20) {
            title = title.substring(0, 20) + "...";
        }
        return title;
    }

    // ========== 内部记录 ==========

    private record SessionInfo(String sessionId, boolean isNewSession) {}

    public record ChatResult(boolean success, String message, String sessionId) {}
}