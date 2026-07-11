# 项目架构修复说明

## 当前问题

1. **历史会话为空** - 数据库status字段问题
2. **每次对话创建多个session** - localStorage有旧sessionId
3. **Agent不执行任务** - TaskDispatcher和WorkerAgent冲突
4. **架构混乱** - MainAgent应该是对话LLM

## 正确架构

```
┌─────────────────────────────────────────────────────────────┐
│                     用户对话界面                             │
│                   AgentService.chat                         │
│                      (MainAgent)                            │
└─────────────────────────────────────────────────────────────┘
                            │
              ┌─────────────┴─────────────┐
              │                           │
              ▼                           ▼
        简单任务                      复杂任务
      (直接回答)              (AI调用DagTaskTool入库)
                                        │
                                        ▼
                               ┌───────────────┐
                               │  dag_tasks表  │
                               │  pending任务  │
                               └───────────────┘
                                        │
                    ┌───────────────────┼───────────────────┐
                    ▼                   ▼                   ▼
              WorkerAgent-1       WorkerAgent-2       WorkerAgent-3
              (原子领取)          (原子领取)          (原子领取)
                    │                   │                   │
                    └───────────────────┼───────────────────┘
                                        ▼
                               TaskExecutor.execute
                                        │
                                        ▼
                               完成后更新状态
```

## 关键修改

### 1. 禁用TaskDispatcher自动执行
- TaskDispatcher默认禁用
- 由WorkerAgent负责领取和执行任务

### 2. 不需要单独注册MainAgent
- MainAgent就是用户对话的LLM
- 通过DagTaskTool入库拆解任务

### 3. WorkerAgent执行流程
```java
// 每2秒轮询
1. 检查自身负载
2. 原子领取pending任务（乐观锁）
3. 设置AgentContext
4. TaskExecutor.execute()
5. 释放任务，更新状态
```

## 数据库修复SQL

```sql
-- 修复会话状态
UPDATE chat_sessions SET status = 'ACTIVE' WHERE status IS NULL;

-- 查看任务状态
SELECT task_id, subject, status, assigned_agent_id FROM dag_tasks;
```

## API路由整理

| 路径 | Controller | 功能 |
|------|------------|------|
| POST /api/agent/chat | HarnessController | 对话（MainAgent） |
| GET /api/agent/sessions | HarnessController | 会话列表 |
| GET /api/agent/history/{id} | HarnessController | 历史消息 |
| DELETE /api/agent/sessions/{id} | HarnessController | 删除会话 |
| GET /api/agent-mgr/list | AgentController | Agent列表 |
| POST /api/agent-mgr/register | AgentController | 注册Agent |
| GET /api/agent-mgr/status/stream | AgentController | SSE状态流 |
```