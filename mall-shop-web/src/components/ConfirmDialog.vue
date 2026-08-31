<template>
  <Teleport to="body">
    <Transition name="confirm-pop">
      <div v-if="visible" class="confirm-overlay" @click.self="onCancel">
        <section
          ref="dialogRef"
          class="confirm-dialog"
          role="alertdialog"
          aria-modal="true"
          :aria-labelledby="titleId"
          :aria-describedby="messageId"
          tabindex="-1"
        >
          <button class="confirm-close" type="button" aria-label="关闭" :disabled="busy" @click="onCancel">
            <X :size="19" />
          </button>
          <div class="confirm-icon" :class="{ danger: isDanger }" aria-hidden="true">
            <component :is="iconComponent" :size="25" :stroke-width="2" />
          </div>
          <h3 :id="titleId" class="confirm-title">{{ title }}</h3>
          <p :id="messageId" class="confirm-message">{{ message }}</p>
          <div class="confirm-actions" :class="{ single: !showCancel }">
            <button v-if="showCancel" ref="cancelButtonRef" class="confirm-button secondary" type="button" :disabled="busy" @click="onCancel">
              {{ cancelText }}
            </button>
            <button ref="confirmButtonRef" class="confirm-button primary" :class="{ danger: isDanger }" type="button" :disabled="busy" @click="onConfirm">
              <span v-if="busy" class="confirm-spinner" aria-hidden="true"></span>
              {{ busy ? loadingText : confirmText }}
            </button>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { CircleAlert, CircleX, LogOut, PackageCheck, RotateCcw, ShoppingCart, Trash2, X } from 'lucide-vue-next'

const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '确认操作？' },
  message: { type: String, default: '请确认是否继续当前操作。' },
  confirmText: { type: String, default: '确认' },
  cancelText: { type: String, default: '暂不操作' },
  loadingText: { type: String, default: '处理中…' },
  iconType: { type: String, default: 'warning' },
  isDanger: { type: Boolean, default: false },
  busy: { type: Boolean, default: false },
  showCancel: { type: Boolean, default: true },
})

const emit = defineEmits(['confirm', 'cancel'])
const dialogRef = ref(null)
const cancelButtonRef = ref(null)
const confirmButtonRef = ref(null)
const uid = useId()
const titleId = `confirm-title-${uid}`
const messageId = `confirm-message-${uid}`
const iconMap = {
  warning: CircleAlert,
  delete: Trash2,
  logout: LogOut,
  cart: ShoppingCart,
  afterSale: RotateCcw,
  cancel: CircleX,
  receive: PackageCheck,
}
const iconComponent = computed(() => iconMap[props.iconType] || CircleAlert)

const onConfirm = () => {
  if (!props.busy) emit('confirm')
}

const onCancel = () => {
  if (!props.busy) emit('cancel')
}

const handleKeydown = (event) => {
  if (props.visible && event.key === 'Escape') onCancel()
}

watch(() => props.visible, async (visible) => {
  if (!visible) return
  await nextTick()
  if (props.showCancel) cancelButtonRef.value?.focus()
  else confirmButtonRef.value?.focus()
})

onMounted(() => window.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', handleKeydown))
</script>

<style scoped>
.confirm-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: grid;
  place-items: center;
  padding: 22px;
  background: rgba(15, 23, 42, .52);
  backdrop-filter: blur(4px);
}

.confirm-dialog {
  position: relative;
  width: min(348px, 100%);
  padding: 27px 22px 21px;
  overflow: hidden;
  background: var(--white, #fff);
  border: 1px solid rgba(255, 255, 255, .7);
  border-radius: 22px;
  box-shadow: 0 24px 70px rgba(15, 23, 42, .26);
  text-align: center;
  outline: none;
}

.confirm-close {
  position: absolute;
  top: 13px;
  right: 13px;
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  padding: 0;
  color: #98a2b3;
  background: #f7f8fa;
  border: 0;
  border-radius: 50%;
}

.confirm-icon {
  width: 52px;
  height: 52px;
  display: grid;
  place-items: center;
  margin: 0 auto 14px;
  color: var(--brand-primary, #e7193f);
  background: var(--brand-primary-soft, #fff0f3);
  border-radius: 16px;
}

.confirm-icon.danger {
  color: #c43228;
  background: #fff1ef;
}

.confirm-title {
  margin: 0;
  color: #17202e;
  font-size: 19px;
  font-weight: 800;
  letter-spacing: -.2px;
}

.confirm-message {
  margin: 10px 2px 22px;
  color: #667085;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-line;
}

.confirm-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 11px;
}

.confirm-actions.single { grid-template-columns: 1fr; }

.confirm-button {
  min-height: 46px;
  padding: 0 12px;
  border-radius: 13px;
  font-size: 14px;
  font-weight: 750;
}

.confirm-button.secondary {
  color: #475467;
  background: #f7f8fa;
  border: 1px solid #e4e7ec;
}

.confirm-button.primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  color: #fff;
  background: var(--brand-primary, #e7193f);
  border: 1px solid var(--brand-primary, #e7193f);
}

.confirm-button.primary.danger {
  background: #c43228;
  border-color: #c43228;
}

.confirm-button:focus-visible,
.confirm-close:focus-visible {
  outline: 3px solid color-mix(in srgb, var(--brand-primary, #e7193f) 24%, transparent);
  outline-offset: 2px;
}

.confirm-button:disabled,
.confirm-close:disabled {
  cursor: not-allowed;
  opacity: .6;
}

.confirm-spinner {
  width: 15px;
  height: 15px;
  border: 2px solid rgba(255, 255, 255, .45);
  border-top-color: #fff;
  border-radius: 50%;
  animation: confirm-spin .7s linear infinite;
}

.confirm-pop-enter-active,
.confirm-pop-leave-active { transition: opacity .18s ease; }
.confirm-pop-enter-active .confirm-dialog,
.confirm-pop-leave-active .confirm-dialog { transition: transform .2s ease, opacity .18s ease; }
.confirm-pop-enter-from,
.confirm-pop-leave-to { opacity: 0; }
.confirm-pop-enter-from .confirm-dialog,
.confirm-pop-leave-to .confirm-dialog { opacity: 0; transform: translateY(10px) scale(.97); }

@keyframes confirm-spin { to { transform: rotate(360deg); } }

@media (max-width: 360px) {
  .confirm-overlay { padding: 18px; }
  .confirm-dialog { padding: 24px 17px 18px; border-radius: 20px; }
  .confirm-message { font-size: 13px; }
  .confirm-button { min-height: 44px; }
}

@media (prefers-reduced-motion: reduce) {
  .confirm-pop-enter-active,
  .confirm-pop-leave-active,
  .confirm-pop-enter-active .confirm-dialog,
  .confirm-pop-leave-active .confirm-dialog { transition: none; }
}
</style>
