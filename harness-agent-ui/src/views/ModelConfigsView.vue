<script setup>
import {onMounted, reactive, ref} from 'vue'
import {get, post, put, del} from '@/api/request'
import {API_ROUTES} from '@/api/config'

const configs = ref([])
const error = ref('')
const saving = ref(false)
const editingId = ref(null)
const form = reactive({name: '', baseUrl: '', modelName: '', token: '', activate: false})

const resetForm = () => {
  editingId.value = null;
  Object.assign(form, {name: '', baseUrl: '', modelName: '', token: '', activate: false})
}
const load = async () => {
  try {
    configs.value = await get(API_ROUTES.MODEL_CONFIGS) || []
  } catch (e) {
    error.value = e.message
  }
}
const submit = async () => {
  if (!form.name.trim() || !form.baseUrl.trim() || !form.modelName.trim() || (!editingId.value && !form.token.trim())) {
    error.value = '请完整填写名称、Base URL、模型和 Token';
    return
  }
  saving.value = true;
  error.value = ''
  try {
    if (editingId.value) await put(`${API_ROUTES.MODEL_CONFIGS}/${editingId.value}`, {...form}); else await post(API_ROUTES.MODEL_CONFIGS, {...form});
    resetForm();
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}
const edit = (item) => {
  editingId.value = item.id;
  Object.assign(form, {name: item.name, baseUrl: item.baseUrl, modelName: item.modelName, token: '', activate: false})
}
const activate = async (id) => {
  try {
    await post(`${API_ROUTES.MODEL_CONFIGS}/${id}/activate`, {});
    await load()
  } catch (e) {
    error.value = e.message
  }
}
const remove = async (item) => {
  if (!window.confirm(`确定删除“${item.name}”吗？`)) return;
  try {
    await del(`${API_ROUTES.MODEL_CONFIGS}/${item.id}`);
    await load()
  } catch (e) {
    error.value = e.message
  }
}
onMounted(load)
</script>

<template>
  <div class="model-page">
    <header class="page-header">
      <div><h1>模型配置</h1>
        <p>管理 OpenAI 兼容服务，并选择当前启用的模型。</p></div>
      <button class="secondary" @click="resetForm">新建配置</button>
    </header>
    <div v-if="error" class="error">{{ error }}</div>
    <section class="editor"><h2>{{ editingId ? '编辑配置' : '新增配置' }}</h2>
      <div class="form-grid">
        <label>名称<input v-model="form.name" placeholder="例如：生产模型"/></label>
        <label>Base URL<input v-model="form.baseUrl" placeholder="https://api.openai.com/v1"/></label>
        <label>模型<input v-model="form.modelName" placeholder="gpt-4o-mini"/></label>
        <label>Token <span v-if="editingId" class="hint">留空保持不变</span><input v-model="form.token" type="password"
                                                                                   autocomplete="new-password"
                                                                                   placeholder="sk-..."/></label>
      </div>
      <div class="form-actions">
        <button class="primary" :disabled="saving" @click="submit">{{ saving ? '保存中...' : '保存配置' }}</button>
        <button v-if="editingId" class="secondary" @click="resetForm">取消</button>
      </div>
    </section>
    <section class="list-section">
      <div class="section-title"><h2>已保存配置</h2><span>{{ configs.length }} 个</span></div>
      <div v-if="!configs.length" class="empty">暂无配置，请先新增一个模型。</div>
      <div v-for="item in configs" :key="item.id" class="config-row">
        <div class="config-main">
          <div class="config-name">{{ item.name }} <span v-if="item.active" class="active">当前启用</span></div>
          <div class="config-meta">{{ item.modelName }} · {{ item.baseUrl }}</div>
          <div class="config-token">{{ item.tokenConfigured ? item.maskedToken : '未配置 Token' }}</div>
        </div>
        <div class="row-actions">
          <button v-if="!item.active" class="primary small" @click="activate(item.id)">启用</button>
          <span v-else class="enabled">使用中</span>
          <button class="secondary small" @click="edit(item)">编辑</button>
          <button class="danger small" @click="remove(item)">删除</button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.model-page {
  height: 100%;
  overflow: auto;
  padding: 40px 48px;
  color: #1d1d1f
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28px
}

.page-header h1 {
  font-size: 28px;
  margin-bottom: 6px
}

.page-header p {
  color: #6e6e73
}

.editor, .list-section {
  background: rgba(255, 255, 255, .78);
  border: 1px solid rgba(0, 0, 0, .08);
  border-radius: 10px;
  padding: 24px;
  margin-bottom: 20px
}

.editor h2, .list-section h2 {
  font-size: 17px;
  margin-bottom: 18px
}

.form-grid {
  display: grid;
  grid-template-columns:repeat(2, minmax(0, 1fr));
  gap: 16px
}

label {
  display: flex;
  flex-direction: column;
  gap: 7px;
  font-size: 13px;
  color: #515154
}

input {
  border: 1px solid #d2d2d7;
  border-radius: 7px;
  padding: 11px 12px;
  font: inherit;
  color: #1d1d1f;
  background: #fff
}

input:focus {
  outline: 2px solid rgba(10, 132, 255, .25);
  border-color: #0a84ff
}

.hint {
  font-size: 11px;
  color: #86868b
}

.form-actions {
  display: flex;
  gap: 10px;
  margin-top: 20px
}

.primary, .secondary, .danger {
  border: 0;
  border-radius: 7px;
  padding: 10px 15px;
  font: inherit;
  cursor: pointer
}

.primary {
  background: #0a84ff;
  color: #fff
}

.secondary {
  background: #f2f2f7;
  color: #1d1d1f
}

.danger {
  background: #fff0f0;
  color: #d70015
}

.small {
  padding: 7px 10px;
  font-size: 12px
}

.primary:disabled {
  opacity: .6
}

.error {
  padding: 12px 14px;
  margin-bottom: 16px;
  border-radius: 7px;
  background: #fff0f0;
  color: #b00020
}

.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center
}

.section-title span {
  font-size: 12px;
  color: #86868b
}

.empty {
  padding: 28px 0;
  color: #86868b;
  text-align: center
}

.config-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 17px 0;
  border-top: 1px solid #e5e5ea
}

.config-main {
  min-width: 0
}

.config-name {
  font-weight: 600
}

.active {
  display: inline-block;
  margin-left: 8px;
  padding: 3px 7px;
  border-radius: 10px;
  background: #e8f8ed;
  color: #1f8a45;
  font-size: 11px
}

.config-meta, .config-token {
  font-size: 12px;
  color: #6e6e73;
  margin-top: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0
}

.enabled {
  font-size: 12px;
  color: #1f8a45
}

@media (max-width: 720px) {
  .model-page {
    padding: 24px 18px
  }

  .form-grid {
    grid-template-columns:1fr
  }

  .config-row {
    align-items: flex-start;
    flex-direction: column
  }

  .row-actions {
    width: 100%
  }
}
</style>
