import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('../src/views/OrderDetailView.vue', import.meta.url), 'utf8')
const methods = source.slice(source.indexOf('const closeBalanceSetup = () => {'), source.indexOf('const receive = async () => {'))
const fetchMethod = source.slice(source.indexOf('const fetchOrder = async () => {'), source.indexOf('const cancel = async () => {'))

function harness({ setupError = '', walletError = '' } = {}) {
  let hasPassword = false, interval = 0
  const calls = [], ref = value => ({ value })
  const context = { disposed: false, balanceSetupTimer: null, balanceSetupGeneration: 0, balanceWalletRequestVersion: 0,
    balanceSetupVisible: ref(false), balanceSetupBusy: ref(false), balanceSetupSending: ref(false),
    balanceSetupCountdown: ref(0), balanceSetupError: ref(''), balanceSetupNotice: ref(''),
    balanceSetupForm: ref({ loginPassword: '', smsCode: '', newPassword: '', confirmPassword: '' }),
    emptyBalanceSetupForm: () => ({ loginPassword: '', smsCode: '', newPassword: '', confirmPassword: '' }),
    balanceWallet: ref(null), balanceModeEnabled: ref(true), acting: ref(false), error: ref(''),
    order: ref({ id: '11', status: 0, payType: 'BALANCE', payAmount: '50.00' }),
    paymentPassword: ref(''), balancePaymentRequestKey: ref(''),
    getWalletSummary: async () => { if (walletError) throw new Error(walletError); return { data: { hasPaymentPassword: hasPassword, balance: '100.00' } } },
    sendPaymentPasswordSmsCode: async () => { calls.push('sms') },
    setPaymentPassword: async () => { calls.push('set-password'); if (setupError) throw new Error(setupError); hasPassword = true },
    payOrderWithBalance: async () => { calls.push('pay') },
    fetchOrder: async () => { calls.push('reload-order') },
    createIdempotencyKey: () => 'balance-key',
    window: { clearInterval() {}, setInterval() { return ++interval } } }
  vm.runInNewContext(methods + '\nthis.actions = { pay, sendBalanceSetupCode, saveBalanceSetup, closeBalanceSetup }', context)
  return { context, calls }
}

test('H5 旧待支付余额单先核对钱包并在本页设置密码，不能凭空扣款', async () => {
  const { context, calls } = harness()
  await context.actions.pay()
  assert.equal(context.balanceSetupVisible.value, true)
  assert.equal(context.acting.value, false)
  assert.equal(context.paymentPassword.value, '')
  assert.deepEqual(calls, [])
  const template = source.slice(0, source.indexOf('<script setup>'))
  assert.match(template, /@click="saveBalanceSetup"/)
  assert.match(template, /首次余额支付请点“立即支付”在本页设置密码/)
})

test('H5 旧单设置成功后才可输入密码支付原订单，不重建订单', async () => {
  const { context, calls } = harness()
  await context.actions.pay()
  await context.actions.saveBalanceSetup()
  assert.match(context.balanceSetupError.value, /登录密码/)
  context.balanceSetupForm.value = { loginPassword: 'login-secret', smsCode: '123456', newPassword: '654321', confirmPassword: '654321' }
  await context.actions.sendBalanceSetupCode()
  await context.actions.saveBalanceSetup()
  assert.deepEqual(calls, ['sms', 'set-password'])
  assert.equal(context.balanceSetupVisible.value, false)
  assert.equal(context.balanceWallet.value.hasPaymentPassword, true)
  assert.equal(context.balanceSetupForm.value.newPassword, '')
  context.paymentPassword.value = '654321'
  await context.actions.pay()
  assert.deepEqual(calls, ['sms', 'set-password', 'pay', 'reload-order'])
})

test('H5 旧单取消首次设置保留原订单，不残留密码或短信', async () => {
  const { context, calls } = harness()
  await context.actions.pay()
  context.balanceSetupForm.value.loginPassword = 'login-secret'
  context.actions.closeBalanceSetup()
  assert.equal(context.balanceSetupVisible.value, false)
  assert.equal(context.balanceSetupForm.value.loginPassword, '')
  assert.equal(context.balanceSetupCountdown.value, 0)
  assert.deepEqual(calls, [])
})

test('H5 设置失败或钱包状态读取失败都不尝试扣款', async () => {
  const unavailable = harness({ walletError: '安全状态不可用' })
  await unavailable.context.actions.pay()
  assert.match(unavailable.context.error.value, /安全状态不可用/)
  assert.equal(unavailable.context.balanceSetupVisible.value, false)
  assert.deepEqual(unavailable.calls, [])

  const rejected = harness({ setupError: '验证码错误' })
  await rejected.context.actions.pay()
  rejected.context.balanceSetupForm.value = { loginPassword: 'login-secret', smsCode: '123456', newPassword: '654321', confirmPassword: '654321' }
  await rejected.context.actions.saveBalanceSetup()
  assert.match(rejected.context.balanceSetupError.value, /验证码错误/)
  assert.equal(rejected.context.balanceWallet.value.hasPaymentPassword, false)
  assert.deepEqual(rejected.calls, ['set-password'])
})

test('H5 钱包查询期间订单切换，不弹旧单设置框或支付新单', async () => {
  const { context, calls } = harness()
  let resolveWallet
  context.getWalletSummary = () => new Promise(resolve => { resolveWallet = resolve })
  const pending = context.actions.pay()
  context.order.value = { id: '12', status: 0, payType: 'BALANCE', payAmount: '1.00' }
  resolveWallet({ data: { hasPaymentPassword: false, balance: '100.00' } })
  await pending
  assert.equal(context.balanceSetupVisible.value, false)
  assert.match(context.error.value, /订单状态已变化/)
  assert.deepEqual(calls, [])
})

test('H5 旧单详情预读钱包，旧订单的迟到响应不能覆盖新订单', async () => {
  const ref = value => ({ value }), pending = []
  const context = { disposed: false, balanceWalletRequestVersion: 0, balanceWalletOrderId: '',
    route: { params: { id: '11' }, query: {} }, hasToken: ref(true), loading: ref(false), error: ref(''),
    detail: ref({}), balanceWallet: ref(null), paymentPassword: ref(''), balanceSetupNotice: ref(''), logisticsTracking: ref([]),
    applyingAfterSale: ref(false), canApplyAfterSale: ref(false), exceptionRefund: ref(false), notShipped: ref(false),
    afterSaleForm: ref({}), selectedReason: ref(''), selectAllRefundableItems() {},
    getOrder: async id => ({ data: { order: { id, status: 0, payType: 'BALANCE' } } }),
    getWalletSummary: () => new Promise(resolve => pending.push(resolve)) }
  vm.runInNewContext(fetchMethod + '\nthis.runFetch = fetchOrder', context)
  await context.runFetch()
  context.route.params.id = '12'
  await context.runFetch()
  pending[1]({ data: { hasPaymentPassword: true } })
  await Promise.resolve()
  pending[0]({ data: { hasPaymentPassword: false } })
  await Promise.resolve()
  assert.equal(context.balanceWallet.value.hasPaymentPassword, true)
  assert.equal(context.balanceWalletOrderId, '12')
})
