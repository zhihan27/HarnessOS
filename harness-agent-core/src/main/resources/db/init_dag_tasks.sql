-- PostgreSQL 数据库初始化脚本
-- 创建 dag_tasks 表（任务看板系统）
-- 异步 DAG 编排，跨会话持久化

CREATE TABLE IF NOT EXISTS dag_tasks (
    task_id VARCHAR(64) PRIMARY KEY,
    subject VARCHAR(200) NOT NULL,
    description TEXT,
    active_form VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    owner VARCHAR(64),
    session_id VARCHAR(64),
    tenant_id VARCHAR(64),
    user_id VARCHAR(64),
    blocked_by TEXT,          -- JSON 数组格式：["task-id-1", "task-id-2"]
    blocks TEXT,              -- JSON 数组格式：["task-id-3", "task-id-4"]
    metadata TEXT,            -- JSON 格式扩展数据
    result TEXT,
    error TEXT,
    assigned_agent_id VARCHAR(64),     -- 分配的 Worker Agent ID
    assignment_version INTEGER DEFAULT 0, -- 乐观锁版本号
    started_at TIMESTAMP WITHOUT TIME ZONE,
    completed_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE
);

-- 表注释
COMMENT ON TABLE dag_tasks IS 'DAG任务看板：异步编排，跨会话持久化，支持依赖关系管理';

-- 字段注释
COMMENT ON COLUMN dag_tasks.task_id IS '任务唯一ID（UUID格式）';
COMMENT ON COLUMN dag_tasks.subject IS '简要标题（祈使句形式，如"实现用户认证"）';
COMMENT ON COLUMN dag_tasks.description IS '详细需求描述';
COMMENT ON COLUMN dag_tasks.active_form IS '执行时spinner显示文案（现在进行时）';
COMMENT ON COLUMN dag_tasks.status IS '任务状态：pending（待执行）, in_progress（执行中）, completed（已完成）, failed（失败）, deleted（已删除）';
COMMENT ON COLUMN dag_tasks.owner IS '责任人（Agent ID 或人类标识）';
COMMENT ON COLUMN dag_tasks.session_id IS '创建会话ID（可选，记录来源）';
COMMENT ON COLUMN dag_tasks.tenant_id IS '租户ID，用于多租户隔离';
COMMENT ON COLUMN dag_tasks.user_id IS '用户ID，区分不同用户';
COMMENT ON COLUMN dag_tasks.blocked_by IS '依赖的任务ID列表（JSON数组格式）';
COMMENT ON COLUMN dag_tasks.blocks IS '被哪些任务依赖（JSON数组格式）';
COMMENT ON COLUMN dag_tasks.metadata IS '扩展元数据（JSON格式）';
COMMENT ON COLUMN dag_tasks.result IS '执行结果';
COMMENT ON COLUMN dag_tasks.error IS '错误信息';
COMMENT ON COLUMN dag_tasks.assigned_agent_id IS '分配执行的 Worker Agent ID';
COMMENT ON COLUMN dag_tasks.assignment_version IS '乐观锁版本号，用于原子领取任务';
COMMENT ON COLUMN dag_tasks.started_at IS '开始执行时间';
COMMENT ON COLUMN dag_tasks.completed_at IS '完成时间';
COMMENT ON COLUMN dag_tasks.created_at IS '创建时间';
COMMENT ON COLUMN dag_tasks.updated_at IS '更新时间';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_dag_status ON dag_tasks(status);
CREATE INDEX IF NOT EXISTS idx_dag_owner ON dag_tasks(owner);
CREATE INDEX IF NOT EXISTS idx_dag_session ON dag_tasks(session_id);
CREATE INDEX IF NOT EXISTS idx_dag_tenant_user ON dag_tasks(tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_dag_assigned_agent ON dag_tasks(assigned_agent_id);

-- 查询表结构验证
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'dag_tasks'
ORDER BY ordinal_position;