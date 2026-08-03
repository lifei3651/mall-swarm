<template>
  <Teleport to="body">
    <div v-if="visible" class="confirm-overlay" @click.self="onCancel">
      <div class="confirm-dialog">
        <div class="confirm-icon">{{ icon }}</div>
        <h3 class="confirm-title">{{ title }}</h3>
        <p class="confirm-message">{{ message }}</p>
        <div class="confirm-actions">
          <button class="btn secondary" @click="onCancel">{{ cancelText }}</button>
          <button class="btn primary" :class="{ danger: isDanger }" @click="onConfirm">{{ confirmText }}</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '确认操作' },
  message: { type: String, default: '确定要执行此操作吗？' },
  confirmText: { type: String, default: '确定' },
  cancelText: { type: String, default: '取消' },
  icon: { type: String, default: '⚠️' },
  isDanger: { type: Boolean, default: false },
})

const emit = defineEmits(['confirm', 'cancel'])

const onConfirm = () => emit('confirm')
const onCancel = () => emit('cancel')
</script>

<style scoped>
.confirm-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.confirm-dialog {
  background: var(--white, #fff);
  border-radius: 16px;
  padding: 28px;
  max-width: 360px;
  width: 90%;
  text-align: center;
}

.confirm-icon {
  font-size: 36px;
  margin-bottom: 12px;
}

.confirm-title {
  font-size: 16px;
  font-weight: 700;
  margin-bottom: 8px;
}

.confirm-message {
  font-size: 13px;
  color: var(--muted, #999);
  margin-bottom: 20px;
  line-height: 1.5;
}

.confirm-actions {
  display: flex;
  gap: 10px;
}

.confirm-actions .btn {
  flex: 1;
  padding: 10px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  border: none;
}

.btn.secondary {
  background: var(--bg, #f5f5f5);
  color: var(--text, #333);
}

.btn.primary {
  background: var(--accent, #1890ff);
  color: #fff;
}

.btn.primary.danger {
  background: #ff4d4f;
}
</style>
