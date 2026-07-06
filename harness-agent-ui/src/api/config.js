// API 基础配置
export const BASE_URL = '/api'  // 使用 Vite 代理路径

// API 路径定义
export const API_ROUTES = {
  CHAT: '/agent/chat',
  SESSIONS: '/agent/sessions',          // 会话列表
  SESSION_DETAIL: '/agent/sessions',    // 会话详情 (需要拼接sessionId)
  HISTORY: '/agent/history',            // 历史消息 (需要拼接sessionId)
  ARCHIVE_SESSION: '/agent/sessions',   // 归档会话 (需要拼接sessionId + /archive)
  AGENTS: '/agent/list',
  PLUGINS: '/plugin/list',
  MONITOR: '/monitor/stats'
}

// 默认请求头配置
export const DEFAULT_HEADERS = {
  'Content-Type': 'application/json',
  'Accept': 'application/json'
}

// 超时配置（毫秒）
export const REQUEST_TIMEOUT = 120000  // 2分钟