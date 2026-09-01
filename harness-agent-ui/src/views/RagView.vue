<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import {
  BookOpen,
  Check,
  ChevronRight,
  Database,
  FileText,
  FlaskConical,
  FolderOpen,
  MoreHorizontal,
  Plus,
  Search,
  Settings,
  SlidersHorizontal,
  Trash2,
  Upload,
  X
} from 'lucide-vue-next'
import { ragApi } from '@/api/rag'

const knowledgeBases = ref([])
const documents = ref([])
const chunks = ref([])
const hits = ref([])
const activeKnowledgeBaseId = ref('')
const activeDocumentId = ref('')
const activeTab = ref('documents')
const loading = ref(false)
const error = ref('')
const notice = ref('')
const modal = ref('')
const fileInput = ref(null)
const queryInput = ref(null)

const knowledgeForm = reactive({
  name: '',
  description: '',
  retrievalMode: 'blend',
  topK: 5,
  similarityThreshold: 0.1,
  segmentSize: 600,
  overlapSize: 80
})
const documentForm = reactive({ name: '', content: '' })
const searchForm = reactive({ query: '', mode: 'blend' })

const activeSummary = computed(() => (
  knowledgeBases.value.find((item) => item.knowledgeBase.id === activeKnowledgeBaseId.value) || null
))
const activeKnowledgeBase = computed(() => activeSummary.value?.knowledgeBase || null)
const activeDocument = computed(() => (
  documents.value.find((item) => item.id === activeDocumentId.value) || null
))

const formatDate = (value) => {
  if (!value) return '刚刚'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value))
}

const formatNumber = (value) => new Intl.NumberFormat('zh-CN').format(value || 0)
const scorePercent = (value) => `${Math.round((value || 0) * 100)}%`

const showError = (exception) => {
  error.value = exception?.message || '操作失败，请稍后重试'
  notice.value = ''
}

const showNotice = (message) => {
  notice.value = message
  error.value = ''
  window.setTimeout(() => {
    if (notice.value === message) notice.value = ''
  }, 2600)
}

const loadKnowledgeBases = async (preferredId = activeKnowledgeBaseId.value) => {
  loading.value = true
  try {
    knowledgeBases.value = await ragApi.listKnowledgeBases() || []
    const target = knowledgeBases.value.find((item) => item.knowledgeBase.id === preferredId)
      || knowledgeBases.value[0]
    activeKnowledgeBaseId.value = target?.knowledgeBase.id || ''
    if (activeKnowledgeBaseId.value) {
      searchForm.mode = target.knowledgeBase.retrievalMode
      await loadDocuments()
    } else {
      documents.value = []
      chunks.value = []
    }
  } catch (exception) {
    showError(exception)
  } finally {
    loading.value = false
  }
}

const selectKnowledgeBase = async (id) => {
  if (id === activeKnowledgeBaseId.value) return
  activeKnowledgeBaseId.value = id
  activeDocumentId.value = ''
  chunks.value = []
  hits.value = []
  searchForm.query = ''
  searchForm.mode = activeKnowledgeBase.value?.retrievalMode || 'blend'
  activeTab.value = 'documents'
  await loadDocuments()
}

const loadDocuments = async () => {
  if (!activeKnowledgeBaseId.value) return
  try {
    documents.value = await ragApi.listDocuments(activeKnowledgeBaseId.value) || []
  } catch (exception) {
    showError(exception)
  }
}

const openKnowledgeModal = (editing = false) => {
  if (editing && activeKnowledgeBase.value) {
    Object.assign(knowledgeForm, activeKnowledgeBase.value)
    modal.value = 'editKnowledge'
  } else {
    Object.assign(knowledgeForm, {
      name: '',
      description: '',
      retrievalMode: 'blend',
      topK: 5,
      similarityThreshold: 0.1,
      segmentSize: 600,
      overlapSize: 80
    })
    modal.value = 'knowledge'
  }
}

const saveKnowledgeBase = async () => {
  try {
    let saved
    if (modal.value === 'editKnowledge') {
      saved = await ragApi.updateKnowledgeBase(activeKnowledgeBaseId.value, { ...knowledgeForm })
    } else {
      saved = await ragApi.createKnowledgeBase({ ...knowledgeForm })
    }
    modal.value = ''
    await loadKnowledgeBases(saved.id)
    showNotice('知识库配置已保存')
  } catch (exception) {
    showError(exception)
  }
}

const removeKnowledgeBase = async () => {
  if (!activeKnowledgeBase.value) return
  if (!window.confirm(`确定删除“${activeKnowledgeBase.value.name}”及其全部文档吗？`)) return
  try {
    await ragApi.deleteKnowledgeBase(activeKnowledgeBaseId.value)
    await loadKnowledgeBases('')
    showNotice('知识库已删除')
  } catch (exception) {
    showError(exception)
  }
}

const openDocumentModal = () => {
  Object.assign(documentForm, { name: '', content: '' })
  modal.value = 'document'
}

const saveDocument = async () => {
  try {
    await ragApi.createDocument(activeKnowledgeBaseId.value, { ...documentForm })
    modal.value = ''
    await Promise.all([loadDocuments(), loadKnowledgeBases(activeKnowledgeBaseId.value)])
    showNotice('文档已完成分段和向量化')
  } catch (exception) {
    showError(exception)
  }
}

const uploadFile = async (event) => {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  loading.value = true
  try {
    await ragApi.uploadDocument(activeKnowledgeBaseId.value, file)
    await Promise.all([loadDocuments(), loadKnowledgeBases(activeKnowledgeBaseId.value)])
    showNotice(`${file.name} 已完成分段和向量化`)
  } catch (exception) {
    showError(exception)
  } finally {
    loading.value = false
  }
}

const inspectDocument = async (document) => {
  activeDocumentId.value = document.id
  try {
    chunks.value = await ragApi.listChunks(activeKnowledgeBaseId.value, document.id) || []
    modal.value = 'chunks'
  } catch (exception) {
    showError(exception)
  }
}

const removeDocument = async (document) => {
  if (!window.confirm(`确定删除“${document.name}”吗？`)) return
  try {
    await ragApi.deleteDocument(activeKnowledgeBaseId.value, document.id)
    await Promise.all([loadDocuments(), loadKnowledgeBases(activeKnowledgeBaseId.value)])
    showNotice('文档已删除')
  } catch (exception) {
    showError(exception)
  }
}

const runSearch = async () => {
  if (!searchForm.query.trim()) {
    error.value = '请输入要检索的内容'
    return
  }
  loading.value = true
  try {
    hits.value = await ragApi.search(activeKnowledgeBaseId.value, { ...searchForm }) || []
    error.value = ''
  } catch (exception) {
    showError(exception)
  } finally {
    loading.value = false
  }
}

const changeTab = async (tab) => {
  activeTab.value = tab
  if (tab === 'search') {
    await nextTick()
    queryInput.value?.focus()
  }
}

onMounted(loadKnowledgeBases)
</script>

<template>
  <div class="rag-page">
    <aside class="knowledge-panel">
      <div class="panel-heading">
        <div>
          <span class="kicker">KNOWLEDGE</span>
          <h1>RAG</h1>
        </div>
        <button class="icon-button primary-icon" type="button" title="新建知识库" aria-label="新建知识库" @click="openKnowledgeModal()">
          <Plus :size="18" />
        </button>
      </div>

      <div class="knowledge-search">
        <Search :size="15" />
        <span>知识库</span>
        <span class="knowledge-count">{{ knowledgeBases.length }}</span>
      </div>

      <div class="knowledge-list">
        <button
          v-for="item in knowledgeBases"
          :key="item.knowledgeBase.id"
          class="knowledge-item"
          :class="{ active: item.knowledgeBase.id === activeKnowledgeBaseId }"
          type="button"
          @click="selectKnowledgeBase(item.knowledgeBase.id)"
        >
          <span class="knowledge-mark"><Database :size="17" /></span>
          <span class="knowledge-copy">
            <strong>{{ item.knowledgeBase.name }}</strong>
            <small>{{ item.documentCount }} 文档 · {{ item.chunkCount }} 分段</small>
          </span>
          <ChevronRight :size="15" class="chevron" />
        </button>

        <div v-if="!knowledgeBases.length && !loading" class="knowledge-empty">
          <FolderOpen :size="28" />
          <span>暂无知识库</span>
          <button type="button" @click="openKnowledgeModal()">立即创建</button>
        </div>
      </div>

      <div class="panel-note">
        <span class="ready-dot"></span>
        PostgreSQL · pgvector
      </div>
    </aside>

    <main class="rag-workspace">
      <div v-if="notice" class="toast success"><Check :size="16" />{{ notice }}</div>
      <div v-if="error" class="toast error"><X :size="16" />{{ error }}<button type="button" aria-label="关闭错误" @click="error = ''"><X :size="14" /></button></div>

      <template v-if="activeKnowledgeBase">
        <header class="workspace-header">
          <div class="title-row">
            <span class="title-icon"><BookOpen :size="22" /></span>
            <div>
              <h2>{{ activeKnowledgeBase.name }}</h2>
              <p>{{ activeKnowledgeBase.description || '用于组织、分段和检索项目知识。' }}</p>
            </div>
          </div>
          <div class="header-actions">
            <button class="button secondary" type="button" @click="changeTab('search')"><FlaskConical :size="16" />命中测试</button>
            <button class="icon-button" type="button" title="知识库设置" aria-label="知识库设置" @click="openKnowledgeModal(true)"><Settings :size="18" /></button>
            <button class="icon-button danger" type="button" title="删除知识库" aria-label="删除知识库" @click="removeKnowledgeBase"><Trash2 :size="17" /></button>
          </div>
        </header>

        <div class="metric-strip">
          <div><span>文档</span><strong>{{ formatNumber(activeSummary.documentCount) }}</strong></div>
          <div><span>分段</span><strong>{{ formatNumber(activeSummary.chunkCount) }}</strong></div>
          <div><span>检索模式</span><strong>{{ activeKnowledgeBase.retrievalMode === 'blend' ? '混合检索' : activeKnowledgeBase.retrievalMode === 'embedding' ? '向量检索' : '关键词检索' }}</strong></div>
          <div><span>Top K</span><strong>{{ activeKnowledgeBase.topK }}</strong></div>
          <div><span>相似度阈值</span><strong>{{ scorePercent(activeKnowledgeBase.similarityThreshold) }}</strong></div>
        </div>

        <nav class="content-tabs" aria-label="RAG 工作区">
          <button type="button" :class="{ active: activeTab === 'documents' }" @click="changeTab('documents')"><FileText :size="16" />文档</button>
          <button type="button" :class="{ active: activeTab === 'search' }" @click="changeTab('search')"><FlaskConical :size="16" />命中测试</button>
          <button type="button" :class="{ active: activeTab === 'settings' }" @click="changeTab('settings')"><SlidersHorizontal :size="16" />分段与检索</button>
        </nav>

        <section v-if="activeTab === 'documents'" class="content-section">
          <div class="section-toolbar">
            <div>
              <h3>知识文档</h3>
              <p>导入 UTF-8 文本，系统将自动分段并写入 pgvector。</p>
            </div>
            <div class="toolbar-actions">
              <input ref="fileInput" class="visually-hidden" type="file" accept=".txt,.md,.csv,.json,.html,text/*" @change="uploadFile" />
              <button class="button secondary" type="button" @click="fileInput?.click()"><Upload :size="16" />上传文件</button>
              <button class="button primary" type="button" @click="openDocumentModal"><Plus :size="16" />新建文档</button>
            </div>
          </div>

          <div v-if="documents.length" class="document-table">
            <div class="table-row table-head"><span>文档名称</span><span>字符数</span><span>分段数</span><span>状态</span><span>更新时间</span><span></span></div>
            <div v-for="document in documents" :key="document.id" class="table-row" @click="inspectDocument(document)">
              <div class="document-name"><span class="file-mark"><FileText :size="17" /></span><span><strong>{{ document.name }}</strong><small>纯文本知识文档</small></span></div>
              <span>{{ formatNumber(document.characterCount) }}</span>
              <span>{{ document.chunkCount }}</span>
              <span><span class="status-badge"><span></span>可检索</span></span>
              <span>{{ formatDate(document.updatedAt) }}</span>
              <div class="row-actions">
                <button class="icon-button small" type="button" title="查看分段" aria-label="查看分段" @click.stop="inspectDocument(document)"><MoreHorizontal :size="17" /></button>
                <button class="icon-button small danger" type="button" title="删除文档" aria-label="删除文档" @click.stop="removeDocument(document)"><Trash2 :size="15" /></button>
              </div>
            </div>
          </div>

          <div v-else class="empty-state">
            <span class="empty-illustration"><FileText :size="32" /></span>
            <strong>还没有知识文档</strong>
            <p>上传文本文件或直接粘贴内容，开始构建可检索的知识库。</p>
            <button class="button primary" type="button" @click="openDocumentModal"><Plus :size="16" />新建第一个文档</button>
          </div>
        </section>

        <section v-else-if="activeTab === 'search'" class="content-section search-section">
          <div class="section-toolbar">
            <div>
              <h3>命中测试</h3>
              <p>直接检查召回分段与得分，不调用任何 LLM。</p>
            </div>
          </div>
          <form class="search-console" @submit.prevent="runSearch">
            <div class="query-box">
              <Search :size="19" />
              <input ref="queryInput" v-model="searchForm.query" placeholder="输入要检索的问题或关键词" />
              <select v-model="searchForm.mode" aria-label="检索模式">
                <option value="blend">混合检索</option>
                <option value="embedding">向量检索</option>
                <option value="keywords">关键词检索</option>
              </select>
              <button class="button primary" type="submit" :disabled="loading">检索</button>
            </div>
          </form>

          <div v-if="hits.length" class="hit-list">
            <article v-for="(hit, index) in hits" :key="hit.chunkId" class="hit-row">
              <div class="hit-rank">{{ String(index + 1).padStart(2, '0') }}</div>
              <div class="hit-content">
                <div class="hit-header"><strong>{{ hit.documentName }}</strong><span>{{ hit.title }}</span></div>
                <p>{{ hit.content }}</p>
                <div class="score-line"><span>向量 {{ scorePercent(hit.vectorScore) }}</span><span>关键词 {{ scorePercent(hit.keywordScore) }}</span></div>
              </div>
              <div class="score-ring"><strong>{{ scorePercent(hit.score) }}</strong><span>综合得分</span></div>
            </article>
          </div>
          <div v-else class="search-placeholder">
            <FlaskConical :size="30" />
            <strong>{{ searchForm.query ? '暂无命中结果' : '等待检索' }}</strong>
            <span>{{ searchForm.query ? '可以降低相似度阈值或切换检索模式。' : '输入问题后，这里将展示实际召回的知识分段。' }}</span>
          </div>
        </section>

        <section v-else class="content-section settings-section">
          <div class="section-toolbar">
            <div>
              <h3>分段与检索</h3>
              <p>当前设置只影响 RAG 数据处理和召回，不接入对话模型。</p>
            </div>
            <button class="button primary" type="button" @click="openKnowledgeModal(true)"><Settings :size="16" />修改设置</button>
          </div>
          <dl class="settings-list">
            <div><dt>分段长度</dt><dd>{{ activeKnowledgeBase.segmentSize }} 字符</dd><p>单个向量分段的目标最大长度。</p></div>
            <div><dt>重叠长度</dt><dd>{{ activeKnowledgeBase.overlapSize }} 字符</dd><p>相邻分段保留的上下文长度。</p></div>
            <div><dt>检索模式</dt><dd>{{ activeKnowledgeBase.retrievalMode }}</dd><p>支持 embedding、keywords 与 blend。</p></div>
            <div><dt>召回数量</dt><dd>Top {{ activeKnowledgeBase.topK }}</dd><p>命中测试最多返回的分段数量。</p></div>
            <div><dt>相似度阈值</dt><dd>{{ scorePercent(activeKnowledgeBase.similarityThreshold) }}</dd><p>低于阈值的分段不会进入结果集。</p></div>
            <div><dt>向量存储</dt><dd>vector(1024)</dd><p>PostgreSQL 14 pgvector 余弦距离检索。</p></div>
          </dl>
        </section>
      </template>

      <section v-else class="welcome-state">
        <span class="welcome-icon"><Database :size="34" /></span>
        <h2>创建第一个 RAG 知识库</h2>
        <p>管理文档、分段、向量化和召回测试。该模块独立运行，不会接入 LLM 对话链路。</p>
        <button class="button primary" type="button" @click="openKnowledgeModal()"><Plus :size="17" />新建知识库</button>
      </section>
    </main>

    <div v-if="modal" class="modal-backdrop" @click.self="modal = ''">
      <section v-if="modal === 'knowledge' || modal === 'editKnowledge'" class="modal knowledge-modal" role="dialog" aria-modal="true">
        <header><div><span class="kicker">RAG CONFIGURATION</span><h2>{{ modal === 'editKnowledge' ? '编辑知识库' : '新建知识库' }}</h2></div><button class="icon-button" type="button" aria-label="关闭" @click="modal = ''"><X :size="19" /></button></header>
        <form @submit.prevent="saveKnowledgeBase">
          <div class="form-grid">
            <label class="full">知识库名称<input v-model="knowledgeForm.name" maxlength="120" placeholder="例如：产品使用手册" /></label>
            <label class="full">描述<textarea v-model="knowledgeForm.description" maxlength="500" rows="3" placeholder="说明该知识库包含的内容"></textarea></label>
            <label>检索模式<select v-model="knowledgeForm.retrievalMode"><option value="blend">混合检索</option><option value="embedding">向量检索</option><option value="keywords">关键词检索</option></select></label>
            <label>返回数量<input v-model.number="knowledgeForm.topK" type="number" min="1" max="20" /></label>
            <label>分段长度<input v-model.number="knowledgeForm.segmentSize" type="number" min="100" max="4000" /></label>
            <label>重叠长度<input v-model.number="knowledgeForm.overlapSize" type="number" min="0" :max="knowledgeForm.segmentSize - 1" /></label>
            <label class="full range-label"><span>相似度阈值 <strong>{{ scorePercent(knowledgeForm.similarityThreshold) }}</strong></span><input v-model.number="knowledgeForm.similarityThreshold" type="range" min="0" max="1" step="0.01" /></label>
          </div>
          <footer><button class="button secondary" type="button" @click="modal = ''">取消</button><button class="button primary" type="submit">保存知识库</button></footer>
        </form>
      </section>

      <section v-else-if="modal === 'document'" class="modal document-modal" role="dialog" aria-modal="true">
        <header><div><span class="kicker">NEW DOCUMENT</span><h2>新建知识文档</h2></div><button class="icon-button" type="button" aria-label="关闭" @click="modal = ''"><X :size="19" /></button></header>
        <form @submit.prevent="saveDocument">
          <label>文档名称<input v-model="documentForm.name" maxlength="255" placeholder="例如：部署指南.md" /></label>
          <label>文档内容<textarea v-model="documentForm.content" rows="15" placeholder="粘贴需要进入知识库的文本内容"></textarea></label>
          <div class="document-hint"><SlidersHorizontal :size="15" />保存后按 {{ activeKnowledgeBase.segmentSize }} 字符分段，重叠 {{ activeKnowledgeBase.overlapSize }} 字符。</div>
          <footer><button class="button secondary" type="button" @click="modal = ''">取消</button><button class="button primary" type="submit">保存并向量化</button></footer>
        </form>
      </section>

      <section v-else-if="modal === 'chunks'" class="modal chunks-modal" role="dialog" aria-modal="true">
        <header><div><span class="kicker">DOCUMENT CHUNKS</span><h2>{{ activeDocument?.name }}</h2><p>{{ chunks.length }} 个分段 · 已写入 pgvector</p></div><button class="icon-button" type="button" aria-label="关闭" @click="modal = ''"><X :size="19" /></button></header>
        <div class="chunk-list">
          <article v-for="chunk in chunks" :key="chunk.id" class="chunk-row"><span class="chunk-index">{{ chunk.position }}</span><div><strong>{{ chunk.title }}</strong><p>{{ chunk.content }}</p><small>{{ chunk.tokenCount }} tokens · 可检索</small></div></article>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.rag-page { display: flex; height: 100%; min-width: 0; overflow: hidden; color: #202124; background: #f7f8fa; }
button, input, textarea, select { font: inherit; letter-spacing: 0; }
button { cursor: pointer; }
.knowledge-panel { width: 272px; min-width: 272px; display: flex; flex-direction: column; border-right: 1px solid #e4e7ec; background: #fff; }
.panel-heading { display: flex; align-items: center; justify-content: space-between; padding: 30px 22px 18px; }
.kicker { display: block; color: #9aa0a6; font-size: 10px; font-weight: 700; line-height: 1; letter-spacing: .12em; }
.panel-heading h1 { margin-top: 7px; font-size: 25px; line-height: 1; }
.icon-button { display: inline-grid; place-items: center; width: 36px; height: 36px; flex: 0 0 36px; border: 1px solid #e1e4e8; border-radius: 7px; color: #5f6368; background: #fff; transition: color .18s, border-color .18s, background .18s; }
.icon-button:hover { color: #1769e0; border-color: #b7cdf1; background: #f5f9ff; }
.icon-button.primary-icon { color: #fff; border-color: #1769e0; background: #1769e0; }
.icon-button.danger:hover { color: #c5221f; border-color: #f0b8b7; background: #fff6f6; }
.icon-button.small { width: 30px; height: 30px; flex-basis: 30px; border-color: transparent; background: transparent; }
.knowledge-search { display: flex; align-items: center; gap: 8px; margin: 0 14px 10px; padding: 10px 12px; border-radius: 6px; color: #5f6368; background: #f3f5f8; font-size: 12px; font-weight: 600; }
.knowledge-count { margin-left: auto; color: #8a9099; font-size: 11px; }
.knowledge-list { min-height: 0; flex: 1; overflow-y: auto; padding: 0 9px; }
.knowledge-item { width: 100%; display: flex; align-items: center; gap: 10px; padding: 10px; border: 0; border-radius: 6px; color: #3c4043; background: transparent; text-align: left; transition: background .18s; }
.knowledge-item:hover { background: #f5f7fa; }
.knowledge-item.active { color: #1558b0; background: #edf4ff; }
.knowledge-mark { display: grid; place-items: center; width: 34px; height: 34px; flex: 0 0 34px; border: 1px solid #e0e5eb; border-radius: 6px; color: #64748b; background: #fff; }
.knowledge-item.active .knowledge-mark { color: #1769e0; border-color: #bfd3f3; }
.knowledge-copy { min-width: 0; flex: 1; }
.knowledge-copy strong, .knowledge-copy small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.knowledge-copy strong { margin-bottom: 4px; font-size: 13px; font-weight: 650; }
.knowledge-copy small { color: #8a9099; font-size: 10px; }
.chevron { flex: 0 0 auto; opacity: 0; }
.knowledge-item.active .chevron { opacity: 1; }
.knowledge-empty { display: flex; flex-direction: column; align-items: center; gap: 9px; padding: 54px 16px; color: #a0a6ae; font-size: 12px; text-align: center; }
.knowledge-empty button { border: 0; color: #1769e0; background: transparent; font-size: 12px; }
.panel-note { display: flex; align-items: center; gap: 8px; padding: 17px 22px; border-top: 1px solid #edf0f3; color: #8a9099; font-family: 'SF Mono', Consolas, monospace; font-size: 10px; }
.ready-dot { width: 7px; height: 7px; border-radius: 50%; background: #20a464; }
.rag-workspace { position: relative; min-width: 0; flex: 1; overflow-y: auto; }
.workspace-header { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 28px 34px 24px; border-bottom: 1px solid #e6e8ec; background: #fff; }
.title-row { min-width: 0; display: flex; align-items: center; gap: 14px; }
.title-icon { display: grid; place-items: center; width: 44px; height: 44px; flex: 0 0 44px; border: 1px solid #c8d8f1; border-radius: 7px; color: #1769e0; background: #f2f7ff; }
.title-row h2 { overflow: hidden; color: #202124; font-size: 21px; line-height: 1.3; text-overflow: ellipsis; white-space: nowrap; }
.title-row p { max-width: 660px; margin-top: 4px; overflow: hidden; color: #7b818a; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.header-actions, .toolbar-actions, .row-actions { display: flex; align-items: center; gap: 8px; flex: 0 0 auto; }
.button { display: inline-flex; align-items: center; justify-content: center; gap: 7px; min-height: 36px; padding: 0 14px; border: 1px solid transparent; border-radius: 7px; font-size: 12px; font-weight: 650; transition: background .18s, border-color .18s, color .18s; }
.button.primary { color: #fff; border-color: #1769e0; background: #1769e0; }
.button.primary:hover { background: #0d57c4; }
.button.secondary { color: #4d535b; border-color: #dce0e5; background: #fff; }
.button.secondary:hover { border-color: #b9c4d1; background: #f8fafc; }
.button:disabled { opacity: .55; cursor: wait; }
.metric-strip { display: grid; grid-template-columns: repeat(5, minmax(100px, 1fr)); border-bottom: 1px solid #e6e8ec; background: #fff; }
.metric-strip div { min-width: 0; padding: 17px 24px; border-right: 1px solid #edf0f3; }
.metric-strip div:last-child { border-right: 0; }
.metric-strip span, .metric-strip strong { display: block; }
.metric-strip span { margin-bottom: 7px; color: #8a9099; font-size: 10px; }
.metric-strip strong { overflow: hidden; color: #34383d; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.content-tabs { display: flex; gap: 26px; padding: 0 34px; border-bottom: 1px solid #e2e5e9; background: #fff; }
.content-tabs button { position: relative; display: flex; align-items: center; gap: 7px; height: 49px; border: 0; color: #747b84; background: transparent; font-size: 12px; font-weight: 600; }
.content-tabs button.active { color: #1769e0; }
.content-tabs button.active::after { content: ''; position: absolute; right: 0; bottom: -1px; left: 0; height: 2px; background: #1769e0; }
.content-section { min-height: 420px; padding: 28px 34px 48px; }
.section-toolbar { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 22px; }
.section-toolbar h3 { margin-bottom: 5px; color: #2f3338; font-size: 16px; }
.section-toolbar p { color: #858b94; font-size: 12px; line-height: 1.5; }
.document-table { border-top: 1px solid #dfe3e8; background: #fff; }
.table-row { display: grid; grid-template-columns: minmax(260px, 2fr) minmax(70px, .5fr) minmax(70px, .5fr) minmax(90px, .65fr) minmax(130px, .8fr) 72px; align-items: center; min-height: 68px; padding: 0 14px; border-right: 1px solid #e5e8ec; border-bottom: 1px solid #e5e8ec; border-left: 1px solid #e5e8ec; color: #60666f; font-size: 12px; cursor: pointer; }
.table-row:not(.table-head):hover { background: #fafcff; }
.table-head { min-height: 38px; color: #8a9099; background: #f5f7f9; font-size: 10px; font-weight: 650; cursor: default; }
.document-name { min-width: 0; display: flex; align-items: center; gap: 11px; }
.file-mark { display: grid; place-items: center; width: 34px; height: 34px; flex: 0 0 34px; border-radius: 6px; color: #1769e0; background: #edf4ff; }
.document-name > span:last-child { min-width: 0; }
.document-name strong, .document-name small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.document-name strong { color: #373b40; font-size: 12px; }
.document-name small { margin-top: 4px; color: #9aa0a6; font-size: 10px; }
.status-badge { display: inline-flex; align-items: center; gap: 6px; color: #188656; font-size: 11px; font-weight: 600; }
.status-badge > span { width: 6px; height: 6px; border-radius: 50%; background: #20a464; }
.empty-state, .search-placeholder, .welcome-state { display: flex; flex-direction: column; align-items: center; justify-content: center; color: #868d96; text-align: center; }
.empty-state { min-height: 360px; border: 1px dashed #d9dde3; background: #fff; }
.empty-illustration, .welcome-icon { display: grid; place-items: center; width: 62px; height: 62px; margin-bottom: 17px; border: 1px solid #d9e4f6; border-radius: 8px; color: #1769e0; background: #f2f7ff; }
.empty-state strong, .search-placeholder strong { color: #454a51; font-size: 14px; }
.empty-state p { margin: 7px 0 18px; font-size: 11px; }
.search-console { margin-bottom: 24px; }
.query-box { display: grid; grid-template-columns: auto minmax(0, 1fr) 130px auto; align-items: center; gap: 10px; padding: 9px 10px 9px 14px; border: 1px solid #ccd4de; border-radius: 7px; background: #fff; box-shadow: 0 3px 10px rgba(32, 48, 67, .04); }
.query-box > svg { color: #7e8792; }
.query-box input, .query-box select { height: 36px; border: 0; outline: 0; color: #34383d; background: transparent; }
.query-box input { min-width: 0; font-size: 13px; }
.query-box select { padding: 0 8px; border-left: 1px solid #e3e6ea; color: #626a74; font-size: 11px; }
.hit-list { display: flex; flex-direction: column; gap: 10px; }
.hit-row { display: grid; grid-template-columns: 42px minmax(0, 1fr) 90px; gap: 14px; padding: 18px; border: 1px solid #e0e4e9; border-radius: 7px; background: #fff; }
.hit-rank { color: #a1a7ae; font-family: 'SF Mono', Consolas, monospace; font-size: 11px; }
.hit-content { min-width: 0; }
.hit-header { display: flex; align-items: center; gap: 10px; margin-bottom: 9px; }
.hit-header strong { color: #34383d; font-size: 12px; }
.hit-header span { color: #949aa2; font-size: 10px; }
.hit-content p { display: -webkit-box; overflow: hidden; color: #5f6670; font-size: 12px; line-height: 1.7; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.score-line { display: flex; gap: 14px; margin-top: 12px; color: #9298a0; font-size: 9px; }
.score-ring { display: flex; flex-direction: column; align-items: flex-end; justify-content: center; }
.score-ring strong { color: #1769e0; font-size: 19px; }
.score-ring span { margin-top: 4px; color: #9ba1a9; font-size: 9px; }
.search-placeholder { min-height: 280px; gap: 8px; border-top: 1px solid #e5e8ec; }
.search-placeholder > svg { margin-bottom: 7px; color: #aab3bd; }
.search-placeholder span { font-size: 11px; }
.settings-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); border-top: 1px solid #e1e5ea; border-left: 1px solid #e1e5ea; background: #fff; }
.settings-list > div { position: relative; min-height: 112px; padding: 20px; border-right: 1px solid #e1e5ea; border-bottom: 1px solid #e1e5ea; }
.settings-list dt { margin-bottom: 13px; color: #858b94; font-size: 10px; }
.settings-list dd { position: absolute; top: 18px; right: 20px; color: #1769e0; font-size: 12px; font-weight: 700; }
.settings-list p { max-width: 70%; color: #4f5660; font-size: 12px; line-height: 1.6; }
.welcome-state { height: 100%; padding: 30px; }
.welcome-state h2 { color: #33383e; font-size: 22px; }
.welcome-state p { max-width: 490px; margin: 10px 0 22px; font-size: 12px; line-height: 1.7; }
.toast { position: fixed; top: 20px; right: 24px; z-index: 40; display: flex; align-items: center; gap: 8px; max-width: 420px; padding: 11px 13px; border: 1px solid; border-radius: 7px; background: #fff; box-shadow: 0 10px 28px rgba(25, 33, 44, .13); font-size: 12px; }
.toast.success { color: #16724a; border-color: #b9e3cf; }
.toast.error { color: #a6221f; border-color: #f0c1c0; }
.toast button { display: grid; place-items: center; margin-left: 8px; padding: 0; border: 0; color: inherit; background: transparent; }
.modal-backdrop { position: fixed; inset: 0; z-index: 30; display: grid; place-items: center; padding: 24px; background: rgba(28, 34, 42, .34); backdrop-filter: blur(4px); }
.modal { width: min(620px, 100%); max-height: calc(100vh - 48px); overflow: auto; border: 1px solid rgba(255,255,255,.8); border-radius: 8px; background: #fff; box-shadow: 0 24px 70px rgba(19, 27, 38, .24); }
.modal > header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; padding: 24px 26px 19px; border-bottom: 1px solid #e7eaee; }
.modal h2 { margin-top: 7px; color: #30343a; font-size: 20px; }
.modal header p { margin-top: 6px; color: #8a9099; font-size: 11px; }
.modal form { padding: 22px 26px 0; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 17px 14px; }
.form-grid .full { grid-column: 1 / -1; }
.modal label { display: flex; flex-direction: column; gap: 8px; color: #555c65; font-size: 11px; font-weight: 650; }
.modal input:not([type="range"]), .modal textarea, .modal select { width: 100%; padding: 10px 11px; border: 1px solid #d8dde3; border-radius: 6px; outline: none; color: #30343a; background: #fff; font-size: 12px; font-weight: 400; resize: vertical; }
.modal input:focus, .modal textarea:focus, .modal select:focus { border-color: #6da0e7; box-shadow: 0 0 0 3px rgba(23, 105, 224, .1); }
.range-label span { display: flex; justify-content: space-between; }
.range-label strong { color: #1769e0; }
.range-label input { accent-color: #1769e0; }
.modal footer { display: flex; justify-content: flex-end; gap: 9px; margin-top: 24px; padding: 17px 0 20px; border-top: 1px solid #e7eaee; }
.document-modal label + label { margin-top: 17px; }
.document-hint { display: flex; align-items: center; gap: 7px; margin-top: 12px; color: #89919a; font-size: 10px; }
.chunks-modal { width: min(820px, 100%); }
.chunk-list { max-height: 620px; overflow-y: auto; padding: 0 26px 24px; }
.chunk-row { display: grid; grid-template-columns: 34px minmax(0, 1fr); gap: 13px; padding: 18px 0; border-bottom: 1px solid #e8ebef; }
.chunk-index { display: grid; place-items: center; width: 28px; height: 28px; border-radius: 5px; color: #1769e0; background: #edf4ff; font-family: 'SF Mono', Consolas, monospace; font-size: 10px; }
.chunk-row strong { color: #3a3f45; font-size: 12px; }
.chunk-row p { margin: 8px 0; color: #626a74; font-size: 12px; line-height: 1.75; white-space: pre-wrap; }
.chunk-row small { color: #959ca5; font-size: 9px; }
.visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; }
@media (max-width: 1050px) { .knowledge-panel { width: 230px; min-width: 230px; } .metric-strip { grid-template-columns: repeat(3, 1fr); } .metric-strip div:nth-child(3) { border-right: 0; } .metric-strip div:nth-child(n+4) { border-top: 1px solid #edf0f3; } .table-row { grid-template-columns: minmax(220px, 2fr) 70px 70px 90px 72px; } .table-row > :nth-child(5) { display: none; } }
@media (max-width: 800px) { .knowledge-panel { width: 190px; min-width: 190px; } .workspace-header, .section-toolbar { align-items: flex-start; flex-direction: column; } .workspace-header, .content-section { padding-right: 20px; padding-left: 20px; } .content-tabs { padding: 0 20px; } .header-actions, .toolbar-actions { width: 100%; flex-wrap: wrap; } .metric-strip { grid-template-columns: repeat(2, 1fr); } .metric-strip div:nth-child(2n) { border-right: 0; } .metric-strip div:nth-child(3) { border-right: 1px solid #edf0f3; } .table-head { display: none; } .table-row { grid-template-columns: minmax(0, 1fr) auto; gap: 6px; padding: 12px; } .table-row > span { display: none; } .settings-list { grid-template-columns: 1fr; } .query-box { grid-template-columns: auto minmax(0, 1fr) auto; } .query-box select { grid-column: 2 / 3; border-left: 0; } }
</style>
