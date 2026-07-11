package com.harness.core.service;

import com.harness.core.entity.AgentInstance;
import com.harness.core.entity.DagTask;
import com.harness.core.mapper.AgentInstanceMapper;
import com.harness.core.tool.DagTaskToolProvider;
import com.harness.core.tool.SubAgentToolProvider;
import com.harness.core.tool.TodoWriteToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;

/**
 * WorkerAgent 服务
 *
 * 负责：
 * 1. Worker 轮询循环
 * 2. 原子领取任务
 * 3. 执行任务（含 SubAgent 支持）
 */
@Service
public class WorkerAgentService {

    private static final Logger logger = LoggerFactory.getLogger(WorkerAgentService.class);

    private final AgentRegistryService registryService;
    private final TaskExecutor taskExecutor;
    private final AgentInstanceMapper agentMapper;

    // Worker 线程池（每个 Worker 有独立的执行池）
    private final Map<String, ExecutorService> workerExecutors = new ConcurrentHashMap<>();

    // Worker 调度器（定期检查任务）
    private final Map<String, ScheduledExecutorService> workerSchedulers = new ConcurrentHashMap<>();

    // 轮询间隔（毫秒）
    private static final long POLL_INTERVAL_MS = 2000;

    public WorkerAgentService(AgentRegistryService registryService,
                              TaskExecutor taskExecutor,
                              AgentInstanceMapper agentMapper) {
        this.registryService = registryService;
        this.taskExecutor = taskExecutor;
        this.agentMapper = agentMapper;
    }

    /**
     * 启动 Worker Agent 执行循环
     *
     * @param workerAgentId Worker Agent ID
     */
    public void startWorkerLoop(String workerAgentId) {
        AgentInstance agent = registryService.getAgent(workerAgentId);

        if (agent == null) {
            throw new IllegalArgumentException("Agent 不存在: " + workerAgentId);
        }

        if (!"WORKER".equals(agent.getAgentType())) {
            throw new IllegalStateException("只有 Worker Agent 可以启动执行循环");
        }

        logger.info("启动 Worker 循环: agentId={}, maxConcurrency={}", workerAgentId, agent.getMaxConcurrency());

        // 1. 创建任务执行线程池
        ExecutorService executor = Executors.newFixedThreadPool(agent.getMaxConcurrency());
        workerExecutors.put(workerAgentId, executor);

        // 2. 创建调度器（定期检查任务）
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                executeWorkerCycle(workerAgentId);
            } catch (Exception e) {
                logger.error("Worker 循环异常: agentId={}, error={}", workerAgentId, e.getMessage());
            }
        }, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);

        workerSchedulers.put(workerAgentId, scheduler);

        logger.info("Worker 循环已启动: agentId={}", workerAgentId);
    }

    /**
     * Worker 执行周期
     * 检查并领取可用任务
     */
    private void executeWorkerCycle(String workerAgentId) {
        AgentInstance agent = registryService.getAgent(workerAgentId);

        if (agent == null || "STOPPED".equals(agent.getStatus())) {
            logger.debug("Worker 已停止或不存在: agentId={}", workerAgentId);
            return;
        }

        // 检查是否有空闲容量
        if (agent.getCurrentLoad() >= agent.getMaxConcurrency()) {
            logger.debug("Worker 已达最大负载: agentId={}, load={}/{}", workerAgentId, agent.getCurrentLoad(), agent.getMaxConcurrency());
            return;
        }

        // 尝试领取任务
        DagTask task = registryService.claimTaskForWorker(workerAgentId);

        if (task != null) {
            // 异步执行任务
            ExecutorService executor = workerExecutors.get(workerAgentId);
            executor.submit(() -> executeTaskWithAgent(workerAgentId, task));
        }
    }

    /**
     * 使用 Agent 身份执行任务
     */
    private void executeTaskWithAgent(String agentId, DagTask task) {
        String taskId = task.getTaskId();
        logger.info("Worker 开始执行任务: agentId={}, taskId={}, subject={}", agentId, taskId, task.getSubject());

        try {
            // 1. 设置 Agent 上下文（供 SubAgent 等使用）
            AgentContext.setContext(agentId, task.getSessionId(), taskId);

            // 2. 设置工具上下文
            String tenantId = registryService.getAgent(agentId).getTenantId();
            String userId = registryService.getAgent(agentId).getUserId();
            TodoWriteToolProvider.setSessionContext(tenantId, userId, task.getSessionId());
            SubAgentToolProvider.setSessionContext(tenantId, userId, task.getSessionId());
            DagTaskToolProvider.setSessionContext(tenantId, userId, task.getSessionId());

            // 3. 执行任务
            TaskExecutor.ExecutionResult result = taskExecutor.execute(task);

            // 4. 释放任务
            registryService.releaseTask(agentId, taskId, result.success(), result.result());

            logger.info("Worker 任务执行完成: agentId={}, taskId={}, success={}", agentId, taskId, result.success());

        } catch (Exception e) {
            logger.error("Worker 任务执行异常: agentId={}, taskId={}, error={}", agentId, taskId, e.getMessage(), e);
            registryService.releaseTask(agentId, taskId, false, e.getMessage());

        } finally {
            // 清理上下文
            AgentContext.clearAll();
            TodoWriteToolProvider.clearSessionContext();
            SubAgentToolProvider.clearSessionContext();
            DagTaskToolProvider.clearSessionContext();
        }
    }

    /**
     * 停止 Worker Agent
     */
    public void stopWorker(String workerAgentId) {
        logger.info("停止 Worker: agentId={}", workerAgentId);

        // 1. 停止调度器
        ScheduledExecutorService scheduler = workerSchedulers.get(workerAgentId);
        if (scheduler != null) {
            scheduler.shutdownNow();
            workerSchedulers.remove(workerAgentId);
        }

        // 2. 停止执行线程池
        ExecutorService executor = workerExecutors.get(workerAgentId);
        if (executor != null) {
            executor.shutdownNow();
            workerExecutors.remove(workerAgentId);
        }

        // 3. 更新 Agent 状态
        registryService.stopAgent(workerAgentId);

        logger.info("Worker 已停止: agentId={}", workerAgentId);
    }

    /**
     * 检查 Worker 是否运行
     */
    public boolean isWorkerRunning(String workerAgentId) {
        return workerSchedulers.containsKey(workerAgentId) &&
               !workerSchedulers.get(workerAgentId).isShutdown();
    }

    /**
     * 获取所有运行的 Worker ID
     */
    public java.util.Set<String> getRunningWorkers() {
        return workerSchedulers.keySet();
    }
}