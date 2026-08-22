<template>
  <div class="assistant-message">
    <div class="avatar">
      <span>AI</span>
    </div>
    <div class="message-content">
      <!-- 思考中状态 -->
      <template v-if="isThinking">
        <div class="thinking-indicator">
          <div class="thinking-dots">
            <span></span>
            <span></span>
            <span></span>
          </div>
          <span class="thinking-text">{{ message.content }}</span>
        </div>
      </template>

      <!-- 正常消息 -->
      <template v-else>
        <div class="message-body">
          <MarkdownRenderer :content="message.content" />
        </div>
        <div class="message-footer">
          <span class="timestamp">{{ message.timestamp }}</span>
          <div class="message-actions">
            <button @click="copyMessage" class="action-btn" title="复制">📋</button>
            <button @click="regenerate" class="action-btn" title="重新生成">🔄</button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import MarkdownRenderer from './MarkdownRenderer.vue'

const props = defineProps({
  message: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['regenerate', 'copy'])

const isThinking = computed(() => props.message.isThinking)

const copyMessage = () => {
  navigator.clipboard.writeText(props.message.content)
  emit('copy', props.message)
}

const regenerate = () => {
  emit('regenerate', props.message)
}
</script>

<style scoped>
.assistant-message {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  padding: 0 16px;
  animation: fadeIn 0.3s ease-in-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #0078d4 0%, #00a4ef 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: bold;
  flex-shrink: 0;
}

.message-content {
  flex: 1;
  max-width: 70%;
}

.message-body {
  background: #f3f3f3;
  border: 1px solid #e0e0e0;
  border-radius: 12px;
  padding: 12px 16px;
  color: #242424;
}

.message-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
  padding: 0 4px;
}

.timestamp {
  font-size: 11px;
  color: #616161;
}

.message-actions {
  display: flex;
  gap: 8px;
  opacity: 0;
  transition: opacity 0.2s;
}

.assistant-message:hover .message-actions {
  opacity: 1;
}

.action-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 14px;
  padding: 4px;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.action-btn:hover {
  background: rgba(0, 0, 0, 0.05);
}

/* 思考中指示器 */
.thinking-indicator {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #f3f3f3;
  border: 1px solid #e0e0e0;
  border-radius: 12px;
  padding: 12px 16px;
}

.thinking-dots {
  display: flex;
  gap: 4px;
}

.thinking-dots span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #0078d4;
  animation: bounce 1.4s infinite ease-in-out;
}

.thinking-dots span:nth-child(1) {
  animation-delay: -0.32s;
}

.thinking-dots span:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

.thinking-text {
  color: #0078d4;
  font-style: italic;
  font-size: 14px;
}
</style>