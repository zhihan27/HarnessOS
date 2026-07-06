<script setup>
import { ref, nextTick, computed, onMounted, watch } from 'vue'
import { useChatStore } from '@/stores/chat'

const chatStore = useChatStore()
const inputMessage = ref('')
const messagesContainer = ref(null)
const inputFocused = ref(false)
const showSessionList = ref(false)
const isLoadingMore = ref(false)
const hasMoreHistory = ref(true)
const pageSize = 20  // 每次加载20条

const modelType = computed({
  get: () => chatStore.modelType,
  set: (val) => chatStore.setModelType(val)
})

const messages = computed(() => chatStore.messages)
const sessions = computed(() => chatStore.sessions)
const currentSessionId = computed(() => chatStore.currentSessionId)
const loading = computed(() => chatStore.loading)

// 滚动到底部（instant 模式，确保立即滚动）
const scrollToBottom = async (instant = true) => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTo({
      top: messagesContainer.value.scrollHeight,
      behavior: instant ? 'instant' : 'smooth'
    })
  }
}

// 监听滚动，顶部加载更多
const handleScroll = async () => {
  if (!messagesContainer.value || isLoadingMore.value || !hasMoreHistory.value) return

  const { scrollTop, scrollHeight } = messagesContainer.value
  // 滚动到顶部附近时加载更多
  if (scrollTop < 50 && messages.value.length >= pageSize) {
    isLoadingMore.value = true
    const prevHeight = scrollHeight

    // 加载更多历史
    const loaded = await chatStore.loadMoreHistory(currentSessionId.value, pageSize)
    hasMoreHistory.value = loaded.hasMore

    // 保持滚动位置（新内容加载后，调整scrollTop让用户看到之前的内容）
    await nextTick()
    const newHeight = messagesContainer.value.scrollHeight
    messagesContainer.value.scrollTop = newHeight - prevHeight

    isLoadingMore.value = false
  }
}

// 监听消息变化，新消息时滚动到底部
watch(messages, (newMsgs, oldMsgs) => {
  if (newMsgs.length > oldMsgs?.length) {
    // 只在新消息增加时滚动（加载历史不滚动）
    const lastOldMsg = oldMsgs?.[oldMsgs.length - 1]
    const lastNewMsg = newMsgs[newMsgs.length - 1]
    if (lastOldMsg?.timestamp !== lastNewMsg?.timestamp) {
      scrollToBottom(false)
    }
  }
}, { deep: true })

onMounted(async () => {
  await chatStore.fetchSessions()
  // 如果有当前会话，自动加载历史并滚动到底部
  if (currentSessionId.value) {
    await chatStore.fetchRecentHistory(currentSessionId.value, pageSize)
    // 确保 DOM 渲染完成后滚动到底部
    await scrollToBottom(true)
  }
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
  // 只加载最近的消息
  await chatStore.fetchRecentHistory(sessionId, pageSize)
  showSessionList.value = false
  // 加载后立即滚动到底部
  await scrollToBottom(true)
}

const newSession = async () => {
  chatStore.clearMessages()
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
        <button @click="toggleSessionList" class="session-btn" title="会话列表">
          📋
        </button>
        <select v-model="modelType" class="model-select">
          <option value="openai">DeepSeek R1 / V3</option>
          <option value="anthropic">Claude 3.5 Sonnet</option>
        </select>
        <button @click="newSession" class="new-btn" title="新会话">
          ➕
        </button>
        <button @click="clearChat" class="clear-btn" title="清空当前对话">
          🗑️
        </button>
      </div>
    </div>

    <!-- 会话列表面板 -->
    <div v-if="showSessionList" class="session-panel">
      <div class="session-panel-header">
        <span>历史会话</span>
        <button @click="newSession" class="session-new-btn">新建</button>
      </div>
      <div class="session-list">
        <div v-if="sessions.length === 0" class="session-empty">
          暂无历史会话
        </div>
        <div
          v-for="session in sessions"
          :key="session.sessionId"
          :class="['session-item', { active: session.sessionId === currentSessionId }]"
          @click="selectSession(session.sessionId)"
        >
          <div class="session-info">
            <div class="session-title">{{ session.title || session.sessionId.slice(0, 8) }}</div>
            <div class="session-time">{{ session.lastMessageAt ? new Date(session.lastMessageAt).toLocaleString('zh-CN') : '无消息' }}</div>
          </div>
          <div class="session-stats">
            <span class="token-count">{{ session.totalTokens || 0 }} tokens</span>
            <span class="usage-percent">{{ session.tokenUsagePercent ? session.tokenUsagePercent.toFixed(1) + '%' : '0%' }}</span>
          </div>
          <button @click.stop="deleteSession(session.sessionId)" class="session-delete-btn" title="删除">
            ✕
          </button>
        </div>
      </div>
    </div>

    <div
      ref="messagesContainer"
      class="messages-area"
      @scroll="handleScroll"
    >
      <!-- 加载更多提示 -->
      <div v-if="isLoadingMore" class="loading-more">
        <span class="spinner-small"></span>
        加载更多...
      </div>
      <div v-else-if="hasMoreHistory && messages.length >= pageSize" class="load-more-tip">
        ↑ 向上滚动加载更多历史
      </div>

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

    <div class="input-area">
      <div :class="['input-container', { focused: inputFocused, loading: loading }]">
        <input
          v-model="inputMessage"
          type="text"
          placeholder="输入消息..."
          :disabled="loading"
          @focus="inputFocused = true"
          @blur="inputFocused = false"
          @keyup.enter="sendMessage"
          class="chat-input"
        />
        <button
          @click="sendMessage"
          :disabled="loading || !inputMessage.trim()"
          class="send-btn"
        >
          <span v-if="loading" class="spinner"></span>
          <span v-else>发送</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: transparent;
}

.chat-header {
  padding: 24px 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-icon {
  font-size: 24px;
}

.header-title {
  font-size: 20px;
  font-weight: 600;
  color: #1d1d1f;
}

.session-indicator {
  margin-left: 8px;
}

.session-badge {
  padding: 4px 10px;
  background: rgba(10, 132, 255, 0.1);
  border-radius: 8px;
  font-size: 12px;
  color: #0a84ff;
}

.header-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.session-btn, .new-btn, .clear-btn {
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 10px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.session-btn:hover {
  background: rgba(10, 132, 255, 0.1);
  border-color: rgba(10, 132, 255, 0.2);
}

.new-btn:hover {
  background: rgba(48, 209, 88, 0.1);
  border-color: rgba(48, 209, 88, 0.2);
}

.clear-btn:hover {
  background: rgba(255, 59, 48, 0.1);
  border-color: rgba(255, 59, 48, 0.2);
}

.model-select {
  padding: 8px 16px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 12px;
  font-size: 13px;
  color: #1d1d1f;
  cursor: pointer;
  outline: none;
  transition: all 0.2s ease;
  font-family: inherit;
}

.model-select:hover {
  border-color: rgba(10, 132, 255, 0.3);
}

.model-select:focus {
  border-color: #0a84ff;
  box-shadow: 0 0 0 3px rgba(10, 132, 255, 0.15);
}

/* 会话列表面板 */
.session-panel {
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 16px;
  margin: 0 32px 16px;
  max-height: 300px;
  overflow: hidden;
}

.session-panel-header {
  padding: 16px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  font-weight: 500;
  color: #1d1d1f;
}

.session-new-btn {
  padding: 6px 12px;
  background: #0a84ff;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 12px;
  cursor: pointer;
}

.session-list {
  overflow-y: auto;
  max-height: 240px;
}

.session-empty {
  padding: 40px 20px;
  text-align: center;
  color: #86868b;
}

.session-item {
  padding: 12px 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: background 0.2s ease;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
}

.session-item:hover {
  background: rgba(0, 0, 0, 0.02);
}

.session-item.active {
  background: rgba(10, 132, 255, 0.08);
}

.session-info {
  flex: 1;
}

.session-title {
  font-size: 14px;
  font-weight: 500;
  color: #1d1d1f;
}

.session-time {
  font-size: 12px;
  color: #86868b;
}

.session-stats {
  display: flex;
  gap: 8px;
  font-size: 11px;
}

.token-count {
  color: #515154;
}

.usage-percent {
  color: #0a84ff;
}

.session-delete-btn {
  padding: 4px 8px;
  background: transparent;
  border: none;
  color: #86868b;
  cursor: pointer;
  font-size: 12px;
}

.session-delete-btn:hover {
  color: #ff453a;
}

.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 加载更多样式 */
.loading-more {
  text-align: center;
  padding: 10px;
  color: #86868b;
  font-size: 13px;
}

.spinner-small {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid rgba(0, 0, 0, 0.1);
  border-top-color: #0a84ff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  margin-right: 8px;
  vertical-align: middle;
}

.load-more-tip {
  text-align: center;
  padding: 10px;
  color: #86868b;
  font-size: 12px;
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-bottom: 60px;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 20px;
  opacity: 0.6;
}

.empty-title {
  font-size: 18px;
  font-weight: 500;
  color: #515154;
  margin-bottom: 8px;
}

.empty-desc {
  font-size: 13px;
  color: #86868b;
}

.message-row {
  display: flex;
  gap: 12px;
  max-width: 75%;
}

.message-row.user {
  flex-direction: row-reverse;
  align-self: flex-end;
}

.message-row.assistant {
  align-self: flex-start;
}

.message-row.error {
  align-self: center;
  max-width: 85%;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}

.avatar.user {
  background: linear-gradient(135deg, #0a84ff, #5e5ce6);
  color: #fff;
}

.avatar.assistant {
  background: rgba(48, 209, 88, 0.15);
  color: #30d158;
  border: 1px solid rgba(48, 209, 88, 0.25);
}

.avatar.error {
  background: rgba(255, 59, 48, 0.15);
  color: #ff453a;
  border: 1px solid rgba(255, 59, 48, 0.25);
}

.message-bubble {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.bubble-content {
  padding: 14px 18px;
  border-radius: 20px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
}

.message-row.user .bubble-content {
  background: #0a84ff;
  color: #fff;
  border-bottom-right-radius: 6px;
}

.message-row.assistant .bubble-content {
  background: rgba(0, 0, 0, 0.04);
  color: #1d1d1f;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-bottom-left-radius: 6px;
}

.message-row.error .bubble-content {
  background: rgba(255, 59, 48, 0.08);
  color: #ff453a;
  border: 1px solid rgba(255, 59, 48, 0.2);
  text-align: center;
}

.bubble-time {
  font-size: 11px;
  color: #86868b;
  padding: 0 4px;
}

.message-row.user .bubble-time {
  text-align: right;
}

.input-area {
  padding: 24px 32px 32px;
}

.input-container {
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(15px);
  -webkit-backdrop-filter: blur(15px);
  border-radius: 24px;
  padding: 8px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  box-shadow: 0 4px 30px rgba(0, 0, 0, 0.06);
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.input-container.focused {
  border-color: rgba(10, 132, 255, 0.5);
  box-shadow: 0 0 0 3px rgba(10, 132, 255, 0.15), 0 4px 30px rgba(0, 0, 0, 0.06);
}

.chat-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  padding: 12px 16px;
  font-size: 15px;
  color: #1d1d1f;
  font-weight: 400;
}

.chat-input::placeholder {
  color: #86868b;
}

.chat-input:disabled {
  color: #86868b;
}

.send-btn {
  padding: 12px 20px;
  border-radius: 14px;
  border: none;
  background: #0a84ff;
  color: #fff;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.send-btn:hover:not(:disabled) {
  background: #0070e0;
  transform: scale(1.02);
}

.send-btn:disabled {
  background: #d1d1d6;
  color: #86868b;
  cursor: not-allowed;
}

.spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>