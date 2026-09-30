import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { refundedQuantity, partialRefundSummary } from '../src/utils/orderItemRefunds.js'

const first = { id: '9212345678901234568', productName: '复购补充装', quantity: 1 }
const second = { id: '9212345678901234570', productName: '复购补充装', quantity: 1 }
const paidRefund = { applyType: 4, status: 1, items: [{ orderItemId: first.id, refundQuantity: 1 }] }

test('同名商品只标记对应订单商品，部分退款不覆盖剩余履约状态', () => {
  const sales = [paidRefund, { applyType: 3, status: 1, items: [{ orderItemId: second.id, refundQuantity: 1 }] }]
  assert.equal(refundedQuantity(first, sales), 1)
  assert.equal(refundedQuantity(second, sales), 0)
  assert.equal(partialRefundSummary({ status: 1 }, [first, second], sales), '已退款 1 件，剩余 1 件待发货')
  assert.equal(partialRefundSummary({ status: 4 }, [first, second], sales), '')
})

test('拒绝、取消、退款处理中不提前写成已退款，单商品数量不能超出原数量', () => {
  const unfinished = [0, 2, 3, 6].map((status) => ({ applyType: 4, status, items: [{ orderItemId: first.id, refundQuantity: 1 }] }))
  assert.equal(refundedQuantity(first, unfinished), 0)
  assert.equal(refundedQuantity(first, [paidRefund, paidRefund]), 1)
  assert.equal(refundedQuantity({ ...first, quantity: 2 }, [paidRefund]), 1)
})

test('详情把已退款状态和原实付金额标在对应商品旁', () => {
  const source = readFileSync(new URL('../src/views/OrderDetailView.vue', import.meta.url), 'utf8')
  assert.match(source, /consumer-item-refund-status/)
  assert.match(source, /refundedQuantity\(item, afterSales\)/)
  assert.match(source, /'原实付' : '实付款'/)
  assert.match(source, /partialRefundSummary\(order\.value, detail\.value\.items, detail\.value\.afterSales\)/)
})
