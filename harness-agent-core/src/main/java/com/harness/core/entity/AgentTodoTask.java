package com.harness.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Agent 任务实体类（秘书模式）
 * 极简架构：只记录任务描述和完成状态
 */
@Data
@Accessors(chain = true)
@TableName("agent_todo_tasks")
public class AgentTodoTask {

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
     * 会话 ID（区分同用户的不同任务流）
     */
    private String sessionId;

    /**
     * 任务描述（如：Plan: 1.读取配置, 2.修改配置, 3.重启服务）
     */
    private String taskDescription;

    /**
     * 状态: PENDING（待完成）, COMPLETED（已完成）
     */
    private String status;

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