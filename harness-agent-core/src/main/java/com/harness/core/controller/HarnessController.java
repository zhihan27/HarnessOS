package com.harness.core.controller;

import com.harness.core.service.AgentService;
import org.springframework.web.bind.annotation.*;

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
        return agentService.chat(request.message(), request.modelType());
    }

    /**
     * 聊天请求 DTO
     */
    public record ChatRequest(String message, String modelType) {}
}