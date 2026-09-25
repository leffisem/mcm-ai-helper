<template>
  <div :class="['message-wrapper', role]">
    <div class="message-avatar" v-if="role === 'assistant'">
      <div class="avatar-icon">AI</div>
    </div>
    <div class="message-content-wrapper">
      <div :class="['message-bubble', role]">
        <div ref="textRef" class="message-text" v-html="renderedContent"></div>
        <div v-if="streaming" class="cursor-blink">▊</div>
      </div>
      <!-- 单条消息复制按钮（流式输出中隐藏） -->
      <div v-if="content && !streaming" class="message-actions">
        <button :class="['copy-btn', { copied }]" @click="handleCopy">
          <svg v-if="!copied" viewBox="0 0 24 24" fill="none" width="14" height="14">
            <path d="M16 1H4C2.9 1 2 1.9 2 3v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z" fill="currentColor"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="none" width="14" height="14">
            <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41L9 16.17z" fill="currentColor"/>
          </svg>
          <span>{{ copied ? '已复制' : '复制' }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script>
// 模块级配置（仅执行一次）
import { marked } from 'marked'
import hljs from 'highlight.js'

const renderer = new marked.Renderer()

// 代码块高亮
// 注意：marked v5+ 的 renderer 方法接收 token 对象（{ text, lang }），
// 不是旧版的 (code, language) 两参数签名
// 结构：.code-block > .code-header(语言标签 + 复制按钮) + pre > code
renderer.code = ({ text, lang }) => {
  const escapeHtml = (s) =>
    String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  try {
    const code = String(text ?? '')
    const language = lang && hljs.getLanguage(lang) ? lang : ''
    const highlighted = language
      ? hljs.highlight(code, { language }).value
      : hljs.highlightAuto(code).value
    const langClass = language ? ` class="language-${language}"` : ''
    const langLabel = escapeHtml(lang || '代码')
    return `<div class="code-block"><div class="code-header"><span class="code-lang">${langLabel}</span><button class="code-copy-btn" type="button">复制代码</button></div><pre><code${langClass}>${highlighted}</code></pre></div>\n`
  } catch {
    const escaped = escapeHtml(text ?? '')
    return `<div class="code-block"><div class="code-header"><span class="code-lang">代码</span><button class="code-copy-btn" type="button">复制代码</button></div><pre><code>${escaped}</code></pre></div>\n`
  }
}

marked.use({
  renderer,
  gfm: true,
  breaks: true
})
</script>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import katex from 'katex'

const props = defineProps({
  role: {
    type: String,
    default: 'user' // 'user' | 'assistant'
  },
  content: {
    type: String,
    default: ''
  },
  streaming: {
    type: Boolean,
    default: false
  }
})

// === 单条消息复制 ===
const copied = ref(false)
let copyTimer = null

function handleCopy() {
  const text = props.content
  if (!text) return

  const showCopied = () => {
    copied.value = true
    clearTimeout(copyTimer)
    copyTimer = setTimeout(() => {
      copied.value = false
    }, 2000)
  }

  // 优先使用 Clipboard API（localhost 属于安全上下文）
  if (navigator.clipboard && window.isSecureContext) {
    navigator.clipboard.writeText(text).then(showCopied).catch(() => {
      fallbackCopy(text, showCopied)
    })
  } else {
    fallbackCopy(text, showCopied)
  }
}

function fallbackCopy(text, done) {
  const ta = document.createElement('textarea')
  ta.value = text
  ta.style.position = 'fixed'
  ta.style.opacity = '0'
  document.body.appendChild(ta)
  ta.select()
  document.execCommand('copy')
  document.body.removeChild(ta)
  done()
}

// === 代码块单独复制（事件委托：v-html 内容不受 Vue 管理） ===
const textRef = ref(null)

function handleCodeCopyClick(e) {
  const btn = e.target.closest('.code-copy-btn')
  if (!btn || !textRef.value?.contains(btn)) return

  const block = btn.closest('.code-block')
  const codeEl = block?.querySelector('code')
  if (!codeEl) return

  const showCopied = () => {
    btn.textContent = '已复制'
    btn.classList.add('copied')
    setTimeout(() => {
      btn.textContent = '复制代码'
      btn.classList.remove('copied')
    }, 2000)
  }

  const code = codeEl.textContent
  if (navigator.clipboard && window.isSecureContext) {
    navigator.clipboard.writeText(code).then(showCopied).catch(() => {
      fallbackCopy(code, showCopied)
    })
  } else {
    fallbackCopy(code, showCopied)
  }
}

onMounted(() => {
  textRef.value?.addEventListener('click', handleCodeCopyClick)
})

onBeforeUnmount(() => {
  textRef.value?.removeEventListener('click', handleCodeCopyClick)
})

const renderedContent = computed(() => {
  const content = props.content
  if (!content) return ''

  // 0) 提取围栏代码块 → 占位符
  // 保护代码内容不被后续公式转换/文本预处理误改（代码里可能有 \( 或 $）
  const codeBlocks = []
  let processed = content.replace(/```[\s\S]*?```|```[\s\S]*$/g, (m) => {
    const idx = codeBlocks.length
    codeBlocks.push(m)
    return `\x02CODE_${idx}\x02`
  })

  // 0b) LaTeX 定界符标准化（AI 常输出 LaTeX 风格而非 $ 风格）：
  // \[...\] → $$...$$，\(...\) → $...$
  // 不转换会导致 \( \mu \) 显示为字面 "\mu" 而非 μ
  processed = processed.replace(/\\\[([\s\S]*?)\\\]/g, (_, m) => `$$${m}$$`)
  processed = processed.replace(/\\\(([\s\S]*?)\\\)/g, (_, m) => `$${m}$`)

  // 1) 提取 $$...$$（块级公式），替换为占位符
  const displayMaths = []
  processed = processed.replace(/\$\$([\s\S]*?)\$\$/g, (_, math) => {
    const idx = displayMaths.length
    displayMaths.push(math.trim())
    return `\x02MATH_D_${idx}\x02`
  })

  // 2) 提取 $...$（行内公式），替换为占位符
  const inlineMaths = []
  processed = processed.replace(/\$([^$\n]*?)\$/g, (_, math) => {
    const idx = inlineMaths.length
    inlineMaths.push(math.trim())
    return `\x02MATH_I_${idx}\x02`
  })

  // 3) 修复 AI 返回的非标准 Markdown 格式
  // 处理行首 ### 无空格（如 "###标题" → "### 标题"）
  processed = processed.replace(/^(#{1,6})([^#\s\d\r\n])/gm, '$1 $2')
  // 处理行中间的 ###（如 "内容。###标题" → "内容。\n\n### 标题"）
  // 注意：before 不能是 #，否则会把行首 ### 的第一个 # 误当 before，
  // 导致 "### 蒙特卡洛" 被拆成 "#\n\n## 蒙特卡洛"（空 h1 + h2）
  processed = processed.replace(/([^\n#])(#{2,6}\s*)([^#\s\r\n])/g, (_, before, hash, word) => {
    const spaced = hash.endsWith(' ') ? hash : hash + ' '
    return before + '\n\n' + spaced + word
  })

  // 确保 --- 水平线前后有空行（防止被解析为 setext 标题下划线）
  processed = processed.replace(/([^\n])---/g, '$1\n\n---')
  processed = processed.replace(/---([^\n])/g, '---\n\n$1')

  // 恢复代码块占位符（在 marked 解析前还原，让 renderer.code 处理高亮和复制按钮）
  processed = processed.replace(/\x02CODE_(\d+)\x02/g, (_, idx) => codeBlocks[parseInt(idx)])

  // 4) Markdown 渲染
  let html
  try {
    html = marked.parse(processed)
  } catch {
    // 渲染失败时，直接返回 HTML 转义后的原文
    return content
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/\n/g, '<br>')
  }

  // 5) 恢复占位符 → KaTeX HTML
  html = html.replace(/\x02MATH_D_(\d+)\x02/g, (_, idx) => {
    const math = displayMaths[parseInt(idx)]
    try {
      return katex.renderToString(math, { displayMode: true, throwOnError: true })
    } catch {
      return `$$${math}$$`
    }
  })
  html = html.replace(/\x02MATH_I_(\d+)\x02/g, (_, idx) => {
    const math = inlineMaths[parseInt(idx)]
    try {
      return katex.renderToString(math, { displayMode: false, throwOnError: true })
    } catch {
      return `$${math}$`
    }
  })

  return html
})
</script>

<style scoped>
.message-wrapper {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  padding: 0 8px;
}

.message-wrapper.user {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.avatar-icon {
  width: 34px;
  height: 34px;
  border-radius: 12px;
  background: linear-gradient(135deg, #10a37f 0%, #0ea5e9 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  box-shadow: 0 12px 20px rgba(16, 163, 127, 0.2);
}

.message-content-wrapper {
  max-width: 78%;
}

.message-wrapper.user .message-content-wrapper {
  max-width: 76%;
}

.message-bubble {
  padding: 14px 16px;
  border-radius: 20px;
  line-height: 1.7;
  font-size: 15px;
  word-wrap: break-word;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
}

.message-bubble.user {
  background: linear-gradient(135deg, #e0f2fe 0%, #dbeafe 100%);
  color: #0f172a;
  border-bottom-right-radius: 8px;
  border: 1px solid rgba(147, 197, 253, 0.35);
}

.message-bubble.assistant {
  background: rgba(255, 255, 255, 0.92);
  color: #1f2937;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-bottom-left-radius: 8px;
}

.message-text {
  white-space: normal;
  word-wrap: break-word;
}

/* === 单条消息复制按钮 === */
.message-actions {
  display: flex;
  margin-top: 6px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.message-wrapper:hover .message-actions {
  opacity: 1;
}

.message-wrapper.user .message-actions {
  justify-content: flex-end;
}

.copy-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.75);
  color: #64748b;
  font-size: 12px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s ease;
}

.copy-btn:hover {
  background: rgba(255, 255, 255, 0.96);
  color: #0f172a;
}

.copy-btn.copied {
  color: #10a37f;
  border-color: rgba(16, 163, 127, 0.3);
}

.copy-btn.copied:hover {
  background: rgba(16, 163, 127, 0.08);
}

/* === Markdown 样式 === */
.message-text :deep(h2) {
  font-size: 1.3em;
  font-weight: 700;
  margin: 20px 0 10px;
  padding-bottom: 8px;
  border-bottom: 2px solid rgba(16, 163, 127, 0.3);
  color: #0f172a;
}

.message-text :deep(h3) {
  font-size: 1.15em;
  font-weight: 700;
  margin: 18px 0 8px;
  color: #1e293b;
}

.message-text :deep(ul),
.message-text :deep(ol) {
  padding-left: 22px;
  margin: 8px 0;
}

.message-text :deep(li) {
  margin-bottom: 6px;
}

.message-text :deep(p) {
  margin: 10px 0;
}

.message-text :deep(strong) {
  font-weight: 700;
  color: #0f172a;
}

.message-text :deep(blockquote) {
  border-left: 3px solid #10a37f;
  margin: 12px 0;
  padding: 8px 12px;
  color: #475569;
  background: rgba(16, 163, 127, 0.04);
  border-radius: 0 8px 8px 0;
}

.message-text :deep(table) {
  border-collapse: collapse;
  margin: 10px 0;
  font-size: 14px;
  width: 100%;
}

.message-text :deep(th),
.message-text :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 8px 10px;
  text-align: left;
}

.message-text :deep(th) {
  background: #f8fafc;
  font-weight: 700;
}

.message-text :deep(hr) {
  border: none;
  border-top: 1px solid rgba(148, 163, 184, 0.28);
  margin: 18px 0;
}

/* === 代码块样式 === */
.message-text :deep(.code-block) {
  background: #0f172a;
  border: 1px solid rgba(148,163,184,0.2);
  border-radius: 12px;
  margin: 12px 0;
  overflow: hidden;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.04);
}

.message-text :deep(.code-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: rgba(15, 23, 42, 0.85);
  border-bottom: 1px solid rgba(148,163,184,0.15);
  font-size: 12px;
}

.message-text :deep(.code-lang) {
  color: #cbd5e1;
  font-family: 'SFMono-Regular', Consolas, Menlo, monospace;
}

.message-text :deep(.code-copy-btn) {
  display: inline-flex;
  align-items: center;
  padding: 4px 9px;
  border: 1px solid rgba(148,163,184,0.25);
  border-radius: 6px;
  background: rgba(255,255,255,0.04);
  color: #e2e8f0;
  font-size: 12px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s ease;
}

.message-text :deep(.code-copy-btn:hover) {
  background: rgba(255,255,255,0.08);
  color: #fff;
}

.message-text :deep(.code-copy-btn.copied) {
  color: #86efac;
  border-color: rgba(134, 239, 172, 0.4);
}

.message-text :deep(.code-block pre) {
  background: none;
  border: none;
  border-radius: 0;
  padding: 14px 16px;
  margin: 0;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.6;
}

.message-text :deep(code) {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 0.9em;
}

.message-text :deep(p code),
.message-text :deep(li code) {
  background: rgba(15, 23, 42, 0.06);
  padding: 2px 6px;
  border-radius: 6px;
  color: #d63384;
}

.message-text :deep(pre code) {
  background: none;
  padding: 0;
  border-radius: 0;
  color: inherit;
}

/* === KaTeX 样式 === */
.message-text :deep(.katex-display) {
  margin: 10px 0;
  overflow-x: auto;
  overflow-y: hidden;
}

.message-text :deep(.katex) {
  font-size: 1.05em;
}

/* === 光标闪烁 === */
.cursor-blink {
  display: inline;
  animation: blink 1s step-end infinite;
  color: #2563eb;
  font-size: 15px;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}
</style>