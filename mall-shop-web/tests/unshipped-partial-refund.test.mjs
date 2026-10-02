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


test('89元退款完成后H5按钮显示0.01，实际POST只包含B，商家备注原文独立于状态', async () => {
  const { partialRefundTitle } = await import('../src/utils/orderItemRefunds.js')
  const items = [{ id: '1', quantity: 1, totalAmount: '89.00' }, { id: '2', quantity: 1, totalAmount: '0.01' }]
  const sales = [{ applyType: 4, status: 1, productRefundAmount: '89.00', auditRemark: '1', items: [{ orderItemId: '1', refundQuantity: 1 }] }]
  const calls = []
  const context = {
    computed: fn => ({ get value() { return fn() } }),
    detail: { value: { items } }, order: { value: { id: '10', status: 1, totalAmount: '89.01', freightAmount: '0.00' } },
    afterSales: { value: sales }, shipments: { value: [] }, refundQuantities: { value: {} },
    afterSaleErrors: { value: {} }, exceptionRefund: { value: false }, afterSaleForm: { value: { reasonDetail: '' } },
    selectedReason: { value: '' }, applyingAfterSale: { value: false }, submittingAfterSale: { value: false }, uploadingProofs: { value: false }, proofUploads: { value: [] },
    partialRefundTitle, money: n => Number(n).toFixed(2), clearProofUploads() {}, fetchOrder: async () => {},
    applyExceptionRefund: async (id, data) => calls.push({ id, data }), applyAfterSale: async () => { throw Error('错误请求') },
  }
  const start = source.indexOf('const usedQuantity ='), end = source.indexOf('const selectAfterSaleReason =')
  const submit = source.slice(source.indexOf('const submitAfterSale ='), source.indexOf('onMounted(() => {', source.indexOf('const submitAfterSale =')))
  vm.runInNewContext(source.slice(start, end) + submit + '\nthis.page = { startExceptionRefund, submitAfterSale, cancellationActionText, estimatedProductRefund, selectedRefundItems }', context)
  assert.equal(partialRefundTitle(context.order.value, items, sales), '部分退款完成，剩余1件待发货')
  assert.equal(context.page.cancellationActionText.value, '取消剩余商品并退款（¥0.01）')
  context.page.startExceptionRefund()
  assert.equal(Number(context.page.estimatedProductRefund.value.toFixed(2)), 0.01)
  await context.page.submitAfterSale()
  assert.equal(calls[0].id, '10')
  assert.deepEqual(JSON.parse(JSON.stringify(calls[0].data.items)), [{ orderItemId: '2', quantity: 1 }])
  assert.match(source, /typeof sale.auditRemark === 'string'/)
  assert.match(source, /商家处理说明：/)
})
