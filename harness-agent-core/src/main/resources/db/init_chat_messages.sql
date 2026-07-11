-- PostgreSQL 数据库初始化脚本
-- 创建 chat_messages 表（消息持久化）
-- 功能：持久化会话消息历史，支持上下文恢复和Token统计

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGSERIAL PRIMARY KEY,

    -- 会话关联
    session_id VARCHAR(64) NOT NULL,

    -- 消息内容
    message_type VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,

    -- Token统计
    token_count INT NOT NULL DEFAULT 0,

    -- 元数据
    message_order INT NOT NULL,
    is_compressed BOOLEAN NOT NULL DEFAULT FALSE,
    is_important BOOLEAN NOT NULL DEFAULT FALSE,

    -- 工具调用信息（可选）
    tool_name VARCHAR(64),
    tool_args TEXT,
    tool_result TEXT,

    -- 工具调用扩展字段（支持多工具调用和ID匹配）
    tool_call_id VARCHAR(64),
    tool_execution_requests TEXT,

    -- 时间记录
    created_at TIMESTAMP WITHOUT TIME ZONE
);

-- ========== 表注释 ==========
COMMENT ON TABLE chat_messages IS '聊天消息表：持久化会话消息历史，支持上下文恢复和Token统计';

-- ========== 字段注释 ==========
COMMENT ON COLUMN chat_messages.id IS '主键ID，自增';
COMMENT ON COLUMN chat_messages.session_id IS '关联的会话ID，外键关联chat_sessions.session_id';
COMMENT ON COLUMN chat_messages.message_type IS '消息类型：SYSTEM(系统提示)/USER(用户消息)/AI(AI回复)/TOOL(工具调用结果)';
COMMENT ON COLUMN chat_messages.content IS '消息内容，TEXT类型支持长文本';
COMMENT ON COLUMN chat_messages.token_count IS '消息Token数，用于Token统计和压缩判断';
COMMENT ON COLUMN chat_messages.message_order IS '消息序号，用于排序，保证消息顺序正确';
COMMENT ON COLUMN chat_messages.is_compressed IS '是否被压缩过，标记已参与压缩的消息';
COMMENT ON COLUMN chat_messages.is_important IS '是否重要消息，压缩时优先保留（如系统消息）';
COMMENT ON COLUMN chat_messages.tool_name IS '工具名称（TOOL类型消息时），记录调用的工具';
COMMENT ON COLUMN chat_messages.tool_args IS '工具参数（TOOL类型消息时），JSON格式';
COMMENT ON COLUMN chat_messages.tool_result IS '工具返回结果（TOOL类型消息时）';
COMMENT ON COLUMN chat_messages.tool_call_id IS '工具调用ID，用于匹配工具结果与AI请求的tool_call_id';
COMMENT ON COLUMN chat_messages.tool_execution_requests IS '工具调用请求JSON数组，格式：[{"id":"xxx","name":"xxx","arguments":"xxx"}]';
COMMENT ON COLUMN chat_messages.created_at IS '创建时间，消息发送时间';

-- ========== 索引 ==========
CREATE INDEX IF NOT EXISTS idx_chat_messages_session ON chat_messages(session_id);
CREATE INDEX IF NOT EXISTS idx_chat_messages_order ON chat_messages(session_id, message_order);
CREATE INDEX IF NOT EXISTS idx_chat_messages_type ON chat_messages(message_type);

-- ========== 索引注释 ==========
COMMENT ON INDEX idx_chat_messages_session IS '会话ID索引，用于查询会话的所有消息';
COMMENT ON INDEX idx_chat_messages_order IS '会话+序号复合索引，用于按顺序加载消息历史';
COMMENT ON INDEX idx_chat_messages_type IS '消息类型索引，用于统计不同类型消息数量';