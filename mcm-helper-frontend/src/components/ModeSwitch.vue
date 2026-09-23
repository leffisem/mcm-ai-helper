<template>
  <div class="mode-switch">
    <button
      :class="['mode-btn', { active: modelValue === 'normal' }]"
      @click="$emit('update:modelValue', 'normal')"
    >
      普通模式
    </button>
    <button
      :class="['mode-btn', { active: modelValue === 'expert' }]"
      @click="$emit('update:modelValue', 'expert')"
    >
      专家模式
    </button>
  </div>
</template>

<script setup>
defineProps({
  modelValue: {
    type: String,
    default: 'normal'
  }
})

defineEmits(['update:modelValue'])
</script>

<style scoped>
.mode-switch {
  display: flex;
  background: #f0f0f0;
  border-radius: 8px;
  padding: 3px;
  width: fit-content;
  margin: 0 auto 12px;
}

.mode-btn {
  padding: 6px 20px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #666;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}

.mode-btn.active {
  background: #10a37f;
  color: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.mode-btn:not(.active):hover {
  color: #333;
}
</style>