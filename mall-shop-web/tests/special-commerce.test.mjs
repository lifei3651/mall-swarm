import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8')

test('flash sale uses a dedicated endpoint and keeps its business source through checkout', () => {
  const api = read('../src/api/shop.js')
  const page = read('../src/views/FlashSaleView.vue')
  const checkout = read('../src/views/CheckoutView.vue')
  assert.match(api, /flash-sales\/\$\{activityId\}\/orders/)
  assert.match(page, /businessType: 'FLASH_SALE'/)
  assert.match(page, /businessSourceId: row\.activity\.id/)
  assert.match(checkout, /submitFlashSaleOrder\(businessSourceId/)
})

test('repurchase products use an isolated direct checkout and cannot mix with the ordinary cart', () => {
  const page = read('../src/views/RepurchaseView.vue')
  const checkout = read('../src/views/CheckoutView.vue')
  assert.match(page, /businessType:'REPURCHASE'/)
  assert.match(page, /beginDirectCheckout/)
  assert.match(checkout, /普通商品、秒杀商品和复购商品不能混合下单/)
})

test('optional business entries only render when enabled by tenant configuration', () => {
  const home = read('../src/views/HomeView.vue')
  assert.match(home, /config\.flashSaleEnabled/)
  assert.match(home, /config\.repurchaseMallEnabled/)
  assert.match(home, /v-if="businessEntries\.length"/)
})
