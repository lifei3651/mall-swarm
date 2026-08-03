<template>
  <div class="page">
    <div class="section-head">
      <div>
        <h2>找回密码</h2>
        <p>通过手机号验证码重置密码</p>
      </div>
    </div>

    <section class="panel auth-panel">
      <!-- 步骤1：验证手机号 -->
      <div v-if="step === 1">
        <div class="form-grid">
          <div class="form-item full">
            <label>手机号</label>
            <input v-model="phone" class="field" placeholder="请输入注册手机号" maxlength="11" inputmode="tel" autocomplete="tel" @input="handlePhoneInput" />
          </div>
          <div class="form-item full">
            <label>验证码</label>
            <div class="sms-row">
              <input v-model="code" class="field sms-input" placeholder="请输入验证码" maxlength="6" />
              <button class="btn sms-btn" :disabled="cooldown > 0" @click="sendCode">
                {{ cooldown > 0 ? `${cooldown}s` : '获取验证码' }}
              </button>
            </div>
          </div>
        </div>
        <button class="btn primary" style="width: 100%; margin-top: 18px" @click="goToResetStep">下一步</button>
      </div>

      <!-- 步骤2：设置新密码 -->
      <div v-if="step === 2">
        <div class="form-grid">
          <div class="form-item full">
            <label>新密码</label>
            <input v-model="newPassword" class="field" type="password" placeholder="至少6位" />
          </div>
          <div class="form-item full">
            <label>确认密码</label>
            <input v-model="confirmPassword" class="field" type="password" placeholder="再次输入密码" />
          </div>
        </div>
        <button class="btn primary" style="width: 100%; margin-top: 18px" :disabled="loading" @click="doResetPassword">
          {{ loading ? '提交中' : '重置密码' }}
        </button>
      </div>

      <!-- 步骤3：成功 -->
      <div v-if="step === 3" class="success-state">
        <div class="success-icon">✅</div>
        <h3>密码重置成功</h3>
        <p>请使用新密码登录</p>
        <button class="btn primary" style="width: 100%; margin-top: 18px" @click="$router.push('/login')">去登录</button>
      </div>

      <p v-if="error" style="color: var(--coral); margin-top: 12px">{{ error }}</p>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { sendSmsCode, resetPassword } from '@/api/shop'
import { isValidMainlandPhone, normalizeMainlandPhone } from '@/utils/phone'
import { clearShopSession } from '@/utils/shopSession'

const step = ref(1)
const phone = ref('')
const code = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const cooldown = ref(0)
const loading = ref(false)
const error = ref('')

const handlePhoneInput = () => {
  phone.value = normalizeMainlandPhone(phone.value)
  if (error.value === '请输入正确的11位手机号') error.value = ''
}

const sendCode = async () => {
  error.value = ''
  if (!isValidMainlandPhone(phone.value)) {
    error.value = '请输入正确的11位手机号'
    return
  }
  try {
    await sendSmsCode(phone.value, 3) // 3=找回密码
    cooldown.value = 60
    const timer = setInterval(() => { cooldown.value--; if (cooldown.value <= 0) clearInterval(timer) }, 1000)
  } catch (e) {
    error.value = e.message || '发送失败'
  }
}

const goToResetStep = async () => {
  error.value = ''
  if (!isValidMainlandPhone(phone.value)) { error.value = '请输入正确的11位手机号'; return }
  if (!code.value || code.value.length !== 6) { error.value = '请输入6位验证码'; return }
  // 不在这里验证验证码，而是直接进入下一步，由 resetPassword 接口一次性验证并重置
  step.value = 2
}

const doResetPassword = async () => {
  error.value = ''
  if (!newPassword.value || newPassword.value.length < 6) { error.value = '密码至少6位'; return }
  if (newPassword.value !== confirmPassword.value) { error.value = '两次密码不一致'; return }
  loading.value = true
  try {
    await resetPassword({ phone: phone.value, code: code.value, newPassword: newPassword.value })
    clearShopSession()
    step.value = 3
  } catch (e) {
    error.value = e.message || '重置失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.sms-row { display: flex; gap: 10px; }
.sms-input { flex: 1; }
.sms-btn { white-space: nowrap; padding: 0 16px; background: var(--accent, #0f766e); color: #fff; border: none; border-radius: 8px; font-size: 13px; cursor: pointer; }
.sms-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.success-state { text-align: center; padding: 30px 0; }
.success-icon { font-size: 48px; margin-bottom: 16px; }
</style>
