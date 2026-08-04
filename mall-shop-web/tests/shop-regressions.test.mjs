import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { resolveQuickCartItem } from '../src/utils/quickCart.js'
import { extractModuleEntry } from '../src/utils/buildFreshness.js'

const readView = (name) => readFile(new URL(`../src/views/${name}`, import.meta.url), 'utf8')

test('quick add uses the first in-stock SKU without opening product detail', () => {
  const item = resolveQuickCartItem(
    { id: 7, productName: '套装', salePrice: 100, pvValue: 100, stock: 8 },
    {
      product: { id: 7, productName: '套装', coverUrl: '/product.jpg', pvValue: 80 },
      skus: [
        { id: 70, skuName: '缺货款', salePrice: 90, stock: 0 },
        { id: 71, skuName: '默认款', attrsJson: '{"规格":"默认"}', salePrice: 99, pvValue: 120, stock: 3 },
      ],
    },
  )

  assert.equal(item.skuId, 71)
  assert.equal(item.skuName, '默认款')
  assert.equal(item.salePrice, 99)
  assert.equal(item.pvValue, 99)
  assert.equal(item.stock, 3)
})

test('quick add rejects products whose SKUs are all out of stock', () => {
  const item = resolveQuickCartItem(
    { id: 7, stock: 8 },
    { product: { id: 7 }, skus: [{ id: 70, salePrice: 90, stock: 0 }] },
  )
  assert.equal(item, null)
})

test('profile hides unused labels and uses a reliable invite navigation', async () => {
  const source = await readView('ProfileView.vue')
  assert.doesNotMatch(source, /注册用户/)
  assert.doesNotMatch(source, /to="\/orders">全部/)
  assert.match(source, /window\.location\.assign\('\/invite'\)/)
})

test('checkout switches addresses inline so the remark field stays mounted', async () => {
  const source = await readView('CheckoutView.vue')
  assert.doesNotMatch(source, /router\.push\('\/profile\/addresses'\)/)
  assert.match(source, /showAllAddresses = !showAllAddresses/)
  assert.match(source, /v-model="form\.remark"/)
})

test('home quick add no longer redirects SKU products to product detail', async () => {
  const source = await readView('HomeView.vue')
  assert.match(source, /resolveQuickCartItem/)
  assert.doesNotMatch(source, /router\.push\(`\/product\/\$\{product\.id\}`\)/)
})

test('build freshness guard can identify the current production entry', () => {
  assert.equal(
    extractModuleEntry('<script type="module" crossorigin src="/assets/index-new.js"></script>'),
    '/assets/index-new.js',
  )
})

test('cart deletion requires confirmation but checkout navigates directly', async () => {
  const source = await readView('CartView.vue')
  assert.match(source, /@click="requestRemoveSelected"/)
  assert.match(source, /@click="requestCheckoutSelected"/)
  assert.match(source, /@click="requestCheckoutAll"/)
  assert.match(source, /确认删除选中商品/)
  assert.match(source, /if \(selectedKeys\.size\) checkoutSelected\(\)/)
  assert.match(source, /if \(items\.length\) checkoutAll\(\)/)
  assert.doesNotMatch(source, /确认进入结算/)
  assert.match(source, /confirmPendingAction/)
})

test('category view includes search bar for keyword filtering', async () => {
  const source = await readView('CategoryView.vue')
  assert.match(source, /cat-search-bar/)
  assert.match(source, /v-model="query\.keyword"/)
  assert.match(source, /@submit\.prevent="submitSearch"/)
  assert.match(source, /placeholder="搜索商品"/)
})

test('product detail renders rating distribution bars computed from star counts', async () => {
  const source = await readView('ProductDetailView.vue')
  assert.match(source, /barPercent/)
  assert.match(source, /countForStar/)
  assert.match(source, /rating-distribution/)
})

test('product detail always renders the standard after-sale policy section', async () => {
  const source = await readView('ProductDetailView.vue')
  assert.match(source, /after-sale-section/)
  assert.match(source, /defaultAfterSalePolicy/)
  assert.match(source, /售后说明/)
})

test('product detail back button falls back home when there is no browser history', async () => {
  const source = await readView('ProductDetailView.vue')
  assert.match(source, /@click="goBack"/)
  assert.match(source, /window\.history\.state\?\.back/)
  assert.match(source, /router\.replace\(\{ name: 'Home' \}\)/)
})

test('order detail renders logistics timeline with shipping steps', async () => {
  const source = await readView('OrderDetailView.vue')
  assert.match(source, /timeline-steps/)
  assert.match(source, /courierInitial/)
  assert.match(source, /estimatedDelivery/)
})
