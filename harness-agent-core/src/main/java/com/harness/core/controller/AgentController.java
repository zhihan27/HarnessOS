package com.harness.core.controller;

import com.harness.core.entity.AgentInstance;
import com.harness.core.entity.DagTask;
import com.harness.core.service.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Agent 管理控制器
 *
 * 提供：
 * - Agent 注册/停止
 * - Agent 状态查询
 * - MainAgent 任务拆解
 * - SSE 状态流（Agent 状态 + 工具进度）
 *
 * 注意：使用 /api/agent-mgr 路径，避免与 HarnessController 的 /api/agent/sessions 路由冲突
 */
@RestController
@RequestMapping("/api/agent-mgr")
public class AgentController {

    private final AgentRegistryService registryService;
    private final MainAgentService mainAgentService;
    private final WorkerAgentService workerAgentService;
    private final AgentStatusBroadcaster broadcaster;
    private final ToolProgressBroadcaster toolProgressBroadcaster;

    public AgentController(AgentRegistryService registryService,
                           MainAgentService mainAgentService,
                           WorkerAgentService workerAgentService,
                           AgentStatusBroadcaster broadcaster,
                           ToolProgressBroadcaster toolProgressBroadcaster) {
        this.registryService = registryService;
        this.mainAgentService = mainAgentService;
        this.workerAgentService = workerAgentService;
        this.broadcaster = broadcaster;
        this.toolProgressBroadcaster = toolProgressBroadcaster;
    }

    // ==================== Agent 注册与管理 ====================

    /**
     * 注册新 Agent
     */
    @PostMapping("register")
    public AgentResponse registerAgent(@RequestBody AgentRegistrationRequest request) {
        AgentInstance agent = registryService.registerAgent(
                request.agentType(),
                request.agentName(),
                request.tenantId() != null ? request.tenantId() : "default-tenant",
                request.userId() != null ? request.userId() : "default-user",
                request.maxConcurrency() != null ? request.maxConcurrency() : 1,
                request.capabilities()
        );

        // Worker 自动启动执行循环
        if ("WORKER".equals(agent.getAgentType())) {
            workerAgentService.startWorkerLoop(agent.getAgentId());
        }

        // 广播注册事件
        broadcaster.broadcastAgentRegistered(agent);

        return toAgentResponse(agent);
    }

    /**
     * 获取所有 Agent
     */
    @GetMapping("list")
    public List<AgentResponse> listAgents(@RequestParam(required = false) String type) {
        List<AgentInstance> agents = type != null
                ? registryService.getAgentsByType(type)
                : registryService.getActiveAgents();

        return agents.stream().map(this::toAgentResponse).toList();
    }

    /**
     * 获取 Agent 详情
     */
    @GetMapping("{agentId}")
    public AgentResponse getAgent(@PathVariable String agentId) {
        AgentInstance agent = registryService.getAgent(agentId);
        return agent != null ? toAgentResponse(agent) : null;
    }

    /**
     * 停止 Agent
     */
    @PostMapping("{agentId}/stop")
    public AgentResponse stopAgent(@PathVariable String agentId) {
        AgentInstance agent = registryService.getAgent(agentId);

        if (agent == null) {
            return null;
        }

        // Worker 需要停止执行循环
        if ("WORKER".equals(agent.getAgentType())) {
            workerAgentService.stopWorker(agentId);
        } else {
            registryService.stopAgent(agentId);
        }

        // 广播停止事件
        AgentInstance stoppedAgent = registryService.getAgent(agentId);
        if (stoppedAgent != null) {
            broadcaster.broadcastAgentStopped(stoppedAgent);
        }

        return toAgentResponse(stoppedAgent);
    }

    // ==================== MainAgent 任务拆解 ====================

    /**
     * MainAgent 拆解复杂任务
     */
    @PostMapping("{agentId}/decompose")
    public DecompositionResponse decomposeTask(
            @PathVariable String agentId,
            @RequestBody DecompositionRequest request) {

        MainAgentService.DecompositionResult result = mainAgentService.decomposeComplexTask(
                agentId,
                request.taskDescription(),
                request.sessionId()
        );

        if (result.isSuccess()) {
            // 广播状态变更
            AgentInstance agent = registryService.getAgent(agentId);
            broadcaster.broadcastAgentStatusChanged(agent);

            return new DecompositionResponse(
                    true,
                    result.createdTasks().size(),
                    result.createdTasks().stream().map(DagTask::getTaskId).toList(),
                    null
            );
        } else {
            return new DecompositionResponse(false, 0, null, result.error());
        }
    }

    // ==================== SSE 状态流 ====================

    /**
     * SSE 连接获取实时 Agent 状态
     */
    @GetMapping(value = "status/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAgentStatus() {
        SseEmitter emitter = broadcaster.createConnection();

        // 发送当前所有 Agent 的初始状态
        List<AgentInstance> agents = registryService.getActiveAgents();
        broadcaster.sendInitialState(emitter, agents);

        return emitter;
    }

    /**
     * 获取 SSE 连接数
     */
    @GetMapping("status/connections")
    public ConnectionCountResponse getConnectionCount() {
        return new ConnectionCountResponse(broadcaster.getConnectionCount());
    }

    // ==================== 工具执行进度 SSE ====================

    /**
     * SSE 连接获取实时工具执行进度
     * 推送工具调用开始、完成、错误等事件
     */
    @GetMapping(value = "tools/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamToolProgress() {
        return toolProgressBroadcaster.createConnection();
    }

    /**
     * 获取工具进度 SSE 连接数
     */
    @GetMapping("tools/connections")
    public ConnectionCountResponse getToolProgressConnectionCount() {
        return new ConnectionCountResponse(toolProgressBroadcaster.getConnectionCount());
    }

    // ==================== Worker 控制 ====================

    /**
     * 手动触发 Worker 领取任务
     */
    @PostMapping("{agentId}/claim")
    public TaskClaimResponse claimTask(@PathVariable String agentId) {
        DagTask task = registryService.claimTaskForWorker(agentId);

        if (task != null) {
            // 广播任务分配
            broadcaster.broadcastTaskAssigned(agentId, task.getTaskId(), task.getSubject());

            return new TaskClaimResponse(true, task.getTaskId(), task.getSubject());
        } else {
            return new TaskClaimResponse(false, null, "无可用任务或 Agent 已满载");
        }
    }

    // ==================== Helper ====================

    private AgentResponse toAgentResponse(AgentInstance agent) {
        return new AgentResponse(
                agent.getAgentId(),
                agent.getAgentType(),
                agent.getAgentName(),
                agent.getStatus(),
                agent.getCurrentTaskId(),
                agent.getMaxConcurrency(),
                agent.getCurrentLoad(),
                agent.getCapabilities(),
                agent.getSessionId(),
                agent.getStartedAt(),
                agent.getLastActiveAt()
        );
    }

    // ==================== Request/Response Records ====================

    public record AgentRegistrationRequest(
            String agentType,
            String agentName,
            String tenantId,
            String userId,
            Integer maxConcurrency,
            List<String> capabilities
    ) {}

    public record AgentResponse(
            String agentId,
            String agentType,
            String agentName,
            String status,
            String currentTaskId,
            int maxConcurrency,
            int currentLoad,
            String capabilities,
            String sessionId,
            java.time.LocalDateTime startedAt,
            java.time.LocalDateTime lastActiveAt
    ) {}

    public record DecompositionRequest(
            String taskDescription,
            String sessionId
    ) {}

    public record DecompositionResponse(
            boolean success,
            int taskCount,
            List<String> taskIds,
            String error
    ) {}

    public record TaskClaimResponse(
            boolean success,
            String taskId,
            String message
    ) {}

    public record ConnectionCountResponse(int count) {}
}