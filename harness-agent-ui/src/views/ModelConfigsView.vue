<script setup>
import { onMounted, reactive, ref } from 'vue'
import { get, post, put, del } from '@/api/request'
import { API_ROUTES } from '@/api/config'

const configs = ref([])
const error = ref('')
const saving = ref(false)
const editingId = ref(null)
const form = reactive({ name: '', baseUrl: '', modelName: '', token: '', activate: false })

const resetForm = () => {
  editingId.value = null
  Object.assign(form, { name: '', baseUrl: '', modelName: '', token: '', activate: false })
}

const openCreate = () => {
  error.value = ''
  resetForm()
  editingId.value = 'new'
}

const load = async () => {
  try {
    configs.value = await get(API_ROUTES.MODEL_CONFIGS) || []
  } catch (e) {
    error.value = e.message
  }
}

const submit = async () => {
  const missingRequiredField = !form.name.trim() || !form.baseUrl.trim() || !form.modelName.trim()
  const missingToken = (editingId.value === 'new' || !editingId.value) && !form.token.trim()
  if (missingRequiredField || missingToken) {
    error.value = '请完整填写名称、Base URL、模型和 Token'
    return
  }

  saving.value = true
  error.value = ''
  try {
    if (editingId.value && editingId.value !== 'new') {
      await put(`${API_ROUTES.MODEL_CONFIGS}/${editingId.value}`, { ...form })
    } else {
      await post(API_ROUTES.MODEL_CONFIGS, { ...form })
    }
    resetForm()
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}

const edit = (item) => {
  error.value = ''
  editingId.value = item.id
  Object.assign(form, {
    name: item.name,
    baseUrl: item.baseUrl,
    modelName: item.modelName,
    token: '',
    activate: false
  })
}

const activate = async (id) => {
  try {
    await post(`${API_ROUTES.MODEL_CONFIGS}/${id}/activate`, {})
    await load()
  } catch (e) {
    error.value = e.message
  }
}

const remove = async (item) => {
  if (!window.confirm(`确定删除“${item.name}”吗？`)) {
    return
  }
  try {
    await del(`${API_ROUTES.MODEL_CONFIGS}/${item.id}`)
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
      <div class="header-copy"><div class="eyebrow"><span class="eyebrow-dot"></span>AI PROVIDERS</div><h1>模型配置</h1><p>集中管理兼容 OpenAI API 的服务，并选择当前使用的模型。</p></div>
      <button class="primary add-button" type="button" @click="openCreate"><span class="button-icon">＋</span>新建配置</button>
    </header>
    <div v-if="error" class="error" role="alert"><span class="error-icon">!</span>{{ error }}</div>
    <section class="list-section">
      <div class="section-heading"><div><h2>已保存的服务</h2><p>启用后，智能对话将使用对应的模型服务。</p></div><span class="count-badge">{{ configs.length }} 个</span></div>
      <div v-if="!configs.length" class="empty"><div class="empty-icon">◌</div><strong>还没有模型配置</strong><span>添加一个服务，开始使用你的 AI 模型。</span><button class="secondary" type="button" @click="openCreate">添加第一个配置</button></div>
      <div v-else class="config-list">
        <article v-for="item in configs" :key="item.id" class="config-row" :class="{ 'is-active': item.active }">
          <div class="provider-mark">{{ item.name?.charAt(0)?.toUpperCase() || 'AI' }}</div>
          <div class="config-main"><div class="config-title-row"><h3>{{ item.name }}</h3><span v-if="item.active" class="active"><span class="active-dot"></span>当前使用</span></div><div class="config-model">{{ item.modelName }}</div><div class="config-meta"><span class="meta-label">Endpoint</span>{{ item.baseUrl }}<span class="meta-divider"></span><span class="meta-label">Token</span>{{ item.tokenConfigured ? item.maskedToken : '未配置' }}</div></div>
          <div class="row-actions"><button v-if="!item.active" class="text-button" type="button" @click="activate(item.id)">设为当前</button><span v-else class="enabled">正在使用</span><button class="icon-button" type="button" title="编辑配置" aria-label="编辑配置" @click="edit(item)">✎</button><button class="icon-button danger-icon" type="button" title="删除配置" aria-label="删除配置" @click="remove(item)">⌫</button></div>
        </article>
      </div>
    </section>
    <div v-if="editingId" class="modal-backdrop" @click.self="resetForm">
      <section class="editor-modal" role="dialog" aria-modal="true" aria-labelledby="editor-title" @click.stop>
        <div class="modal-header"><div><div class="modal-kicker">{{ editingId === 'new' ? 'NEW PROVIDER' : 'EDIT PROVIDER' }}</div><h2 id="editor-title">{{ editingId === 'new' ? '新建模型配置' : '编辑模型配置' }}</h2></div><button class="close-button" type="button" title="关闭" aria-label="关闭" @click="resetForm">×</button></div>
        <p class="modal-description">填写服务连接信息，保存后即可在对话中使用。</p>
        <div v-if="error" class="modal-error">{{ error }}</div>
        <form @submit.prevent="submit"><div class="form-grid"><label>显示名称<input v-model="form.name" autocomplete="off" placeholder="例如：生产环境" /></label><label>模型名称<input v-model="form.modelName" autocomplete="off" placeholder="例如：gpt-4o-mini" /></label><label class="full-width">Base URL<input v-model="form.baseUrl" autocomplete="url" placeholder="https://api.openai.com/v1" /></label><label class="full-width">API Token <span v-if="editingId !== 'new'" class="hint">留空则保持原 Token</span><input v-model="form.token" type="password" autocomplete="new-password" placeholder="sk-..." /></label></div><div class="form-actions"><button class="secondary" type="button" @click="resetForm">取消</button><button class="primary" type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存配置' }}</button></div></form>
      </section>
    </div>
  </div>
</template>

<style scoped>
.model-page { height: 100%; overflow: auto; padding: 48px clamp(24px, 5vw, 72px); color: #1d1d1f; }
.page-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; max-width: 980px; margin: 0 auto 36px; }
.eyebrow, .modal-kicker { display: flex; align-items: center; gap: 8px; color: #0a84ff; font-size: 11px; font-weight: 700; letter-spacing: .12em; }
.eyebrow-dot, .active-dot { width: 7px; height: 7px; border-radius: 50%; background: #30d158; box-shadow: 0 0 0 3px rgba(48,209,88,.12); }
.page-header h1 { margin: 10px 0 8px; font-size: clamp(28px, 3vw, 36px); letter-spacing: -.02em; }
.page-header p, .section-heading p, .modal-description { color: #86868b; font-size: 14px; }
button, input { font: inherit; } button { cursor: pointer; transition: all .2s ease; }
.primary, .secondary { border-radius: 9px; padding: 11px 16px; border: 1px solid transparent; font-size: 13px; font-weight: 600; }
.primary { background: #1d1d1f; color: #fff; box-shadow: 0 5px 14px rgba(29,29,31,.16); } .primary:hover { background: #3a3a3c; transform: translateY(-1px); } .primary:disabled { opacity: .55; cursor: wait; transform: none; } .secondary { color: #515154; background: #f5f5f7; border-color: #e5e5ea; } .secondary:hover { background: #eaeaef; } .button-icon { font-size: 18px; vertical-align: -1px; margin-right: 4px; }
.error { display: flex; align-items: center; gap: 10px; max-width: 980px; margin: 0 auto 18px; padding: 12px 14px; border: 1px solid #ffd6d6; border-radius: 9px; color: #b00020; background: #fff7f7; font-size: 13px; } .error-icon { display: grid; place-items: center; width: 18px; height: 18px; border-radius: 50%; color: #fff; background: #d70015; font-size: 11px; font-weight: 700; }
.list-section { max-width: 980px; margin: 0 auto; padding: 26px 28px 10px; border: 1px solid rgba(0,0,0,.07); border-radius: 14px; background: rgba(255,255,255,.78); box-shadow: 0 12px 40px rgba(0,0,0,.05); backdrop-filter: blur(14px); }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding-bottom: 20px; } .section-heading h2 { margin-bottom: 6px; font-size: 18px; } .count-badge { padding: 6px 10px; border-radius: 999px; color: #6e6e73; background: #f2f2f7; font-size: 12px; } .config-list { border-top: 1px solid #e5e5ea; }
.config-row { display: flex; align-items: center; gap: 16px; padding: 20px 4px; border-bottom: 1px solid #e5e5ea; } .config-row:last-child { border-bottom: 0; } .config-row.is-active { margin: 0 -12px; padding-left: 16px; padding-right: 16px; border-radius: 10px; background: linear-gradient(90deg, rgba(48,209,88,.07), transparent 75%); } .provider-mark { display: grid; place-items: center; width: 42px; height: 42px; flex: 0 0 42px; border-radius: 11px; color: #0a84ff; background: #eef6ff; font-size: 17px; font-weight: 700; } .config-main { min-width: 0; flex: 1; } .config-title-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; } .config-title-row h3 { font-size: 15px; font-weight: 650; } .active { display: inline-flex; align-items: center; gap: 6px; padding: 4px 8px; border-radius: 999px; color: #1f8a45; background: #eaf8ee; font-size: 11px; font-weight: 600; } .config-model { margin-top: 5px; color: #515154; font-size: 13px; font-weight: 600; } .config-meta { display: flex; align-items: center; gap: 7px; min-width: 0; margin-top: 7px; overflow: hidden; color: #86868b; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; } .meta-label { color: #a1a1a6; font-size: 10px; text-transform: uppercase; letter-spacing: .05em; } .meta-divider { width: 3px; height: 3px; border-radius: 50%; background: #c7c7cc; } .row-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; } .text-button { border: 0; background: transparent; color: #0a84ff; font-size: 12px; font-weight: 600; } .text-button:hover { color: #0065c8; } .enabled { color: #1f8a45; font-size: 12px; font-weight: 600; } .icon-button, .close-button { display: grid; place-items: center; border: 0; color: #6e6e73; background: transparent; } .icon-button { width: 31px; height: 31px; border-radius: 8px; font-size: 17px; } .icon-button:hover { color: #1d1d1f; background: #f2f2f7; } .danger-icon:hover { color: #d70015; background: #fff0f0; }
.empty { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 58px 20px; color: #86868b; text-align: center; } .empty-icon { color: #c7c7cc; font-size: 38px; line-height: 1; } .empty strong { margin-top: 4px; color: #515154; font-size: 15px; } .empty span { margin-bottom: 12px; font-size: 13px; }
.modal-backdrop { position: fixed; inset: 0; z-index: 20; display: grid; place-items: center; padding: 20px; background: rgba(29,29,31,.28); backdrop-filter: blur(5px); } .editor-modal { width: min(540px,100%); padding: 28px; border: 1px solid rgba(255,255,255,.75); border-radius: 16px; background: rgba(255,255,255,.96); box-shadow: 0 24px 80px rgba(0,0,0,.2); } .modal-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; } .modal-kicker { color: #86868b; } .modal-header h2 { margin-top: 8px; font-size: 22px; } .close-button { width: 30px; height: 30px; border-radius: 8px; color: #86868b; font-size: 25px; line-height: 1; } .close-button:hover { background: #f2f2f7; color: #1d1d1f; } .modal-description { margin: 10px 0 24px; line-height: 1.6; } .modal-error { margin: -10px 0 18px; padding: 10px 12px; border-radius: 8px; color: #b00020; background: #fff0f0; font-size: 12px; line-height: 1.5; }
.form-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 17px 14px; } label { display: flex; flex-direction: column; gap: 8px; color: #515154; font-size: 12px; font-weight: 600; } .full-width { grid-column: 1 / -1; } input { width: 100%; padding: 11px 12px; border: 1px solid #d2d2d7; border-radius: 8px; outline: none; color: #1d1d1f; background: #fff; font-size: 14px; font-weight: 400; transition: border-color .2s, box-shadow .2s; } input:focus { border-color: #0a84ff; box-shadow: 0 0 0 3px rgba(10,132,255,.14); } .hint { margin-left: 4px; color: #86868b; font-size: 11px; font-weight: 400; } .form-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 26px; padding-top: 20px; border-top: 1px solid #e5e5ea; }
@media (max-width: 720px) { .model-page { padding: 28px 16px; } .page-header { align-items: flex-start; flex-direction: column; margin-bottom: 26px; } .add-button { width: 100%; } .list-section { padding: 20px 16px 4px; } .config-row, .config-row.is-active { align-items: flex-start; flex-wrap: wrap; margin: 0; padding: 17px 0; } .config-main { width: calc(100% - 58px); } .config-meta { white-space: normal; overflow: visible; word-break: break-all; } .row-actions { width: 100%; justify-content: flex-end; padding-left: 58px; } .form-grid { grid-template-columns: 1fr; } .full-width { grid-column: auto; } .editor-modal { padding: 22px 18px; } }
</style>



