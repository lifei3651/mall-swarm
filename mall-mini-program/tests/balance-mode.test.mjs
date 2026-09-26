import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { commerceEnv } from './helpers/commerce-env.mjs'

test('余额交易关闭时，结算不展示余额选项，也不把正常关闭提示成钱包故障', async () => {
  const env = commerceEnv((request) => {
    if (request.url === '/shop/addresses') return []
    if (request.url === '/shop/pay/config') return { wechatPayEnabled: true }
    if (request.url === '/shop/wallet/summary') return { balance: 100, hasPaymentPassword: true }
    if (request.url === '/shop/business-config') return { balanceTransactionsEnabled: 0 }
    return {}
  })
  const page = env.page('checkout')
  page.flashSaleMode = true
  page.loadActivity = async () => ({ rows: [{ productId: '1', skuId: '', quantity: 1, salePrice: 1 }], activityName: '测试' })
  await page.load()
  assert.equal(page.data.balanceAvailable, false)
  assert.equal(page.data.balanceError, '')
  assert.equal(page.data.payType, 'WECHAT')
  const markup = readFileSync(new URL('../pages/checkout/index.wxml', import.meta.url), 'utf8')
  assert.match(markup, /wx:if="{{balanceAvailable}}"/)
  assert.match(markup, /disabled="{{[^\"]*!wechatPayEnabled && !balanceAvailable[^\"]*}}"/)
})

test('旧待付款余额单在停用后，付款弹层也不能继续发起交易', () => {
  const env = commerceEnv()
  const mode = env.load('utils/balance-mode')
  const payment = env.load('utils/balance-order')
  assert.equal(mode.balanceTransactionsEnabled(null), false)
  assert.equal(mode.balanceTransactionsEnabled({ balanceTransactionsEnabled: 0 }), false)
  assert.equal(mode.balanceTransactionsEnabled({}), true)
  assert.throws(() => payment.validateWallet({ balance: 100, hasPaymentPassword: true, balanceTransactionsEnabled: 0 }, 1), /暂停新增余额支付/)
})
