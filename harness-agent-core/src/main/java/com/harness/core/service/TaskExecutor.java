package com.harness.core.service;

import com.harness.core.entity.DagTask;
import com.harness.core.mapper.DagTaskMapper;
import com.harness.core.tool.DagTaskToolProvider;
import com.harness.core.tool.SubAgentToolProvider;
import com.harness.core.tool.TodoWriteToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

/**
 * DAG 任务执行器
 *
 * 负责执行单个任务：
 * 1. 调用 AI 执行任务内容
 * 2. 更新任务状态
 * 3. 触发后续任务解锁
 */
@Component
public class TaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(TaskExecutor.class);

    private final DagTaskService taskService;
    private final DagTaskMapper taskMapper;
    private final AiServiceFactory aiServiceFactory;
    private final DagDependencyResolver dependencyResolver;

    // 工作线程池（最多 5 个并发任务）
    private final ExecutorService workerPool = Executors.newFixedThreadPool(5);

    // AI 调用超时时间（秒）
    private static final int AI_TIMEOUT_SECONDS = 300;

    public TaskExecutor(DagTaskService taskService,
                        DagTaskMapper taskMapper,
                        AiServiceFactory aiServiceFactory,
                        DagDependencyResolver dependencyResolver) {
        this.taskService = taskService;
        this.taskMapper = taskMapper;
        this.aiServiceFactory = aiServiceFactory;
        this.dependencyResolver = dependencyResolver;
    }

    /**
     * 异步执行任务
     *
     * @param task 要执行的任务
     * @return CompletableFuture 包含执行结果
     */
    public CompletableFuture<ExecutionResult> executeAsync(DagTask task) {
        return CompletableFuture.supplyAsync(() -> execute(task), workerPool);
    }

    /**
     * 同步执行任务
     */
    public ExecutionResult execute(DagTask task) {
        String taskId = task.getTaskId();
        logger.info("开始执行任务: taskId={}, subject={}, assignedAgent={}",
                taskId, task.getSubject(), task.getAssignedAgentId());

        try {
            // 1. 重新从数据库获取任务，检查状态
            DagTask latestTask = taskMapper.selectById(taskId);
            if (latestTask == null) {
                return new ExecutionResult(false, taskId, null, "任务不存在");
            }

            // 任务应该已经被 Worker 领取，状态为 in_progress
            if (!"in_progress".equals(latestTask.getStatus())) {
                logger.warn("任务状态异常: taskId={}, status={}, 期望 in_progress",
                        taskId, latestTask.getStatus());
                // 如果状态是 pending，说明还没有被正确领取
                if ("pending".equals(latestTask.getStatus())) {
                    return new ExecutionResult(false, taskId, null, "任务未被正确领取");
                }
                // 其他状态（completed, failed）表示任务已被处理
                return new ExecutionResult(false, taskId, null, "任务已处理: " + latestTask.getStatus());
            }

            // 2. 构造执行提示
            String executionPrompt = buildExecutionPrompt(latestTask);

            // 3. 设置工具上下文（让 AI 可以使用工具）
            String sessionId = latestTask.getSessionId() != null ? latestTask.getSessionId() : "task-exec-" + taskId;
            TodoWriteToolProvider.setSessionContext(
                    latestTask.getTenantId() != null ? latestTask.getTenantId() : "default-tenant",
                    latestTask.getUserId() != null ? latestTask.getUserId() : "default-user",
                    sessionId);
            SubAgentToolProvider.setSessionContext(
                    latestTask.getTenantId() != null ? latestTask.getTenantId() : "default-tenant",
                    latestTask.getUserId() != null ? latestTask.getUserId() : "default-user",
                    sessionId);
            DagTaskToolProvider.setSessionContext(
                    latestTask.getTenantId() != null ? latestTask.getTenantId() : "default-tenant",
                    latestTask.getUserId() != null ? latestTask.getUserId() : "default-user",
                    sessionId);

            try {
                // 4. 调用 AI 执行（带超时控制）
                AiChatService aiService = aiServiceFactory.getService("openai", sessionId);

                ExecutorService aiExecutor = Executors.newSingleThreadExecutor();
                Future<String> future = aiExecutor.submit(() -> aiService.chat(sessionId, executionPrompt));

                String result;
                try {
                    result = future.get(AI_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    throw new RuntimeException("AI 调用超时（" + AI_TIMEOUT_SECONDS + "秒）");
                } finally {
                    aiExecutor.shutdownNow();
                }

                // 5. 标记完成
                latestTask.setStatus("completed");
                latestTask.setResult(result);
                latestTask.setCompletedAt(LocalDateTime.now());
                taskMapper.updateById(latestTask);

                logger.info("任务执行完成: taskId={}, result={}", taskId,
                        result.length() > 100 ? result.substring(0, 100) + "..." : result);

                // 6. 检查并解锁后续任务
                int unlockedCount = unlockDependentTasks(taskId);
                if (unlockedCount > 0) {
                    logger.info("解锁了 {} 个依赖任务: taskId={}", unlockedCount, taskId);
                }

                return new ExecutionResult(true, taskId, result, null);

            } finally {
                // 清理工具上下文
                TodoWriteToolProvider.clearSessionContext();
                SubAgentToolProvider.clearSessionContext();
                DagTaskToolProvider.clearSessionContext();
            }

        } catch (Exception e) {
            logger.error("任务执行失败: taskId={}, error={}", taskId, e.getMessage(), e);

            // 标记失败
            try {
                DagTask failedTask = taskMapper.selectById(taskId);
                if (failedTask != null) {
                    failedTask.setStatus("failed");
                    failedTask.setError(e.getMessage());
                    failedTask.setCompletedAt(LocalDateTime.now());
                    taskMapper.updateById(failedTask);
                }
            } catch (Exception ex) {
                logger.error("更新失败状态异常: {}", ex.getMessage());
            }

            return new ExecutionResult(false, taskId, null, e.getMessage());
        }
    }

    /**
     * 构造任务执行提示
     */
    private String buildExecutionPrompt(DagTask task) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请执行以下任务：\n\n");
        prompt.append("## 任务标题\n").append(task.getSubject()).append("\n\n");

        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            prompt.append("## 任务详情\n").append(task.getDescription()).append("\n\n");
        }

        // 检查是否有依赖任务的结果
        List<String> blockedBy = dependencyResolver.parseJsonArray(task.getBlockedBy());
        if (!blockedBy.isEmpty()) {
            prompt.append("## 前置任务结果\n");
            for (String depId : blockedBy) {
                DagTask depTask = taskService.getTask(depId);
                if (depTask != null && depTask.getResult() != null) {
                    prompt.append("- 任务 [").append(depId).append("]: ")
                          .append(depTask.getResult()).append("\n");
                }
            }
            prompt.append("\n");
        }

        prompt.append("请完成任务并给出结果摘要。");
        return prompt.toString();
    }

    /**
     * 解锁依赖此任务的后续任务
     * @return 解锁的任务数量
     */
    private int unlockDependentTasks(String completedTaskId) {
        List<DagTask> dependents = taskService.getDependents(completedTaskId);
        int unlockedCount = 0;

        for (DagTask dependent : dependents) {
            // 检查是否所有依赖都已完成
            if (!taskService.isTaskBlocked(dependent.getTaskId())) {
                logger.info("任务解锁，可以执行: taskId={}, subject={}",
                        dependent.getTaskId(), dependent.getSubject());
                unlockedCount++;
                // 不自动执行，等待 Dispatcher 调度或 Worker 领取
            }
        }

        return unlockedCount;
    }

    /**
     * 执行结果记录
     */
    public record ExecutionResult(boolean success, String taskId, String result, String error) {}
}