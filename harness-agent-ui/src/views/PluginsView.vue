<script setup>
const plugins = [
  { name: 'CodeInterpreter', status: 'active', desc: 'Python 代码执行沙箱' },
  { name: 'WebSearch', status: 'active', desc: '联网搜索与信息检索' },
  { name: 'FileIO', status: 'pending', desc: '文件读写操作模块' },
  { name: 'MemoryBank', status: 'pending', desc: '持久化记忆存储' }
]

const statusConfig = {
  active: { label: '运行中', bg: 'rgba(48, 209, 88, 0.1)', color: '#30d158', border: 'rgba(48, 209, 88, 0.2)' },
  pending: { label: '待激活', bg: 'rgba(255, 159, 10, 0.1)', color: '#ff9f0a', border: 'rgba(255, 159, 10, 0.2)' }
}
</script>

<template>
  <div class="plugins-view">
    <div class="page-header">
      <span class="header-icon">🛠️</span>
      <div class="header-text">
        <h1 class="title">插件能力中心</h1>
        <p class="subtitle">扩展插件管理与动态能力注入</p>
      </div>
    </div>

    <div class="plugins-grid">
      <div v-for="plugin in plugins" :key="plugin.name" class="plugin-card">
        <div class="plugin-row">
          <span class="plugin-name">{{ plugin.name }}</span>
          <span
            :class="['plugin-badge']"
            :style="{
              background: statusConfig[plugin.status].bg,
              color: statusConfig[plugin.status].color,
              borderColor: statusConfig[plugin.status].border
            }"
          >
            {{ statusConfig[plugin.status].label }}
          </span>
        </div>
        <div class="plugin-desc">{{ plugin.desc }}</div>
        <div class="plugin-actions">
          <button class="action-btn subtle">配置</button>
          <button class="action-btn primary">启用</button>
        </div>
      </div>
    </div>

    <div class="content-card">
      <div class="card-header">
        <span>🔌</span>
        <span>插件扩展市场</span>
      </div>
      <div class="card-body">
        <p class="desc-text">
          插件扩展市场即将开放，支持自定义 Tool 注入、API Hook 与热加载机制。<br/>
          敬请期待更强大的 Agent 能力扩展体系。
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.plugins-view {
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

.plugins-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.plugin-card {
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(15px);
  -webkit-backdrop-filter: blur(15px);
  border-radius: 16px;
  padding: 20px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 4px 30px rgba(0, 0, 0, 0.04);
  transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.plugin-card:hover {
  transform: translateY(-2px);
}

.plugin-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.plugin-name {
  font-size: 15px;
  font-weight: 600;
  color: #1d1d1f;
  font-family: 'SF Mono', 'Courier New', monospace;
}

.plugin-badge {
  font-size: 11px;
  padding: 4px 10px;
  border-radius: 12px;
  font-weight: 500;
  border: 1px solid;
}

.plugin-desc {
  font-size: 13px;
  color: #86868b;
  margin-bottom: 16px;
}

.plugin-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  flex: 1;
  padding: 8px 14px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.action-btn.subtle {
  background: rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(0, 0, 0, 0.08);
  color: #515154;
}

.action-btn.subtle:hover {
  background: rgba(0, 0, 0, 0.08);
}

.action-btn.primary {
  background: rgba(10, 132, 255, 0.1);
  border: 1px solid rgba(10, 132, 255, 0.2);
  color: #0a84ff;
}

.action-btn.primary:hover {
  background: rgba(10, 132, 255, 0.15);
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