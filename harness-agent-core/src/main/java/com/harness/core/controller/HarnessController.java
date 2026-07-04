package com.harness.core.controller;

import com.harness.core.entity.AgentTodoTask;
import com.harness.core.service.AgentService;
import com.harness.core.service.AgentTodoTaskService;
import com.harness.core.tool.SubAgentToolProvider;
import com.harness.core.tool.TodoWriteToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Harness Agent 控制器
 * 负责请求路由，业务逻辑由 AgentService 处理
 *
 * 系统级任务管理：
 * - 用户请求到达时，系统自动创建"跟踪任务"
 * - AI 执行过程中可以细化任务
 * - AgentService 系统级保障任务完整性
 */
@RestController
@RequestMapping("/api/agent")
public class HarnessController {

    private static final Logger logger = LoggerFactory.getLogger(HarnessController.class);

    private final AgentService agentService;
    private final AgentTodoTaskService todoTaskService;

    public HarnessController(AgentService agentService,
                             AgentTodoTaskService todoTaskService) {
        this.agentService = agentService;
        this.todoTaskService = todoTaskService;
    }

    /**
     * AI 聊天接口
     *
     * 系统级任务管理流程：
     * 1. 生成 sessionId，设置上下文
     * 2. 系统自动创建"跟踪任务"（用于强制保障）
     * 3. AI 执行对话
     * 4. AgentService 检查未完成任务，强制继续
     */
    @PostMapping("chat")
    public AgentService.ChatResult chat(@RequestBody ChatRequest request) {
        // 设置会话上下文
        String sessionId = generateSessionId();
        TodoWriteToolProvider.setSessionContext("default-tenant", "default-user", sessionId);
        SubAgentToolProvider.setSessionContext("default-tenant", "default-user", sessionId);

        try {
            // 系统自动创建"跟踪任务"（代码级强制，不依赖 AI）
            AgentTodoTask trackingTask = todoTaskService.createTask(
                "default-tenant",
                "default-user",
                sessionId,
                request.message(),  // 用户原始请求作为任务描述
                null
            );
            logger.info("系统自动创建跟踪任务: id={}, sessionId={}", trackingTask.getId(), sessionId);

            // 执行 AI 对话
            return agentService.chat(request.message(), request.modelType());

        } finally {
            // 清除上下文
            TodoWriteToolProvider.clearSessionContext();
            SubAgentToolProvider.clearSessionContext();
        }
    }

    /**
     * 生成会话 ID
     */
    private String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 聊天请求 DTO
     */
    public record ChatRequest(String message, String modelType) {}
}