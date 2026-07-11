<script setup>
import { computed, onMounted, onUnmounted } from 'vue'
import { useAgentStore } from '@/stores/agent'

const agentStore = useAgentStore()

const agents = computed(() => agentStore.agents)
const mainAgents = computed(() => agentStore.mainAgents)
const workerAgents = computed(() => agentStore.workerAgents)
const stats = computed(() => agentStore.stats)
const isConnected = computed(() => agentStore.isConnected)

onMounted(async () => {
  // 先获取Agent列表，然后启动SSE
  await agentStore.fetchAgents()
  agentStore.startStatusStream()
})

onUnmounted(() => {
  agentStore.stopStatusStream()
})

// 状态样式
const getStatusClass = (status) => {
  return {
    'status-idle': status === 'IDLE',
    'status-working': status === 'WORKING',
    'status-stopped': status === 'STOPPED',
    'status-error': status === 'ERROR'
  }
}

// 状态图标
const getStatusIcon = (status) => {
  switch (status) {
    case 'IDLE': return '💤'
    case 'WORKING': return '⚡'
    case 'STOPPED': return '⏹️'
    case 'ERROR': return '❌'
    default: return '❓'
  }
}

// 状态文本
const getStatusText = (status) => {
  switch (status) {
    case 'IDLE': return '空闲'
    case 'WORKING': return '工作中'
    case 'STOPPED': return '已停止'
    case 'ERROR': return '错误'
    default: return status
  }
}

// 停止Agent
const handleStopAgent = async (agentId) => {
  await agentStore.stopAgent(agentId)
}

// 解析能力标签
const parseCapabilities = (jsonStr) => {
  if (!jsonStr || jsonStr === '[]') return []
  try {
    return JSON.parse(jsonStr)
  } catch {
    return []
  }
}
</script>

<template>
  <div class="agent-status-panel">
    <!-- 连接状态 -->
    <div class="connection-status">
      <span :class="['connection-dot', { connected: isConnected }]"></span>
      <span>{{ isConnected ? '实时连接' : '离线' }}</span>
    </div>

    <!-- 统计概览 -->
    <div class="stats-overview">
      <div class="stat-item">
        <span class="stat-icon">🎯</span>
        <div class="stat-info">
          <span class="stat-value">{{ stats.total }}</span>
          <span class="stat-label">总Agent</span>
        </div>
      </div>
      <div class="stat-item">
        <span class="stat-icon working">⚡</span>
        <div class="stat-info">
          <span class="stat-value">{{ stats.working }}</span>
          <span class="stat-label">工作中</span>
        </div>
      </div>
      <div class="stat-item">
        <span class="stat-icon idle">💤</span>
        <div class="stat-info">
          <span class="stat-value">{{ stats.idle }}</span>
          <span class="stat-label">空闲</span>
        </div>
      </div>
    </div>

    <!-- Main Agents 区块 -->
    <div class="agent-section">
      <div class="section-header">
        <span class="section-icon">🎯</span>
        <span class="section-title">Main Agents</span>
        <span class="section-count">{{ mainAgents.length }}</span>
      </div>
      <div class="agent-list">
        <div v-if="mainAgents.length === 0" class="empty-section">
          暂无 Main Agent
        </div>
        <div v-for="agent in mainAgents" :key="agent.agentId"
             :class="['agent-card', getStatusClass(agent.status)]">
          <div class="agent-header">
            <span class="agent-name">{{ agent.agentName }}</span>
            <span class="agent-status">
              <span class="status-icon">{{ getStatusIcon(agent.status) }}</span>
              {{ getStatusText(agent.status) }}
            </span>
          </div>
          <div class="agent-info">
            <span v-if="agent.currentTaskId" class="current-task">
              当前任务: {{ agent.currentTaskId }}
            </span>
            <span v-else class="no-task">空闲中</span>
          </div>
          <div class="agent-actions">
            <button v-if="agent.status !== 'STOPPED'"
                    class="action-btn stop"
                    @click="handleStopAgent(agent.agentId)">
              停止
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Worker Agents 区块 -->
    <div class="agent-section">
      <div class="section-header">
        <span class="section-icon">🔧</span>
        <span class="section-title">Worker Agents</span>
        <span class="section-count">{{ workerAgents.length }}</span>
      </div>
      <div class="agent-list">
        <div v-if="workerAgents.length === 0" class="empty-section">
          暂无 Worker Agent
        </div>
        <div v-for="agent in workerAgents" :key="agent.agentId"
             :class="['agent-card', getStatusClass(agent.status)]">
          <div class="agent-header">
            <span class="agent-name">{{ agent.agentName }}</span>
            <span class="agent-status">
              <span class="status-icon">{{ getStatusIcon(agent.status) }}</span>
              {{ getStatusText(agent.status) }}
            </span>
          </div>
          <div class="agent-load">
            <span class="load-label">负载:</span>
            <div class="load-bar">
              <div class="load-fill"
                   :style="{ width: `${(agent.currentLoad || 0) / (agent.maxConcurrency || 1) * 100}%` }">
              </div>
            </div>
            <span class="load-value">{{ agent.currentLoad || 0 }}/{{ agent.maxConcurrency || 1 }}</span>
          </div>
          <div v-if="agent.currentTaskId" class="current-task">
            执行中: {{ agent.currentTaskId }}
          </div>
          <div v-if="parseCapabilities(agent.capabilities).length > 0" class="agent-capabilities">
            <span class="capability-label">能力:</span>
            <span class="capability-tag" v-for="cap in parseCapabilities(agent.capabilities)" :key="cap">
              {{ cap }}
            </span>
          </div>
          <div class="agent-actions">
            <button v-if="agent.status !== 'STOPPED'"
                    class="action-btn stop"
                    @click="handleStopAgent(agent.agentId)">
              停止
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.agent-status-panel {
  padding: 16px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 16px;
  border: 1px solid rgba(0, 0, 0, 0.06);
}

/* 连接状态 */
.connection-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  font-size: 12px;
  color: #86868b;
}

.connection-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff453a;
}

.connection-dot.connected {
  background: #30d158;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.2); }
}

/* 统计概览 */
.stats-overview {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stat-icon {
  font-size: 20px;
}

.stat-icon.working { color: #0a84ff; }
.stat-icon.idle { color: #86868b; }

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 18px;
  font-weight: 600;
  color: #1d1d1f;
}

.stat-label {
  font-size: 11px;
  color: #86868b;
}

/* Agent区块 */
.agent-section {
  margin-bottom: 16px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: rgba(0, 0, 0, 0.03);
  border-radius: 10px;
  margin-bottom: 10px;
}

.section-icon {
  font-size: 18px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #1d1d1f;
}

.section-count {
  margin-left: auto;
  background: rgba(0, 0, 0, 0.05);
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
  color: #86868b;
}

.agent-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.empty-section {
  text-align: center;
  color: #86868b;
  font-size: 13px;
  padding: 16px;
}

/* Agent卡片 */
.agent-card {
  background: rgba(0, 0, 0, 0.02);
  border-radius: 10px;
  padding: 12px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  transition: all 0.2s;
}

.agent-card:hover {
  background: rgba(0, 0, 0, 0.04);
}

.agent-card.status-idle { border-left: 3px solid #86868b; }
.agent-card.status-working { border-left: 3px solid #0a84ff; }
.agent-card.status-stopped { border-left: 3px solid #ff453a; }
.agent-card.status-error { border-left: 3px solid #ff453a; }

.agent-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.agent-name {
  font-size: 14px;
  font-weight: 500;
  color: #1d1d1f;
}

.agent-status {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #86868b;
}

.status-icon {
  font-size: 14px;
}

.agent-card.status-working .agent-status { color: #0a84ff; }
.agent-card.status-stopped .agent-status { color: #ff453a; }

.agent-info {
  font-size: 12px;
  color: #515154;
}

.current-task {
  color: #0a84ff;
}

.no-task {
  color: #86868b;
}

/* 负载条 */
.agent-load {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.load-label {
  font-size: 11px;
  color: #86868b;
}

.load-bar {
  flex: 1;
  height: 6px;
  background: rgba(0, 0, 0, 0.1);
  border-radius: 3px;
  overflow: hidden;
}

.load-fill {
  height: 100%;
  background: linear-gradient(90deg, #0a84ff, #30d158);
  border-radius: 3px;
  transition: width 0.3s;
}

.load-value {
  font-size: 11px;
  color: #86868b;
}

/* 能力标签 */
.agent-capabilities {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

.capability-label {
  font-size: 11px;
  color: #86868b;
}

.capability-tag {
  font-size: 10px;
  padding: 2px 6px;
  background: rgba(10, 132, 255, 0.1);
  color: #0a84ff;
  border-radius: 4px;
}

/* 操作按钮 */
.agent-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.action-btn {
  padding: 6px 12px;
  border: none;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn.stop {
  background: rgba(255, 69, 58, 0.1);
  color: #ff453a;
}

.action-btn.stop:hover {
  background: rgba(255, 69, 58, 0.2);
}
</style>