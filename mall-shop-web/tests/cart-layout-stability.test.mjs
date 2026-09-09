import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
const source = readFileSync(new URL('../src/views/CartView.vue', import.meta.url), 'utf8')
test('H5购物车标题件数上下左对齐，管理按钮独立不压缩', () => {
  assert.match(source, /\.cart-header-left\s*\{[^}]*min-width:0;[^}]*flex-direction:column;[^}]*align-items:flex-start/)
  assert.match(source, /\.cart-header-actions\s*\{[^}]*flex:none/)
  assert.match(source, /\.cart-header h2\s*\{[^}]*white-space:nowrap/)
})
test('H5不切换价格库存核对提示，但保留校验与错误处理', () => {
  assert.doesNotMatch(source, /正在核对最新价格/)
  assert.match(source, /await checkCartPurchaseLimit\(item, 1, getProductQuantity\(item.id\)\)/)
  assert.match(source, /await validateCheckoutItems/)
  assert.match(source, /showToast\(error\?\.message/)
  assert.match(source, /商品价格和库存以结算页服务端确认为准/)
})
test('H5购物车增加按钮忙碌不变淡，仍禁重复提交', () => {
  assert.match(source, /:disabled="isQuantityChecking\(item\)" :aria-busy="isQuantityChecking\(item\)"/)
  assert.match(source, /\.quantity button \{ color: inherit; -webkit-tap-highlight-color: transparent/)
  assert.doesNotMatch(source, /\.quantity button:disabled\s*\{[^}]*opacity/)
  assert.match(source, /if \(isQuantityChecking\(item\)\) return/)
})
