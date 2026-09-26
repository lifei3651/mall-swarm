import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { availablePaymentType, balanceTransactionsEnabled } from '../src/utils/balanceMode.js'

test('余额新交易开关关闭时不得显示余额支付，旧接口保持兼容', () => {
  assert.equal(balanceTransactionsEnabled(null), false)
  assert.equal(balanceTransactionsEnabled({ balanceTransactionsEnabled: 0 }), false)
  assert.equal(balanceTransactionsEnabled({ balanceTransactionsEnabled: 1 }), true)
  assert.equal(balanceTransactionsEnabled({}), true)
})

test('停用的支付方式不能从结算草稿恢复或提交', () => {
  const alipay = [{ value: 'ALIPAY' }]
  assert.equal(availablePaymentType('BALANCE', alipay), 'ALIPAY')
  assert.equal(availablePaymentType('BALANCE', []), '')
  const checkout = readFileSync(new URL('../src/views/CheckoutView.vue', import.meta.url), 'utf8')
  assert.match(checkout, /!paymentOptions\.length \|\|/)
  assert.match(checkout, /!paymentOptions\.value\.some\(/)
  assert.match(checkout, /balanceModeEnabled\.value = balanceTransactionsEnabled\(res\.data\)/)
  const detail = readFileSync(new URL('../src/views/OrderDetailView.vue', import.meta.url), 'utf8')
  assert.match(detail, /order\.payType === 'BALANCE' && !balanceModeEnabled/)
  const transfer = readFileSync(new URL('../src/views/BalanceTransferView.vue', import.meta.url), 'utf8')
  assert.match(transfer, /v-if="!balanceModeEnabled"/)
  assert.match(transfer, /if \(!balanceModeEnabled\.value\) return showTransferError/)
})
