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
