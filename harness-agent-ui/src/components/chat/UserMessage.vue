<template>
  <div class="user-message">
    <div class="message-content">
      <div class="message-body">
        <template v-if="isEditing">
          <textarea v-model="editContent" class="edit-textarea" @keyup.enter="saveEdit" @keyup.esc="cancelEdit"></textarea>
          <div class="edit-actions">
            <button @click="saveEdit" class="save-btn">保存</button>
            <button @click="cancelEdit" class="cancel-btn">取消</button>
          </div>
        </template>
        <template v-else>
          {{ message.content }}
        </template>
      </div>
      <div class="message-footer">
        <span class="timestamp">{{ message.timestamp }}</span>
        <div class="message-actions">
          <button @click="editMessage" class="action-btn" title="编辑">✏️</button>
          <button @click="copyMessage" class="action-btn" title="复制">📋</button>
        </div>
      </div>
    </div>
    <div class="avatar">
      <span>ME</span>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  message: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['edit', 'copy'])

const isEditing = ref(false)
const editContent = ref('')

const editMessage = () => {
  isEditing.value = true
  editContent.value = props.message.content
}

const saveEdit = () => {
  if (editContent.value.trim() && editContent.value !== props.message.content) {
    emit('edit', {
      ...props.message,
      content: editContent.value.trim()
    })
  }
  isEditing.value = false
}

const cancelEdit = () => {
  isEditing.value = false
  editContent.value = ''
}

const copyMessage = () => {
  navigator.clipboard.writeText(props.message.content)
  emit('copy', props.message)
}
</script>

<style scoped>
.user-message {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
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
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: bold;
  flex-shrink: 0;
}

.message-content {
  max-width: 70%;
}

.message-body {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 12px;
  padding: 12px 16px;
  word-wrap: break-word;
  overflow-wrap: break-word;
  line-height: 1.5;
}

.edit-textarea {
  width: 100%;
  min-height: 60px;
  background: rgba(255, 255, 255, 0.9);
  border: none;
  border-radius: 8px;
  padding: 8px;
  font-size: 14px;
  font-family: inherit;
  resize: vertical;
  color: #242424;
}

.edit-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  justify-content: flex-end;
}

.save-btn,
.cancel-btn {
  padding: 4px 12px;
  border: none;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.save-btn {
  background: rgba(255, 255, 255, 0.9);
  color: #667eea;
}

.cancel-btn {
  background: rgba(255, 255, 255, 0.2);
  color: white;
}

.save-btn:hover,
.cancel-btn:hover {
  opacity: 0.8;
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
  color: rgba(255, 255, 255, 0.7);
  text-align: right;
}

.message-actions {
  display: flex;
  gap: 8px;
  opacity: 0;
  transition: opacity 0.2s;
}

.user-message:hover .message-actions {
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
  background: rgba(255, 255, 255, 0.2);
}
</style>