<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useTaskStore } from '@/stores/task'
import { useAgentStore } from '@/stores/agent'
import AgentStatusPanel from '@/components/AgentStatusPanel.vue'
import { post } from '@/api/request'
import { API_ROUTES } from '@/api/config'

const taskStore = useTaskStore()
const agentStore = useAgentStore()

const taskProgress = computed(() => taskStore.progress)
const tasks = computed(() => taskStore.tasks)
const teamStatus = computed(() => taskStore.teamStatus)

const pendingTasks = computed(() => tasks.value.filter(t => t.status === 'pending'))
const runningTasks = computed(() => tasks.value.filter(t => t.status === 'in_progress'))
const completedTasks = computed(() => tasks.value.filter(t => t.status === 'completed'))
const failedTasks = computed(() => tasks.value.filter(t => t.status === 'failed'))

const isRunning = computed(() => teamStatus.value.running)

// Agent 统计
const agentStats = computed(() => agentStore.stats)

let pollTimer = null

onMounted(async () => {
  await taskStore.refreshAll()
  // Agent Store 在 AgentStatusPanel 组件中初始化
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
  agentStore.stopStatusStream()
})

const startAutoRefresh = () => {
  if (pollTimer) return
  pollTimer = setInterval(() => {
    taskStore.refreshAll()
  }, 3000)
}

const stopAutoRefresh = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const startTeam = async () => {
  try {
    await post(API_ROUTES.TEAM_START, {})
    await taskStore.fetchTeamStatus()
  } catch (error) {
    console.error('启动失败:', error)
  }
}

const stopTeam = async () => {
  try {
    await post(API_ROUTES.TEAM_STOP, {})
    await taskStore.fetchTeamStatus()
  } catch (error) {
    console.error('停止失败:', error)
  }
}

const formatDate = (date) => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

const getStatusClass = (status) => {
  switch (status) {
    case 'pending': return 'status-pending'
    case 'in_progress': return 'status-running'
    case 'completed': return 'status-completed'
    case 'failed': return 'status-failed'
    default: return ''
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

const parseDeps = (jsonStr) => {
  if (!jsonStr || jsonStr === '[]') return []
  try {
    return JSON.parse(jsonStr.replace(/"/g, '"'))
  } catch {
    return []
  }
}
</script>

<template>
  <div class="agents-view">
    <div class="page-header">
      <span class="header-icon">🎯</span>
      <div class="header-text">
        <h1 class="title">任务看板</h1>
        <p class="subtitle">Task Team 异步任务编排与执行监控</p>
      </div>
      <div class="header-actions">
        <div class="agent-summary">
          <span class="summary-item">
            <span class="summary-icon">🤖</span>
            <span>{{ agentStats.total }} Agents</span>
          </span>
          <span class="summary-item working">
            <span class="summary-icon">⚡</span>
            <span>{{ agentStats.working }} 工作</span>
          </span>
        </div>
        <div :class="['team-status', { running: isRunning }]">
          <span class="status-dot"></span>
          <span>{{ isRunning ? '运行中' : '已停止' }}</span>
        </div>
      </div>
    </div>

    <!-- 主内容区：左侧Agent状态 + 右侧任务看板 -->
    <div class="main-content">
      <!-- 左侧：Agent 状态面板 -->
      <div class="agent-panel">
        <AgentStatusPanel />
      </div>

      <!-- 右侧：任务看板 -->
      <div class="task-board">
        <!-- 统计卡片 -->
        <div class="stats-row">
          <div class="stat-card">
            <div class="stat-icon pending">⏳</div>
            <div class="stat-content">
              <div class="stat-value">{{ taskProgress.pending }}</div>
              <div class="stat-label">等待中</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon running">⚡</div>
            <div class="stat-content">
              <div class="stat-value">{{ taskProgress.inProgress }}</div>
              <div class="stat-label">执行中</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon completed">✅</div>
            <div class="stat-content">
              <div class="stat-value">{{ taskProgress.completed }}</div>
              <div class="stat-label">已完成</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon failed">❌</div>
            <div class="stat-content">
              <div class="stat-value">{{ taskProgress.failed }}</div>
              <div class="stat-label">失败</div>
            </div>
          </div>
        </div>

        <!-- 进度条 -->
        <div class="progress-section">
          <div class="progress-header">
            <span>总体进度</span>
            <span>{{ taskProgress.completed }} / {{ taskProgress.total }}</span>
          </div>
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: `${taskProgress.total > 0 ? (taskProgress.completed / taskProgress.total) * 100 : 0}%` }"></div>
          </div>
        </div>

        <!-- 任务看板 -->
        <div class="board-container">
          <!-- 执行中任务 -->
          <div class="board-column">
            <div class="column-header running">
              <span class="column-icon">⚡</span>
              <span>执行中</span>
              <span class="column-count">{{ runningTasks.length }}</span>
            </div>
            <div class="column-body">
              <div v-if="runningTasks.length === 0" class="empty-column">暂无执行中的任务</div>
              <div v-for="task in runningTasks" :key="task.taskId" class="task-card running">
                <div class="task-header">
                  <span class="task-id">{{ task.taskId }}</span>
                  <span class="task-status running">执行中</span>
                </div>
                <div class="task-subject">{{ task.subject }}</div>
                <div class="task-meta">
                  <span v-if="task.assignedAgentId" class="assigned-agent">
                    Agent: {{ task.assignedAgentId }}
                  </span>
                  <span>开始: {{ formatDate(task.startedAt) }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 等待中任务 -->
          <div class="board-column">
            <div class="column-header pending">
              <span class="column-icon">⏳</span>
              <span>等待中</span>
              <span class="column-count">{{ pendingTasks.length }}</span>
            </div>
            <div class="column-body">
              <div v-if="pendingTasks.length === 0" class="empty-column">暂无等待中的任务</div>
              <div v-for="task in pendingTasks" :key="task.taskId" class="task-card pending">
                <div class="task-header">
                  <span class="task-id">{{ task.taskId }}</span>
                  <span class="task-status pending">等待中</span>
                </div>
                <div class="task-subject">{{ task.subject }}</div>
                <div class="task-deps" v-if="parseDeps(task.blockedBy).length > 0">
                  <span class="deps-label">依赖:</span>
                  <span class="deps-list">{{ parseDeps(task.blockedBy).join(', ') }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 已完成任务 -->
          <div class="board-column">
            <div class="column-header completed">
              <span class="column-icon">✅</span>
              <span>已完成</span>
              <span class="column-count">{{ completedTasks.length }}</span>
            </div>
            <div class="column-body">
              <div v-if="completedTasks.length === 0" class="empty-column">暂无已完成的任务</div>
              <div v-for="task in completedTasks.slice(-10)" :key="task.taskId" class="task-card completed">
                <div class="task-header">
                  <span class="task-id">{{ task.taskId }}</span>
                  <span class="task-status completed">已完成</span>
                </div>
                <div class="task-subject">{{ task.subject }}</div>
                <div class="task-result" v-if="task.result">
                  <span class="result-label">结果:</span>
                  <span class="result-text">{{ task.result.slice(0, 100) }}{{ task.result.length > 100 ? '...' : '' }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 失败任务 -->
          <div class="board-column">
            <div class="column-header failed">
              <span class="column-icon">❌</span>
              <span>失败</span>
              <span class="column-count">{{ failedTasks.length }}</span>
            </div>
            <div class="column-body">
              <div v-if="failedTasks.length === 0" class="empty-column">暂无失败的任务</div>
              <div v-for="task in failedTasks" :key="task.taskId" class="task-card failed">
                <div class="task-header">
                  <span class="task-id">{{ task.taskId }}</span>
                  <span class="task-status failed">失败</span>
                </div>
                <div class="task-subject">{{ task.subject }}</div>
                <div class="task-error" v-if="task.error">
                  <span class="error-label">错误:</span>
                  <span class="error-text">{{ task.error }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.agents-view { height: 100%; padding: 24px 32px; overflow-y: auto; background: transparent; }

.page-header { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; }
.header-icon { font-size: 32px; }
.header-text { flex: 1; }
.title { font-size: 24px; font-weight: 600; color: #1d1d1f; margin-bottom: 4px; }
.subtitle { font-size: 13px; color: #86868b; }
.header-actions { display: flex; align-items: center; gap: 12px; }

/* Agent 摘要 */
.agent-summary { display: flex; gap: 16px; margin-right: 16px; }
.summary-item { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #86868b; }
.summary-item.working { color: #0a84ff; }
.summary-icon { font-size: 16px; }

.team-status { display: flex; align-items: center; gap: 8px; padding: 8px 16px; background: rgba(0, 0, 0, 0.05); border-radius: 20px; font-size: 13px; color: #86868b; }
.team-status.running { background: rgba(48, 209, 88, 0.1); color: #30d158; }
.status-dot { width: 8px; height: 8px; border-radius: 50%; background: #86868b; }
.team-status.running .status-dot { background: #30d158; animation: pulse-dot 1s ease-in-out infinite; }

.action-btn { padding: 8px 16px; border: none; border-radius: 10px; font-size: 13px; font-weight: 500; cursor: pointer; }
.action-btn.start { background: #30d158; color: #fff; }
.action-btn.stop { background: #ff453a; color: #fff; }

@keyframes pulse-dot { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.3); } }

/* 主内容区布局 */
.main-content { display: flex; gap: 20px; min-height: calc(100vh - 200px); }

/* 左侧 Agent 面板 */
.agent-panel { width: 320px; flex-shrink: 0; }

/* 右侧任务看板 */
.task-board { flex: 1; min-width: 0; }

/* 统计卡片 */
.stats-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px; }
.stat-card { display: flex; align-items: center; gap: 12px; background: rgba(255, 255, 255, 0.9); border-radius: 12px; padding: 16px; border: 1px solid rgba(0, 0, 0, 0.06); }
.stat-icon { width: 40px; height: 40px; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 20px; }
.stat-icon.pending { background: rgba(255, 159, 10, 0.15); }
.stat-icon.running { background: rgba(10, 132, 255, 0.15); }
.stat-icon.completed { background: rgba(48, 209, 88, 0.15); }
.stat-icon.failed { background: rgba(255, 69, 58, 0.15); }
.stat-value { font-size: 24px; font-weight: 700; color: #1d1d1f; }
.stat-label { font-size: 11px; color: #86868b; }

/* 进度条 */
.progress-section { background: rgba(255, 255, 255, 0.9); border-radius: 10px; padding: 12px 16px; margin-bottom: 16px; border: 1px solid rgba(0, 0, 0, 0.06); }
.progress-header { display: flex; justify-content: space-between; font-size: 12px; color: #515154; margin-bottom: 6px; }
.progress-bar { height: 6px; background: rgba(0, 0, 0, 0.1); border-radius: 3px; overflow: hidden; }
.progress-fill { height: 100%; background: linear-gradient(90deg, #0a84ff, #30d158); border-radius: 3px; transition: width 0.3s ease; }

/* 任务看板 */
.board-container { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.board-column { background: rgba(255, 255, 255, 0.9); border-radius: 12px; border: 1px solid rgba(0, 0, 0, 0.06); display: flex; flex-direction: column; max-height: calc(100vh - 420px); }
.column-header { padding: 12px; display: flex; align-items: center; gap: 6px; font-weight: 600; font-size: 13px; border-bottom: 1px solid rgba(0, 0, 0, 0.06); }
.column-header.pending { color: #ff9f0a; }
.column-header.running { color: #0a84ff; }
.column-header.completed { color: #30d158; }
.column-header.failed { color: #ff453a; }
.column-count { margin-left: auto; background: rgba(0, 0, 0, 0.05); padding: 2px 6px; border-radius: 8px; font-size: 11px; font-weight: 500; }
.column-body { flex: 1; overflow-y: auto; padding: 10px; display: flex; flex-direction: column; gap: 8px; }
.empty-column { text-align: center; color: #86868b; font-size: 12px; padding: 16px; }

/* 任务卡片 */
.task-card { background: rgba(0, 0, 0, 0.02); border-radius: 8px; padding: 10px; border: 1px solid rgba(0, 0, 0, 0.06); }
.task-card.running { border-left: 3px solid #0a84ff; }
.task-card.pending { border-left: 3px solid #ff9f0a; }
.task-card.completed { border-left: 3px solid #30d158; }
.task-card.failed { border-left: 3px solid #ff453a; }
.task-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.task-id { font-size: 10px; color: #86868b; font-family: monospace; }
.task-status { font-size: 10px; padding: 2px 5px; border-radius: 3px; }
.task-status.running { background: rgba(10, 132, 255, 0.15); color: #0a84ff; }
.task-status.pending { background: rgba(255, 159, 10, 0.15); color: #ff9f0a; }
.task-status.completed { background: rgba(48, 209, 88, 0.15); color: #30d158; }
.task-status.failed { background: rgba(255, 69, 58, 0.15); color: #ff453a; }
.task-subject { font-size: 12px; font-weight: 500; color: #1d1d1f; margin-bottom: 4px; line-height: 1.3; }
.task-meta { font-size: 10px; color: #86868b; }
.assigned-agent { color: #0a84ff; margin-right: 8px; }
.task-deps, .task-result, .task-error { font-size: 10px; margin-top: 6px; }
.deps-label, .result-label, .error-label { color: #86868b; }
.deps-list { color: #0a84ff; }
.result-text { color: #515154; display: block; margin-top: 4px; background: rgba(0, 0, 0, 0.03); padding: 4px; border-radius: 4px; }
.error-text { color: #ff453a; display: block; margin-top: 4px; background: rgba(255, 69, 58, 0.08); padding: 4px; border-radius: 4px; }
</style>