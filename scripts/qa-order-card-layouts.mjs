// Local-only rendered order-card acceptance. Every API call is intercepted;
// this script never reads or mutates a live tenant.
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright')
const origin = process.env.QA_ORIGIN || 'http://127.0.0.1:4194'
const output = process.env.QA_OUTPUT || '/private/tmp/lingqi-order-card-layout-qa'
await mkdir(output, { recursive: true })

const picture = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200"><rect width="200" height="200" rx="24" fill="#e5efec"/><circle cx="100" cy="92" r="52" fill="#137d72"/><text x="100" y="104" text-anchor="middle" fill="white" font-size="34">LQ</text></svg>')
const future = '2099-09-21 12:00:00'
const line = (name, quantity = 1) => ({
  id: `${name}-line`,
  productId: 1,
  productName: `${name} · 轻奢护理套装超长名称用于验证单行省略`,
  productCover: picture,
  skuName: '默认规格',
  quantity,
  price: 89,
  totalAmount: 89 * quantity,
  couponDiscountAmount: 44.5 * quantity,
  serviceTags: JSON.stringify([{ title: '七天无理由', enabled: true }, { title: '晚发赔', enabled: true }]),
})
const row = (id, status, extra = {}) => ({
  order: {
    id,
    orderNo: `LQ-RENDER-${id}-LONG-ORDER-NUMBER`,
    merchantName: '商城订单',
    status,
    payAmount: status === 4 ? 0 : 89,
    createTime: '2026-09-21 08:00:00',
    ...extra.order,
  },
  items: extra.items || [line(extra.name || `订单${id}`)],
  shipments: extra.shipments || [],
  afterSales: extra.afterSales || [],
  afterSaleDeadline: future,
  afterSaleSelfServiceEnabled: true,
  autoReceiveEnabled: Boolean(extra.autoReceiveEnabled),
  autoReceiveDeadline: extra.autoReceiveDeadline,
  pendingReviewCount: extra.pendingReviewCount || 0,
  pendingReviewProductId: extra.pendingReviewProductId,
  pendingReviewOrderItemId: extra.pendingReviewOrderItemId,
})

const orders = [
  row(101, 0, { name: '待支付', order: { tradeId: 'trade-101', tradeNo: 'T-101' } }),
  row(102, 1, { name: '待发货' }),
  row(103, 2, { name: '待收货', autoReceiveEnabled: true, autoReceiveDeadline: future, shipments: [{ id: 1031, deliveryCompany: '圆通速递', deliveryNo: 'YT0000000103' }] }),
  row(104, 3, { name: '已完成待评价', items: [line('已完成商品一'), line('已完成商品二')], shipments: [{ id: 1041, deliveryCompany: '圆通速递', deliveryNo: 'YT0000000104' }], pendingReviewCount: 1, pendingReviewProductId: 1, pendingReviewOrderItemId: 1041 }),
  row(105, 4, { name: '已取消' }),
]

const browser = await chromium.launch({ headless: true, channel: 'chrome' })
const results = []
try {
  for (const width of [320, 390]) {
    const unexpected = []
    const pageErrors = []
    const context = await browser.newContext({ viewport: { width, height: 844 }, serviceWorkers: 'block' })
    await context.route('**/*', async (route) => {
      const request = route.request()
      const url = new URL(request.url())
      if (!['127.0.0.1', 'localhost'].includes(url.hostname)) {
        unexpected.push(request.method() + ' ' + request.url())
        return route.abort()
      }
      if (!url.pathname.startsWith('/api/')) return route.continue()

      const path = url.pathname.slice(4)
      let data
      if (path === '/shop/public/profile') data = { member: { id: 9001, nickname: '本地验收账号' } }
      else if (path === '/shop/profile/order-summary') data = { pendingPayment: 1, pendingShipment: 1, pendingReceipt: 1, pendingReview: 1, afterSale: 0 }
      else if (path === '/shop/orders') data = { list: orders, total: orders.length }
      else if (path === '/shop/events/orders') return route.fulfill({ status: 404, json: { code: 404, message: '本地验收不启用SSE' } })
      else if (path === '/shop/home') data = { brandName: '本地验收商城', categories: [], banners: [], notices: [] }
      else if (path === '/shop/legal-config') data = {}
      else {
        unexpected.push(request.method() + ' ' + path)
        return route.fulfill({ status: 404, json: { code: 404, message: '未定义的本地模拟接口' } })
      }
      return route.fulfill({ json: { code: 200, data } })
    })

    const page = await context.newPage()
    page.on('pageerror', (error) => pageErrors.push(error.message))
    await page.goto(`${origin}/orders`, { waitUntil: 'networkidle' })
    await page.locator('.ui-order-card').last().waitFor()

    const measurement = await page.evaluate(() => {
      const rect = (element) => element.getBoundingClientRect()
      const visibleWithin = (child, parent) => {
        const a = rect(child), b = rect(parent)
        return a.left >= b.left - 1 && a.right <= b.right + 1 && a.top >= b.top - 1 && a.bottom <= b.bottom + 1
      }
      const cards = [...document.querySelectorAll('.ui-order-card')].map((card) => {
        const header = card.querySelector('.ui-order-header')
        const heading = card.querySelector('.ui-order-heading')
        const status = card.querySelector('.ui-order-status')
        const copy = card.querySelector('.ui-order-copy')
        const products = [...card.querySelectorAll('.ui-order-product')]
        const logistics = card.querySelector('.ui-order-logistics')
        const summary = card.querySelector('.ui-order-summary')
        const actions = card.querySelector('.ui-order-actions')
        const buttons = [...actions.querySelectorAll('.ui-order-action')]
        const primary = actions.querySelector('.ui-order-action--primary')
        const buttonRects = buttons.map(rect)
        return {
          id: card.dataset.orderNo,
          height: rect(card).height,
          itemCount: products.length,
          headerOneLine: Math.abs((rect(heading).top + rect(heading).bottom) / 2 - (rect(copy).top + rect(copy).bottom) / 2) <= 2,
          statusVisible: visibleWithin(status, header),
          copyVisible: visibleWithin(copy, header),
          productImagesLarge: products.every((product) => rect(product.querySelector('img')).width >= 82 && rect(product.querySelector('img')).height >= 82),
          productDetailsVisible: products.every((product) => ['.ui-order-product-name', '.ui-order-product-spec', '.ui-order-product-prices'].every((selector) => visibleWithin(product.querySelector(selector), product))),
          serviceTagsVisible: products.every((product) => product.querySelectorAll('.ui-order-service-tags > span').length === 2),
          logisticsOneLine: logistics.scrollHeight <= logistics.clientHeight + 1,
          summaryOneLine: summary.scrollHeight <= summary.clientHeight + 1,
          actionsOneLine: buttonRects.every((box) => Math.abs(box.top - buttonRects[0].top) <= 1),
          actionsNoOverflow: actions.scrollWidth <= actions.clientWidth + 1 && buttons.every((button) => visibleWithin(button, actions)),
          actionHeights: buttonRects.map((box) => Math.round(box.height * 10) / 10),
          primaryRightmost: !primary || Math.abs(rect(primary).right - Math.max(...buttonRects.map((box) => box.right))) <= 1,
          rowsOrdered: [header, card.querySelector('.ui-order-products'), logistics, summary, actions].every((element, index, rows) => index === 0 || rect(element).top >= rect(rows[index - 1]).bottom - 1),
        }
      })
      return {
        bodyOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
        cards,
      }
    })

    assert.equal(measurement.bodyOverflow, false, `${width}px 页面不得横向溢出`)
    assert.equal(measurement.cards.length, orders.length)
    for (const card of measurement.cards) {
      assert.ok(card.height <= 300 + Math.max(0, card.itemCount - 1) * 90, `${width}px ${card.id} 卡片高度必须保持紧凑，实际${card.height}px`)
      assert.equal(card.headerOneLine, true, `${width}px ${card.id} 标题与状态必须同一行`)
      assert.equal(card.statusVisible, true, `${width}px ${card.id} 状态必须完整可见`)
      assert.equal(card.copyVisible, true, `${width}px ${card.id} 复制订单号必须位于头部且完整可见`)
      assert.equal(card.productImagesLarge, true, `${width}px ${card.id} 商品图不得小于82px`)
      assert.equal(card.productDetailsVisible, true, `${width}px ${card.id} 商品名、规格和价格必须完整落在商品区域`)
      assert.equal(card.serviceTagsVisible, true, `${width}px ${card.id} 两个真实保障标签必须可见`)
      assert.equal(card.logisticsOneLine, true, `${width}px ${card.id} 物流提示必须单行`)
      assert.equal(card.summaryOneLine, true, `${width}px ${card.id} 件数与实付必须同一行`)
      assert.equal(card.actionsOneLine, true, `${width}px ${card.id} 操作按钮必须同一行`)
      assert.equal(card.actionsNoOverflow, true, `${width}px ${card.id} 操作按钮不得裁切或溢出`)
      assert.equal(card.primaryRightmost, true, `${width}px ${card.id} 主按钮必须最右`)
      assert.equal(card.rowsOrdered, true, `${width}px ${card.id} 五行顺序错误`)
      assert.ok(card.actionHeights.every((height) => height >= 26 && height <= 28), `${width}px ${card.id} 普通按钮高度必须保持26–28px`)
    }
    assert.deepEqual(pageErrors, [])
    assert.deepEqual(unexpected, [])
    await page.screenshot({ path: `${output}/orders-${width}.png`, fullPage: true, animations: 'disabled' })
    results.push({ width, ...measurement })
    await context.close()
  }
  await writeFile(`${output}/result.json`, JSON.stringify({ passed: true, scope: 'isolated rendered H5 order cards with intercepted APIs', results }, null, 2))
  console.log(JSON.stringify({ passed: true, output, viewports: results.map((item) => item.width), cardsPerViewport: orders.length }))
} finally {
  await browser.close()
}
