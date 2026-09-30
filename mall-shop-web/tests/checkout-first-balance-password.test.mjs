import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('../src/views/CheckoutView.vue', import.meta.url), 'utf8')
const submitSource = source.slice(source.indexOf('const submit = async () => {'), source.indexOf('const confirmPayWithPassword = async () => {'))
const setupSource = source.slice(source.indexOf('const setupPasswordAndPay = async () => {'), source.indexOf('const submit = async () => {'))

function harness(hasPaymentPassword) {
  const calls = [], ref = value => ({ value })
  const context = { form: ref({ payType: 'BALANCE', receiverProvince: '湖南省', receiverCity: '长沙市', receiverDistrict: '岳麓区', receiverDetailAddress: '测试路1号' }),
    remarkEditorVisible: ref(false), submitting: ref(false), payPasswordSubmitting: ref(false),
    paymentPasswordLocked: ref(false), paymentPasswordLockHint: ref('锁定'),
    needSmsVerify: ref(false), smsCode: ref(''), walletSummary: ref({ hasPaymentPassword }),
    payPasswordInput: ref(''), paymentPasswordSaved: ref(false), payPasswordError: ref(''), setupPasswordError: ref(''),
    showPayDialog: ref(false), validate: () => '', clearCheckoutError: () => {},
    showCheckoutError: error => calls.push(['error', error]),
    ensurePendingOrder: async () => { calls.push(['order']); return { checkoutId: '1' } },
    fetchMemberPhone: async () => { calls.push(['phone']); return true },
    doSubmitOrder: async () => calls.push(['wechat']) }
  vm.runInNewContext(submitSource + '\nthis.runSubmit = submit', context)
  return { context, calls }
}

test('H5 首次余额提交先弹密码设置，不在后端门禁前创建订单', async () => {
  const { context, calls } = harness(false)
  await context.runSubmit()
  assert.deepEqual(calls, [['phone']])
  assert.equal(context.showPayDialog.value, true)
  assert.equal(context.submitting.value, false)
  assert.match(source, /此时还没有创建订单；关闭后仍可修改结算信息/)
})

test('H5 已有支付密码沿用原有待支付订单路径', async () => {
  const { context, calls } = harness(true)
  await context.runSubmit()
  assert.deepEqual(calls, [['order']])
  assert.equal(context.showPayDialog.value, true)
})

test('H5 首次设置只保存密码，明确继续后才进入原余额付款路径', async () => {
  const calls = [], ref = value => ({ value })
  const context = { setupPasswordSubmitting: ref(false), payPasswordSubmitting: ref(false), submitting: ref(false),
    setupPasswordError: ref(''), payPasswordError: ref(''),
    setupPasswordForm: ref({ loginPassword: 'login-secret', smsCode: '123456', newPassword: '654321' }),
    setupPasswordConfirm: ref('654321'), walletSummary: ref({ hasPaymentPassword: false }),
    payPasswordInput: ref(''), setupSmsCountdown: ref(10), paymentPasswordSaved: ref(false),
    setupSmsTimer: 1, window: { clearInterval() {} },
    setPaymentPassword: async () => calls.push('set-password'),
    confirmPayWithPassword: async () => calls.push('pay') }
  vm.runInNewContext(setupSource + '\nthis.runSetup = setupPasswordAndPay; this.runContinue = continueAfterPasswordSaved', context)
  await context.runSetup()
  assert.deepEqual(calls, ['set-password'])
  assert.equal(context.paymentPasswordSaved.value, true)
  assert.equal(context.walletSummary.value.hasPaymentPassword, true)
  assert.equal(context.setupPasswordForm.value.loginPassword, '')
  await context.runContinue()
  assert.deepEqual(calls, ['set-password', 'pay'])
})
