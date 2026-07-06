package com.harness.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 聊天消息实体类
 * 持久化会话消息历史
 */
@Data
@Accessors(chain = true)
@TableName("chat_messages")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的会话ID
     */
    private String sessionId;

    /**
     * 消息类型：SYSTEM/USER/AI/TOOL
     */
    private String messageType;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息Token数
     */
    private Integer tokenCount;

    /**
     * 消息序号（用于排序）
     */
    private Integer messageOrder;

    /**
     * 是否被压缩过
     */
    private Boolean isCompressed;

    /**
     * 是否重要消息（压缩时优先保留）
     */
    private Boolean isImportant;

    /**
     * 工具名称（TOOL类型时）
     */
    private String toolName;

    /**
     * 工具参数（TOOL类型时）
     */
    private String toolArgs;

    /**
     * 工具返回结果（TOOL类型时）
     */
    private String toolResult;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}