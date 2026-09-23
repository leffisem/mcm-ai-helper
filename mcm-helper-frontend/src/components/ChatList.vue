<template>
  <div class="chat-list" ref="listRef">
    <div v-if="messages.length === 0" class="welcome">
      <div class="welcome-icon">🤖</div>
      <h2>数学建模 AI 助手</h2>
      <p class="welcome-desc">
        我可以帮你分析赛题、构建模型、生成代码框架、检验结果和指导论文写作。
      </p>
      <div class="welcome-tips">
        <div class="tip-item">
          <span class="tip-icon">📝</span>
          <span>粘贴完整赛题，获取结构化分析</span>
        </div>
        <div class="tip-item">
          <span class="tip-icon">🔍</span>
          <span>针对单一环节（审题/建模/求解）提问</span>
        </div>
        <div class="tip-item">
          <span class="tip-icon">💬</span>
          <span>自由追问，AI 会结合上下文回答</span>
        </div>
      </div>
    </div>
    <template v-for="(msg, index) in messages" :key="index">
      <ChatMessage
        :role="msg.role"
        :content="msg.content"
        :streaming="msg.streaming"
      />
    </template>
  </div>
</template>

<script setup>
import ChatMessage from './ChatMessage.vue'

defineProps({
  messages: {
    type: Array,
    default: () => []
  }
})
</script>

<style scoped>
.chat-list {
  flex: 1;
  overflow-y: auto;
  padding: 0;
  scroll-behavior: smooth;
}

.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  padding: 40px 20px;
}

.welcome-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.welcome h2 {
  font-size: 24px;
  color: #2d2d2d;
  margin: 0 0 12px;
  font-weight: 600;
}

.welcome-desc {
  color: #666;
  font-size: 15px;
  max-width: 400px;
  line-height: 1.6;
  margin: 0 0 32px;
}

.welcome-tips {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tip-item {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #555;
  font-size: 14px;
}

.tip-icon {
  font-size: 18px;
}
</style>