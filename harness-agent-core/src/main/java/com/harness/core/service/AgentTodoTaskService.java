package com.harness.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.harness.core.entity.AgentTodoTask;
import com.harness.core.enums.TaskStatus;
import com.harness.core.mapper.AgentTodoTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Agent 任务服务层（秘书模式）
 * 极简架构：只提供 addTodo、listTodo、finishTodo 三个核心功能
 */
@Service
public class AgentTodoTaskService {

    private static final Logger logger = LoggerFactory.getLogger(AgentTodoTaskService.class);

    private final AgentTodoTaskMapper taskMapper;

    public AgentTodoTaskService(AgentTodoTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    /**
     * 添加任务
     *
     * @param tenantId        租户 ID
     * @param userId          用户 ID
     * @param sessionId       会话 ID
     * @param description     任务描述
     * @return 创建的任务实体
     */
    @Transactional
    public AgentTodoTask createTask(String tenantId, String userId, String sessionId,
                                    String description, Object payload) {
        logger.info("添加任务: tenant={}, user={}, session={}, description={}",
                    tenantId, userId, sessionId, description);

        AgentTodoTask task = new AgentTodoTask()
                .setTenantId(tenantId)
                .setUserId(userId)
                .setSessionId(sessionId)
                .setTaskDescription(description)
                .setStatus(TaskStatus.PENDING.getValue());

        taskMapper.insert(task);
        logger.info("任务添加成功: id={}", task.getId());

        return task;
    }

    /**
     * 获取会话的所有任务（清单）
     *
     * @param sessionId 会话 ID
     * @return 任务列表
     */
    public List<AgentTodoTask> getAllTasks(String sessionId) {
        LambdaQueryWrapper<AgentTodoTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTodoTask::getSessionId, sessionId)
               .orderByAsc(AgentTodoTask::getCreatedAt);
        return taskMapper.selectList(wrapper);
    }

    /**
     * 标记任务完成
     *
     * @param taskId 任务 ID
     * @return 是否标记成功
     */
    @Transactional
    public boolean markCompleted(Long taskId) {
        AgentTodoTask task = taskMapper.selectById(taskId);
        if (task == null) {
            logger.warn("任务不存在: id={}", taskId);
            return false;
        }

        task.setStatus(TaskStatus.COMPLETED.getValue());
        int updated = taskMapper.updateById(task);
        logger.info("任务已完成: id={}", taskId);

        return updated > 0;
    }

    /**
     * 根据会话 ID 删除所有任务（清理会话）
     *
     * @param sessionId 会话 ID
     * @return 删除的任务数量
     */
    @Transactional
    public int deleteBySessionId(String sessionId) {
        LambdaQueryWrapper<AgentTodoTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTodoTask::getSessionId, sessionId);
        int deleted = taskMapper.delete(wrapper);
        logger.info("删除会话任务: session={}, count={}", sessionId, deleted);
        return deleted;
    }
}