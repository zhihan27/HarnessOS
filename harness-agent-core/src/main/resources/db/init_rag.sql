CREATE EXTENSION IF NOT EXISTS vector;

COMMENT ON EXTENSION vector IS 'RAG 向量存储与相似度检索扩展';

-- rag_knowledge_bases：RAG 知识库主表，保存检索模式、召回数量和分段参数等配置。
CREATE TABLE IF NOT EXISTS rag_knowledge_bases (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    retrieval_mode VARCHAR(20) NOT NULL DEFAULT 'blend',
    top_k INTEGER NOT NULL DEFAULT 5,
    similarity_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.10,
    segment_size INTEGER NOT NULL DEFAULT 600,
    overlap_size INTEGER NOT NULL DEFAULT 80,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE rag_knowledge_bases IS 'RAG 知识库配置表';
COMMENT ON COLUMN rag_knowledge_bases.id IS '知识库唯一标识';
COMMENT ON COLUMN rag_knowledge_bases.name IS '知识库名称';
COMMENT ON COLUMN rag_knowledge_bases.description IS '知识库描述';
COMMENT ON COLUMN rag_knowledge_bases.retrieval_mode IS '检索模式：embedding 向量、keywords 关键词、blend 混合';
COMMENT ON COLUMN rag_knowledge_bases.top_k IS '命中测试最多返回的分段数量';
COMMENT ON COLUMN rag_knowledge_bases.similarity_threshold IS '最低相似度阈值，范围 0 到 1';
COMMENT ON COLUMN rag_knowledge_bases.segment_size IS '文档分段目标长度，单位为字符';
COMMENT ON COLUMN rag_knowledge_bases.overlap_size IS '相邻分段重叠长度，单位为字符';
COMMENT ON COLUMN rag_knowledge_bases.created_at IS '创建时间';
COMMENT ON COLUMN rag_knowledge_bases.updated_at IS '最后更新时间';

-- rag_documents：知识库原始文档表，每条记录对应用户导入的一份文本文件或文档。
CREATE TABLE IF NOT EXISTS rag_documents (
    id VARCHAR(36) PRIMARY KEY,
    knowledge_base_id VARCHAR(36) NOT NULL REFERENCES rag_knowledge_bases(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    character_count INTEGER NOT NULL DEFAULT 0,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ready',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- 按知识库查询文档时使用的索引。
CREATE INDEX IF NOT EXISTS idx_rag_documents_knowledge_base ON rag_documents(knowledge_base_id);

COMMENT ON TABLE rag_documents IS 'RAG 知识库原始文档表';
COMMENT ON COLUMN rag_documents.id IS '文档唯一标识';
COMMENT ON COLUMN rag_documents.knowledge_base_id IS '所属知识库标识';
COMMENT ON COLUMN rag_documents.name IS '文档名称';
COMMENT ON COLUMN rag_documents.content IS '文档原始文本内容';
COMMENT ON COLUMN rag_documents.character_count IS '文档字符数';
COMMENT ON COLUMN rag_documents.chunk_count IS '文档分段数量';
COMMENT ON COLUMN rag_documents.status IS '文档处理状态：ready 表示已完成分段和向量化';
COMMENT ON COLUMN rag_documents.created_at IS '创建时间';
COMMENT ON COLUMN rag_documents.updated_at IS '最后更新时间';

-- rag_chunks：文档分段表，每条记录是一个可独立召回的文本片段及其 pgvector 向量。
CREATE TABLE IF NOT EXISTS rag_chunks (
    id VARCHAR(36) PRIMARY KEY,
    knowledge_base_id VARCHAR(36) NOT NULL REFERENCES rag_knowledge_bases(id) ON DELETE CASCADE,
    document_id VARCHAR(36) NOT NULL REFERENCES rag_documents(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL DEFAULT '',
    content TEXT NOT NULL,
    token_count INTEGER NOT NULL DEFAULT 0,
    embedding vector(1024) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- 按知识库筛选召回分段时使用的索引。
CREATE INDEX IF NOT EXISTS idx_rag_chunks_knowledge_base ON rag_chunks(knowledge_base_id);
-- 查看指定文档分段时使用的索引。
CREATE INDEX IF NOT EXISTS idx_rag_chunks_document ON rag_chunks(document_id);
-- 保证同一文档内的分段顺序唯一。
CREATE UNIQUE INDEX IF NOT EXISTS uq_rag_chunks_document_position ON rag_chunks(document_id, position);

COMMENT ON TABLE rag_chunks IS 'RAG 文档分段及向量表';
COMMENT ON COLUMN rag_chunks.id IS '分段唯一标识';
COMMENT ON COLUMN rag_chunks.knowledge_base_id IS '所属知识库标识';
COMMENT ON COLUMN rag_chunks.document_id IS '所属文档标识';
COMMENT ON COLUMN rag_chunks.position IS '分段在文档中的顺序，从 1 开始';
COMMENT ON COLUMN rag_chunks.title IS '分段标题';
COMMENT ON COLUMN rag_chunks.content IS '分段文本内容';
COMMENT ON COLUMN rag_chunks.token_count IS '分段估算 token 数量';
COMMENT ON COLUMN rag_chunks.embedding IS '1024 维文本嵌入向量，使用 pgvector vector 类型存储';
COMMENT ON COLUMN rag_chunks.active IS '是否参与检索';
COMMENT ON COLUMN rag_chunks.created_at IS '创建时间';
COMMENT ON COLUMN rag_chunks.updated_at IS '最后更新时间';

-- search_vector：由分段标题和正文生成的 PostgreSQL 全文检索列，用于关键词召回。
ALTER TABLE rag_chunks
    ADD COLUMN IF NOT EXISTS search_vector tsvector
    GENERATED ALWAYS AS (
        to_tsvector('simple', COALESCE(title, '') || ' ' || COALESCE(content, ''))
    ) STORED;

-- 全文检索倒排索引。
CREATE INDEX IF NOT EXISTS idx_rag_chunks_search_vector
    ON rag_chunks USING GIN(search_vector);

COMMENT ON COLUMN rag_chunks.search_vector IS '由标题和内容生成的 PostgreSQL 全文检索向量';
