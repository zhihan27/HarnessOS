package com.harness.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG 知识库文档实体。
 */
@Data
@TableName("rag_documents")
public class RagDocument {

    @TableId(type = IdType.INPUT)
    /** 文档唯一标识。 */
    private String id;
    /** 所属知识库标识。 */
    private String knowledgeBaseId;
    /** 文档名称。 */
    private String name;
    /** 文档原始正文。 */
    private String content;
    /** 文档字符数。 */
    private Integer characterCount;
    /** 文档生成的分段数量。 */
    private Integer chunkCount;
    /** 文档处理状态。 */
    private String status;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
}
