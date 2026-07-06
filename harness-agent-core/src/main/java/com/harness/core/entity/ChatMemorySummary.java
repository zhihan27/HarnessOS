package com.harness.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 记忆摘要实体类
 * 存储压缩后的会话摘要
 */
@Data
@Accessors(chain = true)
@TableName("chat_memory_summaries")
public class ChatMemorySummary {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的会话ID
     */
    private String sessionId;

    /**
     * 摘要类型：FULL/PARTIAL
     */
    private String summaryType;

    /**
     * 摘要内容
     */
    private String summaryContent;

    /**
     * 被压缩的起始消息ID
     */
    private Long startMessageId;

    /**
     * 被压缩的结束消息ID
     */
    private Long endMessageId;

    /**
     * 被压缩的消息数量
     */
    private Integer messagesCount;

    /**
     * 压缩前的Token总数
     */
    private Integer originalTokens;

    /**
     * 压缩后的Token数
     */
    private Integer compressedTokens;

    /**
     * 节省的Token数
     */
    private Integer tokenSaved;

    /**
     * 压缩比率
     */
    private BigDecimal compressionRatio;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}