<template>
  <div class="page sub-page settings-page">
    <header class="sub-page-head">
      <button type="button" aria-label="返回" @click="router.back()"><ArrowLeft :size="22" /></button>
      <h2>账号设置</h2><span></span>
    </header>

    <section class="settings-card" aria-label="账号资料">
      <div class="settings-row static-row">
        <div><strong>登录账号</strong><small>用于登录，设置后不可自行修改</small></div>
        <span>{{ canSetupAccount ? '未设置' : member.username }}</span>
      </div>
      <button type="button" class="settings-row" @click="openNickname">
        <div><strong>昵称</strong><small>用于个人中心、订单和售后服务</small></div>
        <span>{{ member.nickname || '去设置' }}</span><ChevronRight :size="18" />
      </button>
      <button type="button" class="settings-row" @click="openPhone">
        <div><strong>绑定手机号</strong><small>用于登录、验证和安全通知</small></div>
        <span>{{ maskedPhone }}</span><ChevronRight :size="18" />
      </button>
      <RouterLink to="/profile/security/change-login-password" class="settings-row">
        <div><strong>登录密码</strong><small>建议定期更换并妥善保管</small></div>
        <span>修改</span><ChevronRight :size="18" />
      </RouterLink>
    </section>

    <section v-if="!loading && canSetupAccount" class="settings-card legacy-account">
      <h3>设置登录账号</h3>
      <p>该账号用于密码登录，保存后不能自行修改。</p>
      <input v-model="accountForm.username" class="field" maxlength="20" autocomplete="username" autocapitalize="none" spellcheck="false" placeholder="4至20位，以英文字母开头" @input="handleAccountInput" />
      <input v-model="accountForm.password" class="field" type="password" maxlength="32" autocomplete="new-password" placeholder="设置6至32位登录密码" />
      <button type="button" class="btn primary" :disabled="savingAccount" @click="saveAccount">{{ savingAccount ? '保存中' : '保存登录账号' }}</button>
    </section>

    <div v-if="message" class="form-toast" :class="{ error: messageType === 'error' }" role="status" aria-live="polite">{{ message }}</div>

    <Teleport to="body">
      <div v-if="dialog === 'nickname'" class="dialog-mask" @click.self="closeDialog">
        <section class="dialog-card" role="dialog" aria-modal="true" aria-labelledby="nickname-title">
          <header><h3 id="nickname-title">修改昵称</h3><button type="button" aria-label="关闭" @click="closeDialog"><X :size="20" /></button></header>
          <p class="dialog-hint">2至20个字符，支持中文、字母、数字、空格、·、-和_。</p>
          <input ref="nicknameInput" v-model="nicknameForm" class="field" :class="{ invalid: !!nicknameError }" maxlength="20" placeholder="请输入昵称" @input="handleNicknameInput" />
          <p v-if="nicknameError" class="field-error">{{ nicknameError }}</p>
          <button type="button" class="btn primary dialog-submit" :disabled="savingNickname" @click="saveNickname">{{ savingNickname ? '保存中' : '保存昵称' }}</button>
        </section>
      </div>

      <div v-if="dialog === 'phone'" class="dialog-mask" @click.self="closeDialog">
        <section class="dialog-card phone-dialog" role="dialog" aria-modal="true" aria-labelledby="phone-title">
          <header><h3 id="phone-title">更换绑定手机号</h3><button type="button" aria-label="关闭" @click="closeDialog"><X :size="20" /></button></header>
          <p class="dialog-hint">为保护账号安全，需要分别验证当前手机号和新手机号。更换成功后需重新登录。</p>
          <label>当前手机号</label>
          <div class="sms-row">
            <input :value="maskedPhone" class="field" disabled />
            <button type="button" :disabled="sendingCurrent || currentCountdown > 0" @click="sendCurrentCode">{{ currentCountdown > 0 ? `${currentCountdown}秒` : (sendingCurrent ? '发送中' : '获取验证码') }}</button>
          </div>
          <input v-model="phoneForm.currentPhoneSmsCode" class="field code-field" inputmode="numeric" maxlength="6" autocomplete="one-time-code" placeholder="当前手机号验证码" @input="phoneForm.currentPhoneSmsCode = digits(phoneForm.currentPhoneSmsCode, 6)" />

          <label>新手机号</label>
          <input v-model="phoneForm.newPhone" class="field" inputmode="tel" maxlength="11" placeholder="请输入新的11位手机号" @input="phoneForm.newPhone = normalizeMainlandPhone(phoneForm.newPhone)" />
          <div class="sms-row new-code-row">
            <input v-model="phoneForm.newPhoneSmsCode" class="field" inputmode="numeric" maxlength="6" autocomplete="one-time-code" placeholder="新手机号验证码" @input="phoneForm.newPhoneSmsCode = digits(phoneForm.newPhoneSmsCode, 6)" />
            <button type="button" :disabled="sendingNew || newCountdown > 0" @click="sendNewCode">{{ newCountdown > 0 ? `${newCountdown}秒` : (sendingNew ? '发送中' : '获取验证码') }}</button>
          </div>
          <p v-if="phoneError" class="field-error">{{ phoneError }}</p>
          <button type="button" class="btn primary dialog-submit" :disabled="savingPhone" @click="savePhone">{{ savingPhone ? '更换中' : '确认更换手机号' }}</button>
          <p class="admin-hint">原手机号无法接收验证码时，请联系商城管理员核实身份后处理。</p>
        </section>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ChevronRight, X } from 'lucide-vue-next'
import { getMe, sendSmsCode, setupAccount, updateNickname, updatePhone } from '@/api/shop'
import { normalizeLoginAccountInput, validateLoginAccount } from '@/utils/loginAccount'
import { normalizeNicknameInput, validateNickname } from '@/utils/nickname'
import { isValidMainlandPhone, normalizeMainlandPhone } from '@/utils/phone'
import { clearShopSession } from '@/utils/shopSession'

const router = useRouter()
const member = ref({})
const loading = ref(true)
const message = ref('')
const messageType = ref('success')
const dialog = ref('')
const nicknameInput = ref(null)
const nicknameForm = ref('')
const nicknameError = ref('')
const savingNickname = ref(false)
const phoneForm = ref({ currentPhoneSmsCode: '', newPhone: '', newPhoneSmsCode: '' })
const phoneError = ref('')
const sendingCurrent = ref(false)
const sendingNew = ref(false)
const currentCountdown = ref(0)
const newCountdown = ref(0)
const savingPhone = ref(false)
const accountForm = ref({ username: '', password: '' })
const savingAccount = ref(false)
const timers = []
let messageTimer = null

const maskedPhone = computed(() => String(member.value.phone || '').replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2') || '-')
const canSetupAccount = computed(() => !member.value.username || member.value.username === member.value.phone)
const digits = (value, max) => String(value ?? '').replace(/\D/g, '').slice(0, max)
const showMessage = (text, type = 'error') => {
  window.clearTimeout(messageTimer)
  message.value = text
  messageType.value = type
  messageTimer = window.setTimeout(() => { message.value = '' }, 1800)
}

const loadMember = async () => {
  loading.value = true
  try { member.value = (await getMe()).data || {} }
  catch (e) { showMessage(e.message || '账号信息加载失败') }
  finally { loading.value = false }
}

const openNickname = async () => {
  nicknameForm.value = member.value.nickname || member.value.username || ''
  nicknameError.value = ''
  dialog.value = 'nickname'
  await nextTick()
  nicknameInput.value?.focus()
}
const openPhone = () => {
  phoneForm.value = { currentPhoneSmsCode: '', newPhone: '', newPhoneSmsCode: '' }
  phoneError.value = ''
  dialog.value = 'phone'
}
const closeDialog = () => { if (!savingNickname.value && !savingPhone.value) dialog.value = '' }

const handleNicknameInput = () => {
  nicknameForm.value = normalizeNicknameInput(nicknameForm.value)
  nicknameError.value = ''
}
const saveNickname = async () => {
  nicknameError.value = validateNickname(nicknameForm.value)
  if (nicknameError.value) return
  savingNickname.value = true
  try {
    member.value = (await updateNickname(nicknameForm.value.trim())).data || member.value
    dialog.value = ''
    showMessage('昵称已保存', 'success')
  } catch (e) { nicknameError.value = e.message || '昵称保存失败' }
  finally { savingNickname.value = false }
}

const startCountdown = (target) => {
  target.value = 60
  const timer = window.setInterval(() => {
    target.value -= 1
    if (target.value <= 0) window.clearInterval(timer)
  }, 1000)
  timers.push(timer)
}
const sendCurrentCode = async () => {
  sendingCurrent.value = true
  phoneError.value = ''
  try { await sendSmsCode(member.value.phone, 9); startCountdown(currentCountdown) }
  catch (e) { phoneError.value = e.message || '当前手机号验证码发送失败' }
  finally { sendingCurrent.value = false }
}
const sendNewCode = async () => {
  if (!isValidMainlandPhone(phoneForm.value.newPhone)) return (phoneError.value = '请输入正确的11位新手机号')
  if (phoneForm.value.newPhone === member.value.phone) return (phoneError.value = '新手机号不能与当前手机号相同')
  sendingNew.value = true
  phoneError.value = ''
  try { await sendSmsCode(phoneForm.value.newPhone, 10); startCountdown(newCountdown) }
  catch (e) { phoneError.value = e.message || '新手机号验证码发送失败' }
  finally { sendingNew.value = false }
}
const savePhone = async () => {
  const form = phoneForm.value
  if (!/^\d{6}$/.test(form.currentPhoneSmsCode)) return (phoneError.value = '请输入当前手机号收到的6位验证码')
  if (!isValidMainlandPhone(form.newPhone)) return (phoneError.value = '请输入正确的11位新手机号')
  if (!/^\d{6}$/.test(form.newPhoneSmsCode)) return (phoneError.value = '请输入新手机号收到的6位验证码')
  savingPhone.value = true
  phoneError.value = ''
  try {
    await updatePhone(form)
    clearShopSession()
    await router.replace({ path: '/login', query: { notice: '手机号已更新，请重新登录' } })
  } catch (e) { phoneError.value = e.message || '手机号更换失败' }
  finally { savingPhone.value = false }
}

const handleAccountInput = () => { accountForm.value.username = normalizeLoginAccountInput(accountForm.value.username) }
const saveAccount = async () => {
  const accountError = validateLoginAccount(accountForm.value.username)
  if (accountError) return showMessage(accountError)
  if (accountForm.value.password.length < 6 || accountForm.value.password.length > 32) return showMessage('登录密码需要6至32位')
  savingAccount.value = true
  try {
    await setupAccount(accountForm.value)
    clearShopSession()
    await router.replace({ path: '/login', query: { notice: '登录账号已设置，请使用新账号重新登录' } })
  }
  catch (e) { showMessage(e.message || '登录账号保存失败') }
  finally { savingAccount.value = false }
}

onMounted(loadMember)
onBeforeUnmount(() => {
  timers.forEach((timer) => window.clearInterval(timer))
  window.clearTimeout(messageTimer)
})
</script>

<style scoped>
.settings-page { width:min(620px,calc(100% - 28px)); }
.sub-page-head { display:grid; grid-template-columns:40px 1fr 40px; align-items:center; margin-bottom:14px; }
.sub-page-head h2 { margin:0; text-align:center; font-size:19px; }
.sub-page-head button { width:40px; height:40px; display:grid; place-items:center; padding:0; background:#fff; border:0; border-radius:50%; }
.settings-card { overflow:hidden; background:#fff; border:1px solid var(--line); border-radius:16px; }
.settings-row { width:100%; min-height:72px; display:grid; grid-template-columns:minmax(0,1fr) auto auto; align-items:center; gap:10px; padding:14px 16px; color:var(--ink); background:#fff; border:0; border-bottom:1px solid var(--line); text-align:left; }
.settings-row:last-child { border-bottom:0; }
.settings-row div { min-width:0; }
.settings-row strong,.settings-row small { display:block; }
.settings-row strong { font-size:15px; }
.settings-row small { margin-top:5px; color:var(--muted); font-size:11px; }
.settings-row > span { max-width:130px; overflow:hidden; color:#596273; text-overflow:ellipsis; white-space:nowrap; font-size:13px; }
.static-row { grid-template-columns:minmax(0,1fr) auto; }
.legacy-account { margin-top:12px; padding:16px; }
.legacy-account h3 { margin:0; font-size:16px; }
.legacy-account p { color:var(--muted); font-size:12px; }
.legacy-account .field { margin-top:10px; }
.legacy-account .btn { width:100%; margin-top:12px; }
.form-toast { position:fixed; top:calc(18px + env(safe-area-inset-top)); left:50%; z-index:1200; max-width:min(88vw,420px); padding:11px 16px; color:#fff; background:rgba(8,114,79,.96); border-radius:10px; box-shadow:0 8px 24px rgba(15,23,42,.18); transform:translateX(-50%); font-size:13px; text-align:center; pointer-events:none; }
.form-toast.error { background:rgba(180,35,24,.96); }
.dialog-mask { position:fixed; inset:0; z-index:1000; display:grid; place-items:end center; background:rgba(15,23,42,.46); }
.dialog-card { width:min(620px,100%); padding:20px 18px calc(18px + env(safe-area-inset-bottom)); background:#fff; border-radius:20px 20px 0 0; box-shadow:0 -16px 44px rgba(15,23,42,.16); }
.dialog-card header { display:flex; align-items:center; justify-content:space-between; }
.dialog-card header h3 { margin:0; font-size:18px; }
.dialog-card header button { width:36px; height:36px; display:grid; place-items:center; padding:0; color:#6b7280; background:#f3f5f7; border:0; border-radius:50%; }
.dialog-hint { margin:8px 0 14px; color:var(--muted); font-size:12px; line-height:1.6; }
.dialog-card label { display:block; margin:13px 0 7px; font-size:13px; font-weight:700; }
.dialog-submit { width:100%; margin-top:15px; }
.field.invalid { border-color:#dc2626; }
.field-error { margin:7px 0 0; color:#b42318; font-size:12px; }
.sms-row { display:grid; grid-template-columns:minmax(0,1fr) 106px; gap:8px; }
.sms-row button { padding:0 8px; color:var(--brand-primary); background:var(--brand-primary-soft); border:0; border-radius:10px; font-size:12px; font-weight:700; }
.sms-row button:disabled { color:#9ca3af; background:#f3f4f6; }
.code-field,.new-code-row { margin-top:8px; }
.admin-hint { margin:10px 0 0; color:var(--muted); text-align:center; font-size:11px; }
@media (min-width:621px) { .dialog-mask { place-items:center; }.dialog-card { border-radius:20px; } }
</style>
