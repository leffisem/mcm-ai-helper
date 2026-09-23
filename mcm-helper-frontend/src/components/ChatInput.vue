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
  padding: 0 16px 16px;
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}

.input-container {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 12px;
  padding: 8px 12px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
  transition: box-shadow 0.2s;
}

.input-container:focus-within {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  border-color: #10a37f;
}

.chat-textarea {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  font-size: 15px;
  line-height: 1.5;
  font-family: inherit;
  color: #2d2d2d;
  max-height: 200px;
  padding: 4px 0;
  background: transparent;
}

.chat-textarea::placeholder {
  color: #999;
}

.send-btn {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: #10a37f;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}

.send-btn:hover:not(:disabled) {
  background: #0e8c6b;
}

.send-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.hint-text {
  text-align: center;
  color: #999;
  font-size: 12px;
  margin: 10px 0 0;
}
</style>