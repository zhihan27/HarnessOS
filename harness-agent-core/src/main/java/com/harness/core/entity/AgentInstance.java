package com.harness.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Agent 实例实体类
 *
 * 支持多 Agent 协作架构：
 * - MAIN: 主 Agent，负责复杂任务拆解
 * - WORKER: 工作 Agent，负责原子任务执行
 *
 * 状态流转：IDLE -> WORKING -> IDLE 或 STOPPED
 */
@Data
@Accessors(chain = true)
@TableName("agent_instances")
public class AgentInstance {

    /**
     * Agent 唯一 ID（格式: agent-{uuid-8}）
     */
    @TableId(type = IdType.INPUT)
    private String agentId;

    /**
     * Agent 类型：MAIN 或 WORKER
     */
    private String agentType;

    /**
     * Agent 显示名称（如 MainAgent-01, Worker-Alpha）
     */
    private String agentName;

    /**
     * Agent 状态：IDLE, WORKING, STOPPED, ERROR
     */
    private String status;

    /**
     * 当前执行的任务 ID（IDLE 时为 null）
     */
    private String currentTaskId;

    /**
     * 最大并发任务数（Worker: 1-3, Main: 1）
     */
    private Integer maxConcurrency;

    /**
     * 当前负载（正在执行的任务数）
     */
    private Integer currentLoad;

    /**
     * Agent 能力标签（JSON 数组格式）
     * 如: ["CODING", "RESEARCH", "TESTING"]
     */
    private String capabilities;

    /**
     * 租户 ID
     */
    private String tenantId;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 关联会话 ID（MainAgent 使用）
     */
    private String sessionId;

    /**
     * Agent 启动时间
     */
    private LocalDateTime startedAt;

    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveAt;

    /**
     * Agent 停止时间
     */
    private LocalDateTime stoppedAt;

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