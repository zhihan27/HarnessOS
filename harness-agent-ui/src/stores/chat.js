import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export const useChatStore = defineStore('chat', () => {
  // 从 localStorage 读取初始值
  const savedModelType = localStorage.getItem('harness-model-type')
  const modelType = ref(savedModelType || 'openai')

  const messages = ref([])

  // 监听 modelType 变化，自动持久化到 localStorage
  watch(modelType, (newVal) => {
    localStorage.setItem('harness-model-type', newVal)
  })

  const setModelType = (type) => {
    modelType.value = type
  }

  const addMessage = (msg) => {
    messages.value.push(msg)
  }

  const clearMessages = () => {
    messages.value = []
  }

  return {
    modelType,
    messages,
    setModelType,
    addMessage,
    clearMessages
  }
})