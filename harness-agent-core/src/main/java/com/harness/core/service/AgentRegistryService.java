package com.harness.core.service;

import com.harness.core.context.AgentContext;
import com.harness.core.entity.AgentInstance;
import com.harness.core.entity.AgentTaskAssignment;
import com.harness.core.entity.DagTask;
import com.harness.core.mapper.AgentInstanceMapper;
import com.harness.core.mapper.AgentTaskAssignmentMapper;
import com.harness.core.mapper.DagTaskMapper;
import com.harness.core.model.AiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 注册与生命周期管理服务
 *
 * 核心功能：
 * 1. Agent 注册与注销
 * 2. Worker 任务原子领取（乐观锁）
 * 3. Agent 状态管理
 * 4. 任务分配记录
 */
@Service
public class AgentRegistryService {

    private static final Logger logger = LoggerFactory.getLogger(AgentRegistryService.class);

    private final AgentInstanceMapper agentMapper;
    private final AgentTaskAssignmentMapper assignmentMapper;
    private final DagTaskMapper taskMapper;
    private final DagTaskService taskService;
    private final AgentStatusBroadcaster statusBroadcaster;
    private final AiServiceFactory aiServiceFactory;

    // 内存中活跃的 Agent 缓存（用于快速查询）
    private final Map<String, AgentInstance> activeAgents = new ConcurrentHashMap<>();

    public AgentRegistryService(AgentInstanceMapper agentMapper,
                                AgentTaskAssignmentMapper assignmentMapper,
                                DagTaskMapper taskMapper,
                                DagTaskService taskService,
                                @Lazy AgentStatusBroadcaster statusBroadcaster,
                                @Lazy AiServiceFactory aiServiceFactory) {
        this.agentMapper = agentMapper;
        this.assignmentMapper = assignmentMapper;
        this.taskMapper = taskMapper;
        this.taskService = taskService;
        this.statusBroadcaster = statusBroadcaster;
        this.aiServiceFactory = aiServiceFactory;
    }

    // ==================== Agent 注册与注销 ====================

    /**
     * 注册新的 Agent 实例
     *
     * @param agentType     MAIN 或 WORKER
     * @param agentName     显示名称
     * @param tenantId      租户 ID
     * @param userId        用户 ID
     * @param maxConcurrency 最大并发数
     * @param capabilities   能力标签列表
     * @return 创建的 Agent 实例
     */
    @Transactional
    public AgentInstance registerAgent(String agentType, String agentName,
                                       String tenantId, String userId,
                                       int maxConcurrency, List<String> capabilities) {
        String agentId = generateAgentId();

        AgentInstance agent = new AgentInstance()
                .setAgentId(agentId)
                .setAgentType(agentType)
                .setAgentName(agentName)
                .setStatus("IDLE")
                .setTenantId(tenantId)
                .setUserId(userId)
                .setMaxConcurrency(maxConcurrency)
                .setCurrentLoad(0)
                .setCapabilities(toJsonArray(capabilities))
                .setStartedAt(LocalDateTime.now())
                .setLastActiveAt(LocalDateTime.now());

        agentMapper.insert(agent);
        activeAgents.put(agentId, agent);

        // 广播注册事件
        statusBroadcaster.broadcastAgentRegistered(agent);

        logger.info("注册 Agent: agentId={}, type={}, name={}", agentId, agentType, agentName);

        return agent;
    }

    /**
     * 停止 Agent
     */
    @Transactional
    public void stopAgent(String agentId) {
        AgentInstance agent = getAgent(agentId);
        if (agent == null) {
            logger.warn("Agent 不存在: {}", agentId);
            return;
        }

        agent.setStatus("STOPPED");
        agent.setStoppedAt(LocalDateTime.now());
        agentMapper.updateById(agent);

        activeAgents.remove(agentId);

        // 广播停止事件
        statusBroadcaster.broadcastAgentStopped(agent);

        logger.info("停止 Agent: agentId={}", agentId);
    }

    /**
     * 获取 Agent 实例
     */
    public AgentInstance getAgent(String agentId) {
        AgentInstance agent = activeAgents.get(agentId);
        if (agent == null) {
            agent = agentMapper.selectById(agentId);
            if (agent != null && !"STOPPED".equals(agent.getStatus())) {
                activeAgents.put(agentId, agent);
            }
        }
        return agent;
    }

    /**
     * 获取所有活跃 Agent（从内存缓存）
     */
    public List<AgentInstance> getActiveAgents() {
        return new ArrayList<>(activeAgents.values());
    }

    /**
     * 从数据库加载所有活跃 Agent
     * 用于应用启动时恢复 Agent 状态
     */
    public List<AgentInstance> loadActiveAgentsFromDb() {
        List<AgentInstance> dbAgents = agentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentInstance>()
                        .ne(AgentInstance::getStatus, "STOPPED"));

        // 同步到内存缓存
        for (AgentInstance agent : dbAgents) {
            activeAgents.put(agent.getAgentId(), agent);
        }

        logger.info("从数据库加载活跃 Agent: {} 个", dbAgents.size());
        return new ArrayList<>(activeAgents.values());
    }

    /**
     * 按类型获取 Agent
     */
    public List<AgentInstance> getAgentsByType(String agentType) {
        return activeAgents.values().stream()
                .filter(a -> a.getAgentType().equals(agentType))
                .toList();
    }

    // ==================== Worker 任务领取 ====================

    /**
     * Worker Agent 原子性领取任务
     * 使用乐观锁防止并发竞争
     *
     * @param agentId Worker Agent ID
     * @return 领取的任务，如果没有可用任务则返回 null
     */
    @Transactional
    public DagTask claimTaskForWorker(String agentId) {
        AgentInstance agent = getAgent(agentId);

        if (agent == null) {
            throw new IllegalArgumentException("Agent 不存在: " + agentId);
        }

        if (!"WORKER".equals(agent.getAgentType())) {
            throw new IllegalStateException("只有 Worker Agent 可以领取任务");
        }

        // 检查 Agent 是否有空闲容量
        if (!"IDLE".equals(agent.getStatus()) && !"WORKING".equals(agent.getStatus())) {
            logger.debug("Agent 状态不允许领取: agentId={}, status={}", agentId, agent.getStatus());
            return null;
        }

        if (agent.getCurrentLoad() >= agent.getMaxConcurrency()) {
            logger.debug("Agent 已达最大负载: agentId={}, load={}", agentId, agent.getCurrentLoad());
            return null;
        }

        // 尝试原子领取任务
        DagTask task = tryClaimTaskOptimistically(agentId);

        if (task != null) {
            // 更新 Agent 状态
            int newLoad = agent.getCurrentLoad() + 1;
            String newStatus = newLoad > 0 ? "WORKING" : "IDLE";
            agent.setStatus(newStatus);
            agent.setCurrentTaskId(task.getTaskId());
            agent.setCurrentLoad(newLoad);
            agent.setLastActiveAt(LocalDateTime.now());
            agentMapper.updateById(agent);
            activeAgents.put(agentId, agent);

            // 创建分配记录
            createAssignment(agentId, task.getTaskId(), null, "CLAIMED");

            // 广播任务分配事件
            statusBroadcaster.broadcastTaskAssigned(agentId, task.getTaskId(), task.getSubject());
            // 广播 Agent 状态变更
            statusBroadcaster.broadcastAgentStatusChanged(agent);

            logger.info("Worker 领取任务: agentId={}, taskId={}, load={}", agentId, task.getTaskId(), newLoad);
        }

        return task;
    }

    /**
     * 尝试原子领取任务（乐观锁）
     */
    private DagTask tryClaimTaskOptimistically(String agentId) {
        // 先查找一个就绪任务
        List<DagTask> readyTasks = taskService.getUnblockedTasks();
        logger.debug("Worker尝试领取任务: agentId={}, 可用就绪任务数={}", agentId, readyTasks.size());

        for (DagTask task : readyTasks) {
            logger.debug("检查任务: taskId={}, status={}, blockedBy={}",
                    task.getTaskId(), task.getStatus(), task.getBlockedBy());

            if (!"pending".equals(task.getStatus())) {
                logger.debug("跳过非pending任务: taskId={}, status={}", task.getTaskId(), task.getStatus());
                continue;
            }

            // 使用乐观锁尝试更新
            int version = task.getAssignmentVersion() != null ? task.getAssignmentVersion() : 0;
            int updated = taskMapper.updateTaskAssignment(
                    task.getTaskId(), agentId, version);

            logger.debug("乐观锁更新结果: taskId={}, updated={}, version={}",
                    task.getTaskId(), updated, version);

            if (updated > 0) {
                // 领取成功，重新获取任务
                logger.info("任务领取成功: agentId={}, taskId={}", agentId, task.getTaskId());
                return taskMapper.selectById(task.getTaskId());
            }
        }

        logger.debug("Worker未能领取到任务: agentId={}", agentId);
        return null;
    }

    // ==================== 任务释放 ====================

    /**
     * 释放任务（完成或失败后）
     * 同时更新 DagTask 状态并通知相关方
     */
    @Transactional
    public void releaseTask(String agentId, String taskId, boolean success, String result) {
        AgentInstance agent = getAgent(agentId);
        if (agent == null) return;

        // 1. 更新 DagTask 状态（如果任务尚未被标记为完成）
        DagTask task = taskMapper.selectById(taskId);
        if (task != null && !"completed".equals(task.getStatus()) && !"failed".equals(task.getStatus())) {
            if (success) {
                task.setStatus("completed");
                task.setResult(result);
            } else {
                task.setStatus("failed");
                task.setError(result);
            }
            task.setCompletedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            logger.info("任务状态已更新: taskId={}, status={}", taskId, success ? "completed" : "failed");
        }

        // 2. 更新 Agent 负载
        int newLoad = Math.max(0, agent.getCurrentLoad() - 1);
        String newStatus = newLoad == 0 ? "IDLE" : "WORKING";

        agent.setStatus(newStatus);
        agent.setCurrentLoad(newLoad);
        agent.setCurrentTaskId(null);
        agent.setLastActiveAt(LocalDateTime.now());
        agentMapper.updateById(agent);
        activeAgents.put(agentId, agent);

        // 3. 更新分配记录
        assignmentMapper.releaseAssignment(agentId, taskId);

        // 4. 通知 MainAgent 任务完成并广播状态变更
        if (task != null && task.getSessionId() != null) {
            notifyMainAgentTaskCompleted(task, success, result, agentId);
        } else {
            // 没有关联 session 时直接广播
            statusBroadcaster.broadcastTaskCompleted(agentId, taskId, success, result);
        }

        // 广播 Agent 状态变更
        AgentInstance updatedAgent = getAgent(agentId);
        if (updatedAgent != null) {
            statusBroadcaster.broadcastAgentStatusChanged(updatedAgent);
        }

        logger.info("释放任务: agentId={}, taskId={}, success={}, newLoad={}", agentId, taskId, success, newLoad);
    }

    /**
     * 通知 MainAgent 子任务已完成
     * 通过会话机制让 MainAgent 感知任务进度
     */
    private void notifyMainAgentTaskCompleted(DagTask task, boolean success, String result, String workerAgentId) {
        String sessionId = task.getSessionId();

        // 广播任务完成事件（前端和 MainAgent 可通过 SSE 接收）
        statusBroadcaster.broadcastTaskCompleted(workerAgentId, task.getTaskId(), success, result);

        // 检查该 session 是否所有任务都已完成，如果是则触发汇总
        checkAndNotifySessionCompletion(sessionId);

        logger.debug("已通知任务完成: taskId={}, sessionId={}, success={}", task.getTaskId(), sessionId, success);
    }

    /**
     * 检查 session 的所有任务是否完成，触发汇总通知
     */
    private void checkAndNotifySessionCompletion(String sessionId) {
        List<DagTask> sessionTasks = taskService.getTasksBySession(sessionId);
        if (sessionTasks.isEmpty()) return;

        boolean allCompleted = sessionTasks.stream()
                .allMatch(t -> "completed".equals(t.getStatus()) || "failed".equals(t.getStatus()));

        if (allCompleted) {
            long successCount = sessionTasks.stream().filter(t -> "completed".equals(t.getStatus())).count();
            long failedCount = sessionTasks.stream().filter(t -> "failed".equals(t.getStatus())).count();

            // 广播整个 session 完成事件（前端可据此更新看板）
            statusBroadcaster.broadcastSessionCompleted(sessionId, successCount, failedCount);

            logger.info("Session 所有任务完成: sessionId={}, success={}, failed={}", sessionId, successCount, failedCount);

            // 触发 MainAgent 进行结果汇总
            triggerMainAgentSummary(sessionId, sessionTasks);
        }
    }

    /**
     * 触发 MainAgent 进行结果汇总
     * 当所有子任务完成后，调用对话 LLM 生成总结
     */
    private void triggerMainAgentSummary(String sessionId, List<DagTask> completedTasks) {
        logger.info("触发任务汇总: sessionId={}, tasks={}", sessionId, completedTasks.size());

        try {
            // 构造汇总提示
            String summaryPrompt = buildSummaryPrompt(completedTasks);

            // 直接调用对话 LLM 生成总结（不需要单独的 MainAgent）
            AiChatModel aiModel = aiServiceFactory.getModel("openai", sessionId);
            String summary = aiModel.chat(sessionId, summaryPrompt);

            logger.info("任务汇总完成: sessionId={}, summaryLength={}", sessionId, summary.length());

            // 广播汇总完成事件（推送给前端显示）
            statusBroadcaster.broadcastSummaryCompleted(sessionId, summary);

        } catch (Exception e) {
            logger.error("任务汇总失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 构造汇总提示
     */
    private String buildSummaryPrompt(List<DagTask> completedTasks) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("所有子任务已完成，请根据以下执行结果进行汇总：\n\n");

        prompt.append("## 任务执行结果\n\n");
        for (DagTask task : completedTasks) {
            prompt.append("### ").append(task.getSubject()).append("\n");
            prompt.append("- 状态: ").append("completed".equals(task.getStatus()) ? "✅ 成功" : "❌ 失败").append("\n");
            if (task.getResult() != null && !task.getResult().isEmpty()) {
                prompt.append("- 结果: ").append(truncateText(task.getResult(), 300)).append("\n");
            }
            if (task.getError() != null && !task.getError().isEmpty()) {
                prompt.append("- 错误: ").append(task.getError()).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("## 汇总要求\n");
        prompt.append("1. 总结整体任务完成情况\n");
        prompt.append("2. 汇总关键结果和发现\n");
        prompt.append("3. 如有失败任务，说明影响和建议\n");
        prompt.append("4. 给出简洁的结论（不超过200字）\n");

        return prompt.toString();
    }

    /**
     * 截断文本
     */
    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    // ==================== MainAgent 任务创建记录 ====================

    /**
     * 记录 MainAgent 创建的任务
     */
    @Transactional
    public void recordTaskCreated(String agentId, String taskId, String sessionId) {
        createAssignment(agentId, taskId, sessionId, "CREATED");
    }

    // ==================== 状态更新 ====================

    /**
     * 更新 Agent 状态
     */
    @Transactional
    public void updateAgentStatus(String agentId, String status, String currentTaskId) {
        AgentInstance agent = getAgent(agentId);
        if (agent == null) return;

        agent.setStatus(status);
        agent.setCurrentTaskId(currentTaskId);
        agent.setLastActiveAt(LocalDateTime.now());
        agentMapper.updateById(agent);
        activeAgents.put(agentId, agent);
    }

    // ==================== 工具方法 ====================

    private void createAssignment(String agentId, String taskId, String sessionId, String assignmentType) {
        AgentTaskAssignment assignment = new AgentTaskAssignment()
                .setAgentId(agentId)
                .setTaskId(taskId)
                .setSessionId(sessionId)
                .setAssignmentType(assignmentType)
                .setStatus("ASSIGNED")
                .setAssignedAt(LocalDateTime.now());

        assignmentMapper.insert(assignment);
    }

    private String generateAgentId() {
        return "agent-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append("\"").append(list.get(i)).append("\"");
            if (i < list.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}