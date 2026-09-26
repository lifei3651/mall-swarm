import test from 'node:test'
import assert from 'node:assert/strict'
import { commerceEnv } from './helpers/commerce-env.mjs'
const event=id=>({currentTarget:{dataset:{id}}})
const coupon={id:'81',claimId:'91',title:'本地优惠券',amount:10,minimumAmount:100,businessTypes:['NORMAL'],usable:true,status:'AVAILABLE'}
test('领券失败重试复用请求标识，不重复领取；成功后重读真实列表',async()=>{
  let attempts=0
  const env=commerceEnv(async o=>{if(o.method==='POST'){if(++attempts===1)throw Error('网络中断');return coupon}return {list:[{...coupon,usable:attempts<2}],totalPage:1}})
  const page=env.page('coupons');page.onLoad({tab:'catalog'});page.data.couponEnabled=true;await page.load()
  await page.claim(event('81'));const key=env.calls.find(c=>c.method==='POST').data.requestId
  await page.claim(event('81'))
  const claims=env.calls.filter(c=>c.method==='POST');assert.equal(claims.length,2);assert.equal(claims[1].data.requestId,key)
  assert.equal(page.data.rows[0].usable,false);await page.claim(event('81'));assert.equal(attempts,2)
})
test('选券只传本人券标识，报价以服务端为准；不可用券不能选',async()=>{
  const env=commerceEnv(o=>o.url==='/payment/checkVerify'?{needVerify:false}:{productAmount:189,freightAmount:0,payAmount:179,discountAmount:10,selectedCouponClaimId:'91',couponEnabled:true,coupons:[coupon]})
  const page=env.page('checkout');page.data.address={id:'3'};page.data.rows=[{productId:'1',skuId:'2',quantity:1}];page.data.couponOptions=[coupon];page.data.couponEnabled=true
  page.openCoupons();await page.chooseCoupon(event('92'));assert.equal(env.calls.length,0)
  await page.chooseCoupon(event('91'));assert.equal(page.data.quoteReady,true);assert.equal(page.data.payTotal,'179.00')
  const payload=env.calls[0].data;assert.equal(payload.couponClaimId,'91');assert.equal(payload.discountAmount,undefined);assert.equal(payload.merchantPercent,undefined)
  env.token('different-member');page.openCoupons();env.token('another-member');await page.chooseCoupon(event('91'));assert.equal(env.calls.length,2)
})
test('优惠券总开关关闭时直达领券页仍只读历史，不发起新领取',async()=>{
  const env=commerceEnv(o=>o.url==='/shop/business-config'?{couponEnabled:0}:{list:[coupon],totalPage:1})
  const page=env.page('coupons');page.onLoad({tab:'catalog'});page.onShow()
  await new Promise(resolve=>setTimeout(resolve,0))
  assert.equal(page.data.tab,'mine');assert.equal(page.data.couponEnabled,false)
  assert.equal(env.calls.some(c=>c.url==='/shop/coupons'),false)
  await page.claim(event('81'));assert.equal(env.calls.some(c=>c.method==='POST'),false)
})
test('报价忽略已选优惠券时不允许付款；页面离开不应用旧响应',async()=>{
  let resolve
  const env=commerceEnv(()=>new Promise(done=>{resolve=done}));const page=env.page('checkout')
  page.data.rows=[{productId:'1',quantity:1}];page.data.couponClaimId='91'
  const quote=page.quoteFreight({id:'3'});resolve({productAmount:189,freightAmount:0,payAmount:189});await quote
  assert.equal(page.data.quoteReady,false)
})
