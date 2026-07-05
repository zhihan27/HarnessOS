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
 */
@RestController
@RequestMapping("/api/agent")
public class HarnessController {

    private static final Logger logger = LoggerFactory.getLogger(HarnessController.class);

    private final AgentService agentService;
    private final AgentTodoTaskService todoTaskService;

    public HarnessController(AgentService agentService, AgentTodoTaskService todoTaskService) {
        this.agentService = agentService;
        this.todoTaskService = todoTaskService;
    }

    /**
     * Chat 接口
     * Hook链自动执行：安全→拆解→执行→完整性检查
     */
    @PostMapping("chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String sessionId = generateSessionId();

        // 设置上下文
        TodoWriteToolProvider.setSessionContext("default-tenant", "default-user", sessionId);
        SubAgentToolProvider.setSessionContext("default-tenant", "default-user", sessionId);

        try {
            // 创建跟踪任务
            AgentTodoTask trackingTask = todoTaskService.createTask(
                "default-tenant", "default-user", sessionId, request.message(), null
            );
            logger.info("创建跟踪任务: id={}", trackingTask.getId());

            // 调用AgentService（Hook链自动执行）
            AgentService.ChatResult result = agentService.chat(
                "default-tenant", "default-user", sessionId, request.message()
            );

            // 标记完成
            todoTaskService.markCompleted(trackingTask.getId());

            return new ChatResponse(result.success(), result.message(), sessionId);

        } finally {
            TodoWriteToolProvider.clearSessionContext();
            SubAgentToolProvider.clearSessionContext();
        }
    }

    private String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public record ChatRequest(String message) {}
    public record ChatResponse(boolean success, String message, String sessionId) {}
}