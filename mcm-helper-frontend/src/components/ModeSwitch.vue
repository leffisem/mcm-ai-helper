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
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: linear-gradient(180deg, rgba(148, 163, 184, 0.12), rgba(255, 255, 255, 0.9));
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 999px;
  padding: 4px;
  width: fit-content;
  margin: 0 auto 14px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.mode-btn {
  padding: 8px 20px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  font-family: inherit;
}

.mode-btn.active {
  background: linear-gradient(135deg, #10a37f 0%, #0ea5e9 100%);
  color: #fff;
  box-shadow: 0 8px 18px rgba(16, 163, 127, 0.22);
}

.mode-btn:not(.active):hover {
  color: #0f172a;
  background: rgba(255, 255, 255, 0.5);
}
</style>