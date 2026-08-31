import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: MainLayout,
      redirect: '/chat',
      children: [
        {
          path: 'chat',
          name: 'chat',
          component: () => import('@/views/ChatView.vue'),
          meta: { title: '智能对话面板', icon: '💬' }
        },
        {
          path: 'agents',
          name: 'agents',
          component: () => import('@/views/AgentsView.vue'),
          meta: { title: 'Agent编排中心', icon: '🤖' }
        },
        {
          path: 'plugins',
          name: 'plugins',
          component: () => import('@/views/PluginsView.vue'),
          meta: { title: '插件能力中心', icon: '🛠️' }
        },
        {
          path: 'rag',
          name: 'rag',
          component: () => import('@/views/RagView.vue'),
          meta: { title: 'RAG 知识库', icon: 'database' }
        },
        {
          path: 'models',
          name: 'models',
          component: () => import('@/views/ModelConfigsView.vue'),
          meta: { title: '模型配置', icon: '⚙' }
        }
      ]
    }
  ],
})

export default router
