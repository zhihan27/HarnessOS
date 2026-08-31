package com.harness.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harness.core.dto.RagSearchRow;
import com.harness.core.entity.RagChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * RAG 分段数据访问接口。
 */
@Mapper
public interface RagChunkMapper extends BaseMapper<RagChunk> {

    /**
     * 分别召回向量候选和全文候选，并合并去重。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param vector 查询向量字面量
     * @param queryText 原始查询文本
     * @return 带有向量分数和全文分数的候选分段
     */
    @Select("""
            /* 向量召回：按余弦距离排序，覆盖语义相近但没有完全相同关键词的分段。 */
            WITH vector_candidates AS (
                SELECT c.id
                FROM rag_chunks c
                WHERE c.knowledge_base_id = #{knowledgeBaseId}
                  AND c.active = TRUE
                ORDER BY c.embedding <=> CAST(#{vector} AS vector)
                LIMIT 500
            ),
            /* 关键词召回：优先完整短语，再使用 PostgreSQL 全文排名。 */
            keyword_candidates AS (
                SELECT c.id
                FROM rag_chunks c
                WHERE c.knowledge_base_id = #{knowledgeBaseId}
                  AND c.active = TRUE
                ORDER BY
                    CASE
                        WHEN POSITION(
                                 LOWER(#{queryText}) IN
                                 LOWER(COALESCE(c.title, '') || ' ' || COALESCE(c.content, ''))
                             ) > 0 THEN 1
                        ELSE 0
                    END DESC,
                    ts_rank_cd(
                        c.search_vector,
                        plainto_tsquery('simple', #{queryText}),
                        32
                    ) DESC
                LIMIT 500
            ),
            /* 两路候选去重后统一计算分数，交给 Service 应用模式和阈值。 */
            candidates AS (
                SELECT id FROM vector_candidates
                UNION
                SELECT id FROM keyword_candidates
            )
            SELECT c.id,
                   c.document_id,
                   d.name AS document_name,
                   c.title,
                   c.content,
                   GREATEST(0, 1 - (c.embedding <=> CAST(#{vector} AS vector))) AS vector_score,
                   ts_rank_cd(
                       c.search_vector,
                       plainto_tsquery('simple', #{queryText}),
                       32
                   ) AS keyword_score
            FROM rag_chunks c
            JOIN candidates candidate ON candidate.id = c.id
            JOIN rag_documents d ON d.id = c.document_id
            """)
    List<RagSearchRow> searchCandidates(
            @Param("knowledgeBaseId") String knowledgeBaseId,
            @Param("vector") String vector,
            @Param("queryText") String queryText
    );

    /**
     * 写入分段，并将字符串形式的向量显式转换为 pgvector。
     *
     * @param chunk 分段数据
     * @return 受影响行数
     */
    @Insert("""
            INSERT INTO rag_chunks (
                id, knowledge_base_id, document_id, position, title, content,
                token_count, embedding, active, created_at, updated_at
            ) VALUES (
                #{id}, #{knowledgeBaseId}, #{documentId}, #{position}, #{title}, #{content},
                #{tokenCount}, CAST(#{embedding} AS vector), #{active},
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            )
            """)
    int insertVectorChunk(RagChunk chunk);
}
