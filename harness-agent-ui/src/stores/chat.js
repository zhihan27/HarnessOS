import { defineStore } from 'pinia'
import { ref, watch, computed } from 'vue'
import { post, get, del } from '@/api/request'
import { API_ROUTES } from '@/api/config'

export const useChatStore = defineStore('chat', () => {
  // 从 localStorage 读取初始值
  const savedModelType = localStorage.getItem('harness-model-type')
  const savedSessionId = localStorage.getItem('harness-session-id')
  const modelType = ref(savedModelType || 'openai')
  const currentSessionId = ref(savedSessionId || '')

  const messages = ref([])
  const sessions = ref([])
  const loading = ref(false)
  const loadedCount = ref(0)  // 已加载的消息数量

  // 监听变化，自动持久化到 localStorage
  watch(modelType, (newVal) => {
    localStorage.setItem('harness-model-type', newVal)
  })

  watch(currentSessionId, (newVal) => {
    localStorage.setItem('harness-session-id', newVal)
  })

  const setModelType = (type) => {
    modelType.value = type
  }

  const setCurrentSessionId = (sessionId) => {
    currentSessionId.value = sessionId
  }

  const addMessage = (msg) => {
    messages.value.push(msg)
  }

  const clearMessages = () => {
    messages.value = []
    loadedCount.value = 0
  }

  // 获取会话列表
  const fetchSessions = async () => {
    try {
      const data = await get(API_ROUTES.SESSIONS)
      sessions.value = data || []
    } catch (error) {
      console.error('获取会话列表失败:', error)
    }
  }

  // 创建新会话
  const createSession = async () => {
    try {
      const data = await post(API_ROUTES.SESSIONS, {
        tenantId: 'default-tenant',
        userId: 'default-user'
      })
      currentSessionId.value = data.sessionId
      clearMessages()
      return data.sessionId
    } catch (error) {
      console.error('创建会话失败:', error)
      return null
    }
  }

  // 获取最近的聊天历史（分页加载）
  const fetchRecentHistory = async (sessionId, limit = 20) => {
    try {
      // 请求最近的消息（从最新开始）
      const data = await get(`${API_ROUTES.HISTORY}/${sessionId}?limit=${limit}&order=desc`)

      if (data && data.length > 0) {
        // 反转顺序（API返回的是倒序，需要正序显示）
        const reversedData = [...data].reverse()
        messages.value = reversedData.map(msg => ({
          role: msg.messageType === 'USER' ? 'user' :
                msg.messageType === 'AI' ? 'assistant' :
                msg.messageType === 'SYSTEM' ? 'assistant' : 'assistant',
          content: msg.content,
          timestamp: msg.createdAt ? new Date(msg.createdAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : ''
        }))
        loadedCount.value = data.length
        return { hasMore: data.length >= limit }
      }

      messages.value = []
      loadedCount.value = 0
      return { hasMore: false }
    } catch (error) {
      console.error('获取历史失败:', error)
      return { hasMore: false }
    }
  }

  // 加载更多历史（向上翻页）
  const loadMoreHistory = async (sessionId, limit = 20) => {
    try {
      // 从已加载的消息数量开始，请求更早的消息
      const offset = loadedCount.value
      const data = await get(`${API_ROUTES.HISTORY}/${sessionId}?limit=${limit}&offset=${offset}&order=desc`)

      if (data && data.length > 0) {
        // 反转并添加到现有消息的前面
        const reversedData = [...data].reverse()
        const olderMessages = reversedData.map(msg => ({
          role: msg.messageType === 'USER' ? 'user' :
                msg.messageType === 'AI' ? 'assistant' :
                msg.messageType === 'SYSTEM' ? 'assistant' : 'assistant',
          content: msg.content,
          timestamp: msg.createdAt ? new Date(msg.createdAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : ''
        }))

        messages.value = [...olderMessages, ...messages.value]
        loadedCount.value += data.length
        return { hasMore: data.length >= limit }
      }

      return { hasMore: false }
    } catch (error) {
      console.error('加载更多历史失败:', error)
      return { hasMore: false }
    }
  }

  // 删除会话
  const deleteSession = async (sessionId) => {
    try {
      await del(`${API_ROUTES.SESSIONS}/${sessionId}`)
      await fetchSessions()
      if (currentSessionId.value === sessionId) {
        currentSessionId.value = ''
        clearMessages()
      }
    } catch (error) {
      console.error('删除会话失败:', error)
    }
  }

  // 发送消息（支持会话）
  const sendMessage = async (text) => {
    loading.value = true
    try {
      const data = await post(API_ROUTES.CHAT, {
        message: text,
        sessionId: currentSessionId.value || null
      })

      // 更新sessionId（如果后端返回新的）
      if (data.sessionId) {
        currentSessionId.value = data.sessionId
      }

      const reply = data.message || '无响应内容'
      return reply
    } catch (error) {
      throw error
    } finally {
      loading.value = false
    }
  }

  return {
    modelType,
    currentSessionId,
    messages,
    sessions,
    loading,
    loadedCount,
    setModelType,
    setCurrentSessionId,
    addMessage,
    clearMessages,
    fetchSessions,
    createSession,
    fetchRecentHistory,
    loadMoreHistory,
    deleteSession,
    sendMessage
  }
})