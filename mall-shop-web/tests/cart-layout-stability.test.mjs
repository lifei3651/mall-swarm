import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
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
  assert.match(source, /<button :aria-busy="isQuantityChecking\(item\)" aria-label="增加数量"/)
  assert.doesNotMatch(source, /:disabled="isQuantityChecking/)
  assert.match(source, /\.quantity button \{ color: inherit; -webkit-tap-highlight-color: transparent/)
  assert.match(source, /if \(isQuantityChecking\(item\)\) return\s+if \(delta < 0\)/)
  assert.match(source, /:disabled="item.quantity <= 1"/)
})

for (const approved of [true, false]) test('H5慢数量校验：加减互斥，' + (approved ? '批准后更新一次' : '拒绝后数量不变'), async () => {
  let finish, requests = 0
  const item = { cartKey: '1:0', id: 1, quantity: 2, stock: 20 }, updates = [], notices = []
  const context = {
    quantityCheckingKeys: { value: new Set() },
    getProduct: async () => { requests++; return { data: {} } },
    resolveCurrentStock: () => 20,
    stockAdditionViolation: () => '',
    checkCartPurchaseLimit: () => new Promise((resolve, reject) => { finish = () => approved ? resolve() : reject(new Error('已达到限购')) }),
    getProductQuantity: () => item.quantity,
    update: (key, quantity) => { updates.push([key, quantity]); item.quantity = quantity },
    showToast: message => notices.push(message),
  }
  const start = source.indexOf('const changeQuantity ='), end = source.indexOf('const boundedPv =')
  assert.ok(start > 0 && end > start)
  vm.runInNewContext(source.slice(start, end) + '\nthis.change = changeQuantity', context)
  const pending = context.change(item, 1); await new Promise(resolve => setImmediate(resolve))
  await context.change(item, 1); await context.change(item, -1)
  assert.equal(requests, 1); assert.equal(item.quantity, 2); assert.equal(updates.length, 0)
  finish(); await pending
  assert.equal(item.quantity, approved ? 3 : 2); assert.equal(updates.length, approved ? 1 : 0)
  assert.equal(context.quantityCheckingKeys.value.size, 0)
  assert.equal(notices.length, approved ? 0 : 1)
})
