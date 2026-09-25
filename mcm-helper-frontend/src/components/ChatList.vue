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
  min-height: 430px;
  text-align: center;
  padding: 34px 22px;
  border-radius: 28px;
  background: linear-gradient(180deg, rgba(255,255,255,0.75), rgba(248,250,252,0.95));
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.04);
}

.welcome-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 74px;
  height: 74px;
  border-radius: 22px;
  margin-bottom: 16px;
  background: linear-gradient(135deg, rgba(124,58,237,0.14), rgba(16,163,127,0.16));
  font-size: 38px;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.9);
}

.welcome h2 {
  font-size: 28px;
  color: #111827;
  margin: 0 0 12px;
  font-weight: 700;
  letter-spacing: -0.03em;
}

.welcome-desc {
  color: #475569;
  font-size: 15px;
  max-width: 520px;
  line-height: 1.7;
  margin: 0 0 28px;
}

.welcome-tips {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: min(100%, 470px);
}

.tip-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255,255,255,0.7);
  border: 1px solid rgba(148,163,184,0.14);
  color: #334155;
  font-size: 14px;
  text-align: left;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.03);
}

.tip-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 10px;
  background: rgba(16,163,127,0.08);
  font-size: 16px;
}
</style>