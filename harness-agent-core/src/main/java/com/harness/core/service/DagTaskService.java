package com.harness.core.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.harness.core.entity.DagTask;
import com.harness.core.mapper.DagTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DAG 任务核心业务服务
 *
 * 功能：
 * 1. CRUD 操作
 * 2. 状态管理：pending -> in_progress -> completed
 * 3. 依赖管理：blockedBy/blocks 关系
 * 4. 责任人管理
 */
@Service
public class DagTaskService {

    private static final Logger logger = LoggerFactory.getLogger(DagTaskService.class);

    private final DagTaskMapper taskMapper;
    private final DagDependencyResolver dependencyResolver;

    public DagTaskService(DagTaskMapper taskMapper, DagDependencyResolver dependencyResolver) {
        this.taskMapper = taskMapper;
        this.dependencyResolver = dependencyResolver;
    }

    // ==================== CRUD 操作 ====================

    /**
     * 创建新任务
     *
     * @param subject 简要标题
     * @param description 详细描述
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param sessionId 会话 ID（可选）
     * @return 创建的任务
     */
    @Transactional
    public DagTask createTask(String subject, String description,
                              String tenantId, String userId, String sessionId) {
        String taskId = generateTaskId();

        DagTask task = new DagTask();
        task.setTaskId(taskId);
        task.setSubject(subject);
        task.setDescription(description);
        task.setActiveForm("正在执行: " + subject);
        task.setStatus("pending");
        task.setTenantId(tenantId);
        task.setUserId(userId);
        task.setSessionId(sessionId);
        task.setBlockedBy("[]");
        task.setBlocks("[]");

        taskMapper.insert(task);
        logger.info("创建 DAG 任务: taskId={}, subject={}", taskId, subject);

        return task;
    }

    /**
     * 创建带依赖的任务
     */
    @Transactional
    public DagTask createTask(String subject, String description,
                              String tenantId, String userId, String sessionId,
                              List<String> blockedBy) {
        DagTask task = createTask(subject, description, tenantId, userId, sessionId);

        if (blockedBy != null && !blockedBy.isEmpty()) {
            // 检查依赖是否存在且不会形成循环
            List<DagTask> allTasks = getAllTasks();

            for (String depId : blockedBy) {
                DagTask depTask = getTask(depId);
                if (depTask == null) {
                    logger.warn("依赖任务不存在: {}", depId);
                    continue;
                }

                if (dependencyResolver.wouldCreateCycle(allTasks, task.getTaskId(), depId)) {
                    logger.warn("添加依赖会形成循环: {} -> {}", task.getTaskId(), depId);
                    continue;
                }

                // 设置 blockedBy
                List<String> currentBlockedBy = dependencyResolver.parseJsonArray(task.getBlockedBy());
                currentBlockedBy.add(depId);
                task.setBlockedBy(dependencyResolver.toJsonArray(currentBlockedBy));

                // 设置 blocks（反向依赖）
                List<String> depBlocks = dependencyResolver.parseJsonArray(depTask.getBlocks());
                depBlocks.add(task.getTaskId());
                depTask.setBlocks(dependencyResolver.toJsonArray(depBlocks));
                taskMapper.updateById(depTask);
            }

            taskMapper.updateById(task);
        }

        return task;
    }

    /**
     * 获取任务详情
     */
    public DagTask getTask(String taskId) {
        return taskMapper.selectById(taskId);
    }

    /**
     * 获取所有任务
     */
    public List<DagTask> getAllTasks() {
        return taskMapper.selectList(new QueryWrapper<DagTask>().orderByAsc("created_at"));
    }

    /**
     * 获取活跃任务（pending + in_progress）
     */
    public List<DagTask> getActiveTasks() {
        return taskMapper.findActiveTasks();
    }

    /**
     * 获取指定状态的任务
     */
    public List<DagTask> getTasksByStatus(String status) {
        return taskMapper.findByStatus(status);
    }

    /**
     * 获取指定责任人的任务
     */
    public List<DagTask> getTasksByOwner(String owner) {
        return taskMapper.findByOwner(owner);
    }

    /**
     * 获取指定会话的任务
     */
    public List<DagTask> getTasksBySession(String sessionId) {
        return taskMapper.findBySessionId(sessionId);
    }

    /**
     * 更新任务信息
     */
    @Transactional
    public DagTask updateTask(String taskId, String subject, String description) {
        DagTask task = getTask(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        if (subject != null && !subject.isEmpty()) {
            task.setSubject(subject);
            task.setActiveForm("正在执行: " + subject);
        }
        if (description != null && !description.isEmpty()) {
            task.setDescription(description);
        }

        taskMapper.updateById(task);
        logger.info("更新 DAG 任务: taskId={}", taskId);
        return task;
    }

    // ==================== 状态管理 ====================

    /**
     * 开始执行任务
     * 检查依赖是否满足，不满足则返回错误
     */
    @Transactional
    public DagTask startTask(String taskId) {
        DagTask task = getTask(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        if (!"pending".equals(task.getStatus())) {
            throw new IllegalStateException("任务状态不是 pending: " + task.getStatus());
        }

        // 检查依赖是否完成
        List<DagTask> allTasks = getAllTasks();
        if (!dependencyResolver.areDependenciesCompleted(allTasks, task)) {
            List<String> blockedByList = dependencyResolver.parseJsonArray(task.getBlockedBy());
            throw new IllegalStateException("任务被阻塞，依赖未完成: " + blockedByList);
        }

        task.setStatus("in_progress");
        task.setStartedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        logger.info("开始执行 DAG 任务: taskId={}", taskId);
        return task;
    }

    /**
     * 完成任务
     * 记录结果，解锁依赖此任务的后续任务
     */
    @Transactional
    public DagTask completeTask(String taskId, String result) {
        DagTask task = getTask(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        task.setStatus("completed");
        task.setResult(result);
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        logger.info("完成 DAG 任务: taskId={}, result={}", taskId, result);
        return task;
    }

    /**
     * 标记任务失败
     */
    @Transactional
    public DagTask failTask(String taskId, String error) {
        DagTask task = getTask(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        task.setStatus("failed");
        task.setError(error);
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        logger.error("DAG 任务失败: taskId={}, error={}", taskId, error);
        return task;
    }

    /**
     * 删除任务（软删除）
     */
    @Transactional
    public void deleteTask(String taskId) {
        DagTask task = getTask(taskId);
        if (task == null) {
            return;
        }

        // 清理依赖关系
        List<String> blockedByList = dependencyResolver.parseJsonArray(task.getBlockedBy());
        for (String depId : blockedByList) {
            DagTask depTask = getTask(depId);
            if (depTask != null) {
                List<String> depBlocks = dependencyResolver.parseJsonArray(depTask.getBlocks());
                depBlocks.remove(taskId);
                depTask.setBlocks(dependencyResolver.toJsonArray(depBlocks));
                taskMapper.updateById(depTask);
            }
        }

        List<String> blocksList = dependencyResolver.parseJsonArray(task.getBlocks());
        for (String nextId : blocksList) {
            DagTask nextTask = getTask(nextId);
            if (nextTask != null) {
                List<String> nextBlockedBy = dependencyResolver.parseJsonArray(nextTask.getBlockedBy());
                nextBlockedBy.remove(taskId);
                nextTask.setBlockedBy(dependencyResolver.toJsonArray(nextBlockedBy));
                taskMapper.updateById(nextTask);
            }
        }

        task.setStatus("deleted");
        taskMapper.updateById(task);

        logger.info("删除 DAG 任务: taskId={}", taskId);
    }

    // ==================== 依赖管理 ====================

    /**
     * 添加依赖关系
     * taskId 被 blockedByTaskId 阻塞
     */
    @Transactional
    public DagTask addBlockedBy(String taskId, String blockedByTaskId) {
        DagTask task = getTask(taskId);
        DagTask blockedByTask = getTask(blockedByTaskId);

        if (task == null || blockedByTask == null) {
            throw new IllegalArgumentException("任务不存在");
        }

        // 检查是否会形成循环
        List<DagTask> allTasks = getAllTasks();
        if (dependencyResolver.wouldCreateCycle(allTasks, taskId, blockedByTaskId)) {
            throw new IllegalStateException("添加依赖会形成循环");
        }

        // 设置 blockedBy
        List<String> blockedByList = dependencyResolver.parseJsonArray(task.getBlockedBy());
        if (!blockedByList.contains(blockedByTaskId)) {
            blockedByList.add(blockedByTaskId);
            task.setBlockedBy(dependencyResolver.toJsonArray(blockedByList));
            taskMapper.updateById(task);
        }

        // 设置 blocks（反向依赖）
        List<String> blocksList = dependencyResolver.parseJsonArray(blockedByTask.getBlocks());
        if (!blocksList.contains(taskId)) {
            blocksList.add(taskId);
            blockedByTask.setBlocks(dependencyResolver.toJsonArray(blocksList));
            taskMapper.updateById(blockedByTask);
        }

        logger.info("添加依赖: {} -> {}", taskId, blockedByTaskId);
        return task;
    }

    /**
     * 批量设置依赖关系
     */
    @Transactional
    public DagTask setBlockedBy(String taskId, List<String> blockedByList) {
        DagTask task = getTask(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        // 清除现有依赖
        List<String> oldBlockedBy = dependencyResolver.parseJsonArray(task.getBlockedBy());
        for (String oldDepId : oldBlockedBy) {
            DagTask oldDepTask = getTask(oldDepId);
            if (oldDepTask != null) {
                List<String> oldBlocks = dependencyResolver.parseJsonArray(oldDepTask.getBlocks());
                oldBlocks.remove(taskId);
                oldDepTask.setBlocks(dependencyResolver.toJsonArray(oldBlocks));
                taskMapper.updateById(oldDepTask);
            }
        }

        // 设置新依赖
        for (String newDepId : blockedByList) {
            addBlockedBy(taskId, newDepId);
        }

        return getTask(taskId);
    }

    /**
     * 获取就绪任务（无未完成依赖）
     */
    public List<DagTask> getUnblockedTasks() {
        List<DagTask> allTasks = getAllTasks();
        return dependencyResolver.getReadyTasks(allTasks);
    }

    /**
     * 获取被阻塞的任务
     */
    public List<DagTask> getBlockedTasks() {
        List<DagTask> allTasks = getAllTasks();
        return dependencyResolver.getBlockedTasks(allTasks);
    }

    /**
     * 检查任务是否被阻塞
     */
    public boolean isTaskBlocked(String taskId) {
        DagTask task = getTask(taskId);
        if (task == null) {
            return false;
        }
        return !dependencyResolver.areDependenciesCompleted(getAllTasks(), task);
    }

    /**
     * 获取依赖某个任务的后续任务
     */
    public List<DagTask> getDependents(String taskId) {
        return dependencyResolver.getDependents(getAllTasks(), taskId);
    }

    // ==================== 责任人管理 ====================

    /**
     * 认领任务（设置责任人）
     */
    @Transactional
    public DagTask claimTask(String taskId, String owner) {
        DagTask task = getTask(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        task.setOwner(owner);
        taskMapper.updateById(task);

        logger.info("认领任务: taskId={}, owner={}", taskId, owner);
        return task;
    }

    // ==================== 统计 ====================

    /**
     * 获取进度统计
     */
    public String getProgress() {
        List<DagTask> allTasks = getAllTasks();
        long pending = allTasks.stream().filter(t -> "pending".equals(t.getStatus())).count();
        long inProgress = allTasks.stream().filter(t -> "in_progress".equals(t.getStatus())).count();
        long completed = allTasks.stream().filter(t -> "completed".equals(t.getStatus())).count();
        long blocked = getBlockedTasks().size();

        return String.format(
                "任务进度统计：%d 个任务\n- 待执行: %d\n- 执行中: %d\n- 已完成: %d\n- 被阻塞: %d",
                allTasks.size(), pending, inProgress, completed, blocked
        );
    }

    // ==================== 工具方法 ====================

    /**
     * 生成任务 ID
     */
    private String generateTaskId() {
        return "task-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}