<template>
  <div class="app-container">
    <!-- 顶部导航 -->
    <header class="top-nav">
      <div class="nav-left">
        <span class="nav-logo">🤖</span>
        <span class="nav-title">数学建模 AI 助手</span>
      </div>
      <div class="nav-right">
        <button class="nav-btn" @click="handleCopy">
          <svg viewBox="0 0 24 24" fill="none" width="16" height="16">
            <path d="M16 1H4C2.9 1 2 1.9 2 3v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z" fill="currentColor"/>
          </svg>
          复制
        </button>
        <button class="nav-btn" @click="handleClear">
          <svg viewBox="0 0 24 24" fill="none" width="16" height="16">
            <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" fill="currentColor"/>
          </svg>
          清空
        </button>
      </div>
    </header>

    <!-- 对话区域 -->
    <main class="chat-area" ref="chatAreaRef">
      <div class="chat-container">
        <ChatList ref="chatListRef" :messages="messages" />
      </div>
    </main>

    <!-- 底部输入 -->
    <footer class="bottom-area">
      <ChatInput
        ref="chatInputRef"
        :loading="isLoading"
        @send="handleSend"
        @update:mode="handleModeChange"
      />
    </footer>

    <!-- 清空确认弹窗 -->
    <div v-if="showClearConfirm" class="modal-overlay" @click.self="showClearConfirm = false">
      <div class="modal-dialog">
        <p class="modal-title">确定要清空所有对话吗？</p>
        <div class="modal-actions">
          <button class="modal-btn cancel" @click="showClearConfirm = false">取消</button>
          <button class="modal-btn confirm" @click="confirmClear">确定</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick, watch } from 'vue'
import ChatList from './components/ChatList.vue'
import ChatInput from './components/ChatInput.vue'
import { chatStream, modelingChat } from './api/chat.js'

const messages = ref([])
const isLoading = ref(false)
const showClearConfirm = ref(false)
const currentMode = ref('normal')
const chatAreaRef = ref(null)
const chatListRef = ref(null)
const chatInputRef = ref(null)
let abortSend = null

// 滚动到底部
function scrollToBottom() {
  nextTick(() => {
    const el = chatAreaRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

// 发送消息
function handleSend({ text, mode }) {
  currentMode.value = mode
  const memoryId = mode === 'normal' ? 1 : 10001

  // 添加用户消息
  messages.value.push({
    role: 'user',
    content: text,
    streaming: false
  })

  // 添加 AI 空消息（准备流式填充）
  const aiMsg = reactive({
    role: 'assistant',
    content: '',
    streaming: true
  })
  messages.value.push(aiMsg)
  scrollToBottom()

  isLoading.value = true

  const onData = (chunk) => {
    aiMsg.content += chunk
    scrollToBottom()
  }

  const onError = (err) => {
    aiMsg.content = `请求失败：${err}`
    aiMsg.streaming = false
    isLoading.value = false
  }

  const onDone = () => {
    aiMsg.streaming = false
    isLoading.value = false
  }

  // 根据模式选择接口
  if (mode === 'normal') {
    abortSend = chatStream(text, memoryId, onData, onError, onDone)
  } else {
    abortSend = modelingChat(text, memoryId, onData, onError, onDone)
  }
}

// 模式切换
function handleModeChange(mode) {
  currentMode.value = mode
}

// 清空对话
function handleClear() {
  showClearConfirm.value = true
}

function confirmClear() {
  // 中断正在进行的请求
  if (abortSend) {
    abortSend()
    abortSend = null
  }
  messages.value = []
  isLoading.value = false
  showClearConfirm.value = false
}

// 复制全部对话
function handleCopy() {
  const text = messages.value
    .map((m) => {
      const role = m.role === 'user' ? '用户' : 'AI'
      return `${role}：\n${m.content}`
    })
    .join('\n\n---\n\n')

  if (!text) return

  navigator.clipboard.writeText(text).then(() => {
    alert('对话已复制到剪贴板')
  }).catch(() => {
    // fallback
    const ta = document.createElement('textarea')
    ta.value = text
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    alert('对话已复制到剪贴板')
  })
}
</script>

<style>
/* ==================== 全局重置 ==================== */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body {
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  font-size: 15px;
  line-height: 1.6;
  color: #2d2d2d;
  background: #f7f7f8;
}

#app {
  height: 100%;
}
</style>

<style scoped>
.app-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  max-width: 100vw;
  background:
    radial-gradient(circle at top left, rgba(120, 119, 198, 0.18), transparent 23%),
    radial-gradient(circle at bottom right, rgba(16, 163, 127, 0.12), transparent 22%),
    linear-gradient(180deg, #f5f7ff 0%, #eef3f8 100%);
}

/* ==================== 顶部导航 ==================== */
.top-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(157, 170, 197, 0.25);
  box-shadow: 0 8px 30px rgba(15, 23, 42, 0.04);
  flex-shrink: 0;
}

.nav-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.nav-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 12px;
  background: linear-gradient(135deg, #7c3aed 0%, #10a37f 100%);
  box-shadow: 0 10px 20px rgba(124, 58, 237, 0.2);
  font-size: 18px;
}

.nav-title {
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
  letter-spacing: 0.02em;
}

.nav-right {
  display: flex;
  gap: 8px;
}

.nav-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid rgba(148, 163, 184, 0.25);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.7);
  color: #475569;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s ease;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.nav-btn:hover {
  transform: translateY(-1px);
  background: rgba(255, 255, 255, 0.95);
  border-color: rgba(16, 163, 127, 0.3);
  color: #0f172a;
}

/* ==================== 对话区域 ==================== */
.chat-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px 0 18px;
}

.chat-container {
  max-width: 920px;
  margin: 0 auto;
  width: 100%;
  padding: 0 18px;
}

/* ==================== 底部输入 ==================== */
.bottom-area {
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border-top: 1px solid rgba(148, 163, 184, 0.22);
  padding-top: 12px;
}

/* ==================== 弹窗 ==================== */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.38);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.modal-dialog {
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 18px;
  padding: 24px 28px;
  min-width: 320px;
  box-shadow: 0 20px 45px rgba(15, 23, 42, 0.18);
  text-align: center;
}

.modal-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 20px;
}

.modal-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.modal-btn {
  padding: 10px 22px;
  border-radius: 10px;
  border: none;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s ease;
}

.modal-btn.cancel {
  background: #f1f5f9;
  color: #475569;
}

.modal-btn.cancel:hover {
  background: #e2e8f0;
}

.modal-btn.confirm {
  background: linear-gradient(135deg, #10a37f 0%, #0ea5e9 100%);
  color: #fff;
  box-shadow: 0 10px 18px rgba(16, 163, 127, 0.22);
}

.modal-btn.confirm:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 20px rgba(16, 163, 127, 0.3);
}
</style>