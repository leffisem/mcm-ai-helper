<template>
  <div class="chat-input-area">
    <ModeSwitch v-model="currentMode" />

    <div class="input-container">
      <textarea
        ref="textareaRef"
        v-model="inputText"
        :placeholder="placeholder"
        class="chat-textarea"
        rows="1"
        @keydown.enter.exact="handleSend"
        @input="autoResize"
      ></textarea>
      <button
        class="send-btn"
        :disabled="!inputText.trim() || loading"
        @click="handleSend"
      >
        <svg viewBox="0 0 24 24" fill="none" width="20" height="20">
          <path
            d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"
            fill="currentColor"
          />
        </svg>
      </button>
    </div>

    <p class="hint-text">
      {{ modeHint }}
    </p>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import ModeSwitch from './ModeSwitch.vue'

const props = defineProps({
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['send', 'update:mode'])

const inputText = ref('')
const currentMode = ref('normal')
const textareaRef = ref(null)

const placeholder = computed(() =>
  currentMode.value === 'normal'
    ? '输入你的问题...'
    : '请粘贴完整赛题或描述建模需求...'
)

const modeHint = computed(() =>
  currentMode.value === 'normal'
    ? '普通模式：快速问答 · 专家模式：三段式赛题分析 · AI 回答仅供参考'
    : '专家模式：三段式赛题分析 · 普通模式：快速问答 · AI 回答仅供参考'
)

watch(currentMode, (val) => {
  emit('update:mode', val)
})

function autoResize() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 200) + 'px'
}

function handleSend(e) {
  if (e) e.preventDefault()
  const text = inputText.value.trim()
  if (!text || props.loading) return
  emit('send', { text, mode: currentMode.value })
  inputText.value = ''
  nextTick(() => autoResize())
}

function focus() {
  nextTick(() => textareaRef.value?.focus())
}

defineExpose({ focus, currentMode })
</script>

<style scoped>
.chat-input-area {
  padding: 0 18px 18px;
  max-width: 920px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}

.input-container {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 20px;
  padding: 12px 14px 12px 16px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.06);
  transition: all 0.2s ease;
}

.input-container:focus-within {
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.08), 0 0 0 4px rgba(16, 163, 127, 0.09);
  border-color: rgba(16, 163, 127, 0.32);
  background: rgba(255, 255, 255, 0.95);
}

.chat-textarea {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  font-size: 15px;
  line-height: 1.6;
  font-family: inherit;
  color: #1f2937;
  max-height: 200px;
  padding: 6px 0;
  background: transparent;
}

.chat-textarea::placeholder {
  color: #94a3b8;
}

.send-btn {
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  border: none;
  background: linear-gradient(135deg, #10a37f 0%, #0ea5e9 100%);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12px 20px rgba(16, 163, 127, 0.24);
  transition: transform 0.2s ease, box-shadow 0.2s ease, opacity 0.2s ease;
}

.send-btn:hover:not(:disabled) {
  transform: translateY(-1px) scale(1.02);
  box-shadow: 0 14px 24px rgba(16, 163, 127, 0.28);
}

.send-btn:disabled {
  background: linear-gradient(135deg, #cbd5e1 0%, #e2e8f0 100%);
  box-shadow: none;
  cursor: not-allowed;
}

.hint-text {
  text-align: center;
  color: #64748b;
  font-size: 12px;
  margin: 10px 0 0;
  letter-spacing: 0.01em;
}
</style>