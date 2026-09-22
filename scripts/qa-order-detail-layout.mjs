// Local-only rendered order-detail acceptance. Every API call is intercepted;
// this script never reads or mutates a live tenant.
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright')
const origin = process.env.QA_ORIGIN || 'http://127.0.0.1:4195'
const output = process.env.QA_OUTPUT || '/private/tmp/lingqi-order-detail-layout-qa'
await mkdir(output, { recursive: true })

const picture = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400"><rect width="400" height="400" rx="28" fill="#f3e8e6"/><rect x="118" y="72" width="164" height="250" rx="54" fill="#8d3130"/><path d="M135 88 L174 45 L200 96 L226 45 L265 88" fill="#8d3130"/><text x="200" y="365" text-anchor="middle" fill="#666" font-size="24">商品实拍图</text></svg>')
const order = {
  order: {
    id: '303', orderNo: 'QAORDER20260922001', status: 3, merchantName: '本地验收商城',
    totalAmount: 178, freightAmount: 0, discountAmount: 89, payAmount: 89,
    couponClaimId: '88', payType: 'WECHAT', createTime: '2026-06-12 10:00:09',
    payTime: '2026-06-12 10:00:26', deliveryTime: '2026-06-13 09:18:00',
    receiveTime: '2026-06-14 17:58:00', receiverName: '测试用户', receiverPhone: '13800000000',
    receiverProvince: '示例省', receiverCity: '演示市', receiverDistrict: '测试区',
    receiverDetailAddress: '样例路100号', receiverAddress: '示例省 演示市 测试区 样例路100号',
  },
  memberIncome: {
    totalAmount: 18.4, pendingAmount: 0, settledAmount: 18.4,
    details: [{ bonusType: '客户结算', commissionLevel: 1, commissionAmount: 18.4, status: 1, statusName: '已结算' }],
  },
  items: [
    { id: '1', productId: '71', productName: '本地验收商品 · 轻盈基础款背心', productCover: picture, skuName: 'M；砖红色', quantity: 1, totalAmount: 89, couponDiscountAmount: 44.5, serviceTags: JSON.stringify([{ title: '支持七天无理由', enabled: true }, { title: '晚发赔', enabled: true }]) },
    { id: '2', productId: '72', productName: '本地验收商品 · 轻盈基础款背心', productCover: picture, skuName: 'M；浅蓝色', quantity: 1, totalAmount: 89, couponDiscountAmount: 44.5, serviceTags: JSON.stringify([{ title: '支持七天无理由', enabled: true }, { title: '晚发赔', enabled: true }]) },
  ],
  shipments: [{ id: '901', deliveryCompany: '验收快递', deliveryNo: 'TEST202609220001', deliveryTime: '2026-06-13 09:18:00', shipmentQuantity: 2 }],
  afterSales: [], afterSaleDeadline: '2026-06-29 16:32:17', afterSaleSelfServiceEnabled: true,
  pendingReviewCount: 0, displayConfig: { showPv: 0 },
}
const tracking = [{ shipmentId: '901', deliveryNo: 'TEST202609220001', events: [{ description: '本地验收包裹已签收', location: '测试区样例路', eventTime: '2026-06-14 17:58:00' }] }]

const browser = await chromium.launch({ headless: true, channel: 'chrome' })
try {
  const unexpected = []
  const pageErrors = []
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, serviceWorkers: 'block', deviceScaleFactor: 1 })
  await context.route('**/*', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (!['127.0.0.1', 'localhost'].includes(url.hostname)) {
      unexpected.push(`${request.method()} ${request.url()}`)
      return route.abort()
    }
    if (!url.pathname.startsWith('/api/')) return route.continue()
    const path = url.pathname.slice(4)
    let data
    if (path === '/shop/public/profile') data = { member: { id: '9001', nickname: '本地验收账号' } }
    else if (path === '/shop/orders/303') data = order
    else if (path === '/shop/orders/303/tracking') data = tracking
    else if (path === '/shop/events/orders') return route.fulfill({ status: 404, json: { code: 404, message: '本地验收不启用SSE' } })
    else if (path === '/shop/home') data = { brandName: '本地验收商城', categories: [], banners: [], notices: [] }
    else if (path === '/shop/legal-config') data = {}
    else {
      unexpected.push(`${request.method()} ${path}`)
      return route.fulfill({ status: 404, json: { code: 404, message: '未定义的本地模拟接口' } })
    }
    return route.fulfill({ json: { code: 200, data } })
  })

  const page = await context.newPage()
  page.on('pageerror', (error) => pageErrors.push(error.message))
  await page.goto(`${origin}/orders/303`, { waitUntil: 'networkidle' })
  await page.locator('.products-card').waitFor()
  const measurement = await page.evaluate(() => {
    const top = (selector) => document.querySelector(selector)?.getBoundingClientRect().top ?? -1
    const actionBar = document.querySelector('.consumer-product-actions')
    const actions = [...actionBar.querySelectorAll('.ui-action-button')].map((element) => element.getBoundingClientRect())
    const productImages = [...document.querySelectorAll('.consumer-product-line img')].map((element) => element.getBoundingClientRect())
    return {
      bodyOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
      moduleTops: ['.consumer-status-hero', '.income-card', '.fulfillment-card', '.products-card', '.consumer-amount-card'].map(top),
      productImageSizes: productImages.map((box) => [Math.round(box.width), Math.round(box.height)]),
      actionHeights: actions.map((box) => Math.round(box.height * 10) / 10),
      actionsOneLine: actions.every((box) => Math.abs(box.top - actions[0].top) <= 1),
      actionOverflow: actionBar.scrollWidth > actionBar.clientWidth + 1,
      serviceTagCount: document.querySelectorAll('.consumer-service-tags span').length,
      incomeText: document.querySelector('.income-heading')?.textContent?.trim(),
      recipientText: document.querySelector('.consumer-recipient-row')?.textContent?.replace(/\s+/g, ' ').trim(),
    }
  })
  assert.equal(measurement.bodyOverflow, false)
  assert.deepEqual([...measurement.moduleTops].sort((a, b) => a - b), measurement.moduleTops)
  assert.ok(measurement.productImageSizes.every(([width, height]) => width >= 110 && height >= 110))
  assert.ok(measurement.actionHeights.every((height) => height >= 27 && height <= 29))
  assert.equal(measurement.actionsOneLine, true)
  assert.equal(measurement.actionOverflow, false)
  assert.equal(measurement.serviceTagCount, 4)
  assert.match(measurement.incomeText, /我的结算收入/)
  assert.match(measurement.recipientText, /测\*\*/)
  assert.match(measurement.recipientText, /138\*{4}0000/)
  assert.deepEqual(pageErrors, [])
  assert.deepEqual(unexpected, [])

  await page.screenshot({ path: `${output}/order-detail-full.png`, fullPage: true, animations: 'disabled' })
  await page.locator('.products-card').screenshot({ path: `${output}/order-detail-products.png`, animations: 'disabled' })
  await page.locator('.all-order-toggle').click()
  await page.locator('.consumer-all-info').waitFor()
  await page.locator('.consumer-all-info').screenshot({ path: `${output}/order-detail-all-info.png`, animations: 'disabled' })
  await page.evaluate(() => {
    const scroller = document.querySelector('.app-page-scroll')
    if (scroller) scroller.scrollTop = 0
    document.documentElement.classList.remove('shop-mobile-viewport-locked')
    const captureStyle = document.createElement('style')
    captureStyle.textContent = `
      html, body, #app, .app-shell, .app-page-scroll { height: auto !important; min-height: 0 !important; overflow: visible !important; position: static !important; }
      .bottom-nav { display: none !important; }
    `
    document.head.appendChild(captureStyle)
  })
  await page.screenshot({ path: `${output}/order-detail-content.png`, fullPage: true, animations: 'disabled' })
  await writeFile(`${output}/result.json`, JSON.stringify({ passed: true, scope: 'isolated rendered H5 order detail with intercepted APIs', viewport: [390, 844], measurement }, null, 2))
  console.log(JSON.stringify({ passed: true, output, measurement }))
  await context.close()
} finally {
  await browser.close()
}
