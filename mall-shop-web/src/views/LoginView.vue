<template>
  <div class="page">
    <div class="section-head">
      <div>
        <h2>{{ mode === 'login' ? '商城账号登录' : '注册商城账号' }}</h2>
        <p v-if="mode === 'login'">登录后可管理地址、订单和售后。</p>
      </div>
    </div>

    <section class="panel auth-panel">
      <!-- 登录方式切换 -->
      <div v-if="mode === 'login'" class="login-type-row">
        <button class="login-type-btn" :class="{ active: loginType === 'password' }" @click="loginType = 'password'">密码登录</button>
        <button class="login-type-btn" :class="{ active: loginType === 'sms' }" @click="loginType = 'sms'">验证码登录</button>
      </div>

      <div class="form-grid">
        <!-- 密码登录 -->
        <template v-if="mode === 'login' && loginType === 'password'">
          <div class="form-item full">
            <label>手机号/用户名</label>
            <input v-model="loginForm.account" class="field" placeholder="请输入手机号或用户名" />
          </div>
          <div class="form-item full">
            <label>密码</label>
            <input v-model="loginForm.password" class="field" type="password" placeholder="请输入密码" />
          </div>
          <div class="form-item full">
            <label>图形验证码</label>
            <div class="captcha-row">
              <input v-model="loginForm.captchaCode" class="field" placeholder="请输入图形验证码" maxlength="4" />
              <img :src="captchaImage" class="captcha-image" alt="图形验证码" title="点击刷新" @click="refreshCaptcha" />
            </div>
          </div>
        </template>

        <!-- 验证码登录 -->
        <template v-if="mode === 'login' && loginType === 'sms'">
          <div class="form-item full">
            <label>手机号</label>
            <input v-model="smsForm.phone" class="field" placeholder="请输入11位手机号" maxlength="11" inputmode="tel" autocomplete="tel" @input="handleSmsLoginPhoneInput" />
          </div>
          <div class="form-item full">
            <label>验证码</label>
            <div class="sms-row">
              <input v-model="smsForm.code" class="field sms-input" placeholder="请输入验证码" maxlength="6" />
              <button class="btn sms-btn" :disabled="smsCooldown > 0" @click="sendCode">
                {{ smsCooldown > 0 ? `${smsCooldown}s` : '获取验证码' }}
              </button>
            </div>
          </div>
        </template>

        <!-- 注册 -->
        <template v-if="mode === 'register'">
          <div class="form-item full">
            <label>邀请码 <span class="required-mark" aria-hidden="true">*</span></label>
            <input
              v-model="registerForm.inviteCode"
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
            <label>手机号 <span class="required-mark" aria-hidden="true">*</span></label>
            <input
              v-model="registerForm.phone"
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
            <label>用户名 <span class="required-mark" aria-hidden="true">*</span></label>
            <input
              v-model="registerForm.username"
              class="field"
              :class="{ 'has-error': fieldErrors.username }"
              placeholder="请输入2至64个字符"
              maxlength="64"
              autocomplete="username"
              aria-required="true"
              :aria-invalid="!!fieldErrors.username"
              @input="clearFieldError('username')"
              @blur="validateRegisterField('username')"
            />
            <p v-if="fieldErrors.username" class="field-error">{{ fieldErrors.username }}</p>
          </div>
          <div class="form-item full">
            <label>昵称 <span class="optional-mark">选填</span></label>
            <input
              v-model="registerForm.nickname"
              class="field"
              :class="{ 'has-error': fieldErrors.nickname }"
              placeholder="不填写时默认使用用户名"
              maxlength="64"
              autocomplete="nickname"
              :aria-invalid="!!fieldErrors.nickname"
              @input="clearFieldError('nickname')"
              @blur="validateRegisterField('nickname')"
            />
            <p v-if="fieldErrors.nickname" class="field-error">{{ fieldErrors.nickname }}</p>
          </div>
          <div class="form-item full">
            <label>短信验证码 <span class="required-mark" aria-hidden="true">*</span></label>
            <div class="sms-row">
              <input
                v-model="registerForm.smsCode"
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
            <label>登录密码 <span class="required-mark" aria-hidden="true">*</span></label>
            <input
              v-model="registerForm.password"
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
        </template>
      </div>

      <div v-if="mode === 'register'" class="agreement-row">
        <label class="agreement-check" :class="{ checked: agreeTerms, 'has-error': fieldErrors.agreement }" @click="toggleAgreement">
          <span><Check v-if="agreeTerms" :size="12" /></span>
          <span><span class="required-mark" aria-hidden="true">*</span> 我已阅读并同意 <RouterLink :to="legalRoute('agreement')" @click.stop>《用户服务协议》</RouterLink>和<RouterLink :to="legalRoute('privacy')" @click.stop>《隐私政策》</RouterLink></span>
        </label>
        <p v-if="fieldErrors.agreement" class="field-error agreement-error">{{ fieldErrors.agreement }}</p>
        <p class="agreement-tip">
          注册仅创建商城账号；下单前可查看<RouterLink :to="legalRoute('after-sale')">《交易与售后规则》</RouterLink>，加入会员推广计划时将另行确认对应规则。
        </p>
      </div>

      <button class="btn primary" style="width: 100%; margin-top: 14px" :disabled="loading" @click="submit">
        {{ loading ? '提交中' : mode === 'login' ? '登录' : '注册并登录' }}
      </button>

      <div v-if="mode === 'login'" class="account-links">
        <button type="button" @click="switchMode('register')">注册新账号</button>
        <span></span>
        <RouterLink to="/forgot-password">忘记密码</RouterLink>
      </div>
      <div v-else class="account-links single-link">
        <button type="button" @click="switchMode('login')">已有账号，返回登录</button>
      </div>

      <p v-if="error" style="color: var(--coral); line-height: 1.6">{{ error }}</p>
      <p v-if="success" style="color: var(--teal, #0f766e); line-height: 1.6">{{ success }}</p>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Check } from 'lucide-vue-next'
import { getInviterPreview, getLoginCaptcha, login, register, sendSmsCode } from '@/api/shop'
import { isNativeApp } from '@/utils/appEnvironment'
import { isValidMainlandPhone, normalizeMainlandPhone } from '@/utils/phone'
import { isStaleChunkError } from '@/utils/chunkRecovery'
import { applyShopSession } from '@/utils/shopSession'
import { useRegisterDraft } from '@/store/registerDraft'

const router = useRouter()
const route = useRoute()
const mode = ref('login')
const loginType = ref('password') // password | sms
const loading = ref(false)
const error = ref('')
const success = ref('')
const smsCooldown = ref(0)

const loginForm = ref({ account: '', password: '', captchaId: '', captchaCode: '' })
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
const inviteCodeLocked = computed(() => !!(route.query.inviteCode || route.query.code))
const legalRoute = (type) => ({ name: 'Legal', params: { type }, query: { from: 'register' } })

const switchMode = (value) => {
  mode.value = value
  error.value = ''
  success.value = ''
  if (value === 'login') {
    clearRegisterDraft()
    if (loginType.value === 'password') refreshCaptcha()
  }
}

// 从URL获取邀请码
onMounted(() => {
  const urlInviteCode = route.query.inviteCode || route.query.code
  if (urlInviteCode) {
    registerForm.value.inviteCode = urlInviteCode
    mode.value = 'register' // 自动切换到注册模式
    loadInviter()
  } else if (route.name === 'Register' || route.query.mode === 'register') {
    mode.value = 'register'
    if (registerForm.value.inviteCode) loadInviter()
  }
  if (mode.value === 'login') refreshCaptcha()
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

const handleRegisterPhoneInput = () => {
  registerForm.value.phone = normalizeMainlandPhone(registerForm.value.phone)
  clearFieldError('phone')
  if (registerForm.value.phone.length === 11) validateRegisterField('phone')
}

const handleSmsLoginPhoneInput = () => {
  smsForm.value.phone = normalizeMainlandPhone(smsForm.value.phone)
}

const validateRegisterField = (field) => {
  const form = registerForm.value
  clearFieldError(field)
  if (field === 'phone' && !isValidMainlandPhone(form.phone)) {
    fieldErrors.value.phone = '请输入正确的11位手机号'
  } else if (field === 'username') {
    const username = form.username?.trim() || ''
    if (!username) fieldErrors.value.username = '请输入用户名'
    else if (username.length < 2 || username.length > 64) fieldErrors.value.username = '用户名需为2至64个字符'
  } else if (field === 'nickname' && (form.nickname?.trim().length || 0) > 64) {
    fieldErrors.value.nickname = '昵称最多64个字符'
  } else if (field === 'smsCode' && !/^\d{6}$/.test(form.smsCode?.trim() || '')) {
    fieldErrors.value.smsCode = '请输入6位短信验证码'
  } else if (field === 'password') {
    const length = form.password?.length || 0
    if (length < 6 || length > 32) fieldErrors.value.password = '登录密码需为6至32位'
  }
  return !fieldErrors.value[field]
}

const toggleAgreement = () => {
  agreeTerms.value = !agreeTerms.value
  if (agreeTerms.value) clearFieldError('agreement')
}

const focusFirstRegisterError = async () => {
  await nextTick()
  const firstInvalid = document.querySelector('.auth-panel .field.has-error, .auth-panel .agreement-check.has-error')
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
  validateRegisterField('nickname')
  validateRegisterField('smsCode')
  validateRegisterField('password')
  if (!agreeTerms.value) fieldErrors.value.agreement = '请先阅读并同意用户服务协议和隐私政策'
  return Object.keys(fieldErrors.value).length === 0
}

const loadInviter = async () => {
  const inviteCode = normalizeInviteCode(registerForm.value.inviteCode)
  if (!inviteCode) {
    inviterInfo.value = null
    inviteError.value = ''
    fieldErrors.value.inviteCode = '请输入邀请码（注册必须有邀请人）'
    return false
  }
  registerForm.value.inviteCode = inviteCode
  if (!/^[A-Z0-9]{8}$/.test(inviteCode)) {
    inviterInfo.value = null
    inviteError.value = '请输入完整的8位邀请码'
    fieldErrors.value.inviteCode = inviteError.value
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
  error.value = ''
  if (!isValidMainlandPhone(smsForm.value.phone)) {
    error.value = '请输入正确的11位手机号'
    return
  }
  try {
    await sendSmsCode(smsForm.value.phone, 2) // 2=登录
    success.value = '验证码已发送'
    startCooldown()
  } catch (e) {
    error.value = e.message || '发送失败'
  }
}

// 发送验证码（注册）
const sendCodeForRegister = async () => {
  error.value = ''
  success.value = ''
  if (!validateRegisterField('phone')) {
    error.value = fieldErrors.value.phone
    await focusFirstRegisterError()
    return
  }
  const inviteCode = normalizeInviteCode(registerForm.value.inviteCode)
  if (!inviteCode || !/^[A-Z0-9]{8}$/.test(inviteCode)) {
    fieldErrors.value.inviteCode = inviteCode ? '请输入完整的8位邀请码' : '请输入邀请码（注册必须有邀请人）'
    error.value = '请先填写并确认有效邀请码'
    await focusFirstRegisterError()
    return
  }
  if (!await loadInviter()) {
    error.value = fieldErrors.value.inviteCode || '请先填写并确认有效邀请码'
    await focusFirstRegisterError()
    return
  }
  try {
    await sendSmsCode(registerForm.value.phone, 1) // 1=注册
    success.value = '验证码已发送'
    startCooldown()
  } catch (e) {
    error.value = e.message || '发送失败'
  }
}

// 倒计时
const startCooldown = () => {
  smsCooldown.value = 60
  const timer = setInterval(() => {
    smsCooldown.value--
    if (smsCooldown.value <= 0) clearInterval(timer)
  }, 1000)
}

const submit = async () => {
  error.value = ''
  success.value = ''

  // 前端校验
  if (mode.value === 'login') {
    if (loginType.value === 'password') {
      if (!loginForm.value.account?.trim()) { error.value = '请输入手机号或用户名'; return }
      if (!loginForm.value.password || loginForm.value.password.length < 6) { error.value = '密码至少6位'; return }
      if (!loginForm.value.captchaCode?.trim()) { error.value = '请输入图形验证码'; return }
    } else {
      if (!isValidMainlandPhone(smsForm.value.phone)) { error.value = '请输入正确的11位手机号'; return }
      if (!smsForm.value.code || smsForm.value.code.length !== 6) { error.value = '请输入6位验证码'; return }
    }
  } else {
    if (!validateRegisterForm()) {
      error.value = '请完善标红的必填信息'
      await focusFirstRegisterError()
      return
    }
    if (!await loadInviter()) {
      error.value = fieldErrors.value.inviteCode || '请先填写并确认有效邀请码'
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
        nickname: registerForm.value.nickname.trim(),
        smsCode: registerForm.value.smsCode.trim(),
        inviteCode: normalizeInviteCode(registerForm.value.inviteCode)
      })
    }
    applyShopSession(res.data.token, res.data.member)
    if (registering) {
      clearRegisterDraft()
      if (isNativeApp) {
        await router.replace('/profile')
      } else {
        await router.replace({ name: 'AppDownload', query: { registered: '1' } })
      }
      return
    }
    const redirect = router.currentRoute.value.query.redirect || '/profile'
    await router.push(redirect)
  } catch (e) {
    error.value = isStaleChunkError(e)
      ? '页面资源已更新，正在为您重新加载，请稍候…'
      : (e.message || '提交失败')
    if (loginType.value === 'password') await refreshCaptcha()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
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
.captcha-image { width: 120px; height: 44px; flex: 0 0 120px; cursor: pointer; border: 1px solid var(--line, #dfe7e2); border-radius: 8px; }

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

.single-link { justify-content: center; }

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
.agreement-check { display: flex; align-items: flex-start; gap: 8px; cursor: pointer; touch-action: manipulation; -webkit-tap-highlight-color: transparent; user-select: none; font-size: 12px; color: var(--muted); line-height: 1.5; }
.agreement-check > span:first-child { width: 18px; height: 18px; flex-shrink: 0; display: grid; place-items: center; border: 1.5px solid #c9ced4; border-radius: 4px; margin-top: 1px; }
.agreement-check.checked > span:first-child { color: #fff; background: var(--accent, #0f766e); border-color: var(--accent, #0f766e); }
.agreement-check.has-error > span:first-child { border-color: var(--coral); box-shadow: 0 0 0 2px color-mix(in srgb, var(--coral) 12%, transparent); }
.agreement-check a { color: var(--accent, #0f766e); text-decoration: none; }
.agreement-tip { margin: 7px 0 0 26px; color: #9299a3; font-size: 11px; line-height: 1.55; }
.agreement-tip a { color: var(--accent, #0f766e); text-decoration: none; }
</style>
