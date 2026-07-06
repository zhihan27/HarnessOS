package com.harness.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会话实体类
 * 管理会话元数据和Token统计
 */
@Data
@Accessors(chain = true)
@TableName("chat_sessions")
public class ChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户ID（用于多租户隔离）
     */
    private String tenantId;

    /**
     * 用户ID（区分不同用户）
     */
    private String userId;

    /**
     * 会话ID（全局唯一，LangChain4j memoryId）
     */
    private String sessionId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 模型类型：openai/anthropic
     */
    private String modelType;

    /**
     * 当前会话总Token数
     */
    private Long totalTokens;

    /**
     * 模型最大Token限制
     */
    private Integer maxTokens;

    /**
     * Token使用率百分比
     */
    private BigDecimal tokenUsagePercent;

    /**
     * 是否已压缩
     */
    private Boolean isCompressed;

    /**
     * 最近一次压缩时间
     */
    private LocalDateTime compressedAt;

    /**
     * 累计压缩次数
     */
    private Integer compressionCount;

    /**
     * 会话状态：ACTIVE/ARCHIVED/DELETED
     */
    private String status;

    /**
     * 最后一条消息时间
     */
    private LocalDateTime lastMessageAt;

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