<template>
  <div class="page sub-page">
    <header class="sub-page-head">
      <button type="button" aria-label="返回" @click="router.back()"><ArrowLeft :size="22" /></button>
      <h2>修改登录密码</h2><span></span>
    </header>

    <section class="panel form-panel">
      <p class="form-hint">用于手机号或商城账号的密码登录</p>
      <div class="form-item"><label>当前登录密码</label><input v-model="form.currentPassword" class="field" type="password" autocomplete="current-password" placeholder="请输入当前密码" /></div>
      <div class="form-item">
        <label>短信验证码</label>
        <div class="sms-row">
          <input v-model="form.smsCode" class="field" inputmode="numeric" maxlength="6" autocomplete="one-time-code" placeholder="发送到绑定手机号" />
          <button type="button" class="sms-btn" :disabled="sendingCode || countdown > 0" @click="sendCode">
            {{ countdown > 0 ? `${countdown}秒` : (sendingCode ? '发送中' : '获取验证码') }}
          </button>
        </div>
        <p v-if="maskedPhone" class="phone-hint">验证码将发送至 {{ maskedPhone }}</p>
      </div>
      <div class="form-item"><label>新登录密码</label><input v-model="form.newPassword" class="field" type="password" minlength="10" maxlength="32" autocomplete="new-password" placeholder="10至32位" /></div>
      <div class="form-item"><label>确认新登录密码</label><input v-model="confirmPwd" class="field" type="password" autocomplete="new-password" placeholder="请再次输入" /></div>
      <button class="btn primary save-btn" :disabled="saving" @click="save">{{ saving ? '保存中' : '修改登录密码' }}</button>
      <RouterLink class="forgot-link" to="/forgot-password">忘记当前密码？使用手机验证码找回</RouterLink>
    </section>

    <div v-if="message" class="form-toast" :class="{ error: messageType === 'error' }" role="status" aria-live="polite">{{ message }}</div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import { changeLoginPassword, getMe, sendSmsCode } from '@/api/shop'
import { isValidMainlandPhone } from '@/utils/phone'
import { clearShopSession } from '@/utils/shopSession'

const router = useRouter()
const form = ref({ currentPassword: '', newPassword: '', smsCode: '' })
const confirmPwd = ref('')
const phone = ref('')
const maskedPhone = ref('')
const sendingCode = ref(false)
const countdown = ref(0)
let countdownTimer
let messageTimer
const saving = ref(false)
const message = ref('')
const messageType = ref('success')

const showMessage = (text, type = 'error') => {
  window.clearTimeout(messageTimer)
  message.value = text
  messageType.value = type
  messageTimer = window.setTimeout(() => { message.value = '' }, 1800)
}

onMounted(async () => {
  try {
    const res = await getMe()
    phone.value = res.data?.phone || ''
    maskedPhone.value = phone.value.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2')
  } catch (e) {
    showMessage(e.message || '账号信息加载失败，请重新登录')
  }
})

onBeforeUnmount(() => {
  window.clearInterval(countdownTimer)
  window.clearTimeout(messageTimer)
})

const sendCode = async () => {
  if (!isValidMainlandPhone(phone.value)) return showMessage('账号绑定手机号不正确，请重新登录')
  sendingCode.value = true
  message.value = ''
  try {
    await sendSmsCode(phone.value, 8)
    showMessage('验证码已发送，5分钟内有效', 'success')
    countdown.value = 60
    window.clearInterval(countdownTimer)
    countdownTimer = window.setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) window.clearInterval(countdownTimer)
    }, 1000)
  } catch (e) {
    showMessage(e.message || '验证码发送失败')
  } finally {
    sendingCode.value = false
  }
}

const save = async () => {
  if (saving.value) return
  message.value = ''
  if (!form.value.currentPassword) return showMessage('请输入当前登录密码')
  if (!/^\d{6}$/.test(form.value.smsCode)) return showMessage('请输入6位短信验证码')
  if (form.value.newPassword.length < 10 || form.value.newPassword.length > 32) return showMessage('新登录密码需要10至32位')
  if (form.value.newPassword !== confirmPwd.value) return showMessage('两次输入的新登录密码不一致')
  saving.value = true
  try {
    await changeLoginPassword(form.value)
    clearShopSession()
    await router.replace('/login')
  } catch (e) { showMessage(e.message || '登录密码修改失败') }
  finally { saving.value = false }
}
</script>

<style scoped>
.sub-page-head { display: grid; grid-template-columns: 40px 1fr 40px; align-items: center; margin-bottom: 14px; }
.sub-page-head h2 { margin: 0; text-align: center; font-size: 19px; }
.sub-page-head button { width: 40px; height: 40px; display: grid; place-items: center; padding: 0; background: #fff; border: 0; border-radius: 50%; }
.form-panel { border: 0; border-radius: 16px; }
.form-hint { color: var(--muted); font-size: 12px; margin: 0 0 14px; }
.form-panel .form-item { margin-top: 12px; }
.sms-row { display: grid; grid-template-columns: 1fr auto; gap: 8px; }
.sms-btn { min-width: 104px; padding: 0 12px; border: 1px solid #d8e0e8; border-radius: 10px; background: #fff; color: var(--primary); }
.sms-btn:disabled { color: var(--muted); background: #f5f7f9; }
.phone-hint { margin: 7px 0 0; color: var(--muted); font-size: 12px; }
.save-btn { width: 100%; margin-top: 16px; }
.forgot-link { display: block; margin-top: 13px; color: var(--brand-primary); text-align: center; font-size: 12px; }
.form-toast { position:fixed; top:calc(18px + env(safe-area-inset-top)); left:50%; z-index:1200; max-width:min(88vw,420px); padding:11px 16px; color:#fff; background:rgba(8,114,79,.96); border-radius:10px; box-shadow:0 8px 24px rgba(15,23,42,.18); transform:translateX(-50%); font-size:13px; text-align:center; pointer-events:none; }
.form-toast.error { background:rgba(180,35,24,.96); }
</style>
