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

test('public storefront excludes repurchase pages and rejects non-public checkout modes', () => {
  const router = read('../src/router/index.js')
  const checkout = read('../src/views/CheckoutView.vue')
  assert.doesNotMatch(router, /RepurchaseView|\/repurchase/)
  assert.match(checkout, /\['NORMAL', 'FLASH_SALE'\]\.includes\(businessType\)/)
  assert.doesNotMatch(checkout, /复购/)
})

test('optional business entries only render when enabled by tenant configuration', () => {
  const home = read('../src/views/HomeView.vue')
  assert.match(home, /config\.flashSaleEnabled/)
  assert.doesNotMatch(home, /config\.repurchaseMallEnabled|\/repurchase|复购/)
  assert.match(home, /v-if="businessEntries\.length"/)
})

test('public and team surfaces use independent route trees and build outputs', () => {
  const publicRouter = read('../src/router/index.js')
  const teamRouter = read('../src/surfaces/team/router.js')
  const vite = read('../vite.config.js')
  const packageJson = JSON.parse(read('../package.json'))
  assert.doesNotMatch(publicRouter, /InviteView|TeamPerformanceView|WalletView/)
  assert.match(teamRouter, /InviteView/)
  assert.match(teamRouter, /TeamPerformanceView/)
  assert.match(teamRouter, /WalletView/)
  assert.match(vite, /dist-team/)
  assert.equal(packageJson.scripts['build:public'].includes('check:public-surface'), true)
})
