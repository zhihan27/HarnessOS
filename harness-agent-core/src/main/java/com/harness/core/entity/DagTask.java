package com.harness.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * DAG 任务实体类（任务看板系统）
 *
 * 与 SubAgent 的核心区别：
 * - 异步非阻塞执行（SubAgent 是同步阻塞）
 * - DAG 依赖图编排（SubAgent 是纯父子嵌套）
 * - 跨会话持久化（SubAgent 是会话级，用完即丢）
 *
 * 关键特性：
 * - blockedBy：依赖的任务 ID 列表（JSON 格式）
 * - blocks：被哪些任务依赖（JSON 格式）
 * - owner：责任人（Agent ID 或人类标识）
 */
@Data
@Accessors(chain = true)
@TableName("dag_tasks")
public class DagTask {

    /**
     * 任务唯一 ID（UUID 格式）
     */
    @TableId(type = IdType.INPUT)
    private String taskId;

    /**
     * 简要标题（祈使句形式）
     */
    private String subject;

    /**
     * 详细需求描述
     */
    private String description;

    /**
     * 执行时 spinner 显示文案（现在进行时）
     */
    private String activeForm;

    /**
     * 状态: pending, in_progress, completed, deleted
     */
    private String status;

    /**
     * 责任人（Agent ID 或人类标识）
     */
    private String owner;

    /**
     * 创建会话 ID（可选，记录来源）
     */
    private String sessionId;

    /**
     * 租户 ID（用于多租户隔离）
     */
    private String tenantId;

    /**
     * 用户 ID（区分不同用户）
     */
    private String userId;

    /**
     * 依赖的任务 ID 列表（JSON 数组格式）
     * 这些任务必须完成后，当前任务才能开始
     */
    private String blockedBy;

    /**
     * 被哪些任务依赖（JSON 数组格式）
     * 当前任务完成后，这些任务会被解锁
     */
    private String blocks;

    /**
     * 扩展元数据（JSON 格式）
     */
    private String metadata;

    /**
     * 执行结果
     */
    private String result;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 分配执行的 Worker Agent ID
     */
    private String assignedAgentId;

    /**
     * 乐观锁版本号（用于原子领取任务）
     */
    private Integer assignmentVersion;

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