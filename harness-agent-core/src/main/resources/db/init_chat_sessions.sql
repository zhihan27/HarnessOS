-- PostgreSQL 数据库初始化脚本
-- 创建 chat_sessions 表（会话管理）
-- 功能：管理用户会话元数据和Token统计，支持多租户隔离

CREATE TABLE IF NOT EXISTS chat_sessions (
    id BIGSERIAL PRIMARY KEY,

    -- 租户隔离字段
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL UNIQUE,

    -- 会话信息
    title VARCHAR(255),
    model_type VARCHAR(32) NOT NULL DEFAULT 'openai',

    -- Token统计
    total_tokens BIGINT NOT NULL DEFAULT 0,
    max_tokens INT NOT NULL DEFAULT 128000,
    token_usage_percent DECIMAL(5,2) NOT NULL DEFAULT 0.00,

    -- 压缩状态
    is_compressed BOOLEAN NOT NULL DEFAULT FALSE,
    compressed_at TIMESTAMP WITHOUT TIME ZONE,
    compression_count INT NOT NULL DEFAULT 0,

    -- 会话状态
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_message_at TIMESTAMP WITHOUT TIME ZONE,

    -- 时间记录
    created_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE
);

-- ========== 表注释 ==========
COMMENT ON TABLE chat_sessions IS '会话管理表：管理用户会话元数据和Token统计，支持多租户隔离和持久化记忆';

-- ========== 字段注释 ==========
COMMENT ON COLUMN chat_sessions.id IS '主键ID，自增';
COMMENT ON COLUMN chat_sessions.tenant_id IS '租户ID，用于多租户隔离，区分不同企业/组织';
COMMENT ON COLUMN chat_sessions.user_id IS '用户ID，区分同一租户下的不同用户';
COMMENT ON COLUMN chat_sessions.session_id IS '会话ID，全局唯一，作为LangChain4j的memoryId使用';
COMMENT ON COLUMN chat_sessions.title IS '会话标题，可选，可用于展示会话摘要';
COMMENT ON COLUMN chat_sessions.model_type IS '模型类型：openai/anthropic，标识使用的AI模型';
COMMENT ON COLUMN chat_sessions.total_tokens IS '当前会话总Token数（含压缩后的历史消息）';
COMMENT ON COLUMN chat_sessions.max_tokens IS '模型最大Token限制，默认128000（DeepSeek模型）';
COMMENT ON COLUMN chat_sessions.token_usage_percent IS 'Token使用率百分比，用于触发压缩判断';
COMMENT ON COLUMN chat_sessions.is_compressed IS '是否已压缩过历史消息';
COMMENT ON COLUMN chat_sessions.compressed_at IS '最近一次压缩时间';
COMMENT ON COLUMN chat_sessions.compression_count IS '累计压缩次数，记录压缩历史';
COMMENT ON COLUMN chat_sessions.status IS '会话状态：ACTIVE(活跃)/ARCHIVED(已归档)/DELETED(已删除)';
COMMENT ON COLUMN chat_sessions.last_message_at IS '最后一条消息时间，用于排序和会话活跃度判断';
COMMENT ON COLUMN chat_sessions.created_at IS '创建时间，会话首次建立时间';
COMMENT ON COLUMN chat_sessions.updated_at IS '更新时间，会话元数据最后修改时间';

-- ========== 索引 ==========
CREATE UNIQUE INDEX IF NOT EXISTS idx_chat_sessions_session_id ON chat_sessions(session_id);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_tenant_user ON chat_sessions(tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_status ON chat_sessions(status);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_last_message ON chat_sessions(last_message_at);

-- ========== 索引注释 ==========
COMMENT ON INDEX idx_chat_sessions_session_id IS '会话ID唯一索引，用于快速查找会话';
COMMENT ON INDEX idx_chat_sessions_tenant_user IS '租户+用户复合索引，用于查询用户的所有会话';
COMMENT ON INDEX idx_chat_sessions_status IS '状态索引，用于过滤活跃/归档会话';
COMMENT ON INDEX idx_chat_sessions_last_message IS '最后消息时间索引，用于按活跃度排序';