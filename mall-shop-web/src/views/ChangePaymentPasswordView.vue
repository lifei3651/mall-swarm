<template>
  <div class="page sub-page">
    <header class="sub-page-head">
      <button type="button" aria-label="返回" @click="router.back()"><ArrowLeft :size="22" /></button>
      <h2>{{ hasPassword ? '修改交易密码' : '设置交易密码' }}</h2><span></span>
    </header>

    <section class="panel form-panel">
      <p class="form-hint">交易密码为6位数字，用于余额支付、转账和提现验证</p>
      <p v-if="locked" class="warning">支付密码因连续输入错误已临时锁定，请30分钟后再试。</p>
      <div v-if="hasPassword" class="form-item"><label>当前交易密码</label><input v-model="form.oldPassword" class="field" type="password" inputmode="numeric" maxlength="6" autocomplete="off" placeholder="请输入原6位交易密码" /></div>
      <template v-else>
        <div class="form-item"><label>当前登录密码</label><input v-model="form.loginPassword" class="field" type="password" maxlength="32" autocomplete="current-password" placeholder="请再次输入商城登录密码" /></div>
      </template>
      <div class="form-item"><label>短信验证码</label><div class="sms-row"><input v-model="form.smsCode" class="field" inputmode="numeric" maxlength="6" autocomplete="one-time-code" placeholder="发送到绑定手机号" /><button type="button" class="sms-btn" :disabled="sendingCode || countdown > 0" @click="sendCode">{{ countdown > 0 ? `${countdown}秒` : (sendingCode ? '发送中' : '获取验证码') }}</button></div></div>
      <p v-if="maskedPhone" class="phone-hint">验证码将发送至 {{ maskedPhone }}</p>
      <div class="form-item"><label>新交易密码</label><input v-model="form.newPassword" class="field" type="password" inputmode="numeric" maxlength="6" autocomplete="new-password" placeholder="请输入6位数字" /></div>
      <div class="form-item"><label>确认新交易密码</label><input v-model="confirmPwd" class="field" type="password" inputmode="numeric" maxlength="6" autocomplete="new-password" placeholder="请再次输入" /></div>
      <button class="btn primary save-btn" :disabled="saving" @click="save">{{ saving ? '保存中' : (hasPassword ? '修改交易密码' : '设置交易密码') }}</button>
    </section>

    <p v-if="message" class="page-message" :class="{ error: messageType === 'error' }">{{ message }}</p>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import { getMe, getWalletSummary, sendSmsCode, setPaymentPassword } from '@/api/shop'
import { isValidMainlandPhone } from '@/utils/phone'

const route = useRoute()
const router = useRouter()
const hasPassword = ref(false)
const locked = ref(false)
const form = ref({ oldPassword: '', newPassword: '', loginPassword: '', smsCode: '' })
const confirmPwd = ref('')
const phone = ref('')
const maskedPhone = ref('')
const sendingCode = ref(false)
const countdown = ref(0)
let countdownTimer
const saving = ref(false)
const message = ref('')
const messageType = ref('success')

const showMessage = (text, type = 'error') => { message.value = text; messageType.value = type }

onMounted(async () => {
  try {
    const res = await getWalletSummary()
    hasPassword.value = res.data?.hasPaymentPassword || false
    locked.value = res.data?.paymentPasswordLocked || false
    const memberRes = await getMe()
    phone.value = memberRes.data?.phone || ''
    maskedPhone.value = phone.value.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2')
  } catch {}
})

onBeforeUnmount(() => window.clearInterval(countdownTimer))

const sendCode = async () => {
  if (!isValidMainlandPhone(phone.value)) return showMessage('账号绑定手机号不正确，请重新登录')
  sendingCode.value = true
  try {
    await sendSmsCode(phone.value, 7)
    showMessage('验证码已发送，5分钟内有效', 'success')
    countdown.value = 60
    window.clearInterval(countdownTimer)
    countdownTimer = window.setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) window.clearInterval(countdownTimer)
    }, 1000)
  } catch (e) { showMessage(e.message || '验证码发送失败') }
  finally { sendingCode.value = false }
}

const save = async () => {
  message.value = ''
  if (!/^\d{6}$/.test(form.value.newPassword)) return showMessage('交易密码必须是6位数字')
  if (form.value.newPassword !== confirmPwd.value) return showMessage('两次输入的交易密码不一致')
  if (hasPassword.value && !/^\d{6}$/.test(form.value.oldPassword)) return showMessage('请输入当前6位交易密码')
  if (!hasPassword.value && !form.value.loginPassword) return showMessage('请输入当前登录密码')
  if (!/^\d{6}$/.test(form.value.smsCode)) return showMessage('请输入6位短信验证码')
  saving.value = true
  try {
    await setPaymentPassword(form.value)
    form.value = { oldPassword: '', newPassword: '', loginPassword: '', smsCode: '' }
    confirmPwd.value = ''
    showMessage('交易密码已保存', 'success')
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') ? route.query.redirect : ''
    if (redirect) window.setTimeout(() => router.replace(redirect), 450)
  } catch (e) { showMessage(e.message || '交易密码保存失败') }
  finally { saving.value = false }
}
</script>

<style scoped>
.sub-page-head { display: grid; grid-template-columns: 40px 1fr 40px; align-items: center; margin-bottom: 14px; }
.sub-page-head h2 { margin: 0; text-align: center; font-size: 19px; }
.sub-page-head button { width: 40px; height: 40px; display: grid; place-items: center; padding: 0; background: #fff; border: 0; border-radius: 50%; }
.form-panel { border: 0; border-radius: 16px; }
.form-hint { color: var(--muted); font-size: 12px; margin: 0 0 14px; }
.warning { padding: 10px 12px; color: #b45309; background: #fff8e8; border-radius: 9px; font-size: 12px; margin-bottom: 12px; }
.form-panel .form-item { margin-top: 12px; }
.sms-row { display: grid; grid-template-columns: 1fr auto; gap: 8px; }
.sms-btn { min-width: 104px; padding: 0 12px; border: 1px solid #d8e0e8; border-radius: 10px; background: #fff; color: var(--primary); }
.sms-btn:disabled { color: var(--muted); background: #f5f7f9; }
.phone-hint { margin: 7px 0 0; color: var(--muted); font-size: 12px; }
.save-btn { width: 100%; margin-top: 16px; }
.page-message { padding: 12px 14px; color: #08724f; background: #eaf8f3; border-radius: 10px; margin-top: 12px; }
.page-message.error { color: #b42318; background: #fff1f0; }
</style>
