<template>
  <div class="markdown-content" v-html="renderedContent"></div>
</template>

<script setup>
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'

const props = defineProps({
  content: {
    type: String,
    default: ''
  }
})

// 创建 markdown-it 实例
const md = new MarkdownIt({
  html: false, // 禁用 HTML 标签，防止 XSS 攻击
  linkify: true, // 自动转换 URL 为链接
  breaks: true, // 转换换行符为 <br>
  highlight: (str, lang) => {
    // 代码高亮
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs"><code>${hljs.highlight(str, { language: lang, ignoreIllegals: true }).value}</code></pre>`
      } catch (err) {
        console.error('代码高亮失败:', err)
      }
    }
    // 如果没有指定语言或语言不支持，返回普通的 pre/code 标签
    return `<pre class="hljs"><code>${md.utils.escapeHtml(str)}</code></pre>`
  }
})

// 渲染 Markdown 内容
const renderedContent = computed(() => {
  if (!props.content) return ''
  return md.render(props.content)
})
</script>

<style scoped>
.markdown-content {
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-primary, #242424);
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  margin-top: 24px;
  margin-bottom: 16px;
  font-weight: 600;
  line-height: 1.25;
}

.markdown-content :deep(h1) {
  font-size: 2em;
  border-bottom: 1px solid var(--color-neutral-stroke1, #e0e0e0);
  padding-bottom: 0.3em;
}

.markdown-content :deep(h2) {
  font-size: 1.5em;
  border-bottom: 1px solid var(--color-neutral-stroke1, #e0e0e0);
  padding-bottom: 0.3em;
}

.markdown-content :deep(h3) {
  font-size: 1.25em;
}

.markdown-content :deep(p) {
  margin-bottom: 16px;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin-bottom: 16px;
  padding-left: 2em;
}

.markdown-content :deep(ul) {
  list-style-type: disc;
}

.markdown-content :deep(ol) {
  list-style-type: decimal;
}

.markdown-content :deep(li) {
  margin-bottom: 0.25em;
}

.markdown-content :deep(code) {
  padding: 0.2em 0.4em;
  margin: 0;
  font-size: 85%;
  background-color: rgba(175, 184, 193, 0.2);
  border-radius: 6px;
}

.markdown-content :deep(pre) {
  padding: 16px;
  overflow: auto;
  font-size: 85%;
  line-height: 1.45;
  background-color: #f6f8fa;
  border-radius: 6px;
  margin-bottom: 16px;
}

.markdown-content :deep(pre code) {
  padding: 0;
  margin: 0;
  font-size: 100%;
  background-color: transparent;
  border-radius: 0;
}

.markdown-content :deep(blockquote) {
  padding: 0 1em;
  color: var(--color-text-secondary, #57606a);
  border-left: 0.25em solid var(--color-neutral-stroke1, #d0d7de);
  margin: 0 0 16px 0;
}

.markdown-content :deep(a) {
  color: var(--color-brand-primary, #0969da);
  text-decoration: none;
}

.markdown-content :deep(a:hover) {
  text-decoration: underline;
}

.markdown-content :deep(table) {
  border-spacing: 0;
  border-collapse: collapse;
  margin-bottom: 16px;
  width: 100%;
  overflow: auto;
}

.markdown-content :deep(table th),
.markdown-content :deep(table td) {
  padding: 6px 13px;
  border: 1px solid var(--color-neutral-stroke1, #d0d7de);
}

.markdown-content :deep(table th) {
  font-weight: 600;
  background-color: #f6f8fa;
}

.markdown-content :deep(table tr) {
  background-color: #ffffff;
  border-top: 1px solid var(--color-neutral-stroke1, #dcbdd0c7);
}

.markdown-content :deep(table tr:nth-child(2n)) {
  background-color: #f6f8fa;
}

.markdown-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 6px;
}

.markdown-content :deep(hr) {
  height: 0.25em;
  padding: 0;
  margin: 24px 0;
  background-color: var(--color-neutral-stroke1, #e0e0e0);
  border: 0;
}

/* 代码高亮样式 */
.markdown-content :deep(.hljs) {
  background: #f6f8fa;
  color: #24292f;
}

.markdown-content :deep(.hljs-keyword),
.markdown-content :deep(.hljs-selector-tag),
.markdown-content :deep(.hljs-title),
.markdown-content :deep(.hljs-section),
.markdown-content :deep(.hljs-doctag),
.markdown-content :deep(.hljs-name),
.markdown-content :deep(.hljs-strong) {
  font-weight: bold;
  color: #d73a49;
}

.markdown-content :deep(.hljs-string),
.markdown-content :deep(.hljs-attr) {
  color: #032f62;
}

.markdown-content :deep(.hljs-comment),
.markdown-content :deep(.hljs-quote) {
  color: #6a737d;
  font-style: italic;
}

.markdown-content :deep(.hljs-variable),
.markdown-content :deep(.hljs-template-variable),
.markdown-content :deep(.hljs-tag),
.markdown-content :deep(.hljs-number),
.markdown-content :deep(.hljs-literal),
.markdown-content :deep(.hljs-type),
.markdown-content :deep(.hljs-params),
.markdown-content :deep(.hljs-link) {
  color: #005cc5;
}

.markdown-content :deep(.hljs-function) {
  color: #6f42c1;
}

.markdown-content :deep(.hljs-built_in) {
  color: #e36209;
}
</style>