package com.harness.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.harness.core.entity.SubAgentTask;
import com.harness.core.enums.SubAgentStatus;
import com.harness.core.mapper.SubAgentTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * SubAgent 服务
 * 只负责创建子任务记录
 */
@Service
public class SubAgentService {

    private static final Logger logger = LoggerFactory.getLogger(SubAgentService.class);

    private static final int MAX_DEPTH = 3;
    private static final int DEFAULT_MAX_RETRIES = 3;

    private final SubAgentTaskMapper subAgentTaskMapper;

    public SubAgentService(SubAgentTaskMapper subAgentTaskMapper) {
        this.subAgentTaskMapper = subAgentTaskMapper;
    }

    /**
     * 创建子任务
     */
    @Transactional
    public SubAgentTask createSubTask(String tenantId, String userId,
                                       String parentSessionId, String taskType,
                                       String description, String input) {
        String subAgentSessionId = "SUB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        int depth = calculateDepth(parentSessionId);
        if (depth > MAX_DEPTH) {
            throw new IllegalStateException("超过最大嵌套深度: " + MAX_DEPTH);
        }

        SubAgentTask task = new SubAgentTask()
                .setTenantId(tenantId)
                .setUserId(userId)
                .setParentSessionId(parentSessionId)
                .setSubAgentSessionId(subAgentSessionId)
                .setDepth(depth)
                .setTaskType(taskType)
                .setTaskDescription(description)
                .setTaskInput(input)
                .setStatus(SubAgentStatus.PENDING.getValue())
                .setRetryCount(0)
                .setMaxRetries(DEFAULT_MAX_RETRIES);

        subAgentTaskMapper.insert(task);
        logger.info("创建子任务: id={}, description={}", task.getId(), description);

        return task;
    }

    /**
     * 查询子任务状态
     */
    public SubAgentTask getSubTaskStatus(Long taskId) {
        return subAgentTaskMapper.selectById(taskId);
    }

    /**
     * 查询会话的所有子任务
     */
    public List<SubAgentTask> getSubTasksByParentSession(String parentSessionId) {
        LambdaQueryWrapper<SubAgentTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubAgentTask::getParentSessionId, parentSessionId)
               .orderByAsc(SubAgentTask::getCreatedAt);
        return subAgentTaskMapper.selectList(wrapper);
    }

    private int calculateDepth(String parentSessionId) {
        LambdaQueryWrapper<SubAgentTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubAgentTask::getSubAgentSessionId, parentSessionId);
        SubAgentTask parentTask = subAgentTaskMapper.selectOne(wrapper);
        return parentTask == null ? 0 : parentTask.getDepth() + 1;
    }
}