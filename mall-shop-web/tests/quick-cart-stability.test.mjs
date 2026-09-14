import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
const read = name => readFileSync(new URL('../src/views/' + name + '.vue', import.meta.url), 'utf8')

for (const name of ['HomeView', 'CategoryView']) test(name + '：仅售罄置灰，短时加购锁不改变按钮视觉', () => {
  const source = read(name)
  const buttons = source.match(/<button\b[^>]*isAddingProduct\(product.id\)[^>]*>/g)
  assert.equal(buttons.length, name === 'HomeView' ? 1 : 4)
  for (const button of buttons) {
    assert.match(button, /:aria-busy="isAddingProduct\(product.id\)"/)
    assert.match(button, /:disabled="product.status !== 1 \|\| product.stock <= 0"/)
    assert.doesNotMatch(button, /:disabled="[^"]*isAddingProduct/)
  }
  assert.match(source, /:disabled:not\(\[aria-busy="true"\]\)/)
  assert.doesNotMatch(source, /(?:home-cart-button|quick-cart-button|guide-product-card > div button):disabled\s*\{/)
  assert.doesNotMatch(source, /quick-cart-button:hover:not\(:disabled\)/)
  assert.match(source, /if \(isAddingProduct\(product.id\)\) return/)
  assert.match(source, /await checkCartPurchaseLimit\(cartItem, 1, getProductQuantity\(cartItem.id\)\)/)
})

test('商品详情加购与立即购买共用防重复锁，但不会互相置灰或改字', () => {
  const source = read('ProductDetailView')
  const addButton = source.match(/<button class="main-action cart-action"[^>]*>/)?.[0] || ''
  const buyButton = source.match(/<button class="main-action buy-action"[^>]*>/)?.[0] || ''
  assert.match(addButton, /:disabled="soldOut"/)
  assert.match(addButton, /:aria-busy="activePurchaseAction === 'cart'"/)
  assert.doesNotMatch(addButton, /purchaseActionPending/)
  assert.match(buyButton, /:disabled="soldOut"/)
  assert.match(buyButton, /:aria-busy="activePurchaseAction === 'buy'"/)
  assert.doesNotMatch(buyButton, /purchaseActionPending|正在核对/)
  assert.match(source, /if \(purchaseActionPending\.value\) return/)
})

test('首页和分类售罄提示覆盖商品图片居中显示并提高字号', () => {
  const home = read('HomeView')
  const category = read('CategoryView')
  assert.match(home, /\.home-sold-out\s*\{[^}]*inset: 0;[^}]*place-items: center;[^}]*font-size: 17px;/)
  assert.match(category, /\.sold-out-mask\s*\{[^}]*inset: 0;[^}]*place-items: center;[^}]*font-size: 18px;/)
})
