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
  background: #f7f7f8;
}

/* ==================== 顶部导航 ==================== */
.top-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  background: #fff;
  border-bottom: 1px solid #e5e5e5;
  flex-shrink: 0;
}

.nav-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.nav-logo {
  font-size: 22px;
}

.nav-title {
  font-size: 16px;
  font-weight: 600;
  color: #2d2d2d;
}

.nav-right {
  display: flex;
  gap: 8px;
}

.nav-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  background: #fff;
  color: #555;
  font-size: 13px;
  cursor: pointer;
  font-family: inherit;
  transition: background 0.2s;
}

.nav-btn:hover {
  background: #f5f5f5;
}

/* ==================== 对话区域 ==================== */
.chat-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px 0;
}

.chat-container {
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
}

/* ==================== 底部输入 ==================== */
.bottom-area {
  flex-shrink: 0;
  background: #fff;
  border-top: 1px solid #e5e5e5;
  padding-top: 12px;
}

/* ==================== 弹窗 ==================== */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-dialog {
  background: #fff;
  border-radius: 12px;
  padding: 24px 28px;
  min-width: 320px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  text-align: center;
}

.modal-title {
  font-size: 16px;
  color: #2d2d2d;
  margin-bottom: 20px;
}

.modal-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.modal-btn {
  padding: 8px 24px;
  border-radius: 8px;
  border: none;
  font-size: 14px;
  cursor: pointer;
  font-family: inherit;
  transition: background 0.2s;
}

.modal-btn.cancel {
  background: #f0f0f0;
  color: #555;
}

.modal-btn.cancel:hover {
  background: #e5e5e5;
}

.modal-btn.confirm {
  background: #10a37f;
  color: #fff;
}

.modal-btn.confirm:hover {
  background: #0e8c6b;
}
</style>