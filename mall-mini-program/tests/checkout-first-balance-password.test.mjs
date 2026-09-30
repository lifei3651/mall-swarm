import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { commerceEnv } from './helpers/commerce-env.mjs'

function readyCheckout(respond = () => ({}), hasPaymentPassword = false) {
  const env = commerceEnv(respond), page = env.page('checkout')
  page.onLoad()
  const address = { id: '11' }
  page.setData({ loading: false, quoteReady: true, quoteLoading: false, address,
    rows: [{ productId: '22', skuId: '33', quantity: 1 }], payType: 'BALANCE',
    balanceAvailable: true, balanceSummary: { balance: '100.00', hasPaymentPassword },
    payTotal: '10.00', wechatPayEnabled: true })
  page.quotedPayload = JSON.stringify(page.orderPayload(address, false))
  env.wx.showLoading = () => {}
  env.wx.hideLoading = () => {}
  env.wx.redirectTo = ({ url }) => env.routes.push(url)
  return { env, page }
}

test('小程序首次余额结算先打开设置，不创建订单；取消保留结算', async () => {
  const { env, page } = readyCheckout()
  await page.submit()
  assert.equal(page.data.passwordSetupVisible, true)
  assert.equal(page.data.submitting, false)
  assert.equal(page.orderAttempted, undefined)
  assert.equal(env.calls.length, 0)
  page.passwordSetupInput({ currentTarget: { dataset: { field: 'newPassword' } }, detail: { value: '123456' } })
  page.closePasswordSetup()
  assert.equal(page.data.passwordSetupVisible, false)
  assert.equal(page.data.passwordSetupForm.newPassword, '')
  assert.equal(page.data.address.id, '11')
  assert.equal(env.routes.length, 0)
})

test('小程序设置成功后才继续创建余额订单，重复点击不重复落单', async () => {
  const { env, page } = readyCheckout(({ url }) => {
    if (url === '/shop/orders') return { checkoutId: '44', order: { id: '45' } }
    return {}
  })
  await page.submit()
  await page.sendPasswordSetupCode()
  assert.equal(env.calls[0].url, '/sms/send/payment-password')
  for (const [field, value] of Object.entries({ loginPassword: 'login-secret', smsCode: '123456', newPassword: '654321', confirmPassword: '654321' })) {
    page.passwordSetupInput({ currentTarget: { dataset: { field } }, detail: { value } })
  }
  await page.savePasswordSetup()
  assert.equal(env.calls[1].url, '/shop/wallet/payment-password')
  assert.equal(env.calls[1].method, 'PUT')
  assert.equal(env.calls.some(call => call.url === '/shop/orders'), false)
  assert.equal(page.data.passwordSetupSaved, true)
  assert.equal(page.data.passwordSetupForm.newPassword, '')
  await page.continueAfterPasswordSetup()
  assert.equal(env.calls.filter(call => call.url === '/shop/orders').length, 1)
  assert.equal(env.routes.at(-1), '/pages/order-detail/index?id=45&autoPay=1')
  await page.submit()
  assert.equal(env.calls.filter(call => call.url === '/shop/orders').length, 1)
  page.onHide()
})

test('小程序设置失败或离页晚到不生成订单、不给当前页标记已设密码', async () => {
  let resolveSetup
  const { env, page } = readyCheckout(({ url }) => url === '/shop/wallet/payment-password'
    ? new Promise(resolve => { resolveSetup = resolve }) : {})
  await page.submit()
  await page.savePasswordSetup()
  assert.match(page.data.passwordSetupError, /登录密码/)
  assert.equal(env.calls.length, 0)
  for (const [field, value] of Object.entries({ loginPassword: 'login-secret', smsCode: '123456', newPassword: '654321', confirmPassword: '654321' })) {
    page.passwordSetupInput({ currentTarget: { dataset: { field } }, detail: { value } })
  }
  const pending = page.savePasswordSetup()
  page.onHide()
  resolveSetup({})
  await pending
  assert.equal(page.data.passwordSetupSaved, false)
  assert.equal(page.data.balanceSummary.hasPaymentPassword, false)
  assert.equal(page.data.passwordSetupForm.loginPassword, '')
  assert.equal(env.calls.filter(call => call.url === '/shop/orders').length, 0)
})

test('后端拒绝首次密码设置时停留结算页，不误创建订单', async () => {
  const { env, page } = readyCheckout(({ url }) => {
    if (url === '/shop/wallet/payment-password') throw new Error('验证码错误')
    return {}
  })
  await page.submit()
  for (const [field, value] of Object.entries({ loginPassword: 'login-secret', smsCode: '123456', newPassword: '654321', confirmPassword: '654321' })) {
    page.passwordSetupInput({ currentTarget: { dataset: { field } }, detail: { value } })
  }
  await page.savePasswordSetup()
  assert.match(page.data.passwordSetupError, /验证码错误/)
  assert.equal(page.data.passwordSetupSaved, false)
  assert.equal(page.data.balanceSummary.hasPaymentPassword, false)
  assert.equal(page.data.passwordSetupForm.smsCode, '')
  assert.equal(env.calls.filter(call => call.url === '/shop/orders').length, 0)
  page.onHide()
})

test('短信发送途中取消后可重新打开，不受旧请求卡住或旧验证码状态覆盖', async () => {
  let resolveCode
  const { page } = readyCheckout(({ url }) => url === '/sms/send/payment-password'
    ? new Promise(resolve => { resolveCode = resolve }) : {})
  await page.submit()
  const pending = page.sendPasswordSetupCode()
  assert.equal(page.data.passwordSetupSending, true)
  page.closePasswordSetup()
  await page.submit()
  resolveCode({})
  await pending
  assert.equal(page.data.passwordSetupVisible, true)
  assert.equal(page.data.passwordSetupSending, false)
  assert.equal(page.data.passwordSetupCooldown, 0)
  page.onHide()
})

test('已有密码的余额结算保持直接落单；首次设置入口只属于余额方式', async () => {
  const { env, page } = readyCheckout(({ url }) => url === '/shop/orders'
    ? { checkoutId: '44', order: { id: '45' } } : {}, true)
  await page.submit()
  assert.equal(page.data.passwordSetupVisible, false)
  assert.equal(env.calls.filter(call => call.url === '/shop/orders').length, 1)
  const template = readFileSync(new URL('../pages/checkout/index.wxml', import.meta.url), 'utf8')
  assert.match(template, /bindtap="savePasswordSetup"/)
  assert.match(template, /bindtap="continueAfterPasswordSetup"/)
  assert.match(template, /设置前不会创建订单/)
  page.onHide()
})
