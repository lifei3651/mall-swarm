// Local-only browser acceptance. All API calls are intercepted; never use a live tenant.
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright')
const origin = 'http://127.0.0.1:4192'
const output = process.env.QA_OUTPUT || '/private/tmp/lingqi-page-layout-qa'
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ headless: true, channel: 'chrome' })
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, serviceWorkers: 'block' })
const errors = [], unexpected = [], writes = []
let tenant = { id: 1, brandName: '本地验收商城', tenantName: '本地验收商城', themeColor: '#e7193f', productTemplate: 'retail-red' }
let config = { id: 1, tenantId: 1, layoutTemplate: 'standard', newArrivalWindowDays: 30, extraConfigJson: JSON.stringify({ preservedExtension: { enabled: true } }) }
const picture = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300"><rect width="400" height="300" fill="#e2eee6"/><rect x="160" y="70" width="80" height="160" rx="8" fill="#16734b"/><text x="200" y="157" text-anchor="middle" fill="white" font-size="20">LQ</text></svg>')
const products = [1, 2, 3].map(id => ({ id, productName: ['日常护理套装', '便携保温杯', '轻奢生活礼盒'][id - 1], salePrice: 99 + id, coverUrl: picture, subtitle: '本地模拟商品', categoryId: id }))
await context.addInitScript(() => { localStorage.setItem('admin_session_present', '1'); localStorage.setItem('admin_session_expire_time', '2099-01-01T00:00:00Z') })
await context.route('**/*', async route => {
  const request = route.request(), url = new URL(request.url())
  if (!['127.0.0.1', 'localhost'].includes(url.hostname)) { unexpected.push(request.url()); return route.abort() }
  if (!url.pathname.startsWith('/api/')) return route.continue()
  const path = url.pathname.slice(4), method = request.method()
  let data
  if (path === '/distribution/admin-auth/me') data = { admin: { id: 1, nickname: '本地验收', roleCode: 'SUPER_ADMIN' }, permissions: ['*'], expireTime: '2099-01-01T00:00:00Z' }
  else if (path === '/distribution/tenant/list') data = { list: [tenant], total: 1 }
  else if (path === '/distribution/tenant/1/display-config') data = config
  else if (path === '/distribution/tenant' && method === 'POST') { tenant = request.postDataJSON(); data = tenant; writes.push(path) }
  else if (path === '/distribution/tenant/display-config' && method === 'POST') { config = request.postDataJSON(); data = config; writes.push(path) }
  else if (path === '/shop/admin/categories') data = [1, 2, 3].map(id => ({ id, categoryName: ['美妆护理', '家居生活', '品质礼品'][id - 1], showOnHome: 1, parentId: 0 }))
  else if (path === '/shop/home') data = { tenant, displayConfig: config }
  else if (path === '/shop/admin/events/orders') return route.fulfill({ contentType: 'text/event-stream', body: ': local mock\n\n' })
  else if (path === '/shop/admin/orders/work-summary') data = {}
  else if (['/distribution/merchants', '/shop/admin/merchant-product-reviews', '/distribution/merchant-finance/withdrawals'].includes(path)) data = { list: [], total: 0 }
  else if (path === '/shop/products') data = { list: products, total: products.length }
  else if (['/shop/admin/banners', '/shop/flash-sales'].includes(path)) data = []
  else { unexpected.push(method + ' ' + path); return route.fulfill({ status: 404, json: { code: 404, message: '未定义的本地模拟接口' } }) }
  return route.fulfill({ json: { code: 200, data } })
})
const page = await context.newPage()
page.on('pageerror', error => errors.push(error.message))
const choose = async (name, option) => {
  await page.locator('.el-select').filter({ has: page.getByRole('combobox', { name }) }).click()
  const list = await page.getByRole('combobox', { name }).getAttribute('aria-controls')
  await page.locator(`[id="${list}"]`).getByRole('option', { name: option, exact: true }).click()
  await page.locator(`[id="${list}"]`).waitFor({ state: 'hidden' })
}
const shot = name => page.screenshot({ path: `${output}/${name}.png`, fullPage: true, animations: 'disabled' })
try {
  await page.goto(`${origin}/admin/tenant/list`)
  await page.getByRole('button', { name: '进入装修工作台' }).click()
  await page.getByRole('region', { name: '页面版型设置' }).waitFor()
  await page.locator('.el-radio-button').filter({ hasText: /^小程序$/ }).click()
  await choose('分类页版型', '品类橱窗')
  await page.locator('.preview-guide-showcase').waitFor()
  assert.match(await page.locator('.preview-stage-heading').innerText(), /小程序 · 分类页/)
  await shot('admin-mini-category')
  await page.locator('.el-radio-button').filter({ hasText: /^H5$/ }).click()
  await page.locator('.layout-row').nth(1).getByRole('button', { name: '预览' }).click()
  await page.locator('.preview-category-list').waitFor()
  await page.locator('.el-radio-button').filter({ hasText: /^三端共用$/ }).click()
  await choose('商品详情版型', '留白主图')
  await page.locator('.preview-detail.is-inset').waitFor()
  await shot('admin-shared-product')
  await page.locator('.el-radio-button').filter({ hasText: /^小程序$/ }).click()
  await choose('分类页版型', '跟随共用')
  await page.locator('.preview-category-list').waitFor()
  await choose('分类页版型', '品类橱窗')
  await page.getByRole('button', { name: '保存发布', exact: true }).click()
  await page.getByRole('dialog', { name: '商城视觉装修工作台' }).waitFor({ state: 'hidden' })
  const saved = JSON.parse(config.extraConfigJson)
  assert.deepEqual(saved.pageLayouts.shared, { home: 'standard', category: 'list', product: 'inset' })
  assert.deepEqual(saved.pageLayouts.platforms.mini, { category: 'showcase' })
  assert.deepEqual(saved.pageLayouts.platforms.h5, {})
  assert.deepEqual(saved.preservedExtension, { enabled: true })
  assert.deepEqual(writes, ['/distribution/tenant', '/distribution/tenant/display-config'])
  await page.reload()
  await page.getByRole('button', { name: '进入装修工作台' }).click()
  await page.locator('.el-radio-button').filter({ hasText: /^小程序$/ }).click()
  await page.locator('.layout-row').nth(1).getByRole('button', { name: '预览' }).click()
  await page.locator('.preview-guide-showcase').waitFor()
  await page.setViewportSize({ width: 1280, height: 800 })
  await page.waitForFunction(() => document.querySelector('.page-layout-settings input[value="mini"]')?.checked === true)
  assert.equal(await page.locator('.page-layout-settings .el-radio-button.is-active').innerText(), '小程序')
  await shot('admin-1280-reloaded')
  const overflow = await page.locator('.layout-row').evaluateAll(rows => rows.some(row => row.scrollWidth > row.clientWidth + 1))
  assert.equal(overflow, false, 'layout editor must not overflow')
  assert.deepEqual(errors, [])
  assert.deepEqual(unexpected, [])
  const storefront = await browser.newContext({ viewport: { width: 375, height: 812 }, serviceWorkers: 'block' })
  await storefront.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url())
    if (!['127.0.0.1', 'localhost'].includes(url.hostname)) { unexpected.push(request.url()); return route.abort() }
    if (!url.pathname.startsWith('/api/')) return route.continue()
    const path = url.pathname.slice(4)
    let data
    if (path === '/shop/home') data = { ...tenant, displayConfig: config, categories: [], banners: [], notices: [] }
    else if (path === '/shop/legal-config') data = {}
    else if (path === '/shop/categories') data = [{ id: 1, categoryName: '护理套装', iconUrl: picture }]
    else if (path === '/shop/products') data = { list: products.map(p => ({ ...p, status: 1, stock: 20 })), total: 3 }
    else if (path === '/shop/products/1') data = { product: { ...products[0], status: 1, stock: 20 }, skus: [], displayConfig: config }
    else if (path === '/shop/products/1/reviews') data = { list: [], total: 0 }
    else if (path === '/shop/flash-sales') data = []
    else { unexpected.push(request.method() + ' ' + path); return route.fulfill({ status: 404, json: { code: 404 } }) }
    return route.fulfill({ json: { code: 200, data } })
  })
  const shop = await storefront.newPage()
  shop.on('pageerror', error => errors.push(error.message))
  await shop.goto('http://127.0.0.1:4193/category')
  await shop.getByText('日常护理套装', { exact: true }).first().waitFor()
  assert.equal(await shop.locator('.category-guide').count(), 0, 'H5 must not take mini override')
  await shop.screenshot({ path: `${output}/h5-category-list.png`, fullPage: true })
  const h5Config = JSON.parse(config.extraConfigJson)
  h5Config.pageLayouts.platforms.h5 = { home: 'campaign-feed', category: 'showcase' }
  config.extraConfigJson = JSON.stringify(h5Config)
  await shop.reload()
  await shop.locator('.category-guide.guide-showcase').waitFor()
  await shop.screenshot({ path: `${output}/h5-category-showcase.png`, fullPage: true })
  await shop.goto('http://127.0.0.1:4193/')
  await shop.locator('.home-page.layout-campaign-feed').waitFor()
  await shop.goto('http://127.0.0.1:4193/product/1')
  await shop.locator('.product-layout-inset .gallery-section').waitFor()
  await shop.locator('.gallery-section img').first().evaluate(img => img.decode())
  const gallery = await shop.locator('.gallery-section').boundingBox()
  assert.ok(Math.abs(gallery.width - 343) < 2, '375px viewport has 16px gallery margins')
  assert.ok(Math.abs(gallery.width / gallery.height - 4 / 3) < 0.03, 'inset main image retains native aspect ratio')
  await shop.screenshot({ path: `${output}/h5-product-inset.png`, fullPage: true })
  assert.deepEqual(errors, [])
  assert.deepEqual(unexpected, [])
  await writeFile(`${output}/result.json`, JSON.stringify({ passed: true, scope: 'isolated admin and H5 browser, mocked API persistence only', errors, unexpected, saved: saved.pageLayouts, screenshots: 6 }, null, 2))
  console.log(JSON.stringify({ passed: true, output, screenshots: 6 }))
} catch (error) {
  await shot('failure')
  console.error(JSON.stringify({ errors, unexpected, body: (await page.locator('body').innerText()).slice(0, 3000) }))
  throw error
} finally { await browser.close() }
