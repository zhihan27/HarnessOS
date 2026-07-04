package com.harness.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.harness.core.entity.SubAgentTask;
import com.harness.core.enums.SubAgentStatus;
import com.harness.core.mapper.SubAgentTaskMapper;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 子 Agent 服务
 * 负责创建、执行、管理子任务
 *
 * 关键设计：
 * 1. 上下文隔离：每个子 Agent 创建全新的 ChatMemory 实例，不继承父 Agent 对话历史
 * 2. 自动重试：执行失败时自动重试，直到成功或耗尽重试次数，无需手动干预
 */
@Service
public class SubAgentService {

    private static final Logger logger = LoggerFactory.getLogger(SubAgentService.class);

    /**
     * 最大嵌套深度（防止无限递归）
     */
    private static final int MAX_DEPTH = 3;

    /**
     * 默认最大重试次数
     */
    private static final int DEFAULT_MAX_RETRIES = 3;

    private final SubAgentTaskMapper subAgentTaskMapper;
    private final OpenAiChatModel openAiChatModel;

    public SubAgentService(SubAgentTaskMapper subAgentTaskMapper,
                          OpenAiChatModel openAiChatModel) {
        this.subAgentTaskMapper = subAgentTaskMapper;
        this.openAiChatModel = openAiChatModel;
    }

    /**
     * 创建子任务
     */
    @Transactional
    public SubAgentTask createSubTask(String tenantId, String userId,
                                           String parentSessionId, String taskType,
                                           String description, String input) {
        String subAgentSessionId = generateSubAgentSessionId();

        int depth = calculateDepth(parentSessionId);
        if (depth > MAX_DEPTH) {
            throw new IllegalStateException("超过最大嵌套深度限制: " + MAX_DEPTH);
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
        logger.info("创建子任务: id={}, depth={}, type={}, subSession={}",
                    task.getId(), depth, taskType, subAgentSessionId);

        return task;
    }

    /**
     * 执行子任务（含自动重试机制）
     *
     * 关键：失败时自动重试，直到成功或耗尽重试次数
     * 所有逻辑由 AI 通过 Tool 调用触发，无需手动干预
     */
    @Transactional
    public ExecuteResult executeSubTask(Long taskId) {
        SubAgentTask task = subAgentTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("子任务不存在: " + taskId);
        }

        // 循环执行直到成功或耗尽重试次数
        while (task.getRetryCount() <= task.getMaxRetries()) {
            int currentRetry = task.getRetryCount();

            // 更新状态为执行中
            task.setStatus(SubAgentStatus.RUNNING.getValue());
            task.setStartedAt(LocalDateTime.now());
            task.setLastError(null);
            subAgentTaskMapper.updateById(task);

            try {
                String prompt = buildSubAgentPrompt(task);
                AiChatService isolatedService = createIsolatedService();

                logger.info("执行子任务: id={}, retryCount={}/{}",
                           taskId, currentRetry, task.getMaxRetries());

                String result = isolatedService.chat(prompt);

                // 成功：更新状态并返回
                task.setStatus(SubAgentStatus.COMPLETED.getValue());
                task.setResult(result);
                task.setCompletedAt(LocalDateTime.now());
                subAgentTaskMapper.updateById(task);

                logger.info("子任务成功: id={}, retryCount={}", taskId, currentRetry);
                return new ExecuteResult(true, result, currentRetry);

            } catch (Exception e) {
                logger.error("子任务执行失败: id={}, retryCount={}, error={}",
                           taskId, currentRetry, e.getMessage());

                task.setLastError(e.getMessage());
                task.setRetryCount(currentRetry + 1);

                if (task.getRetryCount() > task.getMaxRetries()) {
                    task.setStatus(SubAgentStatus.FAILED.getValue());
                    task.setResult("执行失败（已尝试" + (currentRetry + 1) + "次）: " + e.getMessage());
                    task.setCompletedAt(LocalDateTime.now());
                    subAgentTaskMapper.updateById(task);

                    logger.warn("子任务终态失败: id={}, totalAttempts={}", taskId, currentRetry + 1);
                    return new ExecuteResult(false, "耗尽重试次数: " + e.getMessage(), currentRetry + 1);
                }

                subAgentTaskMapper.updateById(task);
                logger.info("将自动重试: id={}, nextRetry={}", taskId, task.getRetryCount());
            }
        }

        return new ExecuteResult(false, "未知错误", task.getRetryCount());
    }

    /**
     * 创建隔离的 AI 服务实例（关键：上下文隔离）
     */
    private AiChatService createIsolatedService() {
        return AiServices.builder(AiChatService.class)
                .chatModel(openAiChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }

    /**
     * 构建子任务提示词
     */
    private String buildSubAgentPrompt(SubAgentTask task) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个专门的子任务执行 Agent。\n\n");
        prompt.append("## 任务信息\n");
        prompt.append("- 任务类型: ").append(task.getTaskType()).append("\n");
        prompt.append("- 任务描述: ").append(task.getTaskDescription()).append("\n\n");

        if (task.getTaskInput() != null && !task.getTaskInput().isEmpty()) {
            prompt.append("## 任务输入\n");
            prompt.append(task.getTaskInput()).append("\n\n");
        }

        prompt.append("## 执行要求\n");
        prompt.append("1. 独立完成指定任务\n");
        prompt.append("2. 完成后提供简洁的结果摘要\n");

        return prompt.toString();
    }

    /**
     * 查询子任务状态
     */
    public SubAgentTask getSubTaskStatus(Long taskId) {
        return subAgentTaskMapper.selectById(taskId);
    }

    /**
     * 查询父任务的所有子任务
     */
    public List<SubAgentTask> getSubTasksByParentSession(String parentSessionId) {
        LambdaQueryWrapper<SubAgentTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubAgentTask::getParentSessionId, parentSessionId)
               .orderByAsc(SubAgentTask::getCreatedAt);
        return subAgentTaskMapper.selectList(wrapper);
    }

    /**
     * 生成子会话 ID
     */
    private String generateSubAgentSessionId() {
        return "SUB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /**
     * 计算嵌套深度
     */
    private int calculateDepth(String parentSessionId) {
        LambdaQueryWrapper<SubAgentTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubAgentTask::getSubAgentSessionId, parentSessionId);

        SubAgentTask parentTask = subAgentTaskMapper.selectOne(wrapper);
        if (parentTask == null) {
            return 0;
        }
        return parentTask.getDepth() + 1;
    }

    /**
     * 执行结果 DTO
     */
    public record ExecuteResult(boolean success, String result, int retryCount) {}
}