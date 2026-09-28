import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

const source = readFileSync(new URL('../src/views/OrderDetailView.vue', import.meta.url), 'utf8')
test('H5未发货退款显示逐商品数量选择，不再锁定全部剩余商品', () => {
  assert.match(source, /v-if="applyingAfterSale && remainingQuantity\(item\) > 0" class="quantity-stepper"/)
  assert.doesNotMatch(source, /包含全部剩余商品/)
  assert.match(source, /不申请的商品请将数量调为0/)
})

test('H5未发货退款草稿按所选商品计算金额，部分退不退运费，全退才退', () => {
  const items = [{ id: '1', quantity: 1, totalAmount: 100 }, { id: '2', quantity: 2, totalAmount: 200 }]
  const context = {
    computed: fn => ({ get value() { return fn() } }),
    detail: { value: { items } }, order: { value: { status: 1, totalAmount: 300, discountAmount: 30, freightAmount: 10 } },
    afterSales: { value: [] }, shipments: { value: [] }, refundQuantities: { value: {} },
    afterSaleErrors: { value: {} }, exceptionRefund: { value: false },
    afterSaleForm: { value: {} }, selectedReason: { value: '' }, applyingAfterSale: { value: false },
  }
  const start = source.indexOf('const usedQuantity ='), end = source.indexOf('const selectAfterSaleReason =')
  assert.ok(start > 0 && end > start)
  vm.runInNewContext(source.slice(start, end) + '\nthis.page = { startExceptionRefund, setRefundQuantity, selectedRefundItems, estimatedProductRefund, estimatedFreightRefund }', context)
  const page = context.page
  page.startExceptionRefund()
  assert.equal(context.afterSaleForm.value.applyType, 4)
  assert.equal(context.selectedReason.value, '取消未发货订单')
  assert.equal(page.estimatedFreightRefund.value, 10)
  page.setRefundQuantity(items[1], -1)
  page.setRefundQuantity(items[1], -1)
  assert.equal(page.estimatedProductRefund.value, 90)
  assert.equal(page.estimatedFreightRefund.value, 0)
  assert.deepEqual(JSON.parse(JSON.stringify(page.selectedRefundItems.value)), [{ orderItemId: '1', quantity: 1 }])
  context.afterSales.value = [{ applyType: 4, status: 1, productRefundAmount: 90, items: [{ orderItemId: '1', refundQuantity: 1 }] }]
  page.startExceptionRefund()
  page.setRefundQuantity(items[1], -1)
  assert.equal(page.estimatedProductRefund.value, 90)
  assert.equal(page.estimatedFreightRefund.value, 0)
  page.setRefundQuantity(items[1], 1)
  assert.equal(page.estimatedProductRefund.value, 180)
  assert.equal(page.estimatedFreightRefund.value, 10)
})
