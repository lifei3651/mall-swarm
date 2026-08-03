<template>
  <Teleport to="body">
    <div v-if="visible" class="confirm-overlay" @click.self="onCancel">
      <div class="confirm-dialog">
        <h3>提现确认</h3>
        <div class="withdraw-info">
          <div class="info-row">
            <span class="label">提现金额</span>
            <span class="value amount">¥{{ amount }}</span>
          </div>
          <div class="info-row">
            <span class="label">提现方式</span>
            <span class="value">{{ withdrawType }}</span>
          </div>
        </div>

        <div class="sms-verify">
          <p class="verify-hint">为保障资金安全，请输入手机验证码</p>
          <div class="sms-row">
            <input v-model="code" class="field sms-input" placeholder="请输入验证码" maxlength="6" />
            <button class="btn sms-btn" :disabled="cooldown > 0" @click="sendCode">
              {{ cooldown > 0 ? `${cooldown}s` : '获取验证码' }}
            </button>
          </div>
        </div>

        <div class="confirm-actions">
          <button class="btn cancel" @click="onCancel">取消</button>
          <button class="btn confirm" :disabled="!code || code.length !== 6" @click="onConfirm">确认提现</button>
        </div>

        <p v-if="error" class="error-msg">{{ error }}</p>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, watch } from 'vue'
import { sendSmsCode } from '@/api/shop'

const props = defineProps({
  visible: Boolean,
  amount: { type: [Number, String], default: '0.00' },
  withdrawType: { type: String, default: '银行卡' },
  phone: { type: String, default: '' },
})

const emit = defineEmits(['confirm', 'cancel'])

const code = ref('')
const cooldown = ref(0)
const error = ref('')

watch(() => props.visible, (val) => {
  if (val) { code.value = ''; error.value = '' }
})

const sendCode = async () => {
  error.value = ''
  if (!props.phone) { error.value = '手机号为空'; return }
  try {
    await sendSmsCode(props.phone, 5) // 5=提现确认
    cooldown.value = 60
    const timer = setInterval(() => { cooldown.value--; if (cooldown.value <= 0) clearInterval(timer) }, 1000)
  } catch (e) {
    error.value = e.message || '发送失败'
  }
}

const onConfirm = () => {
  if (!code.value || code.value.length !== 6) { error.value = '请输入6位验证码'; return }
  emit('confirm', code.value)
}

const onCancel = () => emit('cancel')
</script>

<style scoped>
.confirm-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 9999; }
.confirm-dialog { background: #fff; border-radius: 16px; padding: 24px; max-width: 380px; width: 90%; }
.confirm-dialog h3 { text-align: center; margin-bottom: 20px; font-size: 16px; }
.withdraw-info { background: #f8f9fa; border-radius: 10px; padding: 16px; margin-bottom: 16px; }
.info-row { display: flex; justify-content: space-between; margin-bottom: 8px; }
.info-row:last-child { margin-bottom: 0; }
.label { color: #666; font-size: 13px; }
.value { font-weight: 600; font-size: 14px; }
.value.amount { color: #e85d43; font-size: 18px; }
.sms-verify { margin-bottom: 16px; }
.verify-hint { font-size: 12px; color: #999; margin-bottom: 10px; }
.sms-row { display: flex; gap: 10px; }
.sms-input { flex: 1; padding: 10px 12px; border: 1px solid #dfe7e2; border-radius: 8px; font-size: 14px; }
.sms-btn { white-space: nowrap; padding: 0 16px; background: #0f766e; color: #fff; border: none; border-radius: 8px; font-size: 13px; cursor: pointer; }
.sms-btn:disabled { opacity: 0.6; }
.confirm-actions { display: flex; gap: 10px; margin-top: 16px; }
.confirm-actions .btn { flex: 1; padding: 12px; border-radius: 10px; font-size: 14px; font-weight: 600; cursor: pointer; border: none; }
.btn.cancel { background: #f5f5f5; color: #333; }
.btn.confirm { background: #0f766e; color: #fff; }
.btn.confirm:disabled { opacity: 0.5; cursor: not-allowed; }
.error-msg { color: #e85d43; font-size: 12px; text-align: center; margin-top: 10px; }
</style>
