<script setup>
import { ref, nextTick, computed } from 'vue'
import { useChatStore } from '@/stores/chat'
import { post } from '@/api/request'
import { API_ROUTES } from '@/api/config'

const chatStore = useChatStore()
const inputMessage = ref('')
const loading = ref(false)
const messagesContainer = ref(null)
const inputFocused = ref(false)

const modelType = computed({
  get: () => chatStore.modelType,
  set: (val) => chatStore.setModelType(val)
})

const messages = computed(() => chatStore.messages)

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTo({
      top: messagesContainer.value.scrollHeight,
      behavior: 'smooth'
    })
  }
}

const sendMessage = async () => {
  const text = inputMessage.value.trim()
  if (!text || loading.value) return

  chatStore.addMessage({
    role: 'user',
    content: text,
    timestamp: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  })
  inputMessage.value = ''
  await scrollToBottom()

  loading.value = true
  try {
    const data = await post(API_ROUTES.CHAT, {
      message: text,
      modelType: chatStore.modelType
    })

    const reply = data.message || data.data?.message || data.data?.reply || data.data?.content || data.reply || data.content || '无响应内容'

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
    loading.value = false
    await scrollToBottom()
  }
}

const clearChat = () => {
  chatStore.clearMessages()
}
</script>

<template>
  <div class="chat-view">
    <div class="chat-header">
      <div class="header-left">
        <span class="header-icon">💬</span>
        <div class="header-title">智能对话</div>
      </div>
      <div class="header-controls">
        <select v-model="modelType" class="model-select">
          <option value="openai">DeepSeek R1 / V3</option>
          <option value="anthropic">Claude 3.5 Sonnet</option>
        </select>
        <button @click="clearChat" class="clear-btn" title="清空对话">
          🗑️
        </button>
      </div>
    </div>

    <div ref="messagesContainer" class="messages-area">
      <div v-if="messages.length === 0" class="empty-state">
        <div class="empty-icon">🚀</div>
        <div class="empty-title">开始对话</div>
        <div class="empty-desc">输入消息以启动 AI Agent 会话</div>
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

.header-controls {
  display: flex;
  align-items: center;
  gap: 12px;
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

.clear-btn {
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 10px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.clear-btn:hover {
  background: rgba(255, 59, 48, 0.1);
  border-color: rgba(255, 59, 48, 0.2);
}

.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
  display: flex;
  flex-direction: column;
  gap: 20px;
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