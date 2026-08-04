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
      <p class="line-sub">输入完整手机号后核对收款会员名称，转账确认后即时到账。</p>
      <div class="form-item">
        <label>收款会员手机号</label>
        <div class="inline-input">
          <input v-model="transferForm.recipientPhone" class="field" inputmode="tel" maxlength="11" placeholder="请输入11位手机号" @input="handleRecipientPhoneInput" @blur="lookupRecipient" />
          <button type="button" @click="lookupRecipient">核对</button>
        </div>
      </div>
      <div v-if="recipient" class="recipient-card"><span>收款会员</span><strong>{{ recipient.memberName }}</strong><small>{{ recipient.phone }}</small></div>
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

    <p v-if="error" class="page-error">{{ error }}</p>

    <div v-if="showConfirm" class="dialog-overlay" @click.self="showConfirm = false">
      <div class="dialog-box" role="dialog" aria-modal="true" aria-labelledby="transfer-confirm-title">
        <h3 id="transfer-confirm-title">确认转账</h3>
        <div class="confirm-info">
          <div class="confirm-row"><span>收款会员</span><strong>{{ recipient?.memberName || '-' }}</strong></div>
          <div class="confirm-row"><span>手机号</span><strong>{{ transferForm.recipientPhone }}</strong></div>
          <div class="confirm-row"><span>转账金额</span><strong class="amount-highlight">¥{{ money(transferForm.amount) }}</strong></div>
        </div>
        <div class="dialog-actions">
          <button class="btn secondary" @click="showConfirm = false">取消</button>
          <button class="btn primary" :disabled="transferSaving" @click="doTransfer">{{ transferSaving ? '转账中...' : '确认转账' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
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
const canUseBalance = computed(() => wallet.value.hasPaymentPassword && wallet.value.distributionActivated)

const fetchData = async () => {
  try { wallet.value = (await getWalletSummary()).data || wallet.value }
  catch (e) { error.value = e.message || '余额信息加载失败' }
}

const handleRecipientPhoneInput = () => {
  transferForm.value.recipientPhone = normalizeMainlandPhone(transferForm.value.recipientPhone)
  recipient.value = null
  if (error.value.includes('手机号')) error.value = ''
}

const lookupRecipient = async () => {
  if (!isValidMainlandPhone(transferForm.value.recipientPhone)) {
    recipient.value = null
    if (transferForm.value.recipientPhone) error.value = '请输入正确的11位收款会员手机号'
    return
  }
  error.value = ''
  try { recipient.value = (await findBalanceRecipient(transferForm.value.recipientPhone)).data || null }
  catch (e) { recipient.value = null; error.value = e.message || '未找到收款会员' }
}

const handleAmountInput = () => {
  const value = String(transferForm.value.amount ?? '').trim()
  amountError.value = value && !/^\d+$/.test(value) ? '转账金额只能为整数' : ''
  if (value && /^\d+$/.test(value) && Number(value) <= 0) amountError.value = '转账金额必须大于0'
  if (amountError.value && error.value.includes('转账金额')) error.value = ''
}

const requirePaymentPassword = () => {
  if (wallet.value.hasPaymentPassword) return true
  router.push({ name: 'ProfileSecurity', query: { redirect: '/profile/wallet/transfer' } })
  return false
}

const submitTransfer = async () => {
  error.value = ''
  handleAmountInput()
  if (!requirePaymentPassword()) return
  if (!wallet.value.distributionActivated) return (error.value = '完成首笔有效订单成为会员后才可转账')
  if (!isValidMainlandPhone(transferForm.value.recipientPhone)) return (error.value = '请输入正确的11位收款会员手机号')
  if (!recipient.value) return (error.value = '请先核对收款会员')
  if (amountError.value) return (error.value = amountError.value)
  if (!/^\d+$/.test(String(transferForm.value.amount || ''))) return (error.value = '转账金额只能为整数')
  if (Number(transferForm.value.amount) > Number(wallet.value.balance || 0)) return (error.value = '余额不足')
  if (!/^\d{6}$/.test(transferForm.value.paymentPassword)) return (error.value = '请输入6位支付密码')
  showConfirm.value = true
}

const doTransfer = async () => {
  if (transferSaving.value) return
  transferSaving.value = true
  try {
    if (!transferRequestKey.value) transferRequestKey.value = createIdempotencyKey('balance-transfer')
    await transferBalance(transferForm.value, transferRequestKey.value)
    router.replace('/profile/wallet')
  } catch (e) { error.value = e.message || '转账失败'; showConfirm.value = false }
  finally { transferSaving.value = false }
}

onMounted(fetchData)
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
.recipient-card { display:grid; grid-template-columns:1fr auto; gap:3px 12px; margin-top:10px; padding:11px 12px; color:#245446; background:#eaf7f1; border-radius:10px; }
.recipient-card small { grid-column:1/-1; color:#668077; }
.field.invalid { border-color:#dc2626; }
.field-error { margin:6px 0 0; color:#b42318; font-size:12px; }
.submit-button { width:100%; margin-top:16px; }
.form-warning { color:#b45309; font-size:12px; }
.page-error { padding:12px 14px; color:#b42318; background:#fff1f0; border-radius:10px; }
.dialog-overlay { position:fixed; inset:0; z-index:1000; display:grid; place-items:center; background:rgba(0,0,0,.45); backdrop-filter:blur(2px); }
.dialog-box { width:min(380px,calc(100% - 32px)); padding:24px; background:#fff; border-radius:18px; box-shadow:0 20px 60px rgba(0,0,0,.2); }
.dialog-box h3 { margin:0 0 16px; font-size:18px; text-align:center; }
.confirm-info { padding:14px; background:#f8faf9; border-radius:12px; }
.confirm-row { display:flex; justify-content:space-between; align-items:center; padding:8px 0; }
.confirm-row:not(:last-child) { border-bottom:1px solid var(--line); }
.confirm-row span { color:var(--muted); font-size:13px; }
.confirm-row strong { font-size:14px; }
.amount-highlight { color:var(--accent,#e7193f); font-size:20px !important; }
.dialog-actions { display:flex; gap:10px; margin-top:16px; }
.dialog-actions button { flex:1; }
@media (max-width:560px) { .transfer-page{padding-top:10px}.transfer-panel{padding:16px 14px} }
</style>
