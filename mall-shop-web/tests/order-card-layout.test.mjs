import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { formatProductSpec } from '../src/utils/productSpec.js'

const read = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8')

test('H5订单列表与小程序共用清晰商品快照结构', async () => {
  const view = await read('src/views/OrdersView.vue')
  const styles = await read('src/assets/styles.css')
  for (const row of ['ui-order-header', 'ui-order-product', 'ui-order-logistics', 'ui-order-summary', 'ui-order-actions']) {
    assert.match(view, new RegExp(`class="[^"]*${row}`), row)
    assert.match(styles, new RegExp(`\\.${row}`), row)
  }
  assert.match(view, /v-for="line in item\.items/)
  assert.match(view, /class="ui-order-product-name"/)
  assert.match(view, /class="ui-order-product-spec"/)
  assert.match(view, /class="ui-order-service-tags"/)
  assert.match(view, /零售价 ¥\{\{ money\(line\.price\) \}\}/)
  assert.match(view, /lineAmountLabel\(item\)/)
  assert.match(view, /class="ui-copy-action ui-order-copy"/)
  assert.match(styles, /\.ui-order-product > img\s*\{[^}]*width:\s*84px;[^}]*height:\s*84px;/)
  assert.match(view, /class="ui-order-logistics"[\s\S]*未收到 \/ 拒收[\s\S]*class="ui-order-summary"/)
})

test('H5订单操作不换行、主按钮固定最右，取消和进行状态使用统一色阶', async () => {
  const [view, styles] = await Promise.all([read('src/views/OrdersView.vue'), read('src/assets/styles.css')])
  assert.match(styles, /\.ui-order-actions\s*\{[^}]*flex-wrap:\s*nowrap;[^}]*justify-content:\s*flex-end;/)
  assert.match(styles, /\.ui-order-action--primary\s*\{[^}]*order:\s*2;/)
  assert.match(view, /class="ui-status-pill ui-order-status" :class="orderStateClass\(item\)"/)
  assert.match(view, /status\) === 4\) return 'is-cancelled'/)
  assert.match(styles, /\.ui-order-status\.is-cancelled\s*\{[^}]*#7b8492[^}]*#f0f2f4/)
  assert.match(styles, /\.ui-order-status\.is-active\s*\{[^}]*var\(--brand-primary\)[^}]*var\(--brand-primary-soft\)/)
  assert.match(view, />查看物流<\/a>/)
  assert.match(view, />退换\/售后<\/RouterLink>/)
  assert.match(view, /再买一单/)
  const rebuy = view.indexOf('再买一单')
  const receive = view.indexOf('>确认收货</button>')
  assert.ok(rebuy >= 0 && receive > rebuy)
})

test('H5订单列表和详情的操作组共用右对齐且按钮没有自动左右外边距', async () => {
  const [list, detail, styles] = await Promise.all([
    read('src/views/OrdersView.vue'),
    read('src/views/OrderDetailView.vue'),
    read('src/assets/styles.css'),
  ])
  const buttonRule = styles.match(/\.ui-action-button, \.ui-order-action\s*\{([^}]*)\}/)?.[1] || ''
  assert.match(buttonRule, /margin-left:\s*0\s*!important;/)
  assert.match(buttonRule, /margin-right:\s*0\s*!important;/)
  assert.match(styles, /\.ui-action-bar, \.ui-action-group\s*\{[^}]*justify-content:\s*flex-end;/)
  assert.match(list, /class="order-actions ui-action-bar ui-order-actions"/)
  assert.match(detail, /class="consumer-product-actions ui-action-bar"/)
})

test('H5订单列表只在有自动收货期限时显示提示行，不复述订单或售后状态', async () => {
  const view = await read('src/views/OrdersView.vue')
  assert.match(view, /<div v-if="orderAutoReceiveText\(item\)" class="ui-order-logistics">/)
  assert.match(view, /const orderAutoReceiveText = \(item\) => Number\(item\.order\?\.status\) === 2 && item\.autoReceiveEnabled && !isAfterSale\(item\)/)
  for (const repeated of ['付款后将安排发货', '商家正在准备商品', '包裹已发出，可查看物流进度', '售后进度：', '订单已完成', '订单已取消']) {
    assert.ok(!view.includes(repeated), `订单列表不应重复显示：${repeated}`)
  }
})

test('H5普通操作按钮与小程序等比自适应，复制不再显示为胶囊大按钮', async () => {
  const [list, detail, styles] = await Promise.all([
    read('src/views/OrdersView.vue'),
    read('src/views/OrderDetailView.vue'),
    read('src/assets/styles.css'),
  ])
  assert.match(styles, /--shop-action-height:\s*28px;/)
  assert.match(styles, /--shop-action-font-size:\s*13px;/)
  assert.match(styles, /--shop-action-padding-x:\s*12px;/)
  assert.match(styles, /\.ui-action-button, \.ui-order-action\s*\{[^}]*flex:\s*none;[^}]*width:\s*auto\s*!important;/)
  assert.match(styles, /\.ui-copy-action\s*\{[^}]*background:\s*transparent\s*!important;/)
  assert.match(list, /class="ui-copy-action ui-order-copy"/)
  assert.match(detail, /class="ui-copy-action"[^>]*>复制单号<\/button>/)
  assert.match(detail, /class="ui-copy-action"[^>]*>复制<\/button>/)
  assert.match(detail, /class="after-sale-record-actions ui-action-bar"/)
  assert.match(detail, /class="btn secondary ui-action-button after-sale-cancel"/)
  assert.match(detail, /class="btn primary ui-action-button ui-action-button--primary exchange-received-button"/)
  assert.doesNotMatch(list, /class="[^"]*btn primary[^"]*"[^>]*>去评价<\/RouterLink>/)
  assert.doesNotMatch(list, /取消联合订单|支付全部子单/)
  assert.match(list, />取消订单<\/button>/)
  assert.match(list, />立即支付<\/RouterLink>/)
})

test('H5订单详情按状态、本人收入、物流收货人、商品和全部信息顺序收口', async () => {
  const view = await read('src/views/OrderDetailView.vue')
  const consumerDetail = view.split('<main v-if="!applyingAfterSale" class="consumer-order-detail">')[1]?.split('</main>')[0] || ''
  const positions = [
    'class="consumer-status-hero"',
    'class="consumer-card income-card ui-card"',
    'class="consumer-card fulfillment-card ui-card"',
    'class="consumer-card products-card ui-card"',
    'class="consumer-card consumer-amount-card ui-card"',
    'class="consumer-card consumer-all-info ui-card"',
  ].map((needle) => view.indexOf(needle))
  assert.ok(positions.every((position) => position >= 0))
  assert.deepEqual([...positions].sort((a, b) => a - b), positions)
  assert.match(view, /detail\.memberIncome/)
  assert.match(view, /class="consumer-recipient-row"/)
  assert.match(view, /class="consumer-service-tags"/)
  assert.match(view, /售后期截止时间/)
  assert.match(consumerDetail, /class="consumer-carrier-link"[^>]*:href="trackingUrl\(shipment\)"/)
  assert.doesNotMatch(consumerDetail, /consumer-logistics-head|>查看物流<\/a>|>还想买<\/RouterLink>|再买一单/)
  assert.doesNotMatch(view, /getOrderTracking/)
  assert.doesNotMatch(view, /运费险/)
})

test('H5退款关闭订单与取消订单分开展示，缺失规格不显示null', async () => {
  const [detail, list] = await Promise.all([read('src/views/OrderDetailView.vue'), read('src/views/OrdersView.vue')])
  assert.match(detail, /const isRefundedOrder = computed\(\(\) => Number\(order\.value\?\.status\) === 4/)
  assert.match(detail, /isRefundedOrder\.value \? '已退款'/)
  assert.match(detail, /if \(Number\(order\.value\?\.status\) === 4\) return '该订单已关闭，无需继续付款'/)
  assert.match(list, /if \(isRefundedOrder\(item\)\) return '已退款'/)
  assert.match(list, /if \(Number\(item\.order\?\.status\) === 4\) return '已取消'/)
  assert.equal(formatProductSpec({ skuName: null }), '单规格')
  assert.equal(formatProductSpec({ skuName: 'null' }), '单规格')
  assert.equal(formatProductSpec({ skuName: 'M' }), 'M')
})
