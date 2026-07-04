package com.harness.core.tool;

import com.harness.core.entity.AgentTodoTask;
import com.harness.core.service.AgentTodoTaskService;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Todo 工具提供者（秘书模式）
 * 极简架构：只保留三个核心方法
 * - addTodo：添加任务计划
 * - listTodo：查看任务清单
 * - finishTodo：标记完成
 */
@Component
public class TodoWriteToolProvider {

    private static final Logger logger = LoggerFactory.getLogger(TodoWriteToolProvider.class);

    private final AgentTodoTaskService taskService;
    // 当前会话上下文（由拦截器注入）
    private static final ThreadLocal<SessionContext> currentSession = new ThreadLocal<>();

    public TodoWriteToolProvider(AgentTodoTaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 设置当前会话上下文（由拦截器调用）
     */
    public static void setSessionContext(String tenantId, String userId, String sessionId) {
        currentSession.set(new SessionContext(tenantId, userId, sessionId));
        logger.debug("设置会话上下文: tenant={}, user={}, session={}", tenantId, userId, sessionId);
    }

    /**
     * 清除当前会话上下文
     */
    public static void clearSessionContext() {
        currentSession.remove();
        logger.debug("清除会话上下文");
    }

    /**
     * 获取当前会话 ID（供外部调用）
     */
    public static String getCurrentSessionId() {
        SessionContext context = currentSession.get();
        return context != null ? context.sessionId : null;
    }

    /**
     * 获取当前会话上下文
     */
    private SessionContext getSessionContext() {
        SessionContext context = currentSession.get();
        if (context == null) {
            throw new IllegalStateException("未设置会话上下文，请先调用 setSessionContext");
        }
        return context;
    }

    /**
     * 添加任务计划（核心方法）
     *
     * @param description 任务描述（如："Plan: 1.读取配置, 2.修改配置, 3.重启服务"）
     * @return 任务创建结果
     */
    @Tool("添加任务计划到清单。参数：description（任务描述，可以是多步骤计划）")
    public String addTodo(String description) {
        try {
            SessionContext context = getSessionContext();

            logger.info("添加任务: session={}, description={}", context.sessionId, description);

            AgentTodoTask task = taskService.createTask(
                    context.tenantId,
                    context.userId,
                    context.sessionId,
                    description,
                    null
            );

            return String.format("任务已添加 [ID: %d]\n描述: %s", task.getId(), task.getTaskDescription());

        } catch (Exception e) {
            logger.error("添加任务失败: {}", e.getMessage(), e);
            return "任务添加失败: " + e.getMessage();
        }
    }

    /**
     * 查看任务清单（核心方法）
     *
     * @return 当前会话的任务清单
     */
    @Tool("查看当前会话的任务清单，帮助 AI 恢复记忆，防止跑偏")
    public String listTodo() {
        try {
            SessionContext context = getSessionContext();

            List<AgentTodoTask> tasks = taskService.getAllTasks(context.sessionId);

            if (tasks.isEmpty()) {
                return "当前会话没有任务清单";
            }

            String taskList = tasks.stream()
                    .map(t -> String.format("- [%s] ID:%d | %s",
                            "已完成".equals(t.getStatus()) ? "✅" : "⏳",
                            t.getId(),
                            t.getTaskDescription()))
                    .collect(Collectors.joining("\n"));

            return String.format("任务清单 (%d 个):\n%s", tasks.size(), taskList);

        } catch (Exception e) {
            logger.error("查询任务清单失败: {}", e.getMessage(), e);
            return "查询失败: " + e.getMessage();
        }
    }

    /**
     * 标记任务完成（核心方法）
     *
     * @param taskId 任务 ID
     * @return 标记结果
     */
    @Tool("标记任务已完成。参数：taskId（任务 ID）")
    public String finishTodo(Long taskId) {
        try {
            boolean success = taskService.markCompleted(taskId);

            if (success) {
                return String.format("任务已完成 [ID: %d] ✅", taskId);
            } else {
                return String.format("任务不存在或已完成 [ID: %d]", taskId);
            }

        } catch (Exception e) {
            logger.error("标记任务完成失败: {}", e.getMessage(), e);
            return "操作失败: " + e.getMessage();
        }
    }

    /**
     * 检查未完成任务（记忆锚点）
     *
     * AI 应在关键节点调用此 Tool：
     * - 完成一个任务步骤后
     * - 准备给出"任务完成"结论前
     *
     * 返回未完成任务列表，Agent Loop 自动继续执行
     */
    @Tool("检查是否有未完成的任务。返回未完成任务列表，如果有则继续执行。应在完成每个步骤后调用。")
    public String checkPendingTasks() {
        String sessionId = TodoWriteToolProvider.getCurrentSessionId();
        if (sessionId == null) {
            return "无会话上下文";
        }

        List<AgentTodoTask> pendingTasks = taskService.getPendingTasks(sessionId);

        if (pendingTasks.isEmpty()) {
            logger.info("所有任务已完成");
            return "✅ 所有任务已完成，可以给出最终结论。";
        }

        // 构造未完成任务提醒
        String taskList = pendingTasks.stream()
                .map(t -> String.format("- [待完成] ID:%d | %s", t.getId(), t.getTaskDescription()))
                .collect(Collectors.joining("\n"));

        String reminder = String.format(
                "⚠️ 还有 %d 个任务未完成：\n%s\n\n请继续执行这些任务。",
                pendingTasks.size(), taskList
        );

        logger.info("发现未完成任务: {}", pendingTasks.size());
        return reminder;
    }

    /**
     * 获取当前会话的任务完成进度
     */
    @Tool("获取当前会话的任务完成进度统计")
    public String getTaskProgress() {
        String sessionId = TodoWriteToolProvider.getCurrentSessionId();
        if (sessionId == null) {
            return "无会话上下文";
        }

        List<AgentTodoTask> allTasks = taskService.getAllTasks(sessionId);
        List<AgentTodoTask> pendingTasks = taskService.getPendingTasks(sessionId);
        List<AgentTodoTask> completedTasks = allTasks.stream()
                .filter(t -> "COMPLETED".equals(t.getStatus()))
                .collect(Collectors.toList());

        return String.format(
                "任务进度：已完成 %d/%d\n- 完成: %d\n- 待执行: %d",
                completedTasks.size(), allTasks.size(),
                completedTasks.size(), pendingTasks.size()
        );
    }

    /**
     * 会话上下文
     */
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