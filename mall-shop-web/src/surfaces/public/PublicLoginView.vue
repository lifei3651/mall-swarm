<template>
  <div class="public-auth-page">
    <section class="public-auth-card">
      <header class="auth-heading">
        <img :src="brandLogo" :alt="`${brandName} Logo`" @error="logoFailed = true" />
        <div>
          <h1>{{ isRegister ? '注册商城账号' : '商城账号登录' }}</h1>
          <p v-if="isRegister">注册后即可购物、查询订单和申请售后</p>
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
            <button type="button" :disabled="smsCooldown > 0 || sendingCode" @click="sendLoginCode">{{ smsButtonText }}</button>
          </div>
        </template>

        <template v-if="isRegister">
          <label for="public-register-phone">手机号</label>
          <input id="public-register-phone" v-model="registerForm.phone" inputmode="tel" autocomplete="tel" maxlength="11" placeholder="请输入11位手机号" @input="normalizePhone(registerForm)" />
          <label for="public-register-account">登录账号</label>
          <input id="public-register-account" v-model="registerForm.username" autocomplete="username" maxlength="20" placeholder="4至20位，以英文字母开头" @input="normalizeAccount" />
          <label for="public-register-password">登录密码</label>
          <input id="public-register-password" v-model="registerForm.password" type="password" autocomplete="new-password" minlength="10" maxlength="32" placeholder="请输入10至32位密码" />
          <label for="public-register-confirm">确认登录密码</label>
          <input id="public-register-confirm" v-model="confirmPassword" type="password" autocomplete="new-password" minlength="10" maxlength="32" placeholder="请再次输入登录密码" />
          <label for="public-register-invite">邀请码 <span class="optional-mark">选填</span></label>
          <div class="inline-field invite-field">
            <input
              id="public-register-invite"
              v-model="registerForm.inviteCode"
              name="inviteCode"
              autocomplete="off"
              autocapitalize="characters"
              maxlength="8"
              placeholder="请输入8位邀请码"
              :disabled="inviteCodeLocked"
              aria-describedby="public-register-invite-help"
              @input="handleInviteCodeInput"
            />
            <button type="button" :disabled="inviteCodeLocked || inviterLoading" @click="loadInviter">
              {{ inviterLoading ? '核对中…' : inviterInfo ? '已核对' : inviteCodeLocked ? '扫码带入' : '核对邀请人' }}
            </button>
          </div>
          <p id="public-register-invite-help" class="field-help">
            {{ inviteHelpText }}
          </p>
          <section v-if="showInviterCard" class="inviter-card" :class="{ invalid: inviteError }" aria-live="polite">
            <div>
              <span>邀请人昵称</span>
              <strong v-if="inviterLoading">正在核对…</strong>
              <strong v-else-if="inviterInfo">{{ inviterInfo.nickname || '商城会员' }}</strong>
              <strong v-else>邀请码暂不可用</strong>
            </div>
            <p v-if="inviteError">{{ inviteError }}</p>
          </section>
          <label for="public-register-sms">短信验证码</label>
          <div class="inline-field">
            <input id="public-register-sms" v-model.trim="registerForm.smsCode" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="请输入6位验证码" />
            <button type="button" :disabled="smsCooldown > 0 || sendingCode" @click="sendRegistrationCode">{{ smsButtonText }}</button>
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
          <p v-if="isRegister" class="field-help">可与短信验证码任意顺序填写，提交注册时统一校验</p>
        </div>

        <p v-if="error" class="form-message error" role="alert">{{ error }}</p>
        <p v-if="success" class="form-message success" role="status">{{ success }}</p>
        <button class="submit-button" type="submit" :disabled="loading || inviterLoading">{{ submitButtonText }}</button>
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
import { getInviterPreview, getLoginCaptcha, login, registerPublic, sendLoginSmsCode, sendSmsCode } from '@/api/shop'
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
const inviterLoading = ref(false)
const inviterInfo = ref(null)
const inviteError = ref('')
const agreed = ref(false)
const confirmPassword = ref('')
const smsCooldown = ref(0)
let cooldownTimer
let inviteRequestSequence = 0

const loginForm = reactive({ account: '', password: '' })
const smsLoginForm = reactive({ phone: '', smsCode: '' })
const registerForm = reactive({ phone: '', username: '', password: '', smsCode: '', inviteCode: '' })
const captcha = reactive({ id: '', code: '', image: '' })
const needsCaptcha = computed(() => isRegister.value || loginType.value === 'password')
const smsButtonText = computed(() => smsCooldown.value > 0 ? `${smsCooldown.value}s 后重发` : '获取验证码')
const inviteCodeFromUrl = computed(() => {
  const value = route.query.inviteCode || route.query.code
  return typeof value === 'string' ? value.trim().toUpperCase() : ''
})
const inviteCodeLocked = computed(() => !!inviteCodeFromUrl.value)
const normalizedInviteCode = computed(() => String(registerForm.inviteCode || '').trim().toUpperCase())
const hasInviteCode = computed(() => !!normalizedInviteCode.value)
const showInviterCard = computed(() => hasInviteCode.value
  && (inviteCodeLocked.value || inviterLoading.value || !!inviterInfo.value || !!inviteError.value))
const inviteHelpText = computed(() => {
  if (inviterInfo.value) return '邀请人昵称已确认'
  if (hasInviteCode.value) return '请核对邀请人昵称后再注册'
  return '普通购物可不填邀请码'
})
const submitButtonText = computed(() => {
  if (loading.value) return '正在提交…'
  if (!isRegister.value) return '登录'
  return '注册并登录'
})

const normalizePhone = (form) => { form.phone = normalizeMainlandPhone(form.phone) }
const normalizeAccount = () => { registerForm.username = normalizeLoginAccountInput(registerForm.username) }
const handleInviteCodeInput = () => {
  registerForm.inviteCode = String(registerForm.inviteCode || '').replace(/\s+/g, '').toUpperCase().slice(0, 8)
  inviteRequestSequence += 1
  inviterLoading.value = false
  inviterInfo.value = null
  inviteError.value = ''
}

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

const resetFeedback = () => {
  error.value = ''
  success.value = ''
}

const loadInviter = async () => {
  const inviteCode = normalizedInviteCode.value
  inviterInfo.value = null
  inviteError.value = ''
  if (!inviteCode) return true
  registerForm.inviteCode = inviteCode
  if (!/^[A-Z0-9]{8}$/.test(inviteCode)) {
    inviteError.value = inviteCodeLocked.value
      ? '邀请链接不完整，请向邀请人重新获取二维码'
      : '请输入完整的8位邀请码'
    return false
  }
  const requestSequence = ++inviteRequestSequence
  inviterLoading.value = true
  try {
    const res = await getInviterPreview(inviteCode)
    if (requestSequence !== inviteRequestSequence) return false
    if (!res.data?.valid) {
      inviteError.value = res.data?.message || '邀请码无效，请向邀请人核对'
      return false
    }
    inviterInfo.value = res.data
    return true
  } catch (e) {
    if (requestSequence !== inviteRequestSequence) return false
    inviteError.value = e.message || '邀请人暂时无法核对，请稍后重试'
    return false
  } finally {
    if (requestSequence === inviteRequestSequence) inviterLoading.value = false
  }
}

const sendLoginCode = async () => {
  resetFeedback()
  if (!isValidMainlandPhone(smsLoginForm.phone)) {
    error.value = '请输入正确的11位手机号'
    return
  }
  sendingCode.value = true
  try {
    await sendLoginSmsCode(smsLoginForm.phone)
    success.value = '短信验证码已发送'
    startCooldown()
  } catch (e) {
    error.value = e.message || '验证码发送失败'
  } finally {
    sendingCode.value = false
  }
}

const sendRegistrationCode = async () => {
  resetFeedback()
  if (!isValidMainlandPhone(registerForm.phone)) {
    error.value = '请输入正确的11位手机号'
    return
  }
  sendingCode.value = true
  try {
    await sendSmsCode(registerForm.phone, 1)
    success.value = '短信验证码已发送'
    startCooldown()
  } catch (e) {
    error.value = e.message || '验证码发送失败'
  } finally {
    sendingCode.value = false
  }
}

const validate = () => {
  if (isRegister.value) {
    if (!isValidMainlandPhone(registerForm.phone)) return '请输入正确的11位手机号'
    const accountError = validateLoginAccount(registerForm.username)
    if (accountError) return accountError
    if (registerForm.password.length < 10 || registerForm.password.length > 32) return '登录密码需为10至32位'
    if (registerForm.password !== confirmPassword.value) return '两次输入的登录密码不一致'
    if (!/^\d{6}$/.test(registerForm.smsCode)) return '请输入6位短信验证码'
    if (!captcha.id || !/^[A-Za-z0-9]{4}$/.test(captcha.code)) return '请输入4位图形验证码'
    if (!agreed.value) return '请阅读并同意用户服务协议和隐私政策'
    if (hasInviteCode.value && !inviterInfo.value) return inviteError.value || '请先确认邀请人信息'
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

const destination = () => safeShopRedirect(route.query.redirect, '/profile')

const submit = async () => {
  if (isRegister.value && hasInviteCode.value && !inviterInfo.value) await loadInviter()
  error.value = validate()
  success.value = ''
  if (error.value) return
  loading.value = true
  try {
    let res
    if (isRegister.value) {
      res = await registerPublic({
        ...registerForm,
        inviteCode: hasInviteCode.value ? normalizedInviteCode.value : '',
        captchaId: captcha.id,
        captchaCode: captcha.code,
      })
    } else if (loginType.value === 'sms') {
      res = await login({ account: smsLoginForm.phone, smsCode: smsLoginForm.smsCode, loginType: 'sms' })
    } else {
      res = await login({ ...loginForm, captchaId: captcha.id, captchaCode: captcha.code, loginType: 'password' })
    }
    applyShopSession(res.data?.member || res.data)
    // 注册完成后不继承旧登录页的 redirect，固定进入可购物的公开商城首页。
    if (isRegister.value) await router.replace({ name: 'Home' })
    else await router.replace(destination())
  } catch (e) {
    error.value = e.message || (isRegister.value ? '注册失败，请检查填写内容' : '登录失败，请检查账号信息')
    if (loginType.value === 'password' || isRegister.value) await refreshCaptcha()
  } finally {
    loading.value = false
  }
}

const switchMode = () => router.push({ name: isRegister.value ? 'Login' : 'Register', query: route.query })
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
watch(inviteCodeFromUrl, async (inviteCode) => {
  registerForm.inviteCode = inviteCode
  handleInviteCodeInput()
  if (inviteCode) await loadInviter()
}, { immediate: true })
onMounted(refreshCaptcha)
onBeforeUnmount(() => window.clearInterval(cooldownTimer))
</script>

<style scoped>
.public-auth-page{min-height:calc(100vh - 80px);display:grid;place-items:center;padding:28px 16px;background:linear-gradient(145deg,var(--brand-primary-soft,#fff3f5),#f7f8fb 55%)}
.public-auth-card{width:min(480px,100%);padding:28px;background:#fff;border:1px solid #edf0f4;border-radius:24px;box-shadow:0 24px 70px rgba(15,23,42,.1)}
.auth-heading{display:flex;align-items:center;gap:14px;margin-bottom:22px}.auth-heading img{width:54px;height:54px;object-fit:contain;border-radius:14px}.auth-heading h1{margin:0;color:#17202e;font-size:23px}.auth-heading p{margin:6px 0 0;color:#7b8493;font-size:13px}
.inviter-card{display:grid;gap:7px;margin:2px 0 4px;padding:14px 16px;color:#475467;background:#f8fafc;border:1px solid #d8e2ef;border-radius:14px}.inviter-card>div{display:flex;align-items:center;justify-content:space-between;gap:12px}.inviter-card span,.inviter-card small{color:#667085;font-size:12px}.inviter-card strong{color:#17202e}.inviter-card p{margin:0;color:#667085;font-size:12px;line-height:1.6}.inviter-card.invalid{background:#fff7f6;border-color:#f3c5c0}.inviter-card.invalid strong,.inviter-card.invalid p{color:#b42318}
.login-tabs{display:grid;grid-template-columns:1fr 1fr;margin-bottom:20px;padding:4px;background:#f3f5f8;border-radius:12px}.login-tabs button{height:40px;color:#667085;background:transparent;border:0;border-radius:9px}.login-tabs button.active{color:var(--brand-primary,#e7193f);background:#fff;box-shadow:0 3px 12px rgba(15,23,42,.08);font-weight:700}
.auth-form{display:grid;gap:9px}.auth-form label{margin-top:5px;color:#344054;font-size:13px;font-weight:650}.auth-form input{width:100%;height:46px;padding:0 14px;color:#17202e;background:#fff;border:1px solid #d9dfe8;border-radius:12px;outline:none;box-sizing:border-box}.auth-form input:focus{border-color:var(--brand-primary,#e7193f);box-shadow:0 0 0 3px color-mix(in srgb,var(--brand-primary,#e7193f) 12%,transparent)}
.inline-field{display:grid;grid-template-columns:minmax(0,1fr) 122px;gap:9px}.inline-field>button{height:46px;padding:0 10px;color:var(--brand-primary,#e7193f);background:var(--brand-primary-soft,#fff0f3);border:0;border-radius:12px;font-weight:700}.inline-field>button:disabled{opacity:.55}.captcha-block{display:grid;gap:9px}.captcha-field .captcha-button{display:flex;align-items:center;justify-content:center;gap:6px;padding:3px 8px;color:#667085;background:#f6f7f9}.captcha-button img{max-width:72px;height:36px;object-fit:contain}.captcha-button span{font-size:11px}
.optional-mark{margin-left:4px;color:#98a2b3;font-size:11px;font-weight:500}.field-help{margin:-3px 0 2px;color:#667085;font-size:12px;line-height:1.5}.invite-field>button{font-size:12px}
.agreement{display:flex;align-items:flex-start;gap:8px;margin:8px 0!important;font-weight:400!important;line-height:1.6}.agreement input{width:18px;height:18px;margin-top:2px;flex:0 0 auto}.agreement a{color:var(--brand-primary,#e7193f)}
.form-message{margin:4px 0;padding:10px 12px;border-radius:10px;font-size:13px}.form-message.error{color:#b42318;background:#fff1f0}.form-message.success{color:#087443;background:#ecfdf3}.submit-button{height:48px;margin-top:8px;color:#fff;background:var(--brand-primary,#e7193f);border:0;border-radius:13px;font-size:15px;font-weight:750}.submit-button:disabled{opacity:.6}
.auth-footer{display:flex;justify-content:space-between;margin-top:18px}.auth-footer button,.auth-footer a{padding:0;color:#667085;background:none;border:0;font-size:13px}
@media(max-width:560px){.public-auth-page{display:block;padding:10px;background:#f6f7f9}.public-auth-card{padding:22px 18px;border-radius:19px}.inline-field{grid-template-columns:minmax(0,1fr) 112px}}
</style>
