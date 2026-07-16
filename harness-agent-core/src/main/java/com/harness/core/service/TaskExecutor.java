package com.harness.core.service;

import com.harness.core.entity.DagTask;
import com.harness.core.mapper.DagTaskMapper;
import com.harness.core.model.AiChatModel;
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

    // AI 调用超时时间（秒）- 增加到 10 分钟
    private static final int AI_TIMEOUT_SECONDS = 600;

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
                AiChatModel aiModel = aiServiceFactory.getModel("openai", sessionId);

                ExecutorService aiExecutor = Executors.newSingleThreadExecutor();
                Future<String> future = aiExecutor.submit(() -> aiModel.chat(sessionId, executionPrompt));

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

                // 7. 检查是否所有任务完成，触发汇总
                checkAndTriggerSummary(latestTask);

                return new ExecutionResult(true, taskId, result, null);

            } finally {
                // 清理工具上下文
                TodoWriteToolProvider.clearSessionContext();
                SubAgentToolProvider.clearSessionContext();
                DagTaskToolProvider.clearSessionContext();
            }

        } catch (Exception e) {
            logger.error("任务执行失败: taskId={}, error={}", taskId, e.getMessage(), e);

            DagTask failedTask = null;
            // 标记失败
            try {
                failedTask = taskMapper.selectById(taskId);
                if (failedTask != null) {
                    failedTask.setStatus("failed");
                    failedTask.setError(e.getMessage());
                    failedTask.setCompletedAt(LocalDateTime.now());
                    taskMapper.updateById(failedTask);
                }
            } catch (Exception ex) {
                logger.error("更新失败状态异常: {}", ex.getMessage());
            }

            // 检查是否所有任务完成（包括失败的）
            if (failedTask != null) {
                checkAndTriggerSummary(failedTask);
            }

            return new ExecutionResult(false, taskId, null, e.getMessage());
        }
    }

    /**
     * 构造任务执行提示
     */
    private String buildExecutionPrompt(DagTask task) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是子任务执行器（Worker Agent），负责执行具体的操作任务。\n\n");

        prompt.append("# 核心职责\n");
        prompt.append("- 执行文件操作：创建、读取、修改、删除文件\n");
        prompt.append("- 执行代码：运行程序、编译代码、执行测试\n");
        prompt.append("- 系统操作：安装依赖、配置环境、执行bash命令\n");
        prompt.append("- 数据处理：数据库操作、API调用、数据转换\n\n");

        prompt.append("# 任务信息\n");
        prompt.append("- 标题: ").append(task.getSubject()).append("\n");

        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            prompt.append("- 详情: ").append(task.getDescription()).append("\n");
        }
        prompt.append("\n");

        // 检查是否有依赖任务的结果
        List<String> blockedBy = dependencyResolver.parseJsonArray(task.getBlockedBy());
        if (!blockedBy.isEmpty()) {
            prompt.append("## 前置任务结果（可直接使用）\n");
            for (String depId : blockedBy) {
                DagTask depTask = taskService.getTask(depId);
                if (depTask != null && depTask.getResult() != null) {
                    prompt.append("- [").append(depTask.getSubject()).append("]: ")
                          .append(depTask.getResult()).append("\n");
                }
            }
            prompt.append("\n");
        }

        prompt.append("# 执行要求\n");
        prompt.append("1. 使用 Bash 工具执行具体操作（已配置安全策略）\n");
        prompt.append("2. 如果需要创建文件，先确定文件路径和内容，然后使用bash命令创建\n");
        prompt.append("3. 如果需要修改文件，先读取原文件，再进行修改\n");
        prompt.append("4. 完成后给出简洁的结果摘要（不超过200字）\n");
        prompt.append("5. 如果遇到错误，说明错误原因和建议\n\n");

        prompt.append("# 工具使用\n");
        prompt.append("## FileTool（推荐用于文件操作）\n");
        prompt.append("- writeFile(path, content): 写入文件，支持多行文本，无需担心引号转义\n");
        prompt.append("- appendFile(path, content): 追加内容到文件\n");
        prompt.append("- readFile(path): 读取文件内容\n");
        prompt.append("- fileExists(path): 检查文件是否存在\n");
        prompt.append("- deleteFile(path): 删除文件\n");
        prompt.append("- createDirectory(path): 创建目录\n");
        prompt.append("- listDirectory(path): 列出目录内容\n\n");

        prompt.append("## Bash工具（用于命令执行）\n");
        prompt.append("- 执行系统命令、安装依赖、运行程序\n");
        prompt.append("- 已配置宽松安全策略，可直接执行中危操作（如npm install, git pull等）\n");
        prompt.append("- 高危操作（如rm -rf /）会被自动拦截\n\n");

        prompt.append("# 最佳实践\n");
        prompt.append("1. **创建文件**：使用 writeFile，直接传入完整内容\n");
        prompt.append("2. **修改文件**：先 readFile，修改内容后 writeFile\n");
        prompt.append("3. **运行程序**：使用 Bash 执行命令\n");
        prompt.append("4. **安装依赖**：使用 Bash 执行 npm install / pip install\n\n");

        prompt.append("请开始执行任务。\n");

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
     * 检查并触发结果汇总
     * 当某个 session 的所有任务都完成后，标记需要汇总
     */
    private void checkAndTriggerSummary(DagTask completedTask) {
        String sessionId = completedTask.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }

        // 获取该 session 的所有任务
        List<DagTask> sessionTasks = taskService.getTasksBySession(sessionId);
        if (sessionTasks.isEmpty()) {
            return;
        }

        // 检查是否所有任务都已完成
        boolean allCompleted = sessionTasks.stream()
                .allMatch(t -> "completed".equals(t.getStatus()) || "failed".equals(t.getStatus()));

        if (allCompleted) {
            logger.info("Session {} 的所有任务已完成，共 {} 个任务", sessionId, sessionTasks.size());

            // 统计成功/失败数量
            long successCount = sessionTasks.stream().filter(t -> "completed".equals(t.getStatus())).count();
            long failedCount = sessionTasks.stream().filter(t -> "failed".equals(t.getStatus())).count();

            logger.info("Session {} 执行结果: 成功={}, 失败={}", sessionId, successCount, failedCount);

            // TODO: 触发 Main Agent 汇总（可以通过消息队列或直接调用）
            // 这里可以广播汇总完成事件给前端
        }
    }

    /**
     * 执行结果记录
     */
    public record ExecutionResult(boolean success, String taskId, String result, String error) {}
}