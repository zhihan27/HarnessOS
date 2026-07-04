-- PostgreSQL 数据库初始化脚本
-- 创建 sub_agent_tasks 表（子任务管理）

-- ============================================================
-- 表: sub_agent_tasks
-- 功能: 管理父子 Agent 关系和子任务执行状态
-- 核心设计: 父子上下文隔离，自动重试机制
-- ============================================================

CREATE TABLE IF NOT EXISTS sub_agent_tasks (
    id BIGSERIAL PRIMARY KEY,

    -- 租户隔离字段
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,

    -- 父子关系字段（核心：上下文隔离的关键设计）
    parent_session_id VARCHAR(64) NOT NULL,
    sub_agent_session_id VARCHAR(64) NOT NULL,
    parent_task_id BIGINT,
    depth INT NOT NULL DEFAULT 0,

    -- 任务信息字段
    task_type VARCHAR(50) NOT NULL,
    task_description TEXT NOT NULL,
    task_input TEXT,

    -- 状态管理字段
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    result TEXT,

    -- 重试机制字段（自动重试，AI无需干预）
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    last_error TEXT,

    -- 时间记录字段
    started_at TIMESTAMP WITHOUT TIME ZONE,
    completed_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE
);

-- ============================================================
-- 表注释
-- ============================================================
COMMENT ON TABLE sub_agent_tasks IS '子任务管理表：管理父子Agent关系和任务执行状态。核心设计：1.父子上下文隔离 2.自动重试机制（AI调用executeSubAgent后内部自动重试）';

-- ============================================================
-- 字段注释
-- ============================================================

-- 主键
COMMENT ON COLUMN sub_agent_tasks.id IS '主键ID，自增';

-- 租户隔离
COMMENT ON COLUMN sub_agent_tasks.tenant_id IS '租户ID，用于多租户隔离';
COMMENT ON COLUMN sub_agent_tasks.user_id IS '用户ID，区分同一租户下的不同用户';

-- 父子关系（核心设计）
COMMENT ON COLUMN sub_agent_tasks.parent_session_id IS '父Agent会话ID，用于追溯任务来源';
COMMENT ON COLUMN sub_agent_tasks.sub_agent_session_id IS '子Agent独立会话ID（格式：SUB-xxxxxxxxxxxx），上下文隔离的关键：子Agent使用此ID，不继承父对话历史';
COMMENT ON COLUMN sub_agent_tasks.parent_task_id IS '父任务ID，用于多级嵌套场景，NULL表示父Agent是主Agent';
COMMENT ON COLUMN sub_agent_tasks.depth IS '嵌套深度：0=顶层子任务，最大限制3层防止无限递归';

-- 任务信息
COMMENT ON COLUMN sub_agent_tasks.task_type IS '任务类型：RESEARCH/CODING/ANALYSIS/TESTING/DOCUMENTATION/GENERAL';
COMMENT ON COLUMN sub_agent_tasks.task_description IS '任务描述，父Agent拆分时指定的具体内容';
COMMENT ON COLUMN sub_agent_tasks.task_input IS '任务输入参数（JSON格式），可选';

-- 状态管理（注意：无RETRYING状态，重试在RUNNING内部自动进行）
COMMENT ON COLUMN sub_agent_tasks.status IS '任务状态：PENDING(待执行), RUNNING(执行中含自动重试), COMPLETED(成功), FAILED(终态失败已耗尽重试次数)';
COMMENT ON COLUMN sub_agent_tasks.result IS '执行结果，成功时的返回内容';

-- 重试机制（自动进行，AI无需关心）
COMMENT ON COLUMN sub_agent_tasks.retry_count IS '实际尝试次数，0=首次执行，1=第一次重试...';
COMMENT ON COLUMN sub_agent_tasks.max_retries IS '最大重试限制，默认3次';
COMMENT ON COLUMN sub_agent_tasks.last_error IS '最近一次错误信息，供调试分析';

-- 时间记录
COMMENT ON COLUMN sub_agent_tasks.started_at IS '开始执行时间';
COMMENT ON COLUMN sub_agent_tasks.completed_at IS '完成时间（成功或失败）';
COMMENT ON COLUMN sub_agent_tasks.created_at IS '创建时间，MyBatis-Plus自动填充';
COMMENT ON COLUMN sub_agent_tasks.updated_at IS '更新时间，MyBatis-Plus自动填充';

-- ============================================================
-- 索引（先创建索引，再添加注释）
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_sub_agent_parent_session ON sub_agent_tasks(parent_session_id);
CREATE INDEX IF NOT EXISTS idx_sub_agent_session ON sub_agent_tasks(sub_agent_session_id);
CREATE INDEX IF NOT EXISTS idx_sub_agent_status ON sub_agent_tasks(status);
CREATE INDEX IF NOT EXISTS idx_sub_agent_parent_task ON sub_agent_tasks(parent_task_id);
CREATE INDEX IF NOT EXISTS idx_sub_agent_tenant ON sub_agent_tasks(tenant_id);

-- 索引注释
COMMENT ON INDEX idx_sub_agent_parent_session IS '父会话索引：查询某个父Agent创建的所有子任务';
COMMENT ON INDEX idx_sub_agent_session IS '子会话索引：通过sub_agent_session_id定位任务';
COMMENT ON INDEX idx_sub_agent_status IS '状态索引：筛选任务状态';
COMMENT ON INDEX idx_sub_agent_parent_task IS '父任务索引：多级嵌套查询';
COMMENT ON INDEX idx_sub_agent_tenant IS '租户索引：多租户隔离';