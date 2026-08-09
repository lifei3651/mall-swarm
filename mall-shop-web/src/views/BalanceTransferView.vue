<template>
  <div class="page sub-page transfer-page">
    <header class="sub-page-head">
      <button type="button" aria-label="返回" @click="router.back()"><ArrowLeft :size="22" /></button>
      <h2>余额转账</h2><span></span>
    </header>

    <section class="balance-summary">
      <span>可用余额（元）</span>
      <strong>¥{{ money(wallet.balance) }}</strong>
    </section>

    <section class="panel transfer-panel">
      <h3>转账给会员</h3>
      <p class="line-sub">输入完整手机号后，请核对昵称、会员编号和脱敏账号。转账确认后即时到账。</p>
      <div class="form-item">
        <label>收款会员手机号</label>
        <div class="inline-input">
          <input v-model="transferForm.recipientPhone" class="field" inputmode="tel" maxlength="11" placeholder="请输入11位手机号" @input="handleRecipientPhoneInput" @blur="lookupRecipient" />
          <button type="button" @click="lookupRecipient">核对</button>
        </div>
      </div>
      <div v-if="recipient" class="recipient-card">
        <div><span>收款会员</span><strong>{{ recipient.memberName }}</strong></div>
        <dl><div><dt>会员编号</dt><dd>{{ recipient.memberNo }}</dd></div><div><dt>登录账号</dt><dd>{{ recipient.maskedLoginAccount }}</dd></div><div><dt>手机号</dt><dd>{{ recipient.maskedPhone }}</dd></div></dl>
      </div>
      <div class="form-item">
        <label for="transfer-amount">转账金额</label>
        <input id="transfer-amount" v-model="transferForm.amount" class="field" :class="{ invalid: amountError }" type="number" min="1" step="1" inputmode="numeric" placeholder="请输入整数金额" @input="handleAmountInput" />
        <p v-if="amountError" class="field-error">{{ amountError }}</p>
      </div>
      <div class="form-item"><label>支付密码</label><input v-model="transferForm.paymentPassword" class="field" type="password" inputmode="numeric" maxlength="6" autocomplete="off" placeholder="6位数字" /></div>
      <div class="form-item"><label>转账备注（选填）</label><input v-model="transferForm.remark" class="field" maxlength="100" placeholder="给收款方留言" /></div>
      <button class="btn primary submit-button" :disabled="transferSaving || !canUseBalance" @click="submitTransfer">{{ transferSaving ? '转账中' : '确认转账' }}</button>
      <p v-if="!wallet.distributionActivated" class="form-warning">完成首笔有效订单成为会员后，才可以转账和接收余额。</p>
    </section>

    <div v-if="error" class="transfer-toast" role="alert" aria-live="assertive">{{ error }}</div>

    <div v-if="showConfirm" class="dialog-overlay" @click.self="showConfirm = false">
      <div class="dialog-box" role="dialog" aria-modal="true" aria-labelledby="transfer-confirm-title">
        <h3 id="transfer-confirm-title">确认转账</h3>
        <div class="confirm-info">
          <div class="confirm-row"><span>收款会员</span><strong>{{ recipient?.memberName || '-' }}</strong></div>
          <div class="confirm-row"><span>会员编号</span><strong>{{ recipient?.memberNo || '-' }}</strong></div>
          <div class="confirm-row"><span>登录账号</span><strong>{{ recipient?.maskedLoginAccount || '-' }}</strong></div>
          <div class="confirm-row"><span>手机号</span><strong>{{ recipient?.maskedPhone || '-' }}</strong></div>
          <div class="confirm-row"><span>转账金额</span><strong class="amount-highlight">¥{{ money(transferForm.amount) }}</strong></div>
        </div>
        <p class="confirm-warning">请确认收款人信息无误。余额转账成功后即时到账，请勿向陌生账号转账。</p>
        <div class="dialog-actions">
          <button class="btn secondary" @click="showConfirm = false">取消</button>
          <button class="btn primary" :disabled="transferSaving" @click="doTransfer">{{ transferSaving ? '转账中...' : '确认转账' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import { findBalanceRecipient, getWalletSummary, transferBalance } from '@/api/shop'
import { money } from '@/utils/format'
import { createIdempotencyKey } from '@/utils/idempotency'
import { isValidMainlandPhone, normalizeMainlandPhone } from '@/utils/phone'

const router = useRouter()
const wallet = ref({ balance: 0, hasPaymentPassword: false, distributionActivated: false })
const transferForm = ref({ recipientPhone: '', amount: '', paymentPassword: '', remark: '' })
const recipient = ref(null)
const error = ref('')
const amountError = ref('')
const transferSaving = ref(false)
const transferRequestKey = ref('')
const showConfirm = ref(false)
let errorTimer
const clearTransferError = () => { window.clearTimeout(errorTimer); error.value = '' }
const showTransferError = (text) => {
  window.clearTimeout(errorTimer)
  error.value = text
  errorTimer = window.setTimeout(() => { error.value = '' }, 1800)
}
const canUseBalance = computed(() => wallet.value.hasPaymentPassword && wallet.value.distributionActivated)

const fetchData = async () => {
  try { wallet.value = (await getWalletSummary()).data || wallet.value }
  catch (e) { showTransferError(e.message || '余额信息加载失败') }
}

const handleRecipientPhoneInput = () => {
  transferForm.value.recipientPhone = normalizeMainlandPhone(transferForm.value.recipientPhone)
  recipient.value = null
  if (error.value.includes('手机号')) clearTransferError()
}

const lookupRecipient = async () => {
  if (!isValidMainlandPhone(transferForm.value.recipientPhone)) {
    recipient.value = null
    if (transferForm.value.recipientPhone) showTransferError('请输入正确的11位收款会员手机号')
    return
  }
  clearTransferError()
  try { recipient.value = (await findBalanceRecipient(transferForm.value.recipientPhone)).data || null }
  catch (e) { recipient.value = null; showTransferError(e.message || '未找到收款会员') }
}

const handleAmountInput = () => {
  const value = String(transferForm.value.amount ?? '').trim()
  amountError.value = value && !/^\d+$/.test(value) ? '转账金额只能为整数' : ''
  if (value && /^\d+$/.test(value) && Number(value) <= 0) amountError.value = '转账金额必须大于0'
  if (amountError.value && error.value.includes('转账金额')) clearTransferError()
}

const requirePaymentPassword = () => {
  if (wallet.value.hasPaymentPassword) return true
  router.push({ name: 'ProfileSecurity', query: { redirect: '/profile/wallet/transfer' } })
  return false
}

const submitTransfer = async () => {
  clearTransferError()
  handleAmountInput()
  if (!requirePaymentPassword()) return
  if (!wallet.value.distributionActivated) return showTransferError('完成首笔有效订单成为会员后才可转账')
  if (!isValidMainlandPhone(transferForm.value.recipientPhone)) return showTransferError('请输入正确的11位收款会员手机号')
  if (!recipient.value) return showTransferError('请先核对收款会员')
  if (amountError.value) return showTransferError(amountError.value)
  if (!/^\d+$/.test(String(transferForm.value.amount || ''))) return showTransferError('转账金额只能为整数')
  if (Number(transferForm.value.amount) > Number(wallet.value.balance || 0)) return showTransferError('余额不足')
  if (!/^\d{6}$/.test(transferForm.value.paymentPassword)) return showTransferError('请输入6位支付密码')
  showConfirm.value = true
}

const doTransfer = async () => {
  if (transferSaving.value) return
  transferSaving.value = true
  try {
    if (!transferRequestKey.value) transferRequestKey.value = createIdempotencyKey('balance-transfer')
    await transferBalance(transferForm.value, transferRequestKey.value)
    router.replace('/profile/wallet')
  } catch (e) { showTransferError(e.message || '转账失败'); showConfirm.value = false }
  finally { transferSaving.value = false }
}

onMounted(fetchData)
onBeforeUnmount(() => window.clearTimeout(errorTimer))
</script>

<style scoped>
.transfer-page { width:min(620px,calc(100% - 28px)); }
.sub-page-head { display:grid; grid-template-columns:40px 1fr 40px; align-items:center; margin-bottom:14px; }
.sub-page-head h2 { margin:0; text-align:center; font-size:19px; }
.sub-page-head button { width:40px; height:40px; display:grid; place-items:center; padding:0; background:#fff; border:0; border-radius:50%; }
.balance-summary { padding:20px 22px; color:#fff; background:linear-gradient(135deg,#e54b67,#a9183b); border-radius:17px; box-shadow:0 10px 24px rgba(169,24,59,.18); }
.balance-summary span { color:rgba(255,255,255,.8); font-size:13px; }
.balance-summary strong { display:block; margin-top:8px; font-size:30px; }
.transfer-panel { margin-top:12px; border:0; border-radius:16px; }
.transfer-panel > .form-item { margin-top:13px; }
.inline-input { display:grid; grid-template-columns:minmax(0,1fr) auto; gap:8px; }
.inline-input button { min-width:88px; color:var(--brand-primary); background:var(--brand-primary-soft); border:0; border-radius:8px; font-weight:700; }
.recipient-card { margin-top:10px; padding:13px; color:#245446; background:#eaf7f1; border:1px solid #ccebdd; border-radius:12px; }
.recipient-card > div { display:flex; align-items:center; justify-content:space-between; gap:12px; }
.recipient-card span { color:#668077; font-size:12px; }
.recipient-card strong { font-size:16px; }
.recipient-card dl { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:6px; margin:11px 0 0; padding-top:10px; border-top:1px solid rgba(36,84,70,.12); }
.recipient-card dl div { min-width:0; }
.recipient-card dt { color:#789087; font-size:10px; }
.recipient-card dd { margin:4px 0 0; overflow:hidden; color:#245446; text-overflow:ellipsis; white-space:nowrap; font-size:12px; font-weight:700; }
.field.invalid { border-color:#dc2626; }
.field-error { margin:6px 0 0; color:#b42318; font-size:12px; }
.submit-button { width:100%; margin-top:16px; }
.form-warning { color:#b45309; font-size:12px; }
.transfer-toast { position:fixed; top:calc(18px + env(safe-area-inset-top)); left:50%; z-index:1200; max-width:min(88vw,420px); padding:11px 16px; color:#fff; background:rgba(180,35,24,.96); border-radius:10px; box-shadow:0 8px 24px rgba(15,23,42,.18); transform:translateX(-50%); font-size:13px; line-height:1.5; text-align:center; pointer-events:none; }
.dialog-overlay { position:fixed; inset:0; z-index:1000; display:grid; place-items:center; background:rgba(0,0,0,.45); backdrop-filter:blur(2px); }
.dialog-box { width:min(380px,calc(100% - 32px)); padding:24px; background:#fff; border-radius:18px; box-shadow:0 20px 60px rgba(0,0,0,.2); }
.dialog-box h3 { margin:0 0 16px; font-size:18px; text-align:center; }
.confirm-info { padding:14px; background:#f8faf9; border-radius:12px; }
.confirm-row { display:flex; justify-content:space-between; align-items:center; padding:8px 0; }
.confirm-row:not(:last-child) { border-bottom:1px solid var(--line); }
.confirm-row span { color:var(--muted); font-size:13px; }
.confirm-row strong { font-size:14px; }
.amount-highlight { color:var(--accent,#e7193f); font-size:20px !important; }
.confirm-warning { margin:12px 2px 0; color:#a45b14; font-size:11px; line-height:1.55; }
.dialog-actions { display:flex; gap:10px; margin-top:16px; }
.dialog-actions button { flex:1; }
@media (max-width:560px) { .transfer-page{padding-top:10px}.transfer-panel{padding:16px 14px} }
</style>
