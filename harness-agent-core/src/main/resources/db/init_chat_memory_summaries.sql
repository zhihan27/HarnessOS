-- PostgreSQL 数据库初始化脚本
-- 创建 chat_memory_summaries 表（压缩摘要）
-- 功能：存储压缩后的会话摘要，记录压缩过程和Token节省情况

CREATE TABLE IF NOT EXISTS chat_memory_summaries (
    id BIGSERIAL PRIMARY KEY,

    -- 会话关联
    session_id VARCHAR(64) NOT NULL,

    -- 摘要信息
    summary_type VARCHAR(20) NOT NULL,
    summary_content TEXT NOT NULL,

    -- 压缩范围
    start_message_id BIGINT NOT NULL,
    end_message_id BIGINT NOT NULL,
    messages_count INT NOT NULL,
    original_tokens INT NOT NULL,
    compressed_tokens INT NOT NULL,

    -- Token节省
    token_saved INT NOT NULL DEFAULT 0,
    compression_ratio DECIMAL(5,2) NOT NULL DEFAULT 0.00,

    -- 时间记录
    created_at TIMESTAMP WITHOUT TIME ZONE
);

-- ========== 表注释 ==========
COMMENT ON TABLE chat_memory_summaries IS '记忆摘要表：存储压缩后的会话摘要，记录压缩过程和Token节省情况';

-- ========== 字段注释 ==========
COMMENT ON COLUMN chat_memory_summaries.id IS '主键ID，自增';
COMMENT ON COLUMN chat_memory_summaries.session_id IS '关联的会话ID，外键关联chat_sessions.session_id';
COMMENT ON COLUMN chat_memory_summaries.summary_type IS '摘要类型：FULL(全量压缩)/PARTIAL(部分压缩)';
COMMENT ON COLUMN chat_memory_summaries.summary_content IS '摘要内容，AI生成的历史对话摘要';
COMMENT ON COLUMN chat_memory_summaries.start_message_id IS '被压缩的起始消息ID，记录压缩范围';
COMMENT ON COLUMN chat_memory_summaries.end_message_id IS '被压缩的结束消息ID，记录压缩范围';
COMMENT ON COLUMN chat_memory_summaries.messages_count IS '被压缩的消息数量';
COMMENT ON COLUMN chat_memory_summaries.original_tokens IS '压缩前的Token总数';
COMMENT ON COLUMN chat_memory_summaries.compressed_tokens IS '压缩后的Token数（摘要的Token数）';
COMMENT ON COLUMN chat_memory_summaries.token_saved IS '节省的Token数 = original_tokens - compressed_tokens';
COMMENT ON COLUMN chat_memory_summaries.compression_ratio IS '压缩比率百分比，衡量压缩效率';
COMMENT ON COLUMN chat_memory_summaries.created_at IS '创建时间，压缩发生时间';

-- ========== 索引 ==========
CREATE INDEX IF NOT EXISTS idx_chat_summaries_session ON chat_memory_summaries(session_id);
CREATE INDEX IF NOT EXISTS idx_chat_summaries_range ON chat_memory_summaries(session_id, start_message_id, end_message_id);

-- ========== 索引注释 ==========
COMMENT ON INDEX idx_chat_summaries_session IS '会话ID索引，用于查询会话的所有压缩记录';
COMMENT ON INDEX idx_chat_summaries_range IS '会话+范围复合索引，用于查询特定消息范围的压缩情况';