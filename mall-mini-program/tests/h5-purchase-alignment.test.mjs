import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { commerceEnv } from './helpers/commerce-env.mjs'
import { localPurchaseLimitViolation } from '../../mall-shop-web/src/utils/purchaseLimitRules.js'
const require = createRequire(import.meta.url)
const rules = require('../utils/category-product')
const event = data => ({ currentTarget: { dataset: data } })
const detail = () => ({ product: { id: '1', productName: '测试商品', status: 1, salePrice: 19, stock: 20, purchaseLimit: 3 }, skus: [] })
const tick = () => new Promise(resolve => setImmediate(resolve))

test('本地限购提示与H5同规则：不同规格合并，不按单次点击重置', () => {
  for (const [existing, added] of [[3,1],[2,2],[0,4]]) {
    const d=detail(), rows=[{productId:'1',skuId:'other',quantity:existing}]
    assert.throws(() => rules.purchase(d, null, rows, added), {message:localPurchaseLimitViolation(d.product,added,existing)})
  }
})

for (const name of ['home', 'category']) {
  test(`${name}游客加购先登录并保留返回目标，不请求商品或写购物车`, async () => {
    const e=commerceEnv(() => detail(), ''), page=e.page(name)
    await page.quickAdd(event({id:'1'}))
    assert.equal(e.calls.length,0); assert.equal(e.load('utils/cart').list().length,0)
    assert.match(e.routes[0], new RegExp(encodeURIComponent(`/pages/${name}/index`)))
  })
}

for (const name of ['home','category','product','cart']) {
  test(`${name}加购统一发送历史限购校验，失败不增加数量`, async () => {
    const message='测试商品每位会员限购 3 件，您还可购买 1 件'
    const e=commerceEnv(({method})=>method==='POST'?{allowed:false,message}:detail()), cart=e.load('utils/cart'), page=e.page(name)
    cart.add({productId:'1',quantity:1,salePrice:19})
    if(name==='home'||name==='category')await page.quickAdd(event({id:'1'}))
    if(name==='product') {page.productId='1';page.setData({loading:false,product:detail().product,quantity:1});await page.addToCart()}
    if(name==='cart'){page.renderRows(cart.list());await page.quantity(event({key:'1:0',delta:1}))}
    assert.equal(cart.list().length,1);assert.equal(cart.list()[0].quantity,1)
    const check=e.calls.find(c=>c.url.endsWith('/purchase-limit/check'))
    assert.equal(check.method,'POST');assert.equal(check.params.quantity,2)
    assert.ok(e.notices.includes(message))
  })
}

test('不同SKU加购合并数量，后台降低限购后立即按新值拦截', async () => {
  const d=detail();d.product.purchaseLimit=1;d.skus=[{id:'11',stock:10,salePrice:20},{id:'12',stock:10,salePrice:30}]
  const e=commerceEnv(()=>d),cart=e.load('utils/cart'),p=e.page('product')
  cart.add({productId:'1',skuId:'12',quantity:1,salePrice:30})
  p.productId='1';p.setData({loading:false,product:{...d.product,purchaseLimit:10},skus:d.skus,skuIndex:0,quantity:1})
  await p.addToCart();assert.equal(cart.list().length,1);assert.equal(cart.list()[0].quantity,1)
  assert.match(e.notices.at(-1),/每位会员限购 1 件/)
  assert.equal(e.calls.filter(c=>c.method==='POST').length,0)
})

test('直接购买仅核对本次数量，不将未结算购物车算入且刷新单价', async () => {
  const d=detail();d.product.purchaseLimit=1;d.product.salePrice=25
  const e=commerceEnv(({method})=>method==='POST'?{allowed:true}:d),cart=e.load('utils/cart'),p=e.page('product')
  cart.add({productId:'1',quantity:2,salePrice:19});const before=JSON.stringify(cart.list())
  p.productId='1';p.setData({loading:false,product:d.product,quantity:1});await p.buyNow()
  assert.equal(e.calls.find(c=>c.method==='POST').params.quantity,1)
  assert.equal(cart.directItems()[0].salePrice,25);assert.equal(cart.directItems()[0].quantity,1)
  assert.equal(JSON.stringify(cart.list()),before);assert.equal(e.routes.at(-1),'/pages/checkout/index?direct=1')
})

test('购物车同SKU重复加购采用新单价且保留勾选状态', async () => {
  const e=commerceEnv(({method})=>method==='POST'?{allowed:true}:detail()),cart=e.load('utils/cart'),p=e.page('home')
  cart.add({productId:'1',quantity:1,salePrice:9});cart.update('1:0',{selected:false})
  await p.quickAdd(event({id:'1'}))
  assert.equal(cart.list()[0].quantity,2);assert.equal(cart.list()[0].salePrice,19);assert.equal(cart.list()[0].selected,false)
})

test('购物车结算只合并已选规格，未选商品不阻挡本次限购', async () => {
  const d=detail();d.product.purchaseLimit=1;d.skus=[{id:'11',stock:10,salePrice:20},{id:'12',stock:10,salePrice:30}]
  const e=commerceEnv(({method})=>method==='POST'?{allowed:true}:d),cart=e.load('utils/cart'),p=e.page('cart')
  cart.add({productId:'1',skuId:'11',quantity:1,salePrice:20});cart.add({productId:'1',skuId:'12',quantity:1,salePrice:30});cart.update('1:12',{selected:false})
  await p.refresh();assert.ok(p.data.rows.every(r=>r.unavailable));await p.checkout()
  assert.equal(e.calls.find(c=>c.method==='POST').params.quantity,1);assert.equal(e.routes.at(-1),'/pages/checkout/index')
})

test('结算页在剩余额度不足时停止，不获取地址、不报价、更不创建订单', async () => {
  const e=commerceEnv(({method})=>method==='POST'?{allowed:false,message:'已达到累计限购'}:detail()),cart=e.load('utils/cart'),p=e.page('checkout')
  cart.add({productId:'1',quantity:1,salePrice:19});await p.load()
  assert.match(p.data.loadError,/累计限购/);assert.equal(p.data.quoteReady,false)
  assert.equal(e.calls.filter(c=>c.url==='/shop/addresses'||c.url==='/shop/orders'||c.url.includes('freight-quote')).length,0)
})

test('首页加购或详情加购在换号/离开后不落入其他会话，重复点击只查一次', async () => {
  for(const name of ['home','product'])for(const change of ['hide','account']) {
    let resolve;const e=commerceEnv(()=>new Promise(r=>{resolve=r})),p=e.page(name),cart=e.load('utils/cart')
    p.productId='1';p.setData({loading:false,product:detail().product,quantity:1})
    const invoke=()=>name==='home'?p.quickAdd(event({id:'1'})):p.addToCart()
    const pending=invoke();await invoke();assert.equal(e.calls.length,1)
    if(change==='hide')p.onHide();else e.token('other')
    resolve(detail());await pending;assert.equal(cart.list().length,0)
  }
})

test('历史限购接口失败或返回不完整时不可静默加购', async () => {
  for(const result of [undefined,{}, {allowed:false}]) {
    const e=commerceEnv(({method})=>method==='POST'?result:detail()),p=e.page('home')
    await p.quickAdd(event({id:'1'}));assert.equal(e.load('utils/cart').list().length,0)
    assert.match(e.notices.at(-1),/限购/)
  }
})

test('列表/详情/购物车共用限购模块，首页按钮阻止冒泡并复用规格弹层', () => {
  const read=f=>readFileSync(new URL('../'+f,import.meta.url),'utf8')
  for(const f of ['utils/quick-cart.js','pages/product/index.js','pages/cart/index.js'])assert.match(read(f),/purchaseLimit\.checkAddition/)
  const home=read('pages/home/index.wxml');assert.match(home,/catchtap="quickAdd"/);assert.match(home,/include src="\/templates\/quick-cart.wxml"/)
  assert.match(read('pages/checkout/index.js'),/catalog\.refresh\(source\.rows, \{ checkLimits: true \}\)/)
})
