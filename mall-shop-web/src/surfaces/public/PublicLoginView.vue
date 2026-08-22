<template>
  <div class="public-auth-page">
    <section class="public-auth-card">
      <header class="auth-heading">
        <img :src="brandLogo" :alt="`${brandName} Logo`" @error="logoFailed = true" />
        <div>
          <h1>{{ isRegister ? '注册商城账号' : '登录商城账号' }}</h1>
          <p>{{ isRegister ? '注册后即可购物、查询订单和申请售后' : '登录后继续管理购物车和订单' }}</p>
        </div>
      </header>

      <div v-if="!isRegister" class="login-tabs" role="tablist" aria-label="登录方式">
        <button type="button" :class="{ active: loginType === 'password' }" @click="switchLoginType('password')">密码登录</button>
        <button type="button" :class="{ active: loginType === 'sms' }" @click="switchLoginType('sms')">验证码登录</button>
      </div>

      <form class="auth-form" @submit.prevent="submit">
        <template v-if="!isRegister && loginType === 'password'">
          <label for="public-login-account">手机号或登录账号</label>
          <input id="public-login-account" v-model.trim="loginForm.account" autocomplete="username" placeholder="请输入手机号或登录账号" />
          <label for="public-login-password">登录密码</label>
          <input id="public-login-password" v-model="loginForm.password" type="password" autocomplete="current-password" placeholder="请输入登录密码" />
        </template>

        <template v-if="!isRegister && loginType === 'sms'">
          <label for="public-login-phone">手机号</label>
          <input id="public-login-phone" v-model="smsLoginForm.phone" inputmode="tel" autocomplete="tel" maxlength="11" placeholder="请输入11位手机号" @input="normalizePhone(smsLoginForm)" />
          <label for="public-login-sms">短信验证码</label>
          <div class="inline-field">
            <input id="public-login-sms" v-model.trim="smsLoginForm.smsCode" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="请输入6位验证码" />
            <button type="button" :disabled="smsCooldown > 0 || sendingCode" @click="sendCode(smsLoginForm.phone)">{{ smsButtonText }}</button>
          </div>
        </template>

        <template v-if="isRegister">
          <label for="public-register-phone">手机号</label>
          <input id="public-register-phone" v-model="registerForm.phone" inputmode="tel" autocomplete="tel" maxlength="11" placeholder="请输入11位手机号" @input="normalizePhone(registerForm)" />
          <label for="public-register-account">登录账号</label>
          <input id="public-register-account" v-model="registerForm.username" autocomplete="username" maxlength="20" placeholder="4至20位，以英文字母开头" @input="normalizeAccount" />
          <label for="public-register-password">登录密码</label>
          <input id="public-register-password" v-model="registerForm.password" type="password" autocomplete="new-password" minlength="6" maxlength="32" placeholder="请输入6至32位密码" />
          <label for="public-register-confirm">确认登录密码</label>
          <input id="public-register-confirm" v-model="confirmPassword" type="password" autocomplete="new-password" minlength="6" maxlength="32" placeholder="请再次输入登录密码" />
          <label for="public-register-sms">短信验证码</label>
          <div class="inline-field">
            <input id="public-register-sms" v-model.trim="registerForm.smsCode" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="请输入6位验证码" />
            <button type="button" :disabled="smsCooldown > 0 || sendingCode" @click="sendCode(registerForm.phone)">{{ smsButtonText }}</button>
          </div>
          <label class="agreement" for="public-register-agreement">
            <input id="public-register-agreement" v-model="agreed" type="checkbox" />
            <span>我已阅读并同意 <RouterLink to="/legal/agreement">《用户服务协议》</RouterLink>和<RouterLink to="/legal/privacy">《隐私政策》</RouterLink></span>
          </label>
        </template>

        <div v-if="needsCaptcha" class="captcha-block">
          <label for="public-auth-captcha">图形验证码</label>
          <div class="inline-field captcha-field">
            <input id="public-auth-captcha" v-model.trim="captcha.code" autocomplete="off" maxlength="4" placeholder="请输入图形验证码" />
            <button type="button" class="captcha-button" aria-label="刷新图形验证码" @click="refreshCaptcha">
              <img v-if="captcha.image" :src="captcha.image" alt="图形验证码" />
              <span>换一张</span>
            </button>
          </div>
        </div>

        <p v-if="error" class="form-message error" role="alert">{{ error }}</p>
        <p v-if="success" class="form-message success" role="status">{{ success }}</p>
        <button class="submit-button" type="submit" :disabled="loading">{{ loading ? '正在提交…' : (isRegister ? '注册并登录' : '登录') }}</button>
      </form>

      <footer class="auth-footer">
        <button type="button" @click="switchMode">{{ isRegister ? '已有账号，去登录' : '没有账号，立即注册' }}</button>
        <RouterLink v-if="!isRegister" to="/forgot-password">忘记密码</RouterLink>
      </footer>
    </section>
  </div>
</template>

<script setup>
import { computed, inject, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getLoginCaptcha, login, registerPublic, sendSmsCode } from '@/api/shop'
import { currentBrandLogo, currentBrandName } from '@/utils/brand'
import { normalizeLoginAccountInput, validateLoginAccount } from '@/utils/loginAccount'
import { isValidMainlandPhone, normalizeMainlandPhone } from '@/utils/phone'
import { safeShopRedirect } from '@/utils/safeRedirect'
import { applyShopSession } from '@/utils/shopSession'

const route = useRoute()
const router = useRouter()
const shopBrand = inject('shopBrand', null)
const logoFailed = ref(false)
const brandName = computed(() => shopBrand?.value?.brandName || currentBrandName())
const configuredLogo = computed(() => shopBrand?.value?.logoUrl || currentBrandLogo())
const brandLogo = computed(() => logoFailed.value || !configuredLogo.value ? '/lingqi-logo-mark.png' : configuredLogo.value)
const isRegister = computed(() => route.name === 'Register')
const loginType = ref('password')
const loading = ref(false)
const sendingCode = ref(false)
const error = ref('')
const success = ref('')
const agreed = ref(false)
const confirmPassword = ref('')
const smsCooldown = ref(0)
let cooldownTimer

const loginForm = reactive({ account: '', password: '' })
const smsLoginForm = reactive({ phone: '', smsCode: '' })
const registerForm = reactive({ phone: '', username: '', password: '', smsCode: '' })
const captcha = reactive({ id: '', code: '', image: '' })
const needsCaptcha = true
const smsButtonText = computed(() => smsCooldown.value > 0 ? `${smsCooldown.value}s 后重发` : '获取验证码')

const normalizePhone = (form) => { form.phone = normalizeMainlandPhone(form.phone) }
const normalizeAccount = () => { registerForm.username = normalizeLoginAccountInput(registerForm.username) }

const refreshCaptcha = async () => {
  try {
    const res = await getLoginCaptcha()
    captcha.id = res.data?.captchaId || res.data?.id || ''
    captcha.image = res.data?.image || res.data?.captchaImage || ''
    captcha.code = ''
  } catch (e) {
    error.value = e.message || '图形验证码加载失败，请稍后重试'
  }
}

const startCooldown = () => {
  smsCooldown.value = 60
  window.clearInterval(cooldownTimer)
  cooldownTimer = window.setInterval(() => {
    smsCooldown.value -= 1
    if (smsCooldown.value <= 0) window.clearInterval(cooldownTimer)
  }, 1000)
}

const sendCode = async (phone) => {
  error.value = ''
  success.value = ''
  if (!isValidMainlandPhone(phone)) {
    error.value = '请输入正确的11位手机号'
    return
  }
  if (!captcha.id || !captcha.code) {
    error.value = '请先输入图形验证码'
    return
  }
  sendingCode.value = true
  try {
    await sendSmsCode(phone, 1, { captchaId: captcha.id, captchaCode: captcha.code })
    success.value = '短信验证码已发送'
    startCooldown()
    await refreshCaptcha()
  } catch (e) {
    error.value = e.message || '验证码发送失败'
    await refreshCaptcha()
  } finally {
    sendingCode.value = false
  }
}

const validate = () => {
  if (isRegister.value) {
    if (!isValidMainlandPhone(registerForm.phone)) return '请输入正确的11位手机号'
    const accountError = validateLoginAccount(registerForm.username)
    if (accountError) return accountError
    if (registerForm.password.length < 6 || registerForm.password.length > 32) return '登录密码需为6至32位'
    if (registerForm.password !== confirmPassword.value) return '两次输入的登录密码不一致'
    if (!/^\d{6}$/.test(registerForm.smsCode)) return '请输入6位短信验证码'
    if (!agreed.value) return '请阅读并同意用户服务协议和隐私政策'
    return ''
  }
  if (loginType.value === 'sms') {
    if (!isValidMainlandPhone(smsLoginForm.phone)) return '请输入正确的11位手机号'
    if (!/^\d{6}$/.test(smsLoginForm.smsCode)) return '请输入6位短信验证码'
    return ''
  }
  if (!loginForm.account) return '请输入手机号或登录账号'
  if (!loginForm.password) return '请输入登录密码'
  if (!captcha.id || !captcha.code) return '请输入图形验证码'
  return ''
}

const destination = () => {
  return safeShopRedirect(route.query.redirect, '/profile')
}

const submit = async () => {
  error.value = validate()
  success.value = ''
  if (error.value) return
  loading.value = true
  try {
    let res
    if (isRegister.value) {
      res = await registerPublic({ ...registerForm })
    } else if (loginType.value === 'sms') {
      res = await login({ account: smsLoginForm.phone, smsCode: smsLoginForm.smsCode, loginType: 'sms' })
    } else {
      res = await login({ ...loginForm, captchaId: captcha.id, captchaCode: captcha.code, loginType: 'password' })
    }
    applyShopSession(res.data?.member || res.data)
    await router.replace(destination())
  } catch (e) {
    error.value = e.message || (isRegister.value ? '注册失败，请检查填写内容' : '登录失败，请检查账号信息')
    if (loginType.value === 'password' || isRegister.value) await refreshCaptcha()
  } finally {
    loading.value = false
  }
}

const switchMode = () => router.push({ name: isRegister.value ? 'Login' : 'Register' })
const switchLoginType = (type) => {
  loginType.value = type
  error.value = ''
  success.value = ''
  if (type === 'password') refreshCaptcha()
}

watch(() => route.name, () => {
  error.value = ''
  success.value = ''
  refreshCaptcha()
})
onMounted(refreshCaptcha)
onBeforeUnmount(() => window.clearInterval(cooldownTimer))
</script>

<style scoped>
.public-auth-page{min-height:calc(100vh - 80px);display:grid;place-items:center;padding:28px 16px;background:linear-gradient(145deg,var(--brand-primary-soft,#fff3f5),#f7f8fb 55%)}
.public-auth-card{width:min(480px,100%);padding:28px;background:#fff;border:1px solid #edf0f4;border-radius:24px;box-shadow:0 24px 70px rgba(15,23,42,.1)}
.auth-heading{display:flex;align-items:center;gap:14px;margin-bottom:22px}.auth-heading img{width:54px;height:54px;object-fit:contain;border-radius:14px}.auth-heading h1{margin:0;color:#17202e;font-size:23px}.auth-heading p{margin:6px 0 0;color:#7b8493;font-size:13px}
.login-tabs{display:grid;grid-template-columns:1fr 1fr;margin-bottom:20px;padding:4px;background:#f3f5f8;border-radius:12px}.login-tabs button{height:40px;color:#667085;background:transparent;border:0;border-radius:9px}.login-tabs button.active{color:var(--brand-primary,#e7193f);background:#fff;box-shadow:0 3px 12px rgba(15,23,42,.08);font-weight:700}
.auth-form{display:grid;gap:9px}.auth-form label{margin-top:5px;color:#344054;font-size:13px;font-weight:650}.auth-form input{width:100%;height:46px;padding:0 14px;color:#17202e;background:#fff;border:1px solid #d9dfe8;border-radius:12px;outline:none;box-sizing:border-box}.auth-form input:focus{border-color:var(--brand-primary,#e7193f);box-shadow:0 0 0 3px color-mix(in srgb,var(--brand-primary,#e7193f) 12%,transparent)}
.inline-field{display:grid;grid-template-columns:minmax(0,1fr) 122px;gap:9px}.inline-field>button{height:46px;padding:0 10px;color:var(--brand-primary,#e7193f);background:var(--brand-primary-soft,#fff0f3);border:0;border-radius:12px;font-weight:700}.inline-field>button:disabled{opacity:.55}.captcha-block{display:grid;gap:9px}.captcha-field .captcha-button{display:flex;align-items:center;justify-content:center;gap:6px;padding:3px 8px;color:#667085;background:#f6f7f9}.captcha-button img{max-width:72px;height:36px;object-fit:contain}.captcha-button span{font-size:11px}
.agreement{display:flex;align-items:flex-start;gap:8px;margin:8px 0!important;font-weight:400!important;line-height:1.6}.agreement input{width:18px;height:18px;margin-top:2px;flex:0 0 auto}.agreement a{color:var(--brand-primary,#e7193f)}
.form-message{margin:4px 0;padding:10px 12px;border-radius:10px;font-size:13px}.form-message.error{color:#b42318;background:#fff1f0}.form-message.success{color:#087443;background:#ecfdf3}.submit-button{height:48px;margin-top:8px;color:#fff;background:var(--brand-primary,#e7193f);border:0;border-radius:13px;font-size:15px;font-weight:750}.submit-button:disabled{opacity:.6}
.auth-footer{display:flex;justify-content:space-between;margin-top:18px}.auth-footer button,.auth-footer a{padding:0;color:#667085;background:none;border:0;font-size:13px}
@media(max-width:560px){.public-auth-page{display:block;padding:10px;background:#f6f7f9}.public-auth-card{padding:22px 18px;border-radius:19px}.inline-field{grid-template-columns:minmax(0,1fr) 112px}}
</style>
