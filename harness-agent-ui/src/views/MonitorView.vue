<script setup>
const metrics = [
  { label: 'CPU 负载', value: '23%', color: '#30d158' },
  { label: '内存占用', value: '1.2 GB', color: '#0a84ff' },
  { label: '活跃连接', value: '128', color: '#ff9f0a' },
  { label: '响应延迟', value: '45 ms', color: '#5e5ce6' }
]

const logs = [
  { time: '14:32:05', level: 'INFO', msg: 'Agent [DeepSeek-V3] 已成功初始化' },
  { time: '14:32:03', level: 'INFO', msg: '插件 [WebSearch] 加载完成' },
  { time: '14:31:58', level: 'WARN', msg: '缓存命中率低于阈值，建议扩容' },
  { time: '14:31:45', level: 'INFO', msg: '用户会话 [session_7f3a] 建立' },
  { time: '14:31:30', level: 'INFO', msg: '系统启动完成，进入就绪状态' }
]

const levelConfig = {
  INFO: { bg: 'rgba(10, 132, 255, 0.1)', color: '#0a84ff' },
  WARN: { bg: 'rgba(255, 159, 10, 0.1)', color: '#ff9f0a' },
  ERROR: { bg: 'rgba(255, 59, 48, 0.1)', color: '#ff453a' }
}
</script>

<template>
  <div class="monitor-view">
    <div class="page-header">
      <span class="header-icon">📊</span>
      <div class="header-text">
        <h1 class="title">内核指标监控</h1>
        <p class="subtitle">实时系统状态与运行时监控面板</p>
      </div>
    </div>

    <div class="metrics-grid">
      <div v-for="metric in metrics" :key="metric.label" class="metric-card">
        <div class="metric-value" :style="{ color: metric.color }">{{ metric.value }}</div>
        <div class="metric-label">{{ metric.label }}</div>
        <div class="metric-bar">
          <div
            class="metric-fill"
            :style="{ backgroundColor: metric.color, width: metric.value.includes('%') ? metric.value : '60%' }"
          ></div>
        </div>
      </div>
    </div>

    <div class="logs-card">
      <div class="card-header">
        <span>📜</span>
        <span>实时日志流</span>
        <span class="live-badge">
          <span class="live-dot"></span>
          LIVE
        </span>
      </div>
      <div class="logs-body">
        <div v-for="(log, index) in logs" :key="index" class="log-row">
          <span class="log-time">{{ log.time }}</span>
          <span
            class="log-level"
            :style="{ background: levelConfig[log.level].bg, color: levelConfig[log.level].color }"
          >
            {{ log.level }}
          </span>
          <span class="log-msg">{{ log.msg }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.monitor-view {
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

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.metric-card {
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(15px);
  -webkit-backdrop-filter: blur(15px);
  border-radius: 16px;
  padding: 20px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 4px 30px rgba(0, 0, 0, 0.04);
}

.metric-value {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 6px;
  font-family: 'SF Mono', 'Courier New', monospace;
}

.metric-label {
  font-size: 12px;
  color: #86868b;
  font-weight: 500;
  margin-bottom: 12px;
}

.metric-bar {
  height: 4px;
  background: rgba(0, 0, 0, 0.06);
  border-radius: 2px;
  overflow: hidden;
}

.metric-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.5s cubic-bezier(0.4, 0, 0.2, 1);
}

.logs-card {
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(15px);
  -webkit-backdrop-filter: blur(15px);
  border-radius: 16px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 4px 30px rgba(0, 0, 0, 0.04);
}

.card-header {
  padding: 18px 24px;
  display: flex;
  align-items: center;
  gap: 12px;
  color: #515154;
  font-size: 15px;
  font-weight: 500;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.live-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  padding: 4px 10px;
  background: rgba(48, 209, 88, 0.1);
  color: #30d158;
  border-radius: 10px;
  font-weight: 600;
  margin-left: auto;
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #30d158;
  animation: blink 1.5s ease-in-out infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.logs-body {
  padding: 16px 24px;
  max-height: 280px;
  overflow-y: auto;
}

.log-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
}

.log-row:last-child {
  border-bottom: none;
}

.log-time {
  font-size: 12px;
  color: #86868b;
  font-family: 'SF Mono', 'Courier New', monospace;
  min-width: 70px;
}

.log-level {
  font-size: 10px;
  padding: 3px 8px;
  border-radius: 6px;
  font-weight: 600;
  min-width: 42px;
  text-align: center;
}

.log-msg {
  font-size: 13px;
  color: #515154;
  flex: 1;
}
</style>