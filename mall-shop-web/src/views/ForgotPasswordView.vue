<template>
  <div class="page">
    <div class="section-head">
      <div>
        <h2>找回密码</h2>
        <p>通过手机号验证码重置密码</p>
      </div>
      <RouterLink class="back-login-link" to="/login">返回登录</RouterLink>
    </div>

    <section class="panel auth-panel">
      <!-- 步骤1：验证手机号 -->
      <div v-if="step === 1">
        <div class="form-grid">
          <div class="form-item full">
            <label for="forgot-phone">手机号</label>
            <input id="forgot-phone" v-model="phone" name="phone" class="field" placeholder="请输入注册手机号" maxlength="11" inputmode="tel" autocomplete="tel" @input="handlePhoneInput" />
          </div>
          <div class="form-item full">
            <label for="forgot-captcha">图形验证码</label>
            <div class="sms-row">
              <input id="forgot-captcha" v-model="captchaCode" class="field sms-input" placeholder="请输入图形验证码" maxlength="4" autocomplete="off" />
              <button type="button" class="captcha-button" aria-label="刷新图形验证码" @click="refreshCaptcha">
                <img :src="captchaImage" alt="图形验证码" />
              </button>
            </div>
            <p class="field-hint">可与短信验证码任意顺序填写，重置密码时统一校验</p>
          </div>
          <div class="form-item full">
            <label for="forgot-code">验证码</label>
            <div class="sms-row">
              <input id="forgot-code" v-model="code" name="smsCode" class="field sms-input" placeholder="请输入验证码" maxlength="6" inputmode="numeric" autocomplete="one-time-code" />
              <button type="button" class="btn sms-btn" :disabled="cooldown > 0" @click="sendCode">
                {{ cooldown > 0 ? `${cooldown}s` : '获取验证码' }}
              </button>
            </div>
          </div>
        </div>
        <button type="button" class="btn primary" style="width: 100%; margin-top: 18px" @click="goToResetStep">下一步</button>
      </div>

      <!-- 步骤2：设置新密码 -->
      <div v-if="step === 2">
        <div class="form-grid">
          <div class="form-item full">
            <label for="forgot-new-password">新密码</label>
            <input id="forgot-new-password" v-model="newPassword" name="newPassword" class="field" type="password" minlength="6" maxlength="32" autocomplete="new-password" placeholder="至少6位" />
          </div>
          <div class="form-item full">
            <label for="forgot-confirm-password">确认密码</label>
            <input id="forgot-confirm-password" v-model="confirmPassword" name="confirmPassword" class="field" type="password" minlength="6" maxlength="32" autocomplete="new-password" placeholder="再次输入密码" />
          </div>
        </div>
        <button type="button" class="btn primary" style="width: 100%; margin-top: 18px" :disabled="loading" @click="doResetPassword">
          {{ loading ? '提交中' : '重置密码' }}
        </button>
      </div>

      <!-- 步骤3：成功 -->
      <div v-if="step === 3" class="success-state">
        <div class="success-icon">✅</div>
        <h3>密码重置成功</h3>
        <p>请使用新密码登录</p>
        <button type="button" class="btn primary" style="width: 100%; margin-top: 18px" @click="$router.push('/login')">去登录</button>
      </div>

      <div v-if="error" class="form-toast" role="alert" aria-live="assertive">{{ error }}</div>
    </section>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { getLoginCaptcha, sendSmsCode, resetPassword } from '@/api/shop'
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
const captchaId = ref('')
const captchaCode = ref('')
const captchaImage = ref('')
let errorTimer
const clearError = () => { window.clearTimeout(errorTimer); error.value = '' }
const showError = (text) => {
  window.clearTimeout(errorTimer)
  error.value = text
  errorTimer = window.setTimeout(() => { error.value = '' }, 1800)
}

const handlePhoneInput = () => {
  phone.value = normalizeMainlandPhone(phone.value)
  if (error.value === '请输入正确的11位手机号') clearError()
}

const sendCode = async () => {
  clearError()
  if (!isValidMainlandPhone(phone.value)) {
    showError('请输入正确的11位手机号')
    return
  }
  try {
    await sendSmsCode(phone.value, 3) // 3=找回密码
    cooldown.value = 60
    const timer = setInterval(() => { cooldown.value--; if (cooldown.value <= 0) clearInterval(timer) }, 1000)
  } catch (e) {
    showError(e.message || '验证码发送失败')
  }
}

const refreshCaptcha = async () => {
  try {
    const res = await getLoginCaptcha()
    captchaId.value = res.data.captchaId
    captchaImage.value = res.data.image
    captchaCode.value = ''
  } catch (e) {
    showError(e.message || '图形验证码加载失败')
  }
}

const goToResetStep = async () => {
  clearError()
  if (!isValidMainlandPhone(phone.value)) { showError('请输入正确的11位手机号'); return }
  if (!code.value || code.value.length !== 6) { showError('请输入6位验证码'); return }
  if (!captchaId.value || !/^[A-Za-z0-9]{4}$/.test(captchaCode.value.trim())) { showError('请输入4位图形验证码'); return }
  // 不在这里验证验证码，而是直接进入下一步，由 resetPassword 接口一次性验证并重置
  step.value = 2
}

const doResetPassword = async () => {
  if (loading.value) return
  clearError()
  if (!newPassword.value || newPassword.value.length < 6) { showError('新密码至少需要6位'); return }
  if (newPassword.value !== confirmPassword.value) { showError('两次输入的密码不一致'); return }
  loading.value = true
  try {
    await resetPassword({
      phone: phone.value,
      code: code.value,
      newPassword: newPassword.value,
      captchaId: captchaId.value,
      captchaCode: captchaCode.value.trim(),
    })
    clearShopSession()
    step.value = 3
  } catch (e) {
    showError(e.message || '密码重置失败')
    await refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onBeforeUnmount(() => window.clearTimeout(errorTimer))
onMounted(refreshCaptcha)
</script>

<style scoped>
.sms-row { display: flex; gap: 10px; }
.sms-input { flex: 1; }
.sms-btn { white-space: nowrap; padding: 0 16px; background: var(--accent, #0f766e); color: #fff; border: none; border-radius: 8px; font-size: 13px; cursor: pointer; }
.sms-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.captcha-button { width:142px; height:48px; padding:0; border:1px solid #d8dee9; border-radius:8px; overflow:hidden; background:#fff; cursor:pointer; }
.captcha-button img { width:100%; height:100%; object-fit:cover; display:block; }
.field-hint { margin:0; color:var(--muted,#667085); font-size:12px; line-height:1.5; }
.back-login-link { color:var(--accent,#0f766e); font-size:13px; font-weight:700; text-decoration:none; }
.success-state { text-align: center; padding: 30px 0; }
.success-icon { font-size: 48px; margin-bottom: 16px; }
.form-toast { position:fixed; top:calc(18px + env(safe-area-inset-top)); left:50%; z-index:1200; max-width:min(88vw,420px); padding:11px 16px; color:#fff; background:rgba(180,35,24,.96); border-radius:10px; box-shadow:0 8px 24px rgba(15,23,42,.18); transform:translateX(-50%); font-size:13px; line-height:1.5; text-align:center; pointer-events:none; }
</style>
