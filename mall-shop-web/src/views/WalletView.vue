<template>
  <div class="page sub-page wallet-page">
    <header class="sub-page-head">
      <button type="button" aria-label="返回" @click="router.back()"><ArrowLeft :size="22" /></button>
      <h2>余额</h2><span></span>
    </header>

    <RouterLink v-if="!wallet.hasPaymentPassword" class="security-callout" to="/profile/security">
      <ShieldAlert :size="22" />
      <span><strong>首次交易前请设置支付密码</strong><small>用于余额支付、转账和提现验证</small></span>
      <ChevronRight :size="18" />
    </RouterLink>

    <section class="balance-card">
      <span>可用余额（元）</span>
      <strong>{{ money(wallet.balance) }}</strong>
      <p>奖金及其他明确入账进入余额后，可用于商城支付、会员转账和提现。</p>
    </section>

    <nav class="wallet-actions">
      <RouterLink class="wallet-action-link" to="/profile/wallet/transfer"><Send :size="21" /><span>余额转账</span></RouterLink>
      <button :class="{ active: activeTool === 'withdraw' }" type="button" @click="activeTool = 'withdraw'"><Landmark :size="21" /><span>余额提现</span></button>
      <button :class="{ active: activeTool === 'records' }" type="button" @click="activeTool = 'records'"><ReceiptText :size="21" /><span>提现记录</span></button>
      <button :class="{ active: activeTool === 'flows' }" type="button" @click="activeTool = 'flows'; loadFlows()"><History :size="21" /><span>余额记录</span></button>
    </nav>

    <section v-if="activeTool === 'withdraw'" class="panel wallet-form-panel">
      <h3>申请提现</h3>
      <div class="form-item"><label>提现方式</label><select v-model.number="withdrawForm.withdrawType" class="field"><option :value="1">银行卡</option><option :value="2">微信</option><option :value="3">支付宝</option></select></div>
      <div v-if="withdrawForm.withdrawType === 1" class="form-item"><label>开户银行</label><input v-model="withdrawForm.bankName" class="field" placeholder="例如：中国工商银行" /></div>
      <div class="form-item"><label>{{ withdrawAccountLabel }}</label><input v-model="withdrawForm.bankAccount" class="field" :placeholder="withdrawAccountPlaceholder" /></div>
      <div class="form-item"><label>收款人姓名</label><input v-model="withdrawForm.accountName" class="field" placeholder="必须与收款账户实名一致" /></div>
      <div class="form-item"><label>提现金额</label><input v-model="withdrawForm.withdrawAmount" class="field" type="number" min="0.01" step="0.01" placeholder="0.00" /></div>
      <div class="form-item"><label>支付密码</label><input v-model="withdrawForm.paymentPassword" class="field" type="password" inputmode="numeric" maxlength="6" autocomplete="off" placeholder="6位数字" /></div>
      <div class="form-item">
        <label>手机验证码</label>
        <div class="inline-input"><input v-model="withdrawForm.smsCode" class="field" inputmode="numeric" maxlength="6" placeholder="6位验证码" /><button type="button" :disabled="withdrawSmsCooldown > 0" @click="sendWithdrawCode">{{ withdrawSmsCooldown > 0 ? `${withdrawSmsCooldown}s` : '获取验证码' }}</button></div>
      </div>
      <button class="btn primary submit-button" :disabled="withdrawSaving || !canUseBalance" @click="submitWithdrawal">{{ withdrawSaving ? '提交中' : '申请提现' }}</button>
      <p class="line-sub">申请后相应余额会冻结；审核拒绝将自动退回，审核通过后由后台打款。</p>
    </section>

    <section v-else-if="activeTool === 'records'" class="panel records-panel">
      <h3>提现记录</h3>
      <div v-if="!withdrawals.length" class="records-empty">暂无提现记录</div>
      <article v-for="item in withdrawals" :key="item.id" class="record-item">
        <div><strong>{{ item.withdrawTypeName || '余额提现' }}</strong><small>{{ item.createTime || '-' }}</small></div>
        <div><strong>¥{{ money(item.withdrawAmount) }}</strong><small :class="`status-${item.status}`">{{ item.statusName || withdrawStatusName(item.status) }}</small></div>
      </article>
    </section>

    <!-- 余额记录 -->
    <section v-else-if="activeTool === 'flows'" class="panel records-panel">
      <h3>余额记录</h3>
      <div v-if="!balanceFlows.length" class="records-empty">暂无余额记录</div>
      <article v-for="item in balanceFlows" :key="item.id" class="record-item">
        <div><strong>{{ item.remark || flowTypeName(item.changeType) }}<small>{{ item.createTime || '-' }}</small></strong></div>
        <div><strong :class="flowAmountClass(item.changeType)">{{ flowAmountPrefix(item.changeType) }}{{ money(item.amount) }}</strong><small>余额 {{ money(item.balanceAfter) }}</small></div>
      </article>
    </section>

    <p v-if="error" class="page-error">{{ error }}</p>

  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ChevronRight, History, Landmark, ReceiptText, Send, ShieldAlert } from 'lucide-vue-next'
import { applyWithdrawal, getProfile, getWalletSummary, listMyBalanceFlows, listMyWithdrawals, sendSmsCode } from '@/api/shop'
import { money } from '@/utils/format'
import { createIdempotencyKey } from '@/utils/idempotency'
import { isValidMainlandPhone } from '@/utils/phone'

const router = useRouter()
const route = useRoute()
const activeTool = ref(['withdraw', 'records', 'flows'].includes(route.query.action) ? route.query.action : 'withdraw')
const wallet = ref({ balance: 0, hasPaymentPassword: false, distributionActivated: false })
const profile = ref({})
const withdrawals = ref([])
const error = ref('')
const withdrawSaving = ref(false)
const withdrawalRequestKey = ref('')
const withdrawSmsCooldown = ref(0)
const withdrawForm = ref({ withdrawType: 1, withdrawAmount: '', bankName: '', bankAccount: '', accountName: '', paymentPassword: '', smsCode: '' })
const balanceFlows = ref([])
const canUseBalance = computed(() => wallet.value.hasPaymentPassword && wallet.value.distributionActivated)
const withdrawAccountLabel = computed(() => ({ 1: '银行卡号', 2: '微信收款账号', 3: '支付宝账号' }[withdrawForm.value.withdrawType]))
const withdrawAccountPlaceholder = computed(() => ({ 1: '请输入银行卡号', 2: '请输入微信绑定手机号或账号', 3: '请输入支付宝账号' }[withdrawForm.value.withdrawType]))

const fetchData = async () => {
  error.value = ''
  try {
    const [walletRes, profileRes, withdrawRes] = await Promise.all([getWalletSummary(), getProfile(), listMyWithdrawals()])
    wallet.value = walletRes.data || wallet.value
    profile.value = profileRes.data || {}
    withdrawals.value = withdrawRes.data || []
  } catch (e) { error.value = e.message || '余额信息加载失败' }
}

const requirePaymentPassword = () => {
  if (wallet.value.hasPaymentPassword) return true
  router.push({ name: 'ProfileSecurity', query: { redirect: '/profile/wallet' } })
  return false
}

const loadFlows = async () => {
  try { balanceFlows.value = (await listMyBalanceFlows()).data || [] }
  catch { balanceFlows.value = [] }
}
const flowTypeName = (type) => ({ 1: '入账', 2: '支付', 3: '转出', 4: '转入', 5: '扣减' }[type] || '余额变动')
const flowAmountPrefix = (type) => [1, 4].includes(type) ? '+' : '-'
const flowAmountClass = (type) => [1, 4].includes(type) ? 'amount-in' : 'amount-out'

const sendWithdrawCode = async () => {
  error.value = ''
  const phone = profile.value.member?.phone
  if (!isValidMainlandPhone(phone)) return (error.value = '会员手机号格式不正确，请联系管理员')
  try {
    await sendSmsCode(phone, 5)
    withdrawSmsCooldown.value = 60
    const timer = window.setInterval(() => {
      withdrawSmsCooldown.value -= 1
      if (withdrawSmsCooldown.value <= 0) window.clearInterval(timer)
    }, 1000)
  } catch (e) { error.value = e.message || '验证码发送失败' }
}

const submitWithdrawal = async () => {
  if (withdrawSaving.value) return
  error.value = ''
  if (!requirePaymentPassword()) return
  if (!wallet.value.distributionActivated) return (error.value = '完成首笔有效订单成为会员后才可提现')
  if (withdrawForm.value.withdrawType === 1 && !withdrawForm.value.bankName.trim()) return (error.value = '请填写开户银行')
  if (!withdrawForm.value.bankAccount.trim()) return (error.value = `请填写${withdrawAccountLabel.value}`)
  if (!withdrawForm.value.accountName.trim()) return (error.value = '请填写收款人姓名')
  if (Number(withdrawForm.value.withdrawAmount || 0) <= 0) return (error.value = '请输入正确的提现金额')
  if (Number(withdrawForm.value.withdrawAmount) > Number(wallet.value.balance || 0)) return (error.value = '余额不足')
  if (!/^\d{6}$/.test(withdrawForm.value.paymentPassword)) return (error.value = '请输入6位支付密码')
  if (!/^\d{6}$/.test(withdrawForm.value.smsCode)) return (error.value = '请输入6位短信验证码')
  withdrawSaving.value = true
  try {
    if (!withdrawalRequestKey.value) withdrawalRequestKey.value = createIdempotencyKey('withdrawal')
    await applyWithdrawal(withdrawForm.value, withdrawalRequestKey.value)
    withdrawalRequestKey.value = ''
    withdrawForm.value = { withdrawType: 1, withdrawAmount: '', bankName: '', bankAccount: '', accountName: '', paymentPassword: '', smsCode: '' }
    activeTool.value = 'records'
    await fetchData()
  } catch (e) { error.value = e.message || '提现申请失败' }
  finally { withdrawSaving.value = false }
}
const withdrawStatusName = (status) => ({ 0: '待审核', 1: '审核通过', 2: '打款中', 3: '已打款', 4: '已拒绝' }[status] || '处理中')

onMounted(fetchData)
</script>

<style scoped>
.wallet-page { width:min(680px,calc(100% - 28px)); }
.sub-page-head { display:grid; grid-template-columns:40px 1fr 40px; align-items:center; margin-bottom:14px; }
.sub-page-head h2 { margin:0; text-align:center; font-size:19px; }
.sub-page-head button { width:40px; height:40px; display:grid; place-items:center; padding:0; background:#fff; border:0; border-radius:50%; }
.balance-card { padding:24px; color:#fff; background:linear-gradient(135deg,#e54b67,#a9183b); border-radius:19px; box-shadow:0 12px 28px rgba(169,24,59,.21); }
.balance-card span { color:rgba(255,255,255,.8); font-size:13px; }
.balance-card strong { display:block; margin:10px 0 8px; font-size:38px; line-height:1; }
.balance-card p { margin:0; color:rgba(255,255,255,.72); font-size:12px; }
.security-callout { display:grid; grid-template-columns:32px minmax(0,1fr) auto; align-items:center; gap:9px; margin-top:12px; padding:13px 14px; color:#92400e; background:#fff8e8; border:1px solid #fde3aa; border-radius:13px; }
.security-callout strong,.security-callout small { display:block; }
.security-callout small { margin-top:3px; color:#b16b26; font-size:11px; }
.wallet-actions { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:9px; margin:13px 0; }
.wallet-actions button,.wallet-action-link { min-height:72px; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:6px; color:#59616d; background:#fff; border:1px solid transparent; border-radius:14px; font-size:12px; }
.wallet-action-link { text-decoration:none; }
.wallet-actions button.active,.wallet-action-link:focus-visible { color:var(--brand-primary); border-color:var(--brand-primary); background:var(--brand-primary-soft); }
.wallet-form-panel,.records-panel { border:0; border-radius:16px; }
.wallet-form-panel > .form-item { margin-top:13px; }
.inline-input { display:grid; grid-template-columns:minmax(0,1fr) auto; gap:8px; }
.inline-input button { min-width:88px; color:var(--brand-primary); background:var(--brand-primary-soft); border:0; border-radius:8px; font-weight:700; }
.recipient-card { display:grid; grid-template-columns:1fr auto; gap:3px 12px; margin-top:10px; padding:11px 12px; color:#245446; background:#eaf7f1; border-radius:10px; }
.recipient-card small { grid-column:1/-1; color:#668077; }
.submit-button { width:100%; margin-top:16px; }
.form-warning { color:#b45309; font-size:12px; }
.records-empty { padding:34px 0; color:var(--muted); text-align:center; }
.record-item { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:14px 0; border-bottom:1px solid var(--line); }
.record-item:last-child { border-bottom:0; }
.record-item > div:last-child { text-align:right; }
.record-item strong,.record-item small { display:block; }
.record-item small { margin-top:5px; color:var(--muted); font-size:11px; }
.record-item .status-3 { color:#15803d; }.record-item .status-4 { color:#b42318; }
.page-error { padding:12px 14px; color:#b42318; background:#fff1f0; border-radius:10px; }
.record-item .amount-in { color: #15803d; }
.record-item .amount-out { color: #b42318; }

@media (max-width:560px) { .wallet-page{padding-top:10px}.balance-card{padding:21px 18px}.wallet-form-panel{padding:16px 14px} }
</style>
