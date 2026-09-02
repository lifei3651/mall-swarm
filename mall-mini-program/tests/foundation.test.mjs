import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const invite = require('../utils/invite')
const { buildQuery } = require('../utils/request')
const payment = require('../utils/payment')

test('邀请码只接受八位字母数字并统一大写', () => {
  assert.equal(invite.normalizeInviteCode('ab12cd34'), 'AB12CD34')
  assert.equal(invite.normalizeInviteCode('bad-code'), '')
  assert.equal(invite.normalizeInviteCode('<script>'), '')
})

test('二维码 scene 可以承载直接邀请码或查询参数', () => {
  assert.equal(invite.decodeScene('ab12cd34'), 'AB12CD34')
  assert.equal(invite.decodeScene(encodeURIComponent('inviteCode=ZX12CV34')), 'ZX12CV34')
})

test('请求查询参数过滤空值并编码', () => {
  assert.equal(buildQuery({ keyword: '护肤 套装', page: 1, empty: '' }), 'keyword=%E6%8A%A4%E8%82%A4%20%E5%A5%97%E8%A3%85&page=1')
})

test('微信支付参数只接受服务端签发的完整字段并映射package', () => {
  assert.deepEqual(payment.normalizeParameters({
    timeStamp: '1788060000', nonceStr: 'nonce', packageValue: 'prepay_id=wx123',
    signType: 'RSA', paySign: 'signature'
  }), {
    timeStamp: '1788060000', nonceStr: 'nonce', package: 'prepay_id=wx123',
    signType: 'RSA', paySign: 'signature'
  })
  assert.throws(() => payment.normalizeParameters({ timeStamp: '1' }), /参数不完整/)
})

test('用户取消微信支付与真实支付错误分开处理', () => {
  assert.equal(payment.isUserCancel({ errMsg: 'requestPayment:fail cancel' }), true)
  assert.equal(payment.isUserCancel(new Error('商户配置错误')), false)
})

test('小程序结算保留服务端大额支付短信验证', async () => {
  const fs = await import('node:fs/promises')
  const checkout = await fs.readFile(new URL('../pages/checkout/index.js', import.meta.url), 'utf8')
  assert.match(checkout, /url: '\/payment\/checkVerify'/)
  assert.match(checkout, /url: '\/sms\/send\/payment'/)
  assert.match(checkout, /smsCode: this\.data\.needSmsVerify/)
})

test('微信收款确认仅从本人提现单取参数并在原生确认后再次服务端核验', async () => {
  const fs = await import('node:fs/promises')
  const app = JSON.parse(await fs.readFile(new URL('../app.json', import.meta.url), 'utf8'))
  const page = await fs.readFile(new URL('../pages/payout/index.js', import.meta.url), 'utf8')
  assert.ok(app.pages.includes('pages/payout/index'))
  assert.match(page, /wx\.canIUse\('requestMerchantTransfer'\)/)
  assert.match(page, /wx\.requestMerchantTransfer/)
  assert.match(page, /withdrawals\/\$\{withdrawId\}\/wechat-confirmation/)
  assert.equal((page.match(/await this\.prepare\(withdrawId\)/g) || []).length, 2)
})
