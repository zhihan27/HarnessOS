<script setup>
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const menuItems = [
  { path: '/chat', icon: '💬', title: '智能对话' },
  { path: '/agents', icon: '🤖', title: 'Agent编排' },
  { path: '/plugins', icon: '🛠️', title: '插件中心' },
  { path: '/monitor', icon: '📊', title: '内核监控' }
]

const isActive = (path) => route.path === path

const navigate = (path) => router.push(path)
</script>

<template>
  <div class="main-layout">
    <aside class="sidebar">
      <div class="logo-section">
        <div class="logo-icon">
          <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
            <defs>
              <linearGradient id="logoGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stop-color="#0a84ff"/>
                <stop offset="100%" stop-color="#5e5ce6"/>
              </linearGradient>
            </defs>
            <circle cx="16" cy="16" r="14" stroke="url(#logoGrad)" stroke-width="2" fill="none"/>
            <circle cx="16" cy="16" r="6" fill="url(#logoGrad)" opacity="0.8"/>
          </svg>
        </div>
        <div class="logo-text">HarnessOS</div>
        <div class="logo-subtitle">AI Agent Platform</div>
      </div>

      <nav class="menu-nav">
        <div
          v-for="item in menuItems"
          :key="item.path"
          :class="['menu-item', { active: isActive(item.path) }]"
          @click="navigate(item.path)"
        >
          <div class="menu-indicator" v-if="isActive(item.path)"></div>
          <span class="menu-icon">{{ item.icon }}</span>
          <span class="menu-title">{{ item.title }}</span>
        </div>
      </nav>

      <div class="sidebar-footer">
        <div class="status-row">
          <span class="status-dot"></span>
          <span class="status-text">System Ready</span>
        </div>
        <div class="version-text">v1.0.0 Beta</div>
      </div>
    </aside>

    <main class="workspace">
      <router-view v-slot="{ Component }">
        <transition name="slide-fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<style scoped>
.main-layout {
  display: flex;
  height: 100vh;
  width: 100%;
  overflow: hidden;
}

.sidebar {
  width: 240px;
  min-width: 240px;
  height: 100%;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  display: flex;
  flex-direction: column;
  user-select: none;
  position: relative;
}

.sidebar::after {
  content: '';
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  width: 1px;
  background: linear-gradient(180deg, transparent 0%, rgba(0,0,0,0.08) 20%, rgba(0,0,0,0.08) 80%, transparent 100%);
}

.logo-section {
  padding: 36px 24px 32px;
  text-align: center;
}

.logo-icon {
  margin-bottom: 12px;
  display: flex;
  justify-content: center;
}

.logo-text {
  font-size: 20px;
  font-weight: 600;
  color: #1d1d1f;
  letter-spacing: 0.5px;
  margin-bottom: 4px;
}

.logo-subtitle {
  font-size: 11px;
  color: #86868b;
  letter-spacing: 1px;
  font-weight: 400;
}

.menu-nav {
  flex: 1;
  padding: 8px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  color: #86868b;
  background: transparent;
}

.menu-item:hover {
  color: #515154;
  background: rgba(0, 0, 0, 0.04);
}

.menu-item.active {
  color: #1d1d1f;
  background: rgba(10, 132, 255, 0.08);
}

.menu-indicator {
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 3px;
  background: linear-gradient(180deg, #0a84ff, #5e5ce6);
  border-radius: 0 3px 3px 0;
  box-shadow: 0 0 8px rgba(10, 132, 255, 0.4);
}

.menu-icon {
  font-size: 18px;
  width: 24px;
  text-align: center;
}

.menu-title {
  font-size: 14px;
  font-weight: 500;
}

.sidebar-footer {
  padding: 20px 24px;
}

.status-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 8px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #30d158;
  box-shadow: 0 0 12px rgba(48, 209, 88, 0.6);
  animation: breathe 3s ease-in-out infinite;
}

@keyframes breathe {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.7; transform: scale(0.9); }
}

.status-text {
  font-size: 12px;
  color: #86868b;
  font-weight: 500;
}

.version-text {
  font-size: 10px;
  color: #86868b;
  text-align: center;
  font-family: 'SF Mono', 'Courier New', monospace;
}

.workspace {
  flex: 1;
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: transparent;
}

.slide-fade-enter-active {
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-fade-leave-active {
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-fade-enter-from {
  opacity: 0;
  transform: translateX(12px);
}

.slide-fade-leave-to {
  opacity: 0;
  transform: translateX(-12px);
}
</style>