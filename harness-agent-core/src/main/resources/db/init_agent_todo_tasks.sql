-- PostgreSQL 数据库初始化脚本
-- 创建 agent_todo_tasks 表（秘书模式）
-- 时间字段由 MyBatis-Plus MetaObjectHandler 自动填充

CREATE TABLE IF NOT EXISTS agent_todo_tasks (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    task_description TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE
);

-- 表注释
COMMENT ON TABLE agent_todo_tasks IS 'Agent任务清单（秘书模式）：AI的任务计划，帮助AI防止跑偏';

-- 字段注释
COMMENT ON COLUMN agent_todo_tasks.id IS '主键ID，自增';
COMMENT ON COLUMN agent_todo_tasks.tenant_id IS '租户ID，用于多租户隔离';
COMMENT ON COLUMN agent_todo_tasks.user_id IS '用户ID，区分不同用户';
COMMENT ON COLUMN agent_todo_tasks.session_id IS '会话ID，区分同用户的不同任务流';
COMMENT ON COLUMN agent_todo_tasks.task_description IS '任务描述（如：Plan: 1.读取配置, 2.修改配置, 3.重启服务）';
COMMENT ON COLUMN agent_todo_tasks.status IS '任务状态：PENDING（待完成）, COMPLETED（已完成）';
COMMENT ON COLUMN agent_todo_tasks.created_at IS '创建时间';
COMMENT ON COLUMN agent_todo_tasks.updated_at IS '更新时间';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_session ON agent_todo_tasks(session_id);
CREATE INDEX IF NOT EXISTS idx_status ON agent_todo_tasks(status);

-- 查询表结构验证
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'agent_todo_tasks'
ORDER BY ordinal_position;