<template>
  <div class="page auth-page" :class="{ 'register-page': mode === 'register' }">
    <header class="auth-brand-header">
      <img class="auth-brand-logo" :src="displayBrandLogo" :alt="`${brandName} Logo`" @error="logoLoadFailed = true" />
    </header>

    <div class="section-head">
      <div>
        <h2>{{ mode === 'login' ? '商城账号登录' : '注册商城账号' }}</h2>
        <p v-if="mode === 'login'">登录后可管理地址、订单和售后。</p>
      </div>
      <button v-if="mode === 'register'" type="button" class="register-back-login" @click="switchMode('login')">返回登录</button>
    </div>

    <section class="panel auth-panel" :class="{ 'register-panel': mode === 'register' }">
      <!-- 登录方式切换 -->
      <div v-if="mode === 'login'" class="login-type-row">
        <button class="login-type-btn" :class="{ active: loginType === 'password' }" @click="switchLoginType('password')">密码登录</button>
        <button class="login-type-btn" :class="{ active: loginType === 'sms' }" @click="switchLoginType('sms')">验证码登录</button>
      </div>

      <div class="form-grid">
        <!-- 密码登录 -->
        <template v-if="mode === 'login' && loginType === 'password'">
          <div class="form-item full">
            <label for="login-account">手机号/登录账号</label>
            <input id="login-account" v-model="loginForm.account" name="username" autocomplete="username" class="field" :class="{ 'has-error': loginFieldErrors.account }" placeholder="请输入手机号或登录账号" :aria-invalid="!!loginFieldErrors.account" @input="clearLoginFieldError('account')" />
            <p v-if="loginFieldErrors.account" class="field-error">{{ loginFieldErrors.account }}</p>
          </div>
          <div class="form-item full">
            <label for="login-password">密码</label>
            <input id="login-password" v-model="loginForm.password" name="password" autocomplete="current-password" class="field" :class="{ 'has-error': loginFieldErrors.password }" type="password" placeholder="请输入密码" :aria-invalid="!!loginFieldErrors.password" @input="clearLoginFieldError('password')" />
            <p v-if="loginFieldErrors.password" class="field-error">{{ loginFieldErrors.password }}</p>
          </div>
          <div class="form-item full">
            <label for="login-captcha">图形验证码</label>
            <div class="captcha-row">
              <input id="login-captcha" v-model="loginForm.captchaCode" name="captchaCode" autocomplete="off" class="field" :class="{ 'has-error': loginFieldErrors.captchaCode }" placeholder="请输入图形验证码" maxlength="4" :aria-invalid="!!loginFieldErrors.captchaCode" @input="clearLoginFieldError('captchaCode')" />
              <button type="button" class="captcha-refresh" title="看不清？换一张" aria-label="刷新图形验证码" @click="refreshCaptcha">
                <img :src="captchaImage" class="captcha-image" alt="图形验证码" />
                <span>换一张</span>
              </button>
            </div>
            <p v-if="loginFieldErrors.captchaCode" class="field-error">{{ loginFieldErrors.captchaCode }}</p>
          </div>
        </template>

        <!-- 验证码登录 -->
        <template v-if="mode === 'login' && loginType === 'sms'">
          <div class="form-item full">
            <label for="sms-login-phone">手机号</label>
            <input id="sms-login-phone" v-model="smsForm.phone" name="phone" class="field" :class="{ 'has-error': loginFieldErrors.phone }" placeholder="请输入11位手机号" maxlength="11" inputmode="tel" autocomplete="tel" :aria-invalid="!!loginFieldErrors.phone" @input="handleSmsLoginPhoneInput" />
            <p v-if="loginFieldErrors.phone" class="field-error">{{ loginFieldErrors.phone }}</p>
          </div>
          <div class="form-item full">
            <label for="sms-login-captcha">图形验证码</label>
            <div class="captcha-row">
              <input id="sms-login-captcha" v-model="loginForm.captchaCode" class="field" placeholder="请输入图形验证码" maxlength="4" autocomplete="off" />
              <button type="button" class="captcha-refresh" aria-label="刷新图形验证码" @click="refreshCaptcha">
                <img :src="captchaImage" class="captcha-image" alt="图形验证码" /><span>换一张</span>
              </button>
            </div>
          </div>
          <div class="form-item full">
            <label for="sms-login-code">验证码</label>
            <div class="sms-row">
              <input id="sms-login-code" v-model="smsForm.code" name="smsCode" class="field sms-input" :class="{ 'has-error': loginFieldErrors.code }" placeholder="请输入验证码" maxlength="6" inputmode="numeric" autocomplete="one-time-code" :aria-invalid="!!loginFieldErrors.code" @input="clearLoginFieldError('code')" />
              <button class="btn sms-btn" :disabled="smsCooldown > 0" @click="sendCode">
                {{ smsCooldown > 0 ? `${smsCooldown}s` : '获取验证码' }}
              </button>
            </div>
            <p v-if="loginFieldErrors.code" class="field-error">{{ loginFieldErrors.code }}</p>
          </div>
        </template>

        <!-- 注册 -->
        <template v-if="mode === 'register'">
          <div class="form-item full">
            <label for="register-invite-code">邀请码 <span class="required-mark" aria-hidden="true">*</span></label>
            <input
              id="register-invite-code"
              v-model="registerForm.inviteCode"
              name="inviteCode"
              class="field"
              :class="{ 'has-error': fieldErrors.inviteCode }"
              placeholder="请输入邀请码（必填）"
              maxlength="8"
              autocomplete="off"
              aria-required="true"
              :aria-invalid="!!fieldErrors.inviteCode"
              :disabled="inviteCodeLocked"
              @input="handleInviteCodeInput"
              @blur="loadInviter"
            />
            <p v-if="fieldErrors.inviteCode" class="field-error">{{ fieldErrors.inviteCode }}</p>
          </div>
          <div class="inviter-status-slot" aria-live="polite">
            <div v-if="inviterLoading" class="inviter-info-card checking">
              <span>邀请人</span><strong>正在确认...</strong>
            </div>
            <div v-else-if="inviterInfo" class="inviter-info-card valid">
              <span>邀请人</span><strong>{{ inviterInfo.nickname || '-' }}</strong><small>邀请码：{{ inviterInfo.inviteCode }}</small>
            </div>
            <div v-else-if="inviteError" class="inviter-info-card invalid">
              <span>邀请码</span><strong>{{ inviteError }}</strong>
            </div>
            <div v-else class="inviter-hint">输入完整邀请码后，会自动确认邀请人</div>
          </div>
          <div class="form-item">
            <label for="register-phone">手机号 <span class="required-mark" aria-hidden="true">*</span></label>
            <input
              id="register-phone"
              v-model="registerForm.phone"
              name="phone"
              class="field"
              :class="{ 'has-error': fieldErrors.phone }"
              placeholder="请输入11位手机号"
              maxlength="11"
              inputmode="tel"
              autocomplete="tel"
              aria-required="true"
              :aria-invalid="!!fieldErrors.phone"
              @input="handleRegisterPhoneInput"
              @blur="validateRegisterField('phone')"
            />
            <p v-if="fieldErrors.phone" class="field-error">{{ fieldErrors.phone }}</p>
            <p v-else-if="isValidMainlandPhone(registerForm.phone)" class="field-success">格式正确，请通过短信验证码确认号码可用</p>
          </div>
          <div class="form-item">
            <label for="register-username">登录账号 <span class="required-mark" aria-hidden="true">*</span></label>
            <input
              id="register-username"
              v-model="registerForm.username"
              name="username"
              class="field"
              :class="{ 'has-error': fieldErrors.username }"
              placeholder="4至20位，以字母开头"
              maxlength="20"
              autocomplete="username"
              autocapitalize="none"
              spellcheck="false"
              aria-required="true"
              :aria-invalid="!!fieldErrors.username"
              @input="handleRegisterUsernameInput"
              @blur="validateRegisterField('username')"
            />
            <p v-if="fieldErrors.username" class="field-error">{{ fieldErrors.username }}</p>
          </div>
          <div class="form-item">
            <label for="register-password">登录密码 <span class="required-mark" aria-hidden="true">*</span></label>
            <input
              id="register-password"
              v-model="registerForm.password"
              name="newPassword"
              class="field"
              :class="{ 'has-error': fieldErrors.password }"
              type="password"
              placeholder="请输入6至32位密码"
              minlength="6"
              maxlength="32"
              autocomplete="new-password"
              aria-required="true"
              :aria-invalid="!!fieldErrors.password"
              @input="clearFieldError('password')"
              @blur="validateRegisterField('password')"
            />
            <p v-if="fieldErrors.password" class="field-error">{{ fieldErrors.password }}</p>
          </div>
          <div class="form-item full register-sms-item">
            <label for="register-sms-code">短信验证码 <span class="required-mark" aria-hidden="true">*</span></label>
            <div class="sms-row">
              <input
                id="register-sms-code"
                v-model="registerForm.smsCode"
                name="smsCode"
                class="field sms-input"
                :class="{ 'has-error': fieldErrors.smsCode }"
                placeholder="请输入6位验证码"
                maxlength="6"
                inputmode="numeric"
                autocomplete="one-time-code"
                aria-required="true"
                :aria-invalid="!!fieldErrors.smsCode"
                @input="clearFieldError('smsCode')"
                @blur="validateRegisterField('smsCode')"
              />
              <button type="button" class="btn sms-btn" :disabled="smsCooldown > 0" @click="sendCodeForRegister">
                {{ smsCooldown > 0 ? `${smsCooldown}s` : '获取验证码' }}
              </button>
            </div>
            <p v-if="fieldErrors.smsCode" class="field-error">{{ fieldErrors.smsCode }}</p>
          </div>
          <div class="form-item full">
            <label for="register-captcha">发送短信前验证</label>
            <div class="captcha-row">
              <input id="register-captcha" v-model="loginForm.captchaCode" class="field" placeholder="请输入图形验证码" maxlength="4" autocomplete="off" />
              <button type="button" class="captcha-refresh" aria-label="刷新图形验证码" @click="refreshCaptcha">
                <img :src="captchaImage" class="captcha-image" alt="图形验证码" /><span>换一张</span>
              </button>
            </div>
          </div>
        </template>
      </div>

      <div v-if="mode === 'register'" class="agreement-row">
        <div class="agreement-check" :class="{ checked: agreeTerms, 'has-error': fieldErrors.agreement }">
          <input id="register-agreement" v-model="agreeTerms" name="agreement" class="agreement-native" type="checkbox" :aria-invalid="!!fieldErrors.agreement" @change="agreeTerms && clearFieldError('agreement')" />
          <label for="register-agreement" class="agreement-consent">
            <span class="agreement-box" aria-hidden="true"><Check v-if="agreeTerms" :size="12" /></span>
            <span><span class="required-mark" aria-hidden="true">*</span> 我已阅读并同意</span>
          </label>
          <span class="agreement-links"><RouterLink :to="legalRoute('agreement')">《用户服务协议》</RouterLink>和<RouterLink :to="legalRoute('privacy')">《隐私政策》</RouterLink></span>
        </div>
        <p v-if="fieldErrors.agreement" class="field-error agreement-error">{{ fieldErrors.agreement }}</p>
        <p class="agreement-tip">
          注册仅创建商城账号；下单前可查看<RouterLink :to="legalRoute('after-sale')">《交易与售后规则》</RouterLink>，加入会员推广计划时将另行确认对应规则。
        </p>
      </div>

      <button class="btn primary auth-submit" :disabled="loading" @click="submit">
        {{ loading ? '提交中' : mode === 'login' ? '登录' : '注册并登录' }}
      </button>

      <div v-if="mode === 'login'" class="account-links">
        <button type="button" @click="switchMode('register')">注册新账号</button>
        <span></span>
        <RouterLink to="/forgot-password">忘记密码</RouterLink>
      </div>

    </section>

    <Teleport to="body">
      <Transition name="register-popup">
        <div v-if="registerPopup.message" class="register-popup-mask" role="status" aria-live="assertive">
          <section class="register-popup" :class="registerPopup.type">
            <CircleCheckBig v-if="registerPopup.type === 'success'" :size="30" />
            <CircleAlert v-else :size="30" />
            <strong>{{ registerPopup.message }}</strong>
          </section>
        </div>
      </Transition>
    </Teleport>

    <Transition name="auth-feedback">
      <div v-if="error || success" class="auth-feedback-toast" :class="{ success: !!success }" role="status" aria-live="polite">
        {{ error || success }}
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Check, CircleAlert, CircleCheckBig } from 'lucide-vue-next'
import { getInviterPreview, getLoginCaptcha, login, register, sendSmsCode } from '@/api/shop'
import { isNativeApp } from '@/utils/appEnvironment'
import { isValidMainlandPhone, normalizeMainlandPhone } from '@/utils/phone'
import { normalizeLoginAccountInput, resolveRegistrationErrorField, validateLoginAccount } from '@/utils/loginAccount'
import { isStaleChunkError } from '@/utils/chunkRecovery'
import { applyShopSession } from '@/utils/shopSession'
import { useRegisterDraft } from '@/store/registerDraft'
import { currentBrandLogo, currentBrandName } from '@/utils/brand'
import { hasTeamBusiness, isTeamSurface } from '@/utils/appSurface'

const router = useRouter()
const route = useRoute()
const shopBrand = inject('shopBrand', null)
const logoLoadFailed = ref(false)
const brandName = computed(() => shopBrand?.value?.brandName || currentBrandName())
const configuredBrandLogo = computed(() => shopBrand?.value?.logoUrl || currentBrandLogo())
const displayBrandLogo = computed(() => logoLoadFailed.value || !configuredBrandLogo.value ? '/lingqi-logo-mark.png' : configuredBrandLogo.value)
const mode = ref('login')
const loginType = ref('password') // password | sms
const loading = ref(false)
const error = ref('')
const success = ref('')
const registerPopup = ref({ type: '', message: '' })
const smsCooldown = ref(0)

const loginForm = ref({ account: '', password: '', captchaId: '', captchaCode: '' })
const loginFieldErrors = ref({})
const captchaImage = ref('')
const smsForm = ref({ phone: '', code: '' })
const { registerForm, agreeTerms, clearRegisterDraft } = useRegisterDraft()
const fieldErrors = ref({})
const inviterInfo = ref(null)
const inviterLoading = ref(false)
const inviteError = ref('')
const lastCheckedInviteCode = ref('')
let inviteRequestSequence = 0
let activeInviteRequest = null
let activeInviteRequestCode = ''
let feedbackTimer
let fieldErrorTimer
let registerPopupTimer
let cooldownTimer
const inviteCodeLocked = computed(() => !!(route.query.inviteCode || route.query.code))
const legalRoute = (type) => ({ name: 'Legal', params: { type }, query: { from: 'register' } })

const clearFeedback = ({ clearFields = true } = {}) => {
  window.clearTimeout(feedbackTimer)
  window.clearTimeout(fieldErrorTimer)
  error.value = ''
  success.value = ''
  if (clearFields) {
    loginFieldErrors.value = {}
    fieldErrors.value = {}
  }
  window.clearTimeout(registerPopupTimer)
  registerPopup.value = { type: '', message: '' }
}

const showRegisterPopup = (message, type = 'error', duration = 1800) => {
  window.clearTimeout(registerPopupTimer)
  registerPopup.value = { type, message: String(message || '提交失败') }
  registerPopupTimer = window.setTimeout(() => { registerPopup.value = { type: '', message: '' } }, duration)
}

const normalizeRegisterFeedback = (message, field = '') => {
  const text = String(message || '提交失败')
  if (field === 'smsCode' || /短信验证码/.test(text)) {
    if (/过期|失效|超时/.test(text)) return '短信验证码已过期，请重新获取'
    if (/6位|长度|格式/.test(text)) return '短信验证码应为6位'
    return '短信验证码错误，请重新输入'
  }
  return text
}

const waitForPopup = (duration) => new Promise((resolve) => window.setTimeout(resolve, duration))

const switchMode = (value) => {
  mode.value = value
  clearFeedback()
  if (value === 'login') {
    clearRegisterDraft()
    if (loginType.value === 'password') refreshCaptcha()
  }
}

const switchLoginType = (value) => {
  loginType.value = value
  clearFeedback()
  if (value === 'password' && !captchaImage.value) refreshCaptcha()
}

// 从URL获取邀请码
onMounted(() => {
  if (route.query.notice) success.value = String(route.query.notice).slice(0, 80)
  const urlInviteCode = route.query.inviteCode || route.query.code
  if (urlInviteCode) {
    registerForm.value.inviteCode = urlInviteCode
    mode.value = 'register' // 自动切换到注册模式
    loadInviter()
  } else if (route.name === 'Register' || route.query.mode === 'register') {
    mode.value = 'register'
    if (registerForm.value.inviteCode) loadInviter()
  }
  refreshCaptcha()
})

watch([error, success], ([errorMessage, successMessage]) => {
  window.clearTimeout(feedbackTimer)
  if (!errorMessage && !successMessage) return
  feedbackTimer = window.setTimeout(() => {
    error.value = ''
    success.value = ''
  }, 1800)
})

watch(() => route.fullPath, () => clearFeedback())
watch(configuredBrandLogo, () => { logoLoadFailed.value = false })

onBeforeUnmount(() => {
  clearFeedback()
  window.clearInterval(cooldownTimer)
  window.clearTimeout(registerPopupTimer)
})

const normalizeInviteCode = (value) => value?.trim().toUpperCase() || ''

const handleInviteCodeInput = () => {
  clearFieldError('inviteCode')
  const inviteCode = normalizeInviteCode(registerForm.value.inviteCode)
  if (inviteCode === lastCheckedInviteCode.value) return
  inviteRequestSequence++
  lastCheckedInviteCode.value = ''
  activeInviteRequest = null
  activeInviteRequestCode = ''
  inviterLoading.value = false
  inviterInfo.value = null
  inviteError.value = ''
}

const clearFieldError = (field) => {
  if (fieldErrors.value[field]) delete fieldErrors.value[field]
}

const clearLoginFieldError = (field) => {
  if (loginFieldErrors.value[field]) delete loginFieldErrors.value[field]
}

const scheduleRegisterErrorsClear = () => {
  window.clearTimeout(fieldErrorTimer)
  fieldErrorTimer = window.setTimeout(() => { fieldErrors.value = {} }, 2000)
}

const showLoginFieldError = (field, message) => {
  loginFieldErrors.value = { [field]: message }
  window.clearTimeout(fieldErrorTimer)
  fieldErrorTimer = window.setTimeout(() => { loginFieldErrors.value = {} }, 2000)
}

const handleRegisterPhoneInput = () => {
  registerForm.value.phone = normalizeMainlandPhone(registerForm.value.phone)
  clearFieldError('phone')
  if (registerForm.value.phone.length === 11) validateRegisterField('phone')
}

const handleRegisterUsernameInput = () => {
  registerForm.value.username = normalizeLoginAccountInput(registerForm.value.username)
  clearFieldError('username')
}

const handleSmsLoginPhoneInput = () => {
  smsForm.value.phone = normalizeMainlandPhone(smsForm.value.phone)
  clearLoginFieldError('phone')
}

const validateRegisterField = (field) => {
  const form = registerForm.value
  clearFieldError(field)
  if (field === 'phone' && !isValidMainlandPhone(form.phone)) {
    fieldErrors.value.phone = '请输入正确的11位手机号'
  } else if (field === 'username') {
    const message = validateLoginAccount(form.username)
    if (message) fieldErrors.value.username = message
  } else if (field === 'smsCode' && !/^\d{6}$/.test(form.smsCode?.trim() || '')) {
    fieldErrors.value.smsCode = '短信验证码应为6位'
  } else if (field === 'password') {
    const length = form.password?.length || 0
    if (length < 6 || length > 32) fieldErrors.value.password = '登录密码需为6至32位'
  }
  if (fieldErrors.value[field]) scheduleRegisterErrorsClear()
  return !fieldErrors.value[field]
}

const focusFirstRegisterError = async () => {
  await nextTick()
  const firstInvalid = document.querySelector('.auth-panel .field.has-error, .auth-panel .agreement-check.has-error .agreement-native')
  firstInvalid?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  if (typeof firstInvalid?.focus === 'function') firstInvalid.focus({ preventScroll: true })
}

const validateRegisterForm = () => {
  fieldErrors.value = {}
  const inviteCode = normalizeInviteCode(registerForm.value.inviteCode)
  if (!inviteCode) fieldErrors.value.inviteCode = '请输入邀请码（注册必须有邀请人）'
  else if (!/^[A-Z0-9]{8}$/.test(inviteCode)) fieldErrors.value.inviteCode = '请输入完整的8位邀请码'
  validateRegisterField('phone')
  validateRegisterField('username')
  validateRegisterField('smsCode')
  validateRegisterField('password')
  if (!agreeTerms.value) fieldErrors.value.agreement = '请先阅读并同意用户服务协议和隐私政策'
  if (Object.keys(fieldErrors.value).length) scheduleRegisterErrorsClear()
  return Object.keys(fieldErrors.value).length === 0
}

const loadInviter = async () => {
  const inviteCode = normalizeInviteCode(registerForm.value.inviteCode)
  if (!inviteCode) {
    inviterInfo.value = null
    inviteError.value = ''
    fieldErrors.value.inviteCode = '请输入邀请码（注册必须有邀请人）'
    scheduleRegisterErrorsClear()
    return false
  }
  registerForm.value.inviteCode = inviteCode
  if (!/^[A-Z0-9]{8}$/.test(inviteCode)) {
    inviterInfo.value = null
    inviteError.value = '请输入完整的8位邀请码'
    fieldErrors.value.inviteCode = inviteError.value
    scheduleRegisterErrorsClear()
    lastCheckedInviteCode.value = inviteCode
    return false
  }
  if (inviteCode === lastCheckedInviteCode.value) {
    return !!inviterInfo.value
  }
  if (activeInviteRequest && activeInviteRequestCode === inviteCode) {
    return activeInviteRequest
  }

  const requestSequence = ++inviteRequestSequence
  inviterInfo.value = null
  inviteError.value = ''
  inviterLoading.value = true
  activeInviteRequestCode = inviteCode
  const request = (async () => {
    try {
      const res = await getInviterPreview(inviteCode)
      if (requestSequence !== inviteRequestSequence) return false
      lastCheckedInviteCode.value = inviteCode
      if (!res.data?.valid) {
        inviteError.value = res.data?.message || '邀请码无效，请向邀请人核对'
        fieldErrors.value.inviteCode = inviteError.value
        scheduleRegisterErrorsClear()
        return false
      }
      inviterInfo.value = res.data
      clearFieldError('inviteCode')
      return true
    } catch (e) {
      if (requestSequence !== inviteRequestSequence) return false
      lastCheckedInviteCode.value = inviteCode
      inviteError.value = e.message || '邀请码暂时无法确认，请稍后重试'
      fieldErrors.value.inviteCode = inviteError.value
      scheduleRegisterErrorsClear()
      return false
    } finally {
      if (requestSequence === inviteRequestSequence) inviterLoading.value = false
    }
  })()
  activeInviteRequest = request
  try {
    return await request
  } finally {
    if (activeInviteRequest === request) {
      activeInviteRequest = null
      activeInviteRequestCode = ''
    }
  }
}

const refreshCaptcha = async () => {
  try {
    const res = await getLoginCaptcha()
    loginForm.value.captchaId = res.data.captchaId
    loginForm.value.captchaCode = ''
    captchaImage.value = res.data.image
  } catch (e) {
    error.value = e.message || '图形验证码加载失败'
  }
}

// 发送验证码（登录）
const sendCode = async () => {
  clearFeedback()
  if (!isValidMainlandPhone(smsForm.value.phone)) {
    showLoginFieldError('phone', '请输入正确的11位手机号')
    return
  }
  if (!loginForm.value.captchaId || !loginForm.value.captchaCode) {
    error.value = '请先输入图形验证码'
    return
  }
  try {
    await sendSmsCode(smsForm.value.phone, 2, loginForm.value) // 2=登录
    success.value = '验证码已发送'
    startCooldown()
    await refreshCaptcha()
  } catch (e) {
    error.value = e.message || '发送失败'
    await refreshCaptcha()
  }
}

// 发送验证码（注册）
const sendCodeForRegister = async () => {
  clearFeedback({ clearFields: false })
  if (!validateRegisterField('phone')) {
    await focusFirstRegisterError()
    return
  }
  const inviteCode = normalizeInviteCode(registerForm.value.inviteCode)
  if (!inviteCode || !/^[A-Z0-9]{8}$/.test(inviteCode)) {
    fieldErrors.value.inviteCode = inviteCode ? '请输入完整的8位邀请码' : '请输入邀请码（注册必须有邀请人）'
    scheduleRegisterErrorsClear()
    await focusFirstRegisterError()
    return
  }
  if (!await loadInviter()) {
    await focusFirstRegisterError()
    return
  }
  if (!loginForm.value.captchaId || !loginForm.value.captchaCode) {
    error.value = '请先输入图形验证码'
    return
  }
  try {
    await sendSmsCode(registerForm.value.phone, 1, loginForm.value) // 1=注册
    success.value = '验证码已发送'
    startCooldown()
    await refreshCaptcha()
  } catch (e) {
    if (!await showRegisterServerError(e.message)) error.value = e.message || '发送失败'
    await refreshCaptcha()
  }
}

// 倒计时
const startCooldown = () => {
  window.clearInterval(cooldownTimer)
  smsCooldown.value = 60
  cooldownTimer = window.setInterval(() => {
    smsCooldown.value--
    if (smsCooldown.value <= 0) window.clearInterval(cooldownTimer)
  }, 1000)
}

const showRegisterServerError = async (message) => {
  const text = String(message || '提交失败')
  const field = resolveRegistrationErrorField(text)
  if (!field) return false
  const feedback = normalizeRegisterFeedback(text, field)
  fieldErrors.value = { [field]: feedback }
  showRegisterPopup(feedback)
  scheduleRegisterErrorsClear()
  await focusFirstRegisterError()
  return true
}

const submit = async () => {
  if (loading.value) return
  clearFeedback()

  // 前端校验
  if (mode.value === 'login') {
    if (loginType.value === 'password') {
      if (!loginForm.value.account?.trim()) { showLoginFieldError('account', '请输入手机号或登录账号'); return }
      if (!loginForm.value.password || loginForm.value.password.length < 6) { showLoginFieldError('password', '密码至少6位'); return }
      if (!loginForm.value.captchaCode?.trim()) { showLoginFieldError('captchaCode', '请输入图形验证码'); return }
    } else {
      if (!isValidMainlandPhone(smsForm.value.phone)) { showLoginFieldError('phone', '请输入正确的11位手机号'); return }
      if (!smsForm.value.code || smsForm.value.code.length !== 6) { showLoginFieldError('code', '请输入6位验证码'); return }
    }
  } else {
    if (!validateRegisterForm()) {
      const firstMessage = Object.values(fieldErrors.value)[0] || '请检查注册信息'
      showRegisterPopup(normalizeRegisterFeedback(firstMessage, Object.keys(fieldErrors.value)[0]))
      await focusFirstRegisterError()
      return
    }
    if (!await loadInviter()) {
      showRegisterPopup(fieldErrors.value.inviteCode || inviteError.value || '邀请码验证失败')
      await focusFirstRegisterError()
      return
    }
  }

  loading.value = true
  try {
    const registering = mode.value === 'register'
    let res
    if (!registering) {
      if (loginType.value === 'password') {
        res = await login({ ...loginForm.value, account: loginForm.value.account.trim() })
      } else {
        // 验证码登录
        res = await login({ account: smsForm.value.phone, smsCode: smsForm.value.code, loginType: 'sms' })
      }
    } else {
      res = await register({
        ...registerForm.value,
        phone: registerForm.value.phone.trim(),
        username: registerForm.value.username.trim(),
        smsCode: registerForm.value.smsCode.trim(),
        inviteCode: normalizeInviteCode(registerForm.value.inviteCode)
      })
    }
    applyShopSession(res.data.member)
    if (registering) {
      clearRegisterDraft()
      showRegisterPopup('账号注册成功', 'success', 1200)
      await waitForPopup(850)
      if (hasTeamBusiness) {
        await router.replace('/')
      } else if (isNativeApp) {
        await router.replace('/profile')
      } else {
        await router.replace({ name: 'AppDownload', query: { registered: '1' } })
      }
      return
    }
    const redirect = router.currentRoute.value.query.redirect || (isTeamSurface ? '/' : '/profile')
    await router.push(redirect)
  } catch (e) {
    if (mode.value === 'register' && await showRegisterServerError(e.message)) return
    if (mode.value === 'register') {
      showRegisterPopup(e.message || '注册失败，请稍后重试')
      return
    }
    error.value = isStaleChunkError(e)
      ? '页面资源已更新，正在为您重新加载，请稍候…'
      : (e.message || '提交失败')
    if (mode.value === 'login' && loginType.value === 'password') await refreshCaptcha()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page { position: relative; width:min(560px,calc(100% - 40px)); }
.auth-brand-header { min-height:56px; display:flex; align-items:center; justify-content:center; margin:16px auto 18px; }
.auth-brand-logo { display:block; width:auto; max-width:min(160px,48vw); height:auto; max-height:56px; object-fit:contain; }
.auth-page .section-head { margin: 10px 0 12px; }
.register-back-login { min-height:34px; padding:0 12px; color:#667085; background:#fff; border:1px solid #dfe3e8; border-radius:999px; font-size:12px; font-weight:700; }
.auth-page .auth-panel { border-radius: 14px; box-shadow: 0 10px 28px rgba(24, 32, 42, .06); }
.auth-submit { width: 100%; min-height: 42px; margin-top: 12px; }

.login-type-row {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.login-type-btn {
  flex: 1;
  padding: 8px;
  border: 1px solid var(--line, #dfe7e2);
  border-radius: 8px;
  background: transparent;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.login-type-btn.active {
  border-color: var(--accent, #0f766e);
  color: var(--accent, #0f766e);
  font-weight: 600;
}

.sms-row {
  display: flex;
  gap: 10px;
}

.captcha-row { display: flex; gap: 10px; }
.captcha-refresh { width: 120px; flex: 0 0 120px; padding: 0; border: 0; background: transparent; color: var(--muted, #64748b); cursor: pointer; }
.captcha-image { display: block; width: 120px; height: 44px; border: 1px solid var(--line, #dfe7e2); border-radius: 8px; }
.captcha-refresh span { display: block; margin-top: 2px; font-size: 11px; line-height: 1.2; }

.sms-input {
  flex: 1;
}

.sms-btn {
  white-space: nowrap;
  padding: 0 16px;
  background: var(--accent, #0f766e);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
}

.sms-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.account-links {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 14px;
}

.account-links span {
  width: 1px;
  height: 14px;
  background: var(--line, #dfe7e2);
}

.account-links a,
.account-links button {
  padding: 0;
  color: var(--accent, #0f766e);
  background: transparent;
  border: 0;
  font-size: 13px;
  text-decoration: none;
  cursor: pointer;
}

.account-links a:hover,
.account-links button:hover {
  text-decoration: underline;
}

.required-mark { color: var(--coral); font-weight: 700; }
.optional-mark { margin-left: 4px; color: #9aa1aa; font-size: 11px; font-weight: 400; }
.field.has-error { border-color: var(--coral); box-shadow: 0 0 0 2px color-mix(in srgb, var(--coral) 12%, transparent); }
.field-error { margin: 0; color: var(--coral); font-size: 12px; line-height: 1.45; }
.field-success { margin: 0; color: #0f8a62; font-size: 12px; line-height: 1.45; }
.agreement-error { margin: 7px 0 0 26px; }

.inviter-status-slot { min-height: 38px; grid-column: 1 / -1; }
.inviter-info-card { min-height: 38px; display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: #ecf9f4; border: 1px solid transparent; border-radius: 10px; font-size: 13px; }
.inviter-info-card span { color: var(--muted); font-size: 12px; }
.inviter-info-card strong { font-size: 14px; color: #0f6e50; }
.inviter-info-card small { margin-left: auto; color: var(--muted); font-size: 11px; }
.inviter-info-card.checking { background: #f5f7fa; }
.inviter-info-card.checking strong { color: var(--muted); }
.inviter-info-card.invalid { background: #fff4f4; border-color: #ffd6d6; }
.inviter-info-card.invalid strong { color: var(--coral); font-weight: 500; }
.inviter-hint { min-height: 38px; display: flex; align-items: center; padding: 0 12px; color: var(--muted); font-size: 12px; }

.agreement-row { margin-top: 14px; }
.agreement-check { display: flex; align-items: flex-start; gap: 4px; touch-action: manipulation; -webkit-tap-highlight-color: transparent; font-size: 12px; color: var(--muted); line-height: 1.5; }
.agreement-consent { display:flex; align-items:flex-start; gap:8px; cursor:pointer; user-select:none; }
.agreement-links { margin-left:0; }
.agreement-native { position:absolute; width:1px; height:1px; padding:0; margin:-1px; overflow:hidden; clip:rect(0,0,0,0); white-space:nowrap; border:0; }
.agreement-box { width: 18px; height: 18px; flex-shrink: 0; display: grid; place-items: center; border: 1.5px solid #c9ced4; border-radius: 4px; margin-top: 1px; }
.agreement-check.checked .agreement-box { color: #fff; background: var(--accent, #0f766e); border-color: var(--accent, #0f766e); }
.agreement-check.has-error .agreement-box { border-color: var(--coral); box-shadow: 0 0 0 2px color-mix(in srgb, var(--coral) 12%, transparent); }
.agreement-native:focus-visible + .agreement-consent .agreement-box { outline:3px solid color-mix(in srgb, var(--accent, #0f766e) 35%, transparent); outline-offset:2px; }
.agreement-check a { color: var(--accent, #0f766e); text-decoration: none; }
.agreement-tip { margin: 7px 0 0 26px; color: #9299a3; font-size: 11px; line-height: 1.55; }
.agreement-tip a { color: var(--accent, #0f766e); text-decoration: none; }

.auth-feedback-toast {
  position: fixed;
  z-index: 12020;
  top: max(88px, calc(env(safe-area-inset-top) + 18px));
  left: 50%;
  max-width: calc(100% - 40px);
  padding: 10px 16px;
  color: #fff;
  background: rgba(31, 41, 55, .94);
  border-radius: 999px;
  box-shadow: 0 12px 30px rgba(15, 23, 42, .22);
  font-size: 13px;
  font-weight: 700;
  text-align: center;
  transform: translateX(-50%);
}
.auth-feedback-toast.success { background: rgba(15, 118, 110, .96); }
.auth-feedback-enter-active,
.auth-feedback-leave-active { transition: opacity .18s ease, transform .18s ease; }
.auth-feedback-enter-from,
.auth-feedback-leave-to { opacity: 0; transform: translate(-50%, -7px); }
.register-popup-mask { position:fixed; inset:0; z-index:12040; display:grid; place-items:center; padding:24px; background:rgba(15,23,42,.16); pointer-events:none; }
.register-popup { width:min(300px,calc(100vw - 48px)); min-height:128px; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:13px; padding:22px; color:#b42318; background:#fff; border-radius:20px; box-shadow:0 22px 60px rgba(15,23,42,.22); text-align:center; }
.register-popup.success { color:#08724f; }
.register-popup strong { font-size:16px; line-height:1.45; }
.register-popup-enter-active,.register-popup-leave-active { transition:opacity .18s ease; }
.register-popup-enter-active .register-popup,.register-popup-leave-active .register-popup { transition:transform .18s ease,opacity .18s ease; }
.register-popup-enter-from,.register-popup-leave-to { opacity:0; }
.register-popup-enter-from .register-popup,.register-popup-leave-to .register-popup { opacity:0; transform:translateY(8px) scale(.96); }

@media (max-width: 920px) {
  .auth-page { width: calc(100% - 24px); min-height:100vh; min-height:100dvh; padding:clamp(18px,4.5vh,42px) 0 20px; }
  .auth-brand-header { min-height:50px; margin:0 auto clamp(18px,2.8vh,26px); }
  .auth-brand-logo { max-width:min(148px,44vw); max-height:50px; }
  .auth-page .section-head { margin: 4px 0 9px; }
  .auth-page .section-head { align-items:center; flex-direction:row; }
  .auth-page .section-head h2 { font-size: 22px; }
  .auth-page .section-head p { margin-top: 3px; font-size: 13px; }
  .auth-page .auth-panel { padding: 14px; }
  .register-page .form-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 10px; }
  .register-page .form-item { gap: 4px; min-width: 0; }
  .register-page .form-item.full,
  .register-page .inviter-status-slot { grid-column: 1 / -1; }
  .register-page .form-item label { font-size: 12px; }
  .register-page .field { height: 38px; padding: 0 10px; font-size: 12px; }
  .register-page .sms-row,
  .register-page .captcha-row { gap: 6px; }
  .register-page .sms-btn { min-height: 38px; padding: 0 11px; font-size: 12px; }
  .register-page .inviter-status-slot,
  .register-page .inviter-info-card,
  .register-page .inviter-hint { min-height: 32px; }
  .register-page .inviter-info-card { padding: 6px 10px; }
  .register-page .agreement-row { margin-top: 9px; }
  .register-page .agreement-check { gap: 6px; font-size: 11px; line-height: 1.4; }
  .register-page .agreement-box { width: 17px; height: 17px; }
  .register-page .agreement-tip { margin: 4px 0 0 23px; font-size: 10px; line-height: 1.35; }
  .register-page .field-error,
  .register-page .field-success { font-size: 10px; line-height: 1.25; }
  .register-page .auth-submit { min-height: 40px; margin-top: 9px; }
  .register-page { padding-top:clamp(14px,3vh,24px); }
  .register-page .auth-brand-header { min-height:44px; margin:0 auto 12px; }
  .register-page .auth-brand-logo { max-width:min(132px,40vw); max-height:44px; }
}
@media (max-width: 920px) and (max-height: 700px) {
  .auth-page { padding-top:10px; }
  .auth-brand-header { min-height:40px; margin-bottom:10px; }
  .auth-brand-logo { max-height:40px; }
  .auth-page .section-head h2 { font-size:20px; }
  .auth-page .auth-panel { padding:12px; }
  .login-type-row { margin-top:8px; }
  .form-grid { gap:10px; }
  .auth-submit { margin-top:10px; }
  .account-links { margin-top:10px; }
  .register-page { padding-top:6px; }
  .register-page .auth-brand-header { min-height:34px; margin-bottom:6px; }
  .register-page .auth-brand-logo { max-height:34px; }
}
@media (max-width: 380px), (max-height: 600px) {
  .auth-page { width:calc(100% - 18px); padding-top:6px; }
  .auth-brand-header { min-height:34px; margin-bottom:7px; }
  .auth-brand-logo { max-height:34px; }
  .auth-page .section-head { margin-bottom:7px; }
  .auth-page .section-head p { font-size:12px; }
  .auth-page .auth-panel { padding:10px; }
  .login-type-btn { padding:7px; }
  .form-grid { gap:8px; }
  .field { height:38px; }
  .captcha-image { height:38px; }
  .auth-submit { min-height:40px; margin-top:8px; }
  .account-links { margin-top:8px; }
  .register-page { padding-top:3px; }
  .register-page .auth-brand-header { min-height:28px; margin-bottom:3px; }
  .register-page .auth-brand-logo { max-height:28px; }
}
</style>
