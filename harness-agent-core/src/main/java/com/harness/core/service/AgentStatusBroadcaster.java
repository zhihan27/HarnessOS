package com.harness.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harness.core.entity.AgentInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Agent 状态广播服务
 *
 * 使用 SSE (Server-Sent Events) 向前端推送 Agent 状态变化
 */
@Service
public class AgentStatusBroadcaster {

    private static final Logger logger = LoggerFactory.getLogger(AgentStatusBroadcaster.class);

    // SSE 连接列表
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建 SSE 连接
     */
    public SseEmitter createConnection() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            logger.info("SSE 连接关闭");
        });

        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            logger.warn("SSE 连接超时");
        });

        emitter.onError(e -> {
            emitters.remove(emitter);
            logger.error("SSE 连接错误: {}", e.getMessage());
        });

        emitters.add(emitter);

        logger.info("新建 SSE 连接，当前连接数: {}", emitters.size());

        return emitter;
    }

    /**
     * 广播 Agent 注册事件
     */
    public void broadcastAgentRegistered(AgentInstance agent) {
        AgentStatusEvent event = new AgentStatusEvent(
                "AGENT_REGISTERED",
                agent.getAgentId(),
                agent.getAgentType(),
                agent.getAgentName(),
                agent.getStatus(),
                agent.getCurrentTaskId(),
                agent.getCurrentLoad(),
                agent.getMaxConcurrency()
        );
        broadcastEvent(event);
    }

    /**
     * 广播 Agent 状态变更
     */
    public void broadcastAgentStatusChanged(AgentInstance agent) {
        AgentStatusEvent event = new AgentStatusEvent(
                "AGENT_STATUS_CHANGED",
                agent.getAgentId(),
                agent.getAgentType(),
                agent.getAgentName(),
                agent.getStatus(),
                agent.getCurrentTaskId(),
                agent.getCurrentLoad(),
                agent.getMaxConcurrency()
        );
        broadcastEvent(event);
    }

    /**
     * 广播 Agent 停止事件
     */
    public void broadcastAgentStopped(AgentInstance agent) {
        AgentStatusEvent event = new AgentStatusEvent(
                "AGENT_STOPPED",
                agent.getAgentId(),
                agent.getAgentType(),
                agent.getAgentName(),
                "STOPPED",
                null,
                0,
                agent.getMaxConcurrency()
        );
        broadcastEvent(event);
    }

    /**
     * 广播任务分配事件
     */
    public void broadcastTaskAssigned(String agentId, String taskId, String taskSubject) {
        TaskAssignmentEvent event = new TaskAssignmentEvent(
                "TASK_ASSIGNED",
                agentId,
                taskId,
                taskSubject
        );
        broadcastEvent(event);
    }

    /**
     * 广播任务完成事件
     */
    public void broadcastTaskCompleted(String agentId, String taskId, boolean success, String result) {
        TaskAssignmentEvent event = new TaskAssignmentEvent(
                "TASK_COMPLETED",
                agentId,
                taskId,
                success ? "成功: " + truncate(result, 100) : "失败"
        );
        broadcastEvent(event);
    }

    /**
     * 广播 Session 所有任务完成事件
     * 用于通知 MainAgent 可以进行结果汇总
     */
    public void broadcastSessionCompleted(String sessionId, long successCount, long failedCount) {
        SessionCompletionEvent event = new SessionCompletionEvent(
                "SESSION_COMPLETED",
                sessionId,
                successCount,
                failedCount
        );
        broadcastEvent(event);
    }

    /**
     * 广播汇总完成事件
     * 当 MainAgent 完成所有子任务的结果汇总后触发
     */
    public void broadcastSummaryCompleted(String sessionId, String summary) {
        SummaryCompletedEvent event = new SummaryCompletedEvent(
                "SUMMARY_COMPLETED",
                sessionId,
                summary
        );
        broadcastEvent(event);
    }

    /**
     * 广播事件到所有连接
     */
    private void broadcastEvent(Object event) {
        if (emitters.isEmpty()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(event);

            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("agent-status")
                            .data(json));
                } catch (IOException e) {
                    emitters.remove(emitter);
                    logger.warn("发送 SSE 消息失败，移除连接: {}", e.getMessage());
                }
            }

            logger.debug("广播事件到 {} 个连接: {}", emitters.size(), event.getClass().getSimpleName());

        } catch (Exception e) {
            logger.error("序列化事件失败: {}", e.getMessage());
        }
    }

    /**
     * 发送初始状态给新连接
     */
    public void sendInitialState(SseEmitter emitter, List<AgentInstance> agents) {
        try {
            for (AgentInstance agent : agents) {
                AgentStatusEvent event = new AgentStatusEvent(
                        "AGENT_REGISTERED",
                        agent.getAgentId(),
                        agent.getAgentType(),
                        agent.getAgentName(),
                        agent.getStatus(),
                        agent.getCurrentTaskId(),
                        agent.getCurrentLoad(),
                        agent.getMaxConcurrency()
                );
                emitter.send(SseEmitter.event()
                        .name("agent-status")
                        .data(objectMapper.writeValueAsString(event)));
            }
        } catch (IOException e) {
            logger.warn("发送初始状态失败: {}", e.getMessage());
        }
    }

    /**
     * 获取当前连接数
     */
    public int getConnectionCount() {
        return emitters.size();
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }

    // ========== 事件数据结构 ==========

    /**
     * Agent 状态事件
     */
    public record AgentStatusEvent(
            String eventType,
            String agentId,
            String agentType,
            String agentName,
            String status,
            String currentTaskId,
            int currentLoad,
            int maxConcurrency
    ) {}

    /**
     * 任务分配事件
     */
    public record TaskAssignmentEvent(
            String eventType,
            String agentId,
            String taskId,
            String info
    ) {}

    /**
     * Session 完成事件（所有任务完成时触发）
     */
    public record SessionCompletionEvent(
            String eventType,
            String sessionId,
            long successCount,
            long failedCount
    ) {}

    /**
     * 汇总完成事件（MainAgent 完成结果汇总时触发）
     */
    public record SummaryCompletedEvent(
            String eventType,
            String sessionId,
            String summary
    ) {}
}