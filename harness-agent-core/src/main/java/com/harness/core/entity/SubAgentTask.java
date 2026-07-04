package com.harness.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Agent 子任务实体类
 * 管理父子 Agent 关系和任务状态
 * 关键设计：父子上下文隔离（通过独立的 subAgentSessionId 实现）
 */
@Data
@Accessors(chain = true)
@TableName("sub_agent_tasks")
public class SubAgentTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户 ID（用于多租户隔离）
     */
    private String tenantId;

    /**
     * 用户 ID（区分不同用户）
     */
    private String userId;

    /**
     * 父 Agent 会话 ID
     */
    private String parentSessionId;

    /**
     * 子 Agent 会话 ID（独立上下文，不继承父 Agent 对话历史）
     */
    private String subAgentSessionId;

    /**
     * 父任务 ID（用于多级嵌套）
     */
    private Long parentTaskId;

    /**
     * 嵌套深度（0=顶层子任务，限制最大 3 层）
     */
    private Integer depth;

    /**
     * 任务类型：RESEARCH, CODING, ANALYSIS, TESTING, DOCUMENTATION, GENERAL
     */
    private String taskType;

    /**
     * 任务描述
     */
    private String taskDescription;

    /**
     * 任务输入参数（JSON 格式）
     */
    private String taskInput;

    /**
     * 状态: PENDING, RUNNING, COMPLETED, FAILED
     */
    private String status;

    /**
     * 执行结果
     */
    private String result;

    /**
     * 已重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数限制（默认 3 次）
     */
    private Integer maxRetries;

    /**
     * 最近一次失败的错误信息
     */
    private String lastError;

    /**
     * 开始执行时间
     */
    private LocalDateTime startedAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}