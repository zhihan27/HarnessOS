package com.harness.core.tool;

import com.harness.core.entity.SubAgentTask;
import com.harness.core.service.SubAgentService;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 子 Agent 工具提供者
 * 只提供创建子任务功能（执行由Hook链处理）
 */
@Component
public class SubAgentToolProvider {

    private static final Logger logger = LoggerFactory.getLogger(SubAgentToolProvider.class);

    private final SubAgentService subAgentService;

    private static final ThreadLocal<SessionContext> currentSession = new ThreadLocal<>();

    public SubAgentToolProvider(SubAgentService subAgentService) {
        this.subAgentService = subAgentService;
    }

    public static void setSessionContext(String tenantId, String userId, String sessionId) {
        currentSession.set(new SessionContext(tenantId, userId, sessionId));
        logger.debug("设置子 Agent 会话上下文: session={}", sessionId);
    }

    public static void clearSessionContext() {
        currentSession.remove();
    }

    private SessionContext getSessionContext() {
        SessionContext context = currentSession.get();
        if (context == null) {
            throw new IllegalStateException("未设置会话上下文");
        }
        return context;
    }

    /**
     * 创建子任务
     */
    @Tool("创建子任务。参数：taskType, description")
    public String spawnSubAgent(String taskType, String description) {
        try {
            SessionContext context = getSessionContext();

            SubAgentTask task = subAgentService.createSubTask(
                    context.tenantId, context.userId, context.sessionId,
                    taskType, description, null
            );

            return String.format("子任务已创建 [ID: %d]\n类型: %s\n描述: %s",
                    task.getId(), taskType, description);

        } catch (Exception e) {
            logger.error("创建子任务失败: {}", e.getMessage(), e);
            return "创建失败: " + e.getMessage();
        }
    }

    /**
     * 查询子任务状态
     */
    @Tool("查询子任务状态。参数：taskId")
    public String getSubAgentStatus(Long taskId) {
        try {
            SubAgentTask task = subAgentService.getSubTaskStatus(taskId);

            if (task == null) {
                return String.format("任务不存在 [ID: %d]", taskId);
            }

            return String.format(
                    "任务状态 [ID: %d]\n类型: %s\n状态: %s\n描述: %s\n错误: %s\n结果: %s",
                    taskId, task.getTaskType(), task.getStatus(),
                    task.getTaskDescription(),
                    task.getLastError() != null ? task.getLastError() : "无",
                    task.getResult() != null ? task.getResult() : "无"
            );

        } catch (Exception e) {
            return "查询失败: " + e.getMessage();
        }
    }

    /**
     * 列出当前会话的所有子任务
     */
    @Tool("列出当前会话的所有子任务")
    public String listSubAgents() {
        try {
            SessionContext context = getSessionContext();

            List<SubAgentTask> tasks = subAgentService.getSubTasksByParentSession(context.sessionId);

            if (tasks.isEmpty()) {
                return "当前会话没有子任务";
            }

            String taskList = tasks.stream()
                    .map(t -> String.format("- [%s] ID:%d | %s | %s",
                            getStatusIcon(t.getStatus()),
                            t.getId(), t.getTaskType(), t.getTaskDescription()))
                    .collect(Collectors.joining("\n"));

            return String.format("子任务列表 (%d 个):\n%s", tasks.size(), taskList);

        } catch (Exception e) {
            return "查询失败: " + e.getMessage();
        }
    }

    private String getStatusIcon(String status) {
        return switch (status) {
            case "PENDING" -> "待执行";
            case "RUNNING" -> "执行中";
            case "COMPLETED" -> "已完成";
            case "FAILED" -> "失败";
            default -> "未知";
        };
    }

    private static class SessionContext {
        final String tenantId;
        final String userId;
        final String sessionId;

        SessionContext(String tenantId, String userId, String sessionId) {
            this.tenantId = tenantId;
            this.userId = userId;
            this.sessionId = sessionId;
        }
    }
}