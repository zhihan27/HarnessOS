-- Agent-任务关联表
-- 记录 Agent 创建或领取的任务历史

CREATE TABLE IF NOT EXISTS agent_task_assignments (
    id SERIAL PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL,             -- Agent ID
    task_id VARCHAR(64) NOT NULL,              -- 任务 ID
    session_id VARCHAR(64),                    -- 会话 ID
    assignment_type VARCHAR(20) NOT NULL,      -- 'CREATED' 或 'CLAIMED'
    status VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED', -- 'ASSIGNED', 'EXECUTING', 'COMPLETED', 'RELEASED'
    assigned_at TIMESTAMP WITHOUT TIME ZONE,
    released_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_assignment_agent ON agent_task_assignments(agent_id);
CREATE INDEX IF NOT EXISTS idx_assignment_task ON agent_task_assignments(task_id);
CREATE INDEX IF NOT EXISTS idx_assignment_status ON agent_task_assignments(status);
CREATE INDEX IF NOT EXISTS idx_assignment_session ON agent_task_assignments(session_id);

-- 唯一约束：一个 Agent 和任务只能有一个活跃的分配
CREATE UNIQUE INDEX IF NOT EXISTS idx_assignment_active_unique
    ON agent_task_assignments(agent_id, task_id)
    WHERE status IN ('ASSIGNED', 'EXECUTING');

-- 注释
COMMENT ON TABLE agent_task_assignments IS 'Agent 与任务的关联关系表';
COMMENT ON COLUMN agent_task_assignments.assignment_type IS '分配类型：CREATED(MainAgent创建) 或 CLAIMED(Worker领取)';
COMMENT ON COLUMN agent_task_assignments.status IS '分配状态：ASSIGNED(已分配), EXECUTING(执行中), COMPLETED(完成), RELEASED(已释放)';