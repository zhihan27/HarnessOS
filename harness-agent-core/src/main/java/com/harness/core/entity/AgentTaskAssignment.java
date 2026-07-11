package com.harness.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Agent-任务关联实体类
 *
 * 记录 Agent 与任务的关联关系：
 * - CREATED: MainAgent 创建的任务
 * - CLAIMED: WorkerAgent 领取的任务
 *
 * 用于追踪任务来源和 Agent 工作历史
 */
@Data
@Accessors(chain = true)
@TableName("agent_task_assignments")
public class AgentTaskAssignment {

    /**
     * 自增主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Agent ID
     */
    private String agentId;

    /**
     * 任务 ID
     */
    private String taskId;

    /**
     * 会话 ID（关联上下文）
     */
    private String sessionId;

    /**
     * 分配类型：CREATED（MainAgent创建）或 CLAIMED（Worker领取）
     */
    private String assignmentType;

    /**
     * 分配状态：ASSIGNED, EXECUTING, COMPLETED, RELEASED
     */
    private String status;

    /**
     * 分配时间
     */
    private LocalDateTime assignedAt;

    /**
     * 释放时间
     */
    private LocalDateTime releasedAt;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}