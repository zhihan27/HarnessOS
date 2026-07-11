import { defineStore } from 'pinia'
import { get, post } from '@/api/request'
import { API_ROUTES } from '@/api/config'

export const useAgentStore = defineStore('agent', {
  state: () => ({
    // Agent 列表
    agents: [],
    // SSE 连接
    sseConnection: null,
    // 是否已连接
    isConnected: false,
    // 连接错误信息
    connectionError: null
  }),

  getters: {
    // 按ID获取Agent
    getAgentById: (state) => (id) => state.agents.find(a => a.agentId === id),

    // Main Agents
    mainAgents: (state) => state.agents.filter(a => a.agentType === 'MAIN'),

    // Worker Agents
    workerAgents: (state) => state.agents.filter(a => a.agentType === 'WORKER'),

    // 活跃的Agent（非STOPPED）
    activeAgents: (state) => state.agents.filter(a => a.status !== 'STOPPED'),

    // 正在工作的Agent
    workingAgents: (state) => state.agents.filter(a => a.status === 'WORKING'),

    // 空闲的Agent
    idleAgents: (state) => state.agents.filter(a => a.status === 'IDLE'),

    // 统计
    stats: (state) => ({
      total: state.agents.length,
      main: state.agents.filter(a => a.agentType === 'MAIN').length,
      worker: state.agents.filter(a => a.agentType === 'WORKER').length,
      working: state.agents.filter(a => a.status === 'WORKING').length,
      idle: state.agents.filter(a => a.status === 'IDLE').length
    })
  },

  actions: {
    /**
     * 启动 SSE 状态流连接
     */
    startStatusStream() {
      if (this.sseConnection) {
        console.log('SSE 连接已存在')
        return
      }

      // 创建 SSE 连接（使用 agent-mgr 路径）
      const eventSource = new EventSource('/api/agent-mgr/status/stream')

      eventSource.onopen = () => {
        this.isConnected = true
        this.connectionError = null
        console.log('Agent SSE 连接已建立')
      }

      eventSource.addEventListener('agent-status', (event) => {
        try {
          const data = JSON.parse(event.data)
          this.handleStatusEvent(data)
        } catch (e) {
          console.error('解析 SSE 消息失败:', e)
        }
      })

      eventSource.onerror = (err) => {
        this.isConnected = false
        this.connectionError = '连接中断'
        console.error('Agent SSE 连接错误:', err)

        // 5秒后自动重连
        setTimeout(() => {
          if (!this.isConnected) {
            this.stopStatusStream()
            this.startStatusStream()
          }
        }, 5000)
      }

      this.sseConnection = eventSource
    },

    /**
     * 处理状态事件
     */
    handleStatusEvent(event) {
      const existingIndex = this.agents.findIndex(a => a.agentId === event.agentId)

      switch (event.eventType) {
        case 'AGENT_REGISTERED':
          // 添加新 Agent
          if (existingIndex < 0) {
            this.agents.push({
              agentId: event.agentId,
              agentType: event.agentType,
              agentName: event.agentName,
              status: event.status,
              currentTaskId: event.currentTaskId,
              maxConcurrency: event.maxConcurrency,
              currentLoad: event.currentLoad,
              capabilities: event.capabilities
            })
          }
          break

        case 'AGENT_STATUS_CHANGED':
          // 更新 Agent 状态
          if (existingIndex >= 0) {
            this.agents[existingIndex] = {
              ...this.agents[existingIndex],
              status: event.status,
              currentTaskId: event.currentTaskId,
              currentLoad: event.currentLoad
            }
          }
          break

        case 'AGENT_STOPPED':
          // 标记 Agent 已停止
          if (existingIndex >= 0) {
            this.agents[existingIndex].status = 'STOPPED'
            this.agents[existingIndex].currentTaskId = null
            this.agents[existingIndex].currentLoad = 0
          }
          break

        case 'TASK_ASSIGNED':
          // 任务分配事件 - 更新对应 Agent
          const agentIdx = this.agents.findIndex(a => a.agentId === event.agentId)
          if (agentIdx >= 0) {
            this.agents[agentIdx].currentTaskId = event.taskId
            this.agents[agentIdx].status = 'WORKING'
          }
          break

        case 'TASK_COMPLETED':
          // 任务完成事件 - 更新对应 Agent
          const idx = this.agents.findIndex(a => a.agentId === event.agentId)
          if (idx >= 0) {
            this.agents[idx].currentTaskId = null
            if (this.agents[idx].currentLoad > 0) {
              this.agents[idx].currentLoad -= 1
            }
            if (this.agents[idx].currentLoad === 0) {
              this.agents[idx].status = 'IDLE'
            }
          }
          break
      }
    },

    /**
     * 停止 SSE 连接
     */
    stopStatusStream() {
      if (this.sseConnection) {
        this.sseConnection.close()
        this.sseConnection = null
        this.isConnected = false
        console.log('Agent SSE 连接已关闭')
      }
    },

    /**
     * 获取 Agent 列表
     */
    async fetchAgents(type = null) {
      try {
        let url = API_ROUTES.AGENT_LIST
        if (type) {
          url += '?type=' + type
        }
        const response = await get(url)
        this.agents = response || []
        return this.agents
      } catch (error) {
        console.error('获取 Agent 列表失败:', error)
        return []
      }
    },

    /**
     * 注册新 Agent
     */
    async registerAgent(agentType, agentName, maxConcurrency = 1, capabilities = []) {
      try {
        const response = await post(API_ROUTES.AGENT_REGISTER, {
          agentType,
          agentName,
          maxConcurrency,
          capabilities
        })
        return response
      } catch (error) {
        console.error('注册 Agent 失败:', error)
        return null
      }
    },

    /**
     * 停止 Agent
     */
    async stopAgent(agentId) {
      try {
        const response = await post(`${API_ROUTES.AGENT_STOP}/${agentId}/stop`, {})
        return response
      } catch (error) {
        console.error('停止 Agent 失败:', error)
        return null
      }
    },

    /**
     * MainAgent 拆解任务
     */
    async decomposeTask(agentId, taskDescription, sessionId) {
      try {
        const response = await post(`${API_ROUTES.AGENT_DECOMPOSE.replace('{agentId}', agentId)}`, {
          taskDescription,
          sessionId
        })
        return response
      } catch (error) {
        console.error('拆解任务失败:', error)
        return null
      }
    },

    /**
     * Worker 领取任务
     */
    async claimTask(agentId) {
      try {
        const response = await post(`${API_ROUTES.AGENT_CLAIM.replace('{agentId}', agentId)}`, {})
        return response
      } catch (error) {
        console.error('领取任务失败:', error)
        return null
      }
    },

    /**
     * 清空状态
     */
    clearAgents() {
      this.agents = []
      this.stopStatusStream()
    }
  }
})