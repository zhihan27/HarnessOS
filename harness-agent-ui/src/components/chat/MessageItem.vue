<template>
  <div :class="['message-row', message.role]">
    <div :class="['avatar', message.role]">
      <span v-if="message.role === 'user'">ME</span>
      <span v-else-if="message.role === 'assistant'">AI</span>
      <span v-else>!</span>
    </div>
    <div class="message-bubble">
      <!-- 用户消息 -->
      <template v-if="message.role === 'user'">
        <div class="bubble-content">{{ message.content }}</div>
        <div class="bubble-time">{{ message.timestamp }}</div>
      </template>

      <!-- AI消息 -->
      <template v-else-if="message.role === 'assistant'">
        <!-- 思考中状态 -->
        <template v-if="message.isThinking">
          <div class="thinking-indicator">
            <div class="thinking-dots">
              <span></span>
              <span></span>
              <span></span>
            </div>
            <span class="thinking-text">{{ message.content }}</span>
          </div>
        </template>

        <!-- 正常AI消息 -->
        <template v-else>
          <div class="bubble-content">
            <MarkdownRenderer v-if="shouldRenderMarkdown" :content="message.content" />
            <template v-else>{{ message.content }}</template>
          </div>
          <div class="bubble-time">{{ message.timestamp }}</div>
        </template>
      </template>

      <!-- 其他类型消息 -->
      <template v-else>
        <div class="bubble-content">{{ message.content }}</div>
        <div class="bubble-time">{{ message.timestamp }}</div>
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

// 判断是否应该渲染 Markdown（AI 消息且不在思考中）
const shouldRenderMarkdown = computed(() => {
  return props.message.role === 'assistant' && !props.message.isThinking
})
</script>

<style scoped>
.message-row {
  display: flex;
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

.message-row.user {
  justify-content: flex-end;
}

.message-row.assistant {
  justify-content: flex-start;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: bold;
  flex-shrink: 0;
}

.avatar.user {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  margin-left: 12px;
}

.avatar.assistant {
  background: linear-gradient(135deg, #0078d4 0%, #00a4ef 100%);
  color: white;
  margin-right: 12px;
}

.message-bubble {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 12px;
  position: relative;
}

.message-row.user .message-bubble {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.message-row.assistant .message-bubble {
  background: #f3f3f3;
  color: #242424;
  border: 1px solid #e0e0e0;
}

.bubble-content {
  word-wrap: break-word;
  overflow-wrap: break-word;
  line-height: 1.5;
}

.bubble-time {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.7);
  margin-top: 4px;
  text-align: right;
}

.message-row.assistant .bubble-time {
  color: #616161;
}

/* 思考中指示器 */
.thinking-indicator {
  display: flex;
  align-items: center;
  gap: 12px;
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
}
</style>