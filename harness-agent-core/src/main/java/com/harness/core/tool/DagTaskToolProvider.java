package com.harness.core.tool;

import com.harness.core.entity.DagTask;
import com.harness.core.service.DagDependencyResolver;
import com.harness.core.service.DagTaskService;
import com.harness.core.service.TaskDispatcher;
import com.harness.core.service.ToolProgressBroadcaster;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DAG 任务工具提供者（Task Team 任务看板系统）
 *
 * 核心能力：
 * 1. 任务编排：创建任务、设置依赖关系
 * 2. 异步执行：启动 Task Team 后台执行任务
 * 3. 状态追踪：查询任务执行进度
 * 4. SSE 进度推送：实时推送任务执行进度
 *
 * 与 SubAgent 的区别：
 * - SubAgent：同步阻塞，独立上下文，用完即丢
 * - Task Team：异步非阻塞，后台执行，多任务并发
 */
@Component
public class DagTaskToolProvider {

    private static final Logger logger = LoggerFactory.getLogger(DagTaskToolProvider.class);

    private final DagTaskService taskService;
    private final DagDependencyResolver dependencyResolver;
    private final TaskDispatcher taskDispatcher;
    private final ToolProgressBroadcaster progressBroadcaster;

    // 当前会话上下文（由 AgentService 注入）
    private static final ThreadLocal<SessionContext> currentSession = new ThreadLocal<>();

    public DagTaskToolProvider(DagTaskService taskService,
                                DagDependencyResolver dependencyResolver,
                                @Lazy TaskDispatcher taskDispatcher,
                                ToolProgressBroadcaster progressBroadcaster) {
        this.taskService = taskService;
        this.dependencyResolver = dependencyResolver;
        this.taskDispatcher = taskDispatcher;
        this.progressBroadcaster = progressBroadcaster;
    }

    /**
     * 设置当前会话上下文（由 AgentService 调用）
     */
    public static void setSessionContext(String tenantId, String userId, String sessionId) {
        currentSession.set(new SessionContext(tenantId, userId, sessionId));
        logger.debug("设置 DAG Task 会话上下文: tenant={}, user={}, session={}", tenantId, userId, sessionId);
    }

    /**
     * 清除当前会话上下文
     */
    public static void clearSessionContext() {
        currentSession.remove();
        logger.debug("清除 DAG Task 会话上下文");
    }

    /**
     * 获取当前会话 ID
     */
    public static String getCurrentSessionId() {
        SessionContext context = currentSession.get();
        return context != null ? context.sessionId : null;
    }

    private SessionContext getSessionContext() {
        SessionContext context = currentSession.get();
        if (context == null) {
            throw new IllegalStateException("未设置会话上下文，请先调用 setSessionContext");
        }
        return context;
    }

    // ==================== Task Team 执行控制 ====================

    @Tool("启动 Task Team 异步执行。任务已自动由 WorkerAgent 后台执行，无需手动调用。")
    public String startTaskTeam() {
        // TaskDispatcher 已禁用，任务由 WorkerAgent 执行
        return "✅ 任务已入库，WorkerAgent 正在后台自动领取执行。\n\n" +
               "**说明**：无需手动启动，WorkerAgent 会自动轮询领取 pending 状态的任务执行。\n" +
               "可使用 getTaskTeamStatus 查看进度。";
    }

    @Tool("创建任务并等待执行完成。创建任务后会等待 WorkerAgent 执行完成，期间可通过 SSE 看到进度。")
    public String createAndRun(String subject, String description) {
        try {
            SessionContext context = getSessionContext();

            // 推送：任务创建
            progressBroadcaster.broadcastTaskCreated(context.sessionId, "pending", subject);

            // 创建任务
            DagTask task = taskService.createTask(
                    subject,
                    description,
                    context.tenantId,
                    context.userId,
                    context.sessionId
            );

            logger.info("任务已创建: taskId={}, subject={}, sessionId={}", task.getTaskId(), subject, context.sessionId);

            // 推送：任务已入库，等待执行
            progressBroadcaster.broadcastTaskProgress(
                    context.sessionId, task.getTaskId(), subject, 5, "任务已入库，等待 WorkerAgent 执行"
            );

            // 等待任务完成（带超时）
            boolean completed = waitForTaskCompletion(task.getTaskId(), context.sessionId, subject, 300000); // 5分钟超时

            if (completed) {
                // 重新获取任务结果
                DagTask completedTask = taskService.getTask(task.getTaskId());

                // 推送：任务完成
                progressBroadcaster.broadcastTaskCompleted(
                        context.sessionId, task.getTaskId(), subject, completedTask.getResult()
                );

                return String.format(
                        "✅ 任务执行完成！\n" +
                        "任务ID: %s\n" +
                        "标题: %s\n" +
                        "结果: %s",
                        task.getTaskId(), subject,
                        completedTask.getResult() != null ? completedTask.getResult() : "执行成功"
                );
            } else {
                return String.format(
                        "⏳ 任务执行超时\n" +
                        "任务ID: %s\n" +
                        "标题: %s\n" +
                        "状态: 任务仍在执行中，请稍后查询结果",
                        task.getTaskId(), subject
                );
            }

        } catch (Exception e) {
            logger.error("创建任务失败: {}", e.getMessage(), e);
            return "操作失败: " + e.getMessage();
        }
    }

    /**
     * 等待任务完成（轮询检查，带进度推送）
     */
    private boolean waitForTaskCompletion(String taskId, String sessionId, String subject, long timeoutMs) {
        long startTime = System.currentTimeMillis();
        long checkInterval = 2000; // 每 2 秒检查一次
        int lastProgress = 5;

        logger.info("开始等待任务完成: taskId={}, timeout={}ms", taskId, timeoutMs);

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            // 检查任务状态
            DagTask task = taskService.getTask(taskId);
            if (task == null) {
                logger.warn("任务不存在: {}", taskId);
                return false;
            }

            String status = task.getStatus();
            logger.debug("检查任务状态: taskId={}, status={}", taskId, status);

            if ("completed".equals(status)) {
                logger.info("任务已完成: taskId={}", taskId);
                return true;
            }

            if ("failed".equals(status)) {
                logger.info("任务失败: taskId={}, error={}", taskId, task.getError());
                // 推送：任务失败
                progressBroadcaster.broadcastTaskFailed(sessionId, taskId, subject, task.getError());
                return true;
            }

            // 计算进度（基于时间估算）
            long elapsed = System.currentTimeMillis() - startTime;
            int progress = Math.min(90, 5 + (int) (elapsed * 85 / timeoutMs));
            if (progress > lastProgress + 10) {
                lastProgress = progress;
                // 推送：进度更新
                progressBroadcaster.broadcastTaskProgress(
                        sessionId, taskId, subject, progress, "任务执行中..."
                );
            }

            // 等待一段时间再检查
            try {
                Thread.sleep(checkInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("等待被中断: taskId={}", taskId);
                return false;
            }
        }

        logger.warn("等待超时: taskId={}", taskId);
        return false;
    }

    @Tool("等待当前会话的所有任务完成。返回所有任务的最终状态和结果。")
    public String waitForAllTasks() {
        try {
            String sessionId = getCurrentSessionId();
            if (sessionId == null) {
                return "未设置会话上下文";
            }

            // 获取所有任务
            List<DagTask> tasks = taskService.getTasksBySession(sessionId);
            if (tasks.isEmpty()) {
                return "当前会话没有任务";
            }

            int totalTasks = tasks.size();
            logger.info("等待所有任务完成: sessionId={}, totalTasks={}", sessionId, totalTasks);

            // 推送：开始等待
            progressBroadcaster.broadcastTaskProgress(
                    sessionId, "all", "全部任务", 0, "等待任务执行..."
            );

            // 等待所有任务完成
            long timeoutMs = 600000; // 10分钟总超时
            long startTime = System.currentTimeMillis();

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                // 刷新任务列表
                tasks = taskService.getTasksBySession(sessionId);

                // 统计完成情况
                int completedCount = 0;
                int failedCount = 0;
                int runningCount = 0;

                for (DagTask task : tasks) {
                    String status = task.getStatus();
                    if ("completed".equals(status)) {
                        completedCount++;
                    } else if ("failed".equals(status)) {
                        failedCount++;
                    } else if ("in_progress".equals(status)) {
                        runningCount++;
                    }
                }

                // 计算总进度
                int progress = (completedCount + failedCount) * 100 / totalTasks;

                // 推送进度更新
                progressBroadcaster.broadcastTaskProgress(
                        sessionId, "all", "全部任务", progress,
                        String.format("执行中: %d, 完成: %d, 失败: %d", runningCount, completedCount, failedCount)
                );

                // 检查是否全部完成
                if (completedCount + failedCount == totalTasks) {
                    // 推送：所有任务完成
                    progressBroadcaster.broadcastAllTasksCompleted(sessionId, completedCount, failedCount);

                    logger.info("所有任务完成: sessionId={}, success={}, failed={}", sessionId, completedCount, failedCount);
                    return formatAllTasksResult(sessionId, tasks, completedCount, failedCount);
                }

                // 等待
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "等待被中断";
                }
            }

            return "等待超时，部分任务可能仍在执行中";

        } catch (Exception e) {
            logger.error("等待任务失败: {}", e.getMessage(), e);
            return "操作失败: " + e.getMessage();
        }
    }

    /**
     * 格式化所有任务的结果
     */
    private String formatAllTasksResult(String sessionId, List<DagTask> tasks, long successCount, long failedCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ 所有任务已完成！\n\n");

        for (DagTask task : tasks) {
            if ("completed".equals(task.getStatus())) {
                sb.append("✅ ").append(task.getSubject()).append("\n");
                if (task.getResult() != null) {
                    sb.append("   结果: ").append(truncate(task.getResult(), 100)).append("\n");
                }
            } else if ("failed".equals(task.getStatus())) {
                sb.append("❌ ").append(task.getSubject()).append("\n");
                if (task.getError() != null) {
                    sb.append("   错误: ").append(task.getError()).append("\n");
                }
            }
        }

        sb.append("\n统计: 成功 ").append(successCount).append(" 个, 失败 ").append(failedCount).append(" 个");
        return sb.toString();
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }

    @Tool("停止 Task Team 执行。")
    public String stopTaskTeam() {
        try {
            taskDispatcher.stopTaskTeam();
            return "Task Team 已停止\n" + taskDispatcher.getStatus();

        } catch (Exception e) {
            logger.error("停止 Task Team 失败: {}", e.getMessage(), e);
            return "停止 Task Team 失败: " + e.getMessage();
        }
    }

    @Tool("获取 Task Team 执行状态。返回任务执行进度和当前状态。")
    public String getTaskTeamStatus() {
        try {
            return taskDispatcher.getStatus();

        } catch (Exception e) {
            logger.error("获取 Task Team 状态失败: {}", e.getMessage(), e);
            return "获取状态失败: " + e.getMessage();
        }
    }

    // ==================== 核心工具 ====================

    @Tool("创建新任务。参数：subject（简要标题，祈使句形式），description（详细需求描述）。返回任务 ID。")
    public String createTask(String subject, String description) {
        try {
            SessionContext context = getSessionContext();
            logger.info("创建 DAG 任务: subject={}", subject);

            DagTask task = taskService.createTask(
                    subject,
                    description,
                    context.tenantId,
                    context.userId,
                    context.sessionId
            );

            return formatTaskCreated(task);

        } catch (Exception e) {
            logger.error("创建任务失败: {}", e.getMessage(), e);
            return "任务创建失败: " + e.getMessage();
        }
    }

    @Tool("获取任务详情。参数：taskId（任务 ID）。返回任务的完整信息。")
    public String getTask(String taskId) {
        try {
            DagTask task = taskService.getTask(taskId);
            if (task == null) {
                return "任务不存在: " + taskId;
            }
            return formatTaskDetail(task);

        } catch (Exception e) {
            logger.error("获取任务失败: {}", e.getMessage(), e);
            return "获取任务失败: " + e.getMessage();
        }
    }

    @Tool("列出任务。参数：status（可选，筛选状态：pending/in_progress/completed）。不传参数则列出所有活跃任务。")
    public String listTasks(String status) {
        try {
            List<DagTask> tasks;
            if (status == null || status.isEmpty()) {
                tasks = taskService.getActiveTasks();
            } else {
                tasks = taskService.getTasksByStatus(status);
            }

            if (tasks.isEmpty()) {
                return "没有找到任务";
            }

            return formatTaskList(tasks);

        } catch (Exception e) {
            logger.error("列出任务失败: {}", e.getMessage(), e);
            return "列出任务失败: " + e.getMessage();
        }
    }

    @Tool("更新任务。参数：taskId（任务 ID），subject（可选，新标题），description（可选，新描述）。")
    public String updateTask(String taskId, String subject, String description) {
        try {
            DagTask task = taskService.updateTask(taskId, subject, description);
            return String.format("任务已更新 [%s]\n标题: %s", taskId, task.getSubject());

        } catch (IllegalArgumentException e) {
            return "任务不存在: " + taskId;

        } catch (Exception e) {
            logger.error("更新任务失败: {}", e.getMessage(), e);
            return "更新任务失败: " + e.getMessage();
        }
    }

    // ==================== 状态管理 ====================

    @Tool("开始执行任务。参数：taskId（任务 ID）。必须在开始工作前调用。如果任务被阻塞（依赖未完成），会返回错误。")
    public String startTask(String taskId) {
        try {
            DagTask task = taskService.startTask(taskId);
            return String.format("任务已开始执行 [%s]\n标题: %s", taskId, task.getSubject());

        } catch (IllegalStateException e) {
            // 被阻塞的情况
            return "任务被阻塞: " + e.getMessage() + "\n请先完成依赖的任务。";

        } catch (Exception e) {
            logger.error("开始任务失败: {}", e.getMessage(), e);
            return "开始任务失败: " + e.getMessage();
        }
    }

    @Tool("完成任务。参数：taskId（任务 ID），result（执行结果摘要）。必须在完成任务后调用，会解锁依赖此任务的后续任务。")
    public String completeTask(String taskId, String result) {
        try {
            DagTask task = taskService.completeTask(taskId, result);

            // 检查是否有被解锁的任务
            List<DagTask> dependents = taskService.getDependents(taskId);
            String unlockInfo = "";
            if (!dependents.isEmpty()) {
                List<DagTask> readyTasks = taskService.getUnblockedTasks();
                if (!readyTasks.isEmpty()) {
                    unlockInfo = "\n\n已解锁的任务:\n" + readyTasks.stream()
                            .map(t -> "- [" + t.getTaskId() + "] " + t.getSubject())
                            .collect(Collectors.joining("\n"));
                }
            }

            return String.format("任务已完成 [%s] ✅\n标题: %s\n结果: %s%s",
                    taskId, task.getSubject(), result, unlockInfo);

        } catch (Exception e) {
            logger.error("完成任务失败: {}", e.getMessage(), e);
            return "完成任务失败: " + e.getMessage();
        }
    }

    @Tool("标记任务失败。参数：taskId（任务 ID），error（错误信息）。")
    public String failTask(String taskId, String error) {
        try {
            taskService.failTask(taskId, error);
            return String.format("任务已标记失败 [%s]\n错误: %s", taskId, error);

        } catch (Exception e) {
            logger.error("标记任务失败: {}", e.getMessage(), e);
            return "操作失败: " + e.getMessage();
        }
    }

    @Tool("删除任务。参数：taskId（任务 ID）。仅用于不再需要的任务或创建错误的任务。")
    public String deleteTask(String taskId) {
        try {
            taskService.deleteTask(taskId);
            return String.format("任务已删除 [%s]", taskId);

        } catch (Exception e) {
            logger.error("删除任务失败: {}", e.getMessage(), e);
            return "删除任务失败: " + e.getMessage();
        }
    }

    // ==================== 依赖管理 ====================

    @Tool("设置任务依赖。参数：taskId（任务 ID），blockedBy（依赖的任务 ID 列表，逗号分隔）。这些任务必须完成后，当前任务才能开始。")
    public String setBlockedBy(String taskId, String blockedBy) {
        try {
            List<String> blockedByList = parseCommaList(blockedBy);
            DagTask task = taskService.setBlockedBy(taskId, blockedByList);

            return String.format("依赖已设置 [%s]\n依赖: %s\n任务会在这些任务完成后解锁。",
                    taskId, blockedByList);

        } catch (IllegalStateException e) {
            return "设置依赖失败（会形成循环）: " + e.getMessage();

        } catch (Exception e) {
            logger.error("设置依赖失败: {}", e.getMessage(), e);
            return "设置依赖失败: " + e.getMessage();
        }
    }

    @Tool("获取就绪任务。返回所有依赖已完成、可以开始执行的任务。")
    public String getReadyTasks() {
        try {
            List<DagTask> readyTasks = taskService.getUnblockedTasks();

            if (readyTasks.isEmpty()) {
                return "没有就绪的任务（所有 pending 任务都被阻塞或没有 pending 任务）";
            }

            return "就绪任务（可立即执行）:\n" + readyTasks.stream()
                    .map(t -> String.format("- [%s] %s", t.getTaskId(), t.getSubject()))
                    .collect(Collectors.joining("\n"));

        } catch (Exception e) {
            logger.error("获取就绪任务失败: {}", e.getMessage(), e);
            return "获取就绪任务失败: " + e.getMessage();
        }
    }

    @Tool("获取被阻塞任务。返回所有因依赖未完成而无法执行的任务。")
    public String getBlockedTasks() {
        try {
            List<DagTask> blockedTasks = taskService.getBlockedTasks();

            if (blockedTasks.isEmpty()) {
                return "没有被阻塞的任务";
            }

            return "被阻塞的任务:\n" + blockedTasks.stream()
                    .map(t -> {
                        List<String> deps = dependencyResolver.parseJsonArray(t.getBlockedBy());
                        return String.format("- [%s] %s\n  等待: %s", t.getTaskId(), t.getSubject(), deps);
                    })
                    .collect(Collectors.joining("\n"));

        } catch (Exception e) {
            logger.error("获取被阻塞任务失败: {}", e.getMessage(), e);
            return "获取被阻塞任务失败: " + e.getMessage();
        }
    }

    @Tool("检查任务依赖状态。参数：taskId（任务 ID）。返回该任务的依赖是否都已完成。")
    public String checkDependencies(String taskId) {
        try {
            DagTask task = taskService.getTask(taskId);
            if (task == null) {
                return "任务不存在: " + taskId;
            }

            boolean blocked = taskService.isTaskBlocked(taskId);
            List<String> blockedByList = dependencyResolver.parseJsonArray(task.getBlockedBy());

            if (blockedByList.isEmpty()) {
                return "任务无依赖，可以开始执行";
            }

            if (blocked) {
                return String.format("任务被阻塞 [%s]\n依赖未完成: %s", taskId, blockedByList);
            } else {
                return String.format("任务依赖已完成 [%s]\n依赖: %s\n可以开始执行", taskId, blockedByList);
            }

        } catch (Exception e) {
            logger.error("检查依赖失败: {}", e.getMessage(), e);
            return "检查依赖失败: " + e.getMessage();
        }
    }

    // ==================== 责任人管理 ====================

    @Tool("认领任务。参数：taskId（任务 ID）。设置自己为任务的责任人。")
    public String claimTask(String taskId) {
        try {
            SessionContext context = getSessionContext();
            String owner = context.userId;
            DagTask task = taskService.claimTask(taskId, owner);

            return String.format("任务已认领 [%s]\n责任人: %s", taskId, owner);

        } catch (Exception e) {
            logger.error("认领任务失败: {}", e.getMessage(), e);
            return "认领任务失败: " + e.getMessage();
        }
    }

    @Tool("获取指定责任人的任务。参数：owner（责任人 ID）。")
    public String getTasksByOwner(String owner) {
        try {
            List<DagTask> tasks = taskService.getTasksByOwner(owner);

            if (tasks.isEmpty()) {
                return "没有找到责任人的任务: " + owner;
            }

            return formatTaskList(tasks);

        } catch (Exception e) {
            logger.error("获取责任人任务失败: {}", e.getMessage(), e);
            return "获取责任人任务失败: " + e.getMessage();
        }
    }

    // ==================== 统计 ====================

    @Tool("获取任务进度统计。")
    public String getProgress() {
        try {
            return taskService.getProgress();

        } catch (Exception e) {
            logger.error("获取进度失败: {}", e.getMessage(), e);
            return "获取进度失败: " + e.getMessage();
        }
    }

    // ==================== 格式化方法 ====================

    private String formatTaskCreated(DagTask task) {
        return String.format(
                "任务已创建 [%s]\n标题: %s\n描述: %s\n状态: pending\n\n提示：使用 setBlockedBy 设置依赖，使用 startTask 开始执行",
                task.getTaskId(), task.getSubject(), task.getDescription()
        );
    }

    private String formatTaskDetail(DagTask task) {
        List<String> blockedBy = dependencyResolver.parseJsonArray(task.getBlockedBy());
        List<String> blocks = dependencyResolver.parseJsonArray(task.getBlocks());

        return String.format(
                "任务详情 [%s]\n- 标题: %s\n- 描述: %s\n- 状态: %s\n- 责任人: %s\n- 依赖: %s\n- 被依赖: %s\n- 结果: %s\n- 创建时间: %s",
                task.getTaskId(),
                task.getSubject(),
                task.getDescription() != null ? task.getDescription() : "无",
                task.getStatus(),
                task.getOwner() != null ? task.getOwner() : "未认领",
                blockedBy.isEmpty() ? "无" : blockedBy,
                blocks.isEmpty() ? "无" : blocks,
                task.getResult() != null ? task.getResult() : "无",
                task.getCreatedAt()
        );
    }

    private String formatTaskList(List<DagTask> tasks) {
        return String.format("任务列表 (%d 个):\n%s",
                tasks.size(),
                tasks.stream()
                        .map(t -> String.format("- [%s] %s | 状态: %s | 责任人: %s",
                                t.getTaskId(), t.getSubject(), t.getStatus(),
                                t.getOwner() != null ? t.getOwner() : "未认领"))
                        .collect(Collectors.joining("\n"))
        );
    }

    private List<String> parseCommaList(String str) {
        if (str == null || str.isEmpty()) {
            return List.of();
        }
        return List.of(str.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
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