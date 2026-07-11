import { defineStore } from 'pinia'
import { get } from '@/api/request'
import { API_ROUTES } from '@/api/config'

export const useTaskStore = defineStore('task', {
  state: () => ({
    // 任务列表
    tasks: [],
    // 任务进度
    progress: {
      total: 0,
      pending: 0,
      inProgress: 0,
      completed: 0,
      failed: 0,
      isRunning: false
    },
    // Task Team 状态
    teamStatus: {
      running: false,
      message: '',
      details: ''
    },
    // 当前会话的任务
    sessionTasks: [],
    // 轮询定时器
    pollTimer: null,
    // 是否正在轮询
    isPolling: false
  }),

  getters: {
    // 获取指定状态的任务
    getTasksByStatus: (state) => (status) => {
      return state.tasks.filter(t => t.status === status)
    },
    // 获取就绪任务
    readyTasks: (state) => {
      return state.tasks.filter(t => t.status === 'pending' && (!t.blockedBy || t.blockedBy === '[]'))
    },
    // 获取执行中任务
    runningTasks: (state) => {
      return state.tasks.filter(t => t.status === 'in_progress')
    },
    // 是否有任务在执行
    hasRunningTasks: (state) => {
      return state.progress.inProgress > 0
    }
  },

  actions: {
    /**
     * 获取任务列表
     */
    async fetchTasks(sessionId = null, status = null) {
      try {
        let url = API_ROUTES.TASK_LIST
        const params = new URLSearchParams()
        if (sessionId) params.append('sessionId', sessionId)
        if (status) params.append('status', status)
        if (params.toString()) url += '?' + params.toString()

        const response = await get(url)
        this.tasks = response || []
        if (sessionId) {
          this.sessionTasks = this.tasks
        }
        return this.tasks
      } catch (error) {
        console.error('获取任务列表失败:', error)
        return []
      }
    },

    /**
     * 获取任务进度
     */
    async fetchProgress(sessionId = null) {
      try {
        let url = API_ROUTES.TASK_PROGRESS
        if (sessionId) url += '?sessionId=' + sessionId

        const response = await get(url)
        if (response) {
          this.progress = response
        }
        return this.progress
      } catch (error) {
        console.error('获取任务进度失败:', error)
        return this.progress
      }
    },

    /**
     * 获取 Task Team 状态
     */
    async fetchTeamStatus() {
      try {
        const response = await get(API_ROUTES.TEAM_STATUS)
        if (response) {
          this.teamStatus = response
        }
        return this.teamStatus
      } catch (error) {
        console.error('获取 Team 状态失败:', error)
        return this.teamStatus
      }
    },

    /**
     * 刷新所有任务状态
     */
    async refreshAll(sessionId = null) {
      await Promise.all([
        this.fetchTasks(sessionId),
        this.fetchProgress(sessionId),
        this.fetchTeamStatus()
      ])
    },

    /**
     * 启动轮询（每 2 秒刷新一次）
     */
    startPolling(sessionId = null, interval = 2000) {
      if (this.isPolling) return

      this.isPolling = true
      // 立即刷新一次
      this.refreshAll(sessionId)

      // 设置定时轮询
      this.pollTimer = setInterval(() => {
        this.refreshAll(sessionId)
      }, interval)

      console.log('任务状态轮询已启动')
    },

    /**
     * 停止轮询
     */
    stopPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
      this.isPolling = false
      console.log('任务状态轮询已停止')
    },

    /**
     * 清空状态
     */
    clearTasks() {
      this.tasks = []
      this.sessionTasks = []
      this.progress = {
        total: 0,
        pending: 0,
        inProgress: 0,
        completed: 0,
        failed: 0,
        isRunning: false
      }
    }
  }
})