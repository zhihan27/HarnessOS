package com.harness.core.controller;

import com.harness.core.service.AgentService;
import com.harness.core.tool.TodoWriteToolProvider;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Harness Agent 控制器
 * 负责请求路由，业务逻辑由 AgentService 处理
 */
@RestController
@RequestMapping("/api/agent")
public class HarnessController {

    private final AgentService agentService;

    public HarnessController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * AI 聊天接口
     */
    @PostMapping("chat")
    public AgentService.ChatResult chat(@RequestBody ChatRequest request) {
        // 设置 Todo 工具的会话上下文
        String sessionId = generateSessionId();
        TodoWriteToolProvider.setSessionContext("default-tenant", "default-user", sessionId);

        try {
            // 执行 AI 对话
            return agentService.chat(request.message(), request.modelType());
        } finally {
            // 清除上下文
            TodoWriteToolProvider.clearSessionContext();
        }
    }

    /**
     * 生成会话 ID（实际应从请求头或 Token 中获取）
     */
    private String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 聊天请求 DTO
     */
    public record ChatRequest(String message, String modelType) {}
}