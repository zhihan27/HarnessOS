<script setup>
const stats = [
  { label: '活跃 Agent', value: '3', color: '#30d158' },
  { label: '待命 Agent', value: '12', color: '#0a84ff' },
  { label: '任务队列', value: '47', color: '#ff9f0a' }
]
</script>

<template>
  <div class="agents-view">
    <div class="page-header">
      <span class="header-icon">🤖</span>
      <div class="header-text">
        <h1 class="title">Agent 编排中心</h1>
        <p class="subtitle">Multi-Agent 协同调度与生命周期管理</p>
      </div>
    </div>

    <div class="stats-row">
      <div v-for="stat in stats" :key="stat.label" class="stat-card">
        <div class="stat-value" :style="{ color: stat.color }">{{ stat.value }}</div>
        <div class="stat-label">{{ stat.label }}</div>
      </div>
    </div>

    <div class="content-card">
      <div class="card-header">
        <span>⚡</span>
        <span>Agent 编排引擎</span>
      </div>
      <div class="card-body">
        <p class="desc-text">
          基于状态机的 Agent 生命周期编排系统即将上线。<br/>
          支持可视化 DAG 流程设计、条件分支、并行执行与错误回滚机制。
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.agents-view {
  height: 100%;
  padding: 32px;
  overflow-y: auto;
  background: transparent;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
}

.header-icon {
  font-size: 32px;
}

.header-text {
  flex: 1;
}

.title {
  font-size: 24px;
  font-weight: 600;
  color: #1d1d1f;
  margin-bottom: 4px;
}

.subtitle {
  font-size: 13px;
  color: #86868b;
  font-weight: 400;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(15px);
  -webkit-backdrop-filter: blur(15px);
  border-radius: 16px;
  padding: 24px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 4px 30px rgba(0, 0, 0, 0.04);
  text-align: center;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  margin-bottom: 6px;
  font-family: 'SF Mono', 'Courier New', monospace;
}

.stat-label {
  font-size: 12px;
  color: #86868b;
  font-weight: 500;
}

.content-card {
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(15px);
  -webkit-backdrop-filter: blur(15px);
  border-radius: 16px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 4px 30px rgba(0, 0, 0, 0.04);
}

.card-header {
  padding: 20px 24px;
  display: flex;
  align-items: center;
  gap: 12px;
  color: #515154;
  font-size: 15px;
  font-weight: 500;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.card-body {
  padding: 24px;
}

.desc-text {
  font-size: 14px;
  color: #86868b;
  line-height: 1.7;
}
</style>