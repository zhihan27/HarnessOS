package com.harness.core.service;

import com.harness.core.entity.DagTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DAG 任务调度器
 *
 * 负责后台调度：
 * 1. 定期检查就绪任务
 * 2. 派发给 TaskExecutor 执行
 * 3. 追踪执行状态
 *
 * 应用启动后自动运行，处理所有会话的 pending 任务
 */
@Component
public class TaskDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(TaskDispatcher.class);

    private final DagTaskService taskService;
    private final TaskExecutor taskExecutor;

    // 正在执行的任务（防止重复执行）
    private final Map<String, CompletableFuture<TaskExecutor.ExecutionResult>> runningTasks = new ConcurrentHashMap<>();

    // 调度器是否启用（默认禁用，由 WorkerAgent 来执行任务）
    private volatile boolean enabled = false;

    public TaskDispatcher(@Lazy DagTaskService taskService,
                          @Lazy TaskExecutor taskExecutor) {
        this.taskService = taskService;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 应用启动后不再自动启动调度
     * 任务执行由 WorkerAgent 负责
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("应用启动完成，TaskDispatcher 已禁用（由 WorkerAgent 执行任务）");
        enabled = false;
    }

    /**
     * 启动 Task Team 调度（用于手动启动）
     */
    public void startTaskTeam(String sessionId) {
        this.enabled = true;
        logger.info("Task Team 已启动: sessionId={}", sessionId);
    }

    /**
     * 停止 Task Team 调度
     */
    public void stopTaskTeam() {
        this.enabled = false;
        logger.info("Task Team 已停止");
    }

    /**
     * 检查 Task Team 是否运行中
     */
    public boolean isRunning() {
        return enabled;
    }

    /**
     * 定时调度（每 2 秒检查一次）
     * 处理所有会话的就绪任务，实现真正的并发执行
     */
    @Scheduled(fixedRate = 2000)
    public void dispatch() {
        if (!enabled) {
            return;
        }

        try {
            // 1. 获取所有就绪任务（不限制会话）
            List<DagTask> readyTasks = taskService.getUnblockedTasks();

            if (readyTasks.isEmpty()) {
                return;
            }

            // 2. 并发派发所有就绪任务
            for (DagTask task : readyTasks) {
                // 跳过非 pending 状态的任务
                if (!"pending".equals(task.getStatus())) {
                    continue;
                }

                // 使用 putIfAbsent 原子操作防止重复执行
                CompletableFuture<TaskExecutor.ExecutionResult> placeholder = new CompletableFuture<>();
                CompletableFuture<TaskExecutor.ExecutionResult> existing = runningTasks.putIfAbsent(
                        task.getTaskId(), placeholder);

                if (existing != null) {
                    // 任务已在执行中
                    continue;
                }

                logger.info("派发任务执行: taskId={}, subject={}", task.getTaskId(), task.getSubject());

                // 异步执行
                CompletableFuture<TaskExecutor.ExecutionResult> future = taskExecutor.executeAsync(task);

                // 执行完成后清理
                future.whenComplete((result, ex) -> {
                    runningTasks.remove(task.getTaskId());
                    if (result != null) {
                        logger.info("任务执行完成: taskId={}, success={}", result.taskId(), result.success());
                    } else if (ex != null) {
                        logger.error("任务执行异常: taskId={}, error={}", task.getTaskId(), ex.getMessage());
                    }
                });

                // 替换 placeholder 为实际的 future
                runningTasks.put(task.getTaskId(), future);
            }

        } catch (Exception e) {
            logger.error("任务调度异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取执行状态
     */
    public String getStatus() {
        List<DagTask> allTasks = taskService.getAllTasks();

        long pending = allTasks.stream().filter(t -> "pending".equals(t.getStatus())).count();
        long inProgress = allTasks.stream().filter(t -> "in_progress".equals(t.getStatus())).count();
        long completed = allTasks.stream().filter(t -> "completed".equals(t.getStatus())).count();
        long failed = allTasks.stream().filter(t -> "failed".equals(t.getStatus())).count();

        return String.format(
                "Task Team 状态: %s\n- 等待中: %d\n- 执行中: %d\n- 已完成: %d\n- 失败: %d\n- 正在执行: %d",
                enabled ? "运行中" : "已停止", pending, inProgress, completed, failed, runningTasks.size()
        );
    }
}