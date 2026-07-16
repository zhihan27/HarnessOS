// API 基础配置
export const BASE_URL = '/api'  // 使用 Vite 代理路径

// API 路径定义
export const API_ROUTES = {
  CHAT: '/agent/chat',
  CHAT_STREAM: '/chat/stream',          // SSE流式对话接口
  SESSIONS: '/agent/sessions',          // 会话列表
  SESSION_DETAIL: '/agent/sessions',    // 会话详情 (需要拼接sessionId)
  HISTORY: '/agent/history',            // 历史消息 (需要拼接sessionId)
  ARCHIVE_SESSION: '/agent/sessions',   // 归档会话 (需要拼接sessionId + /archive)
  AGENTS: '/agent/list',
  PLUGINS: '/plugin/list',
  MONITOR: '/monitor/stats',
  // Task Team API
  TASK_LIST: '/task/list',              // 任务列表
  TASK_DETAIL: '/task',                 // 任务详情 (需要拼接taskId)
  TASK_PROGRESS: '/task/progress',      // 任务进度
  TASK_READY: '/task/ready',            // 就绪任务
  TASK_BLOCKED: '/task/blocked',        // 被阻塞任务
  TEAM_START: '/task/team/start',       // 启动 Task Team
  TEAM_STOP: '/task/team/stop',         // 停止 Task Team
  TEAM_STATUS: '/task/team/status',     // Task Team 状态

  // Agent 管理 API（使用 agent-mgr 路径，避免与 sessions 路由冲突）
  AGENT_LIST: '/agent-mgr/list',            // Agent 列表
  AGENT_DETAIL: '/agent-mgr',               // Agent 详情 (需要拼接agentId)
  AGENT_REGISTER: '/agent-mgr/register',    // 注册 Agent
  AGENT_STOP: '/agent-mgr',                 // 停止 Agent (需要拼接agentId + /stop)
  AGENT_DECOMPOSE: '/agent-mgr/{agentId}/decompose',  // MainAgent 拆解任务
  AGENT_CLAIM: '/agent-mgr/{agentId}/claim',          // Worker 领取任务
  AGENT_STATUS_STREAM: '/agent-mgr/status/stream',    // SSE 状态流
  AGENT_CONNECTIONS: '/agent-mgr/status/connections'  // SSE 连接数
}

// 默认请求头配置
export const DEFAULT_HEADERS = {
  'Content-Type': 'application/json',
  'Accept': 'application/json'
}

// 超时配置（毫秒）
export const REQUEST_TIMEOUT = 120000  // 2分钟