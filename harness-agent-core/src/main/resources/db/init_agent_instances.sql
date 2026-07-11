-- Agent 实例表
-- 存储所有注册的 Agent 信息（MainAgent 和 WorkerAgent）

CREATE TABLE IF NOT EXISTS agent_instances (
    agent_id VARCHAR(64) PRIMARY KEY,
    agent_type VARCHAR(20) NOT NULL,           -- 'MAIN' 或 'WORKER'
    agent_name VARCHAR(100) NOT NULL,          -- 显示名称
    status VARCHAR(20) NOT NULL DEFAULT 'IDLE', -- 'IDLE', 'WORKING', 'STOPPED', 'ERROR'
    current_task_id VARCHAR(64),               -- 当前执行的任务
    max_concurrency INTEGER DEFAULT 1,         -- 最大并发数
    current_load INTEGER DEFAULT 0,            -- 当前负载
    capabilities TEXT,                         -- 能力标签 JSON 数组
    tenant_id VARCHAR(64),
    user_id VARCHAR(64),
    session_id VARCHAR(64),                    -- MainAgent 关联会话
    started_at TIMESTAMP WITHOUT TIME ZONE,
    last_active_at TIMESTAMP WITHOUT TIME ZONE,
    stopped_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_agent_type ON agent_instances(agent_type);
CREATE INDEX IF NOT EXISTS idx_agent_status ON agent_instances(status);
CREATE INDEX IF NOT EXISTS idx_agent_tenant_user ON agent_instances(tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_agent_session ON agent_instances(session_id);

-- 注释
COMMENT ON TABLE agent_instances IS 'Agent 实例注册表';
COMMENT ON COLUMN agent_instances.agent_type IS 'Agent 类型：MAIN(主Agent-任务拆解) 或 WORKER(工作Agent-执行)';
COMMENT ON COLUMN agent_instances.status IS 'Agent 状态：IDLE(空闲), WORKING(工作中), STOPPED(已停止), ERROR(错误)';
COMMENT ON COLUMN agent_instances.max_concurrency IS '最大并发任务数，Worker 1-3，Main 1';
COMMENT ON COLUMN agent_instances.current_load IS '当前正在执行的任务数';
COMMENT ON COLUMN agent_instances.capabilities IS 'Agent 能力标签 JSON 数组，如 ["CODING","RESEARCH"]';