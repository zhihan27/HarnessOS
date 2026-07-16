import { defineStore } from 'pinia'
import { ref, watch, computed } from 'vue'
import { post, get, del } from '@/api/request'
import { API_ROUTES, BASE_URL } from '@/api/config'

export const useChatStore = defineStore('chat', () => {
  // 从 localStorage 读取初始值
  const savedModelType = localStorage.getItem('harness-model-type')
  const savedSessionId = localStorage.getItem('harness-session-id')
  const modelType = ref(savedModelType || 'openai')

  // 只有当 sessionId 有效（非空）时才使用
  const currentSessionId = ref(savedSessionId && savedSessionId.length > 0 ? savedSessionId : null)

  const messages = ref([])
  const sessions = ref([])
  const loading = ref(false)
  const loadedCount = ref(0)  // 已加载的消息数量

  // 监听变化，自动持久化到 localStorage
  watch(modelType, (newVal) => {
    localStorage.setItem('harness-model-type', newVal)
  })

  // 监听 sessionId 变化，只有有效值才保存
  watch(currentSessionId, (newVal) => {
    if (newVal && newVal.length > 0) {
      localStorage.setItem('harness-session-id', newVal)
    } else {
      // 清除无效值
      localStorage.removeItem('harness-session-id')
    }
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
        // 如果删除的是当前会话，切换到其他会话或清除
        if (sessions.value.length > 0) {
          // 选择最新的会话
          currentSessionId.value = sessions.value[0].sessionId
          await fetchRecentHistory(currentSessionId.value, 20)
        } else {
          // 没有其他会话，清除状态
          currentSessionId.value = null
          localStorage.removeItem('harness-session-id')
          clearMessages()
        }
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

  /**
   * SSE流式发送消息（推荐使用）
   *
   * @param {string} text - 用户消息
   * @param {function} onUserMessage - 用户消息回调
   * @param {function} onAiThinking - AI思考回调
   * @param {function} onAiMessage - AI消息回调
   * @param {function} onComplete - 完成回调
   * @param {function} onError - 错误回调
   * @returns {Promise<void>}
   */
  const sendMessageStream = async (text, callbacks = {}) => {
    const {
      onUserMessage,
      onAiThinking,
      onAiMessage,
      onComplete,
      onError
    } = callbacks

    loading.value = true

    return new Promise((resolve, reject) => {
      // 构建SSE URL（注意：EventSource需要完整路径）
      const params = new URLSearchParams({
        message: text
      })

      if (currentSessionId.value) {
        params.append('sessionId', currentSessionId.value)
      }

      // 添加BASE_URL前缀
      const url = `${BASE_URL}${API_ROUTES.CHAT_STREAM}?${params.toString()}`
      console.log('创建SSE连接:', url)

      const eventSource = new EventSource(url)

      // 会话创建事件
      eventSource.addEventListener('session_created', (event) => {
        try {
          if (!event.data) {
            console.warn('session_created事件无数据')
            return
          }
          const data = JSON.parse(event.data)
          currentSessionId.value = data.sessionId
          console.log('会话创建:', data.sessionId)
        } catch (e) {
          console.error('解析session_created事件失败:', e, '原始数据:', event.data)
        }
      })

      // 用户消息事件
      eventSource.addEventListener('user_message', (event) => {
        try {
          if (!event.data) {
            console.warn('user_message事件无数据')
            return
          }
          const data = JSON.parse(event.data)
          console.log('用户消息:', data)
          if (onUserMessage) {
            onUserMessage(data)
          }
        } catch (e) {
          console.error('解析user_message事件失败:', e, '原始数据:', event.data)
        }
      })

      // AI思考事件
      eventSource.addEventListener('ai_thinking', (event) => {
        try {
          if (!event.data) {
            console.warn('ai_thinking事件无数据')
            return
          }
          const data = JSON.parse(event.data)
          console.log('AI思考:', data)
          if (onAiThinking) {
            onAiThinking(data)
          }
        } catch (e) {
          console.error('解析ai_thinking事件失败:', e, '原始数据:', event.data)
        }
      })

      // AI消息事件
      eventSource.addEventListener('ai_message', (event) => {
        try {
          if (!event.data) {
            console.warn('ai_message事件无数据')
            return
          }
          const data = JSON.parse(event.data)
          console.log('AI消息:', data)
          if (onAiMessage) {
            onAiMessage(data)
          }
        } catch (e) {
          console.error('解析ai_message事件失败:', e, '原始数据:', event.data)
        }
      })

      // 对话完成事件
      eventSource.addEventListener('chat_complete', (event) => {
        try {
          if (!event.data) {
            console.warn('chat_complete事件无数据')
            eventSource.close()
            loading.value = false
            resolve({ success: true })
            return
          }

          const data = JSON.parse(event.data)
          console.log('对话完成:', data)
          eventSource.close()
          loading.value = false

          if (onComplete) {
            onComplete(data)
          }
          resolve(data)
        } catch (e) {
          console.error('解析chat_complete事件失败:', e, '原始数据:', event.data)
          eventSource.close()
          loading.value = false
          resolve({ success: true }) // 即使解析失败也算完成
        }
      })

      // 错误事件
      eventSource.addEventListener('error', (event) => {
        try {
          console.error('收到错误事件:', event)
          if (event.data) {
            const data = JSON.parse(event.data)
            console.error('错误详情:', data)
          }

          eventSource.close()
          loading.value = false

          if (onError) {
            onError({ message: event.data || '对话处理失败' })
          }
          reject(new Error(event.data || '对话处理失败'))
        } catch (e) {
          console.error('解析error事件失败:', e, '原始数据:', event.data)
          eventSource.close()
          loading.value = false
          reject(e)
        }
      })

      // SSE连接错误
      eventSource.onerror = (error) => {
        console.error('SSE连接错误:', error)
        console.error('EventSource readyState:', eventSource.readyState)

        // 只在连接真正失败时关闭
        if (eventSource.readyState === EventSource.CLOSED) {
          loading.value = false
          if (onError) {
            onError({ message: 'SSE连接已关闭' })
          }
          reject(new Error('SSE连接已关闭'))
        } else if (eventSource.readyState === EventSource.CONNECTING) {
          console.log('正在重连...')
        } else {
          eventSource.close()
          loading.value = false
          if (onError) {
            onError({ message: 'SSE连接失败' })
          }
          reject(new Error('SSE连接失败'))
        }
      }

      // 监听连接打开事件
      eventSource.onopen = () => {
        console.log('SSE连接已建立')
      }
    })
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
    sendMessage,
    sendMessageStream  // 导出新的SSE流式方法
  }
})