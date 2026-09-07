import test from 'node:test'
import assert from 'node:assert/strict'
import { commerceEnv } from './helpers/commerce-env.mjs'
const deferred = () => { let resolve; const promise = new Promise(ok => { resolve = ok }); return { promise, resolve } }
const clone = value => JSON.parse(JSON.stringify(value))
test('售后估算复用H5优惠分摊、已退金额扣除、整单未发货退运费和换货零退款', () => {
  const policy=commerceEnv().load('pages/order-detail/policy')
  const detail={order:{status:1,totalAmount:100,discountAmount:20,freightAmount:8},items:[{id:'1',quantity:2,totalAmount:100}],afterSales:[]}
  assert.deepEqual(clone(policy.refundEstimate(detail,[{id:'1',selectedQuantity:1}],1)),{product:40,freight:0,total:40})
  assert.equal(policy.refundEstimate(detail,[{id:'1',selectedQuantity:2}],2).total,88)
  detail.afterSales=[{applyType:1,status:1,productRefundAmount:40,items:[{orderItemId:'1',refundQuantity:1}]}]
  assert.equal(policy.refundEstimate(detail,[{id:'1',selectedQuantity:1}],1).total,48)
  detail.order.status=2;assert.equal(policy.refundEstimate(detail,[{id:'1',selectedQuantity:1}],1).total,40)
  assert.equal(policy.refundEstimate(detail,[{id:'1',selectedQuantity:1}],3).total,0)
})
const wallet = { balance: '100.00', hasPaymentPassword: true, paymentPasswordLocked: false }
test('订单取消、收货、撤销售后、换货收货的旧确认框不能作用到新登录会话', async () => {
  for (const handler of ['receive','cancelOrder','cancelAfterSale','receiveExchange']) {
    const env=commerceEnv(),page=env.page('order-detail');let dialog;env.wx.showModal=options=>{dialog=options}
    page.setData({rows:[{order:{id:'10'},afterSales:[{id:'10',canReceiveExchange:true}]}]});page[handler]({currentTarget:{dataset:{id:'10'}}});assert.ok(dialog)
    env.token('another');await dialog.success({confirm:true});assert.equal(env.calls.length,0)
  }
})
test('售后上传旧账号的401不清除新会话，也不接纳旧文件结果', async () => {
  const env=commerceEnv(),page=env.page('after-sale');let upload;env.wx.uploadFile=options=>{upload=options};page.setData({orderId:'10'})
  const task=page.uploadProof({path:'/tmp/local-only.png'});env.token('another');upload.success({statusCode:401,data:'{"code":401}'})
  await assert.rejects(task,/登录状态/);assert.equal(env.storage.get('mall_mini_access_token'),'another')
})
test('评价正在提交时切后台不解除互斥锁，回来连点仍只有一次请求', async () => {
  const pending=deferred(),env=commerceEnv(()=>pending.promise),page=env.page('product');page.productId='10';page.setData({reviewContent:'测试评价',reviewRating:5})
  const task=page.submitReview();page.onHide();page.purchaseInactive=false;await page.submitReview();assert.equal(env.calls.length,1);pending.resolve({});await task;assert.equal(page.data.reviewSubmitting,false)
})
const rows = [{ order: { id: '11', tradeId: '70', payType: 'BALANCE', status: 0, payAmount: '20.00' } }, { order: { id: '12', tradeId: '70', payType: 'BALANCE', status: 0, payAmount: '30.00' } }]
function setup(respond) {
  let currentRows = clone(rows)
  const env = commerceEnv(async options => options.url === '/shop/wallet/summary' ? wallet : respond?.(options))
  const balance = env.load('utils/balance-order'), policy = env.load('pages/order-detail/policy')
  const page = env.page('order-detail')
  page.load = async () => { page.setData({ rows: clone(currentRows), ...policy.paymentSummary(currentRows) }); return true }
  page.setData({ ...balance.data, rows: clone(rows), ...policy.paymentSummary(rows) })
  return { env, page, policy, setRows(value) { currentRows = clone(value) } }
}
test('余额续付必须同一父交易、同一渠道、完整正数金额；不把子单金额当总额', () => {
  const {policy}=setup(); assert.equal(policy.paymentSummary(rows).totalText,'50.00'); assert.equal(policy.paymentSummary(rows).payOrderId,'11')
  for (const changed of [rows.map(r=>({...r,order:{...r.order,payAmount:0}})),[rows[0],{order:{...rows[1].order,payType:'WECHAT'}}],[rows[0],{order:{...rows[1].order,tradeId:'71'}}],[rows[0],{order:{...rows[1].order,payAmount:-1}}]]) assert.equal(policy.paymentSummary(changed).payOrderId,'')
})
test('余额付款提交H5同接口和独立密码，重复点击只有一次；成功以重查状态为准', async () => {
  const pending=deferred(); const s=setup(()=>pending.promise),{env,page}=s
  await page.openBalancePayment(); assert.equal(page.data.balanceDialog,true)
  page.balanceInput({detail:{value:'654321'}}); const payment=page.confirmBalancePayment(); await new Promise(resolve=>setImmediate(resolve))
  await page.confirmBalancePayment(); assert.equal(env.calls.filter(c=>c.method==='POST').length,1)
  const post=env.calls.find(c=>c.method==='POST'); assert.equal(post.url,'/shop/wallet/orders/11/pay'); assert.deepEqual(post.data,{paymentPassword:'654321'}); assert.ok(post.idempotencyKey)
  s.setRows(rows.map(r=>({...r,order:{...r.order,status:1}}))); pending.resolve({}); await payment
  assert.ok(env.notices.some(n=>n.includes('支付已确认'))); assert.equal(page.data.balancePassword,''); assert.equal(page.data.balanceBusy,false)
})
test('余额不足、锁定、状态失败不弹付款；金额变更或切换账号不能提交', async () => {
  for (const bad of [null,{...wallet,balance:1},{...wallet,paymentPasswordLocked:true}]) {
    const env=commerceEnv(()=>bad),page=env.page('order-detail'); page.load=async()=>true; page.setData({rows:clone(rows),payOrderId:'11',paymentChannel:'BALANCE',totalText:'50.00'}); await page.openBalancePayment(); assert.equal(page.data.balanceDialog,false); assert.equal(page.data.actingId,null)
  }
  const s=setup(),{page,env}=s; await page.openBalancePayment(); page.balanceInput({detail:{value:'654321'}}); s.setRows(rows.map(r=>({...r,order:{...r.order,payAmount:10}}))); await page.confirmBalancePayment(); assert.equal(env.calls.some(c=>c.method==='POST'),false)
  await page.openBalancePayment(); page.balanceInput({detail:{value:'654321'}}); env.token('another'); await page.confirmBalancePayment(); assert.equal(env.calls.some(c=>c.method==='POST'),false)
})
test('余额请求结果未知不冒充成功、不重建订单；相同订单重试保持幂等键', async () => {
  const s=setup(()=>{throw new Error('请求超时')}),{page,env}=s
  for(let i=0;i<2;i++){await page.openBalancePayment();page.balanceInput({detail:{value:'654321'}});await page.confirmBalancePayment()}
  const posts=env.calls.filter(c=>c.method==='POST'); assert.equal(posts.length,2); assert.equal(posts[0].idempotencyKey,posts[1].idempotencyKey); assert.ok(env.notices.some(n=>n.includes('如已扣款，勿重复付款'))); assert.equal(env.calls.some(c=>c.url==='/shop/orders'&&c.method==='POST'),false)
})
test('首次未设支付密码引导安全页，不使用登录密码直接扣款', async () => {
  const env=commerceEnv(()=>({...wallet,hasPaymentPassword:false})),page=env.page('order-detail'); page.load=async()=>true;page.setData({rows:clone(rows),payOrderId:'11',paymentChannel:'BALANCE',totalText:'50.00'}); await page.openBalancePayment(); page.setupPaymentPassword(); assert.equal(env.routes[0],'/pages/account-settings/index?section=payment'); assert.equal(page.data.balanceDialog,false); assert.equal(env.calls.some(c=>c.method==='POST'),false)
})
test('首页搜索/分类保持原页、60条查询、最近5条去重，不读取剪贴板', async () => {
  const env=commerceEnv(()=>({list:[]})),page=env.page('home'); page.setData({keyword:' 护理 '}); await page.search(); assert.equal(env.routes.length,0); assert.deepEqual(env.calls.at(-1).params,{status:1,pageNum:1,pageSize:60,keyword:'护理',categoryName:''})
  await page.openCategory({currentTarget:{dataset:{name:'健康'}}}); assert.equal(env.calls.at(-1).params.categoryName,'健康'); await page.openCategory({currentTarget:{dataset:{name:'健康'}}}); assert.equal(env.calls.at(-1).params.categoryName,'')
  const history=env.load('utils/search-history'); for(const word of ['1','2','3','4','5','6','3'])history.remember(word); assert.deepEqual(Array.from(history.list()),['3','6','5','4','2']); await page.clearFilter(); assert.equal(page.data.searchedKeyword,'')
})
test('首页慢搜索/慢刷新不能覆盖新筛选；离开后不回写商品', async () => {
  const first=deferred(),second=deferred();let count=0
  const env=commerceEnv(({url})=>url==='/shop/home'?{categoryList:[],displayConfig:{}}:++count===1?first.promise:second.promise),page=env.page('home')
  const refresh=page.fetchHome(true); page.setData({keyword:'新查询'});const search=page.search();second.resolve({list:[{id:'2',name:'新结果'}]}); await search;first.resolve({list:[{id:'1',name:'旧结果'}]});await refresh;assert.equal(page.data.products[0].id,'2')
  const delayed=deferred(),env2=commerceEnv(()=>delayed.promise),page2=env2.page('home');const work=page2.filterProducts();page2.onHide();delayed.resolve({list:[{id:'3'}]});await work;assert.equal(page2.data.products.length,0)
})
