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
 * 提供任务拆分能力，执行时自动重试
 *
 * 关键设计：
 * 1. 父子上下文隔离：子 Agent 使用独立的 subAgentSessionId
 * 2. 自动重试：executeSubTask 内部自动重试，AI 无需关心重试细节
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
    @Tool("创建子任务，将复杂任务拆分给专门的子 Agent 执行。子 Agent 使用独立上下文。参数：taskType（RESEARCH/CODING/ANALYSIS/TESTING/DOCUMENTATION/GENERAL）, description（任务描述）")
    public String spawnSubAgent(String taskType, String description) {
        try {
            SessionContext context = getSessionContext();

            SubAgentTask task = subAgentService.createSubTask(
                    context.tenantId, context.userId, context.sessionId,
                    taskType, description, null
            );

            return String.format(
                    "子任务已创建 [ID: %d]\n类型: %s\n描述: %s\n请执行: executeSubAgent(%d)",
                    task.getId(), taskType, description, task.getId()
            );

        } catch (Exception e) {
            logger.error("创建子任务失败: {}", e.getMessage(), e);
            return "创建失败: " + e.getMessage();
        }
    }

    /**
     * 创建带输入参数的子任务
     */
    @Tool("创建带输入参数的子任务。参数：taskType, description, input（任务输入）")
    public String spawnSubAgentWithInput(String taskType, String description, String input) {
        try {
            SessionContext context = getSessionContext();

            SubAgentTask task = subAgentService.createSubTask(
                    context.tenantId, context.userId, context.sessionId,
                    taskType, description, input
            );

            return String.format(
                    "子任务已创建 [ID: %d]\n类型: %s\n描述: %s\n请执行: executeSubAgent(%d)",
                    task.getId(), taskType, description, task.getId()
            );

        } catch (Exception e) {
            return "创建失败: " + e.getMessage();
        }
    }

    /**
     * 执行子任务（自动重试）
     */
    @Tool("执行子任务并返回结果。失败时会自动重试（最多3次），无需手动干预。参数：taskId")
    public String executeSubAgent(Long taskId) {
        try {
            logger.info("执行子任务: id={}", taskId);

            SubAgentService.ExecuteResult result = subAgentService.executeSubTask(taskId);

            if (result.success()) {
                return String.format("任务成功 [ID: %d]\n重试次数: %d\n结果:\n%s",
                        taskId, result.retryCount(), result.result());
            } else {
                return String.format("任务失败 [ID: %d]\n已尝试: %d 次\n原因: %s",
                        taskId, result.retryCount(), result.result());
            }

        } catch (Exception e) {
            logger.error("执行子任务失败: {}", e.getMessage(), e);
            return "执行失败: " + e.getMessage();
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
                    "任务状态 [ID: %d]\n类型: %s\n状态: %s\n描述: %s\n重试: %d/%d\n错误: %s\n结果: %s",
                    taskId, task.getTaskType(), task.getStatus(),
                    task.getTaskDescription(),
                    task.getRetryCount(), task.getMaxRetries(),
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