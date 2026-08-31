package com.harness.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG 知识库配置实体。
 */
@Data
@TableName("rag_knowledge_bases")
public class RagKnowledgeBase {

    @TableId(type = IdType.INPUT)
    /** 知识库唯一标识。 */
    private String id;
    /** 知识库名称。 */
    private String name;
    /** 知识库描述。 */
    private String description;
    /** 默认检索模式：embedding、keywords 或 blend。 */
    private String retrievalMode;
    /** 命中测试最多返回的分段数量。 */
    private Integer topK;
    /** 最低综合相似度阈值。 */
    private Double similarityThreshold;
    /** 文档分段目标长度。 */
    private Integer segmentSize;
    /** 相邻分段重叠长度。 */
    private Integer overlapSize;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
}
