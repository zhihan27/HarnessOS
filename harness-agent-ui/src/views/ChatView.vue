<script setup>
import { ref, nextTick, computed, onMounted, onUnmounted, watch } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useTaskStore } from '@/stores/task'

const chatStore = useChatStore()
const taskStore = useTaskStore()
const inputMessage = ref('')
const messagesContainer = ref(null)
const inputFocused = ref(false)
const showSessionList = ref(false)
const isLoadingMore = ref(false)
const hasMoreHistory = ref(true)
const pageSize = 20

const modelType = computed({
  get: () => chatStore.modelType,
  set: (val) => chatStore.setModelType(val)
})

const messages = computed(() => chatStore.messages)
const sessions = computed(() => chatStore.sessions)
const currentSessionId = computed(() => chatStore.currentSessionId)
const loading = computed(() => chatStore.loading)

const taskProgress = computed(() => taskStore.progress)
const sessionTasks = computed(() => taskStore.sessionTasks)
const runningTasks = computed(() => sessionTasks.value.filter(t => t.status === 'in_progress'))
const hasRunningTasks = computed(() => taskStore.hasRunningTasks)

const scrollToBottom = async (instant = true) => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTo({
      top: messagesContainer.value.scrollHeight,
      behavior: instant ? 'instant' : 'smooth'
    })
  }
}

const handleScroll = async () => {
  if (!messagesContainer.value || isLoadingMore.value || !hasMoreHistory.value) return
  const { scrollTop, scrollHeight } = messagesContainer.value
  if (scrollTop < 50 && messages.value.length >= pageSize) {
    isLoadingMore.value = true
    const prevHeight = scrollHeight
    const loaded = await chatStore.loadMoreHistory(currentSessionId.value, pageSize)
    hasMoreHistory.value = loaded.hasMore
    await nextTick()
    const newHeight = messagesContainer.value.scrollHeight
    messagesContainer.value.scrollTop = newHeight - prevHeight
    isLoadingMore.value = false
  }
}

watch(messages, (newMsgs, oldMsgs) => {
  if (newMsgs.length > oldMsgs?.length) {
    const lastOldMsg = oldMsgs?.[oldMsgs.length - 1]
    const lastNewMsg = newMsgs[newMsgs.length - 1]
    if (lastOldMsg?.timestamp !== lastNewMsg?.timestamp) {
      scrollToBottom(false)
    }
  }
}, { deep: true })

watch(hasRunningTasks, (hasRunning) => {
  if (hasRunning) {
    taskStore.startPolling(currentSessionId.value)
  }
})

onMounted(async () => {
  // 先获取会话列表
  await chatStore.fetchSessions()
  await taskStore.fetchProgress()

  // 如果没有当前会话，尝试选择一个现有会话或创建新会话
  if (!chatStore.currentSessionId) {
    if (chatStore.sessions.length > 0) {
      // 选择最新的会话
      chatStore.setCurrentSessionId(chatStore.sessions[0].sessionId)
    } else {
      // 没有现有会话，创建新会话
      await chatStore.createSession()
    }
  }

  // 加载当前会话的历史消息
  if (chatStore.currentSessionId) {
    await chatStore.fetchRecentHistory(chatStore.currentSessionId, pageSize)
    await scrollToBottom(true)
    await taskStore.fetchTasks(chatStore.currentSessionId)
  }
})

onUnmounted(() => {
  taskStore.stopPolling()
})

const sendMessage = async () => {
  const text = inputMessage.value.trim()
  if (!text || loading.value) return

  chatStore.addMessage({
    role: 'user',
    content: text,
    timestamp: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  })
  inputMessage.value = ''
  await scrollToBottom(false)

  try {
    const reply = await chatStore.sendMessage(text)
    chatStore.addMessage({
      role: 'assistant',
      content: reply,
      timestamp: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    })
    await taskStore.refreshAll(currentSessionId.value)
  } catch (error) {
    const errorMsg = error.message.includes('超时')
      ? '请求超时，后端响应时间过长'
      : '核心服务连接异常，请检查后端状态'
    chatStore.addMessage({
      role: 'error',
      content: errorMsg,
      timestamp: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    })
  } finally {
    await scrollToBottom(false)
  }
}

const clearChat = async () => {
  chatStore.clearMessages()
  hasMoreHistory.value = true
  await chatStore.createSession()
}

const selectSession = async (sessionId) => {
  chatStore.setCurrentSessionId(sessionId)
  hasMoreHistory.value = true
  await chatStore.fetchRecentHistory(sessionId, pageSize)
  await taskStore.fetchTasks(sessionId)
  showSessionList.value = false
  await scrollToBottom(true)
}

const newSession = async () => {
  chatStore.clearMessages()
  taskStore.clearTasks()
  hasMoreHistory.value = true
  await chatStore.createSession()
  showSessionList.value = false
}

const deleteSession = async (sessionId) => {
  await chatStore.deleteSession(sessionId)
}

const toggleSessionList = () => {
  showSessionList.value = !showSessionList.value
  if (showSessionList.value) {
    chatStore.fetchSessions()
  }
}

const getStatusText = (status) => {
  switch (status) {
    case 'pending': return '等待中'
    case 'in_progress': return '执行中'
    case 'completed': return '已完成'
    case 'failed': return '失败'
    default: return status
  }
}
</script>

<template>
  <div class="chat-view">
    <div class="chat-header">
      <div class="header-left">
        <span class="header-icon">💬</span>
        <div class="header-title">智能对话</div>
        <div class="session-indicator" v-if="currentSessionId">
          <span class="session-badge">{{ currentSessionId.slice(0, 8) }}</span>
        </div>
      </div>
      <div class="header-controls">
        <button @click="toggleSessionList" class="session-btn" title="会话列表">📋</button>
        <select v-model="modelType" class="model-select">
          <option value="openai">DeepSeek R1 / V3</option>
          <option value="anthropic">Claude 3.5 Sonnet</option>
        </select>
        <button @click="newSession" class="new-btn" title="新会话">➕</button>
        <button @click="clearChat" class="clear-btn" title="清空当前对话">🗑️</button>
      </div>
    </div>

    <!-- 会话列表面板 -->
    <div v-if="showSessionList" class="session-panel">
      <div class="session-panel-header">
        <span>历史会话</span>
        <button @click="newSession" class="session-new-btn">新建</button>
      </div>
      <div class="session-list">
        <div v-if="sessions.length === 0" class="session-empty">暂无历史会话</div>
        <div v-for="session in sessions" :key="session.sessionId"
             :class="['session-item', { active: session.sessionId === currentSessionId }]"
             @click="selectSession(session.sessionId)">
          <div class="session-info">
            <div class="session-title">{{ session.title || session.sessionId.slice(0, 8) }}</div>
            <div class="session-time">{{ session.lastMessageAt ? new Date(session.lastMessageAt).toLocaleString('zh-CN') : '无消息' }}</div>
          </div>
          <div class="session-stats">
            <span class="token-count">{{ session.totalTokens || 0 }} tokens</span>
            <span class="usage-percent">{{ session.tokenUsagePercent ? session.tokenUsagePercent.toFixed(1) + '%' : '0%' }}</span>
          </div>
          <button @click.stop="deleteSession(session.sessionId)" class="session-delete-btn" title="删除">✕</button>
        </div>
      </div>
    </div>

    <!-- 消息区域 -->
    <div ref="messagesContainer" class="messages-area" @scroll="handleScroll">
      <div v-if="isLoadingMore" class="loading-more"><span class="spinner-small"></span>加载更多...</div>
      <div v-else-if="hasMoreHistory && messages.length >= pageSize" class="load-more-tip">↑ 向上滚动加载更多历史</div>

      <div v-if="messages.length === 0" class="empty-state">
        <div class="empty-icon">🚀</div>
        <div class="empty-title">开始对话</div>
        <div class="empty-desc">选择历史会话或输入消息开始</div>
      </div>

      <div v-for="(msg, index) in messages" :key="index" :class="['message-row', msg.role]">
        <div :class="['avatar', msg.role]">
          <span v-if="msg.role === 'user'">ME</span>
          <span v-else-if="msg.role === 'assistant'">AI</span>
          <span v-else>!</span>
        </div>
        <div class="message-bubble">
          <div class="bubble-content">{{ msg.content }}</div>
          <div class="bubble-time">{{ msg.timestamp }}</div>
        </div>
      </div>
    </div>

    <!-- 任务状态面板 -->
    <div v-if="runningTasks.length > 0 || taskProgress.inProgress > 0" class="task-status-panel">
      <div class="task-status-header">
        <span class="task-status-icon">⚡</span>
        <span class="task-status-title">任务执行中</span>
        <span class="task-status-count">{{ taskProgress.inProgress }} 个任务</span>
      </div>
      <div class="task-status-list">
        <div v-for="task in runningTasks" :key="task.taskId" class="task-status-item">
          <div class="task-status-dot"></div>
          <div class="task-status-subject">{{ task.subject }}</div>
          <div class="task-status-badge">{{ getStatusText(task.status) }}</div>
        </div>
      </div>
      <div class="task-progress-bar">
        <div class="task-progress-fill" :style="{ width: `${taskProgress.total > 0 ? (taskProgress.completed / taskProgress.total) * 100 : 0}%` }"></div>
      </div>
      <div class="task-progress-text">进度: {{ taskProgress.completed }}/{{ taskProgress.total }} 完成</div>
    </div>

    <!-- 输入区域 -->
    <div class="input-area">
      <div :class="['input-container', { focused: inputFocused, loading: loading }]">
        <input v-model="inputMessage" type="text"
               :placeholder="hasRunningTasks ? '任务正在后台执行中...' : '输入消息...'"
               :disabled="loading" @focus="inputFocused = true" @blur="inputFocused = false"
               @keyup.enter="sendMessage" class="chat-input" />
        <button @click="sendMessage" :disabled="loading || !inputMessage.trim()" class="send-btn">
          <span v-if="loading" class="spinner"></span>
          <span v-else-if="hasRunningTasks">执行中</span>
          <span v-else>发送</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-view { height: 100%; display: flex; flex-direction: column; background: transparent; }
.chat-header { padding: 24px 32px; display: flex; justify-content: space-between; align-items: center; flex-shrink: 0; }
.header-left { display: flex; align-items: center; gap: 12px; }
.header-icon { font-size: 24px; }
.header-title { font-size: 20px; font-weight: 600; color: #1d1d1f; }
.session-indicator { margin-left: 8px; }
.session-badge { padding: 4px 10px; background: rgba(10, 132, 255, 0.1); border-radius: 8px; font-size: 12px; color: #0a84ff; }
.header-controls { display: flex; align-items: center; gap: 12px; }
.session-btn, .new-btn, .clear-btn { padding: 8px 12px; background: rgba(255, 255, 255, 0.9); border: 1px solid rgba(0, 0, 0, 0.08); border-radius: 10px; font-size: 14px; cursor: pointer; transition: all 0.2s ease; }
.session-btn:hover { background: rgba(10, 132, 255, 0.1); border-color: rgba(10, 132, 255, 0.2); }
.new-btn:hover { background: rgba(48, 209, 88, 0.1); border-color: rgba(48, 209, 88, 0.2); }
.clear-btn:hover { background: rgba(255, 59, 48, 0.1); border-color: rgba(255, 59, 48, 0.2); }
.model-select { padding: 8px 16px; background: rgba(255, 255, 255, 0.9); border: 1px solid rgba(0, 0, 0, 0.08); border-radius: 12px; font-size: 13px; color: #1d1d1f; cursor: pointer; outline: none; transition: all 0.2s ease; font-family: inherit; }

.session-panel { background: rgba(255, 255, 255, 0.95); border: 1px solid rgba(0, 0, 0, 0.08); border-radius: 16px; margin: 0 32px 16px; max-height: 280px; overflow: hidden; flex-shrink: 0; }
.session-panel-header { padding: 16px 20px; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid rgba(0, 0, 0, 0.06); font-weight: 500; color: #1d1d1f; }
.session-new-btn { padding: 6px 12px; background: #0a84ff; color: #fff; border: none; border-radius: 8px; font-size: 12px; cursor: pointer; }
.session-list { overflow-y: auto; max-height: 220px; }
.session-empty { padding: 40px 20px; text-align: center; color: #86868b; }
.session-item { padding: 12px 20px; display: flex; align-items: center; gap: 12px; cursor: pointer; transition: background 0.2s ease; border-bottom: 1px solid rgba(0, 0, 0, 0.04); }
.session-item:hover { background: rgba(0, 0, 0, 0.02); }
.session-item.active { background: rgba(10, 132, 255, 0.08); }
.session-info { flex: 1; }
.session-title { font-size: 14px; font-weight: 500; color: #1d1d1f; }
.session-time { font-size: 12px; color: #86868b; }
.session-stats { display: flex; gap: 8px; font-size: 11px; }
.token-count { color: #515154; }
.usage-percent { color: #0a84ff; }
.session-delete-btn { padding: 4px 8px; background: transparent; border: none; color: #86868b; cursor: pointer; font-size: 12px; }
.session-delete-btn:hover { color: #ff453a; }

.messages-area { flex: 1; overflow-y: auto; padding: 24px 32px; display: flex; flex-direction: column; gap: 20px; min-height: 200px; }
.loading-more { text-align: center; padding: 10px; color: #86868b; font-size: 13px; }
.spinner-small { display: inline-block; width: 14px; height: 14px; border: 2px solid rgba(0, 0, 0, 0.1); border-top-color: #0a84ff; border-radius: 50%; animation: spin 0.7s linear infinite; margin-right: 8px; vertical-align: middle; }
.load-more-tip { text-align: center; padding: 10px; color: #86868b; font-size: 12px; }
.empty-state { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding-bottom: 60px; }
.empty-icon { font-size: 64px; margin-bottom: 20px; opacity: 0.6; }
.empty-title { font-size: 18px; font-weight: 500; color: #515154; margin-bottom: 8px; }
.empty-desc { font-size: 13px; color: #86868b; }

.message-row { display: flex; gap: 12px; max-width: 75%; }
.message-row.user { flex-direction: row-reverse; align-self: flex-end; }
.message-row.assistant { align-self: flex-start; }
.message-row.error { align-self: center; max-width: 85%; }
.avatar { width: 36px; height: 36px; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 600; flex-shrink: 0; }
.avatar.user { background: linear-gradient(135deg, #0a84ff, #5e5ce6); color: #fff; }
.avatar.assistant { background: rgba(48, 209, 88, 0.15); color: #30d158; border: 1px solid rgba(48, 209, 88, 0.25); }
.avatar.error { background: rgba(255, 59, 48, 0.15); color: #ff453a; border: 1px solid rgba(255, 59, 48, 0.25); }
.message-bubble { display: flex; flex-direction: column; gap: 4px; }
.bubble-content { padding: 14px 18px; border-radius: 20px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; font-size: 14px; }
.message-row.user .bubble-content { background: #0a84ff; color: #fff; border-bottom-right-radius: 6px; }
.message-row.assistant .bubble-content { background: rgba(0, 0, 0, 0.04); color: #1d1d1f; border: 1px solid rgba(0, 0, 0, 0.08); border-bottom-left-radius: 6px; }
.message-row.error .bubble-content { background: rgba(255, 59, 48, 0.08); color: #ff453a; border: 1px solid rgba(255, 59, 48, 0.2); text-align: center; }
.bubble-time { font-size: 11px; color: #86868b; padding: 0 4px; }
.message-row.user .bubble-time { text-align: right; }

/* 任务状态面板 - 只在有执行中任务时显示 */
.task-status-panel { margin: 0 32px; padding: 12px 16px; background: linear-gradient(135deg, rgba(10, 132, 255, 0.08), rgba(94, 92, 230, 0.08)); border: 1px solid rgba(10, 132, 255, 0.2); border-radius: 12px; flex-shrink: 0; }
.task-status-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.task-status-icon { font-size: 16px; animation: pulse 1.5s ease-in-out infinite; }
.task-status-title { font-weight: 600; color: #0a84ff; font-size: 14px; }
.task-status-count { margin-left: auto; font-size: 11px; color: #5e5ce6; background: rgba(94, 92, 230, 0.15); padding: 3px 8px; border-radius: 8px; }
.task-status-list { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
.task-status-item { display: flex; align-items: center; gap: 6px; padding: 4px 10px; background: rgba(255, 255, 255, 0.7); border-radius: 8px; font-size: 12px; }
.task-status-dot { width: 6px; height: 6px; border-radius: 50%; background: #0a84ff; animation: pulse-dot 1s ease-in-out infinite; }
.task-status-subject { color: #1d1d1f; }
.task-status-badge { font-size: 10px; padding: 1px 6px; background: rgba(10, 132, 255, 0.15); color: #0a84ff; border-radius: 4px; }
.task-progress-bar { height: 3px; background: rgba(0, 0, 0, 0.1); border-radius: 2px; overflow: hidden; }
.task-progress-fill { height: 100%; background: linear-gradient(90deg, #0a84ff, #30d158); border-radius: 2px; transition: width 0.3s ease; }

@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.6; } }
@keyframes pulse-dot { 0%, 100% { transform: scale(1); opacity: 1; } 50% { transform: scale(1.2); opacity: 0.7; } }
@keyframes spin { to { transform: rotate(360deg); } }

.input-area { padding: 24px 32px 32px; flex-shrink: 0; }
.input-container { display: flex; align-items: center; background: rgba(255, 255, 255, 0.9); backdrop-filter: blur(15px); -webkit-backdrop-filter: blur(15px); border-radius: 24px; padding: 8px; border: 1px solid rgba(0, 0, 0, 0.08); box-shadow: 0 4px 30px rgba(0, 0, 0, 0.06); transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1); }
.input-container.focused { border-color: rgba(10, 132, 255, 0.5); box-shadow: 0 0 0 3px rgba(10, 132, 255, 0.15), 0 4px 30px rgba(0, 0, 0, 0.06); }
.chat-input { flex: 1; background: transparent; border: none; outline: none; padding: 12px 16px; font-size: 15px; color: #1d1d1f; font-weight: 400; }
.chat-input::placeholder { color: #86868b; }
.chat-input:disabled { color: #86868b; }
.send-btn { padding: 12px 20px; border-radius: 14px; border: none; background: #0a84ff; color: #fff; cursor: pointer; font-size: 14px; font-weight: 500; display: flex; align-items: center; justify-content: center; transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1); }
.send-btn:hover:not(:disabled) { background: #0070e0; transform: scale(1.02); }
.send-btn:disabled { background: #d1d1d6; color: #86868b; cursor: not-allowed; }
.spinner { width: 18px; height: 18px; border: 2px solid rgba(255, 255, 255, 0.3); border-top-color: #fff; border-radius: 50%; animation: spin 0.7s linear infinite; }
</style>