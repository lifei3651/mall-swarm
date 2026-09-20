import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const read = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8')

test('H5订单列表与小程序共用五行信息结构且商品只占一行', async () => {
  const view = await read('src/views/OrdersView.vue')
  const styles = await read('src/assets/styles.css')
  for (const row of ['ui-order-header', 'ui-order-product', 'ui-order-logistics', 'ui-order-summary', 'ui-order-actions']) {
    assert.match(view, new RegExp(`class="[^"]*${row}`), row)
    assert.match(styles, new RegExp(`\\.${row}`), row)
  }
  assert.doesNotMatch(view, /v-for="line in item\.items/)
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
  const detail = view.indexOf('>查看详情</RouterLink>')
  const pay = view.indexOf('ui-order-action--primary')
  assert.ok(detail >= 0 && pay > detail)
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
  assert.match(detail, /class="ui-copy-action"[^>]*>复制单号<\/button>/)
  assert.match(detail, /class="ui-copy-action"[^>]*>复制<\/button>/)
  assert.match(detail, /class="after-sale-record-actions ui-action-bar"/)
  assert.match(detail, /class="btn secondary ui-action-button after-sale-cancel"/)
  assert.match(detail, /class="btn primary ui-action-button ui-action-button--primary exchange-received-button"/)
  assert.doesNotMatch(list, /class="[^"]*btn primary[^"]*"[^>]*>去评价<\/RouterLink>/)
})
