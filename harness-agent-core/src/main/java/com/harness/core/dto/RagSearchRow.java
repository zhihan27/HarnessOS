package com.harness.core.dto;

import lombok.Data;

/**
 * RAG 检索 SQL 返回的原始分数行。
 */
@Data
public class RagSearchRow {

    /** 分段唯一标识。 */
    private String id;
    /** 所属文档标识。 */
    private String documentId;
    /** 文档名称。 */
    private String documentName;
    /** 分段标题。 */
    private String title;
    /** 分段正文。 */
    private String content;
    /** pgvector 计算出的语义相似度。 */
    private Double vectorScore;
    /** PostgreSQL 全文检索计算出的关键词得分。 */
    private Double keywordScore;
}
