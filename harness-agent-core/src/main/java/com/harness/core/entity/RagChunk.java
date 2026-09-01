package com.harness.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG 文档分段及其向量数据实体。
 */
@Data
@TableName("rag_chunks")
public class RagChunk {

    @TableId(type = IdType.INPUT)
    /** 分段唯一标识。 */
    private String id;
    /** 所属知识库标识。 */
    private String knowledgeBaseId;
    /** 所属文档标识。 */
    private String documentId;
    /** 分段在文档中的顺序，从 1 开始。 */
    private Integer position;
    /** 分段标题。 */
    private String title;
    /** 分段正文。 */
    private String content;
    /** 分段 token 数量估算值。 */
    private Integer tokenCount;
    /** 1024 维向量的数据库映射字段。 */
    private String embedding;
    /** 是否参与检索。 */
    private Boolean active;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
}
