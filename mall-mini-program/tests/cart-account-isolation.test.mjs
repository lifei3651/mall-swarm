import test from 'node:test'
import assert from 'node:assert/strict'
import { commerceEnv } from './helpers/commerce-env.mjs'

const item = { productId: '1', quantity: 1, salePrice: 19 }
const switchMember = (env, id) => {
  env.token(`session-${id}`)
  env.storage.set('mall_mini_member', { id })
}

test('购物车按会员隔离，退出不展示，重新登录恢复本人商品', () => {
  const env = commerceEnv(), cart = env.load('utils/cart')
  cart.add(item)
  assert.equal(cart.beginDirectCheckout(item), true)
  switchMember(env, '2')
  assert.equal(cart.list().length, 0)
  assert.equal(cart.directItems().length, 0)
  cart.add({ ...item, quantity: 2 })
  env.token('')
  assert.equal(cart.list().length, 0)
  assert.throws(() => cart.add(item), /重新登录/)
  switchMember(env, '1')
  assert.equal(cart.list()[0].quantity, 1)
  switchMember(env, '2')
  assert.equal(cart.list()[0].quantity, 2)
})

test('旧共享购物车不自动认领、不删除，通过明确弹窗说明重新添加', async () => {
  const env = commerceEnv(), cart = env.load('utils/cart')
  const legacy = [{ ...item, productName: '归属不明的商品' }]
  env.storage.set('mall_mini_cart', legacy)
  assert.equal(cart.list().length, 0)
  assert.equal(cart.needsLegacyReview(), true)
  const page = env.page('cart')
  page.onShow()
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(env.notices.length, 1)
  assert.match(env.notices[0], /重新添加.*已有订单不受影响/)
  assert.doesNotMatch(env.notices[0], /归属不明的商品/)
  assert.equal(cart.needsLegacyReview(), false)
  assert.deepEqual(env.storage.get('mall_mini_cart'), legacy)
  switchMember(env, '2')
  assert.equal(cart.list().length, 0)
  assert.equal(env.calls.length, 0)
})

test('未登录或会员标识缺失不能写入共享回退区，大整数会员标识保持完整', () => {
  const env = commerceEnv(), cart = env.load('utils/cart')
  for (const member of [null, {}, { id: 0 }, { id: '../2' }, { id: Number.MAX_SAFE_INTEGER + 1 }]) {
    env.storage.set('mall_mini_member', member)
    assert.equal(cart.list().length, 0)
    assert.equal(cart.beginDirectCheckout(item), false)
    assert.throws(() => cart.add(item), /登录信息不完整/)
  }
  switchMember(env, '9223372036854775807')
  cart.add(item)
  assert.equal(env.storage.get('mall_mini_cart_v2:member:9223372036854775807')[0].quantity, 1)
  assert.equal(env.storage.has('mall_mini_cart'), false)
})

test('移除商品确认框打开后换号，不删除另一个账号的同款商品', () => {
  const env = commerceEnv(), cart = env.load('utils/cart'), page = env.page('cart')
  // Capture the wx callback through this VM's shared wx object.
  let confirm
  env.wx.showModal = options => { confirm = options.success }
  cart.add(item)
  page.remove({ currentTarget: { dataset: { key: '1:0' } } })
  switchMember(env, '2')
  cart.add(item)
  confirm({ confirm: true })
  assert.equal(cart.list().length, 1)
})

test('直接购买遇到不完整会员缓存给出明确提示，不静默无响应或继续结算', async () => {
  const product = { id: '1', productName: '测试商品', status: 1, salePrice: 19, stock: 20 }
  const env = commerceEnv(({ method }) => method === 'POST' ? { allowed: true } : { product, skus: [] })
  env.storage.delete('mall_mini_member')
  const page = env.page('product')
  page.productId = '1'
  page.setData({ loading: false, product, quantity: 1 })
  await page.buyNow()
  assert.equal(env.routes.length, 0)
  assert.match(env.notices.at(-1), /登录信息不完整.*重新登录/)
})
