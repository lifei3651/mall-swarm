import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { readFileSync } from 'node:fs'
import { couponRefundPreview, couponQuoteValid, canReviseRejectedOrder } from '../src/utils/couponAmounts.js'
const native = createRequire(import.meta.url)('../../mall-mini-program/utils/h5-rules/couponAmounts.js')

for (const [surface, rules] of [['H5', {couponRefundPreview,couponQuoteValid}], ['小程序', native]]) {
  test(`${surface} 拒绝优惠券被旧接口忽略、非数字及金额不守恒的报价`, () => {
    const q={productAmount:'189',freightAmount:'0',discountAmount:'10',payAmount:'179',selectedCouponClaimId:'9223372036854775701'}
    assert.equal(rules.couponQuoteValid(q,q.selectedCouponClaimId),true)
    assert.equal(rules.couponQuoteValid({...q,payAmount:'189'},q.selectedCouponClaimId),false)
    assert.equal(rules.couponQuoteValid({...q,selectedCouponClaimId:null},q.selectedCouponClaimId),false)
    assert.equal(rules.couponQuoteValid({...q,discountAmount:'bad'},q.selectedCouponClaimId),false)
    assert.equal(rules.couponQuoteValid(q,null),false)
    assert.equal(rules.couponQuoteValid({productAmount:'189',freightAmount:0,payAmount:189},null),true)
  })
  test(`${surface} 指定商品退款不分摊其他商品，分次退款金额守恒`, () => {
    const items=[{id:'1',quantity:3,totalAmount:'897',couponDiscountAmount:'.01'},{id:'2',quantity:1,totalAmount:'198',couponDiscountAmount:0}]
    const sale=q=>({status:1,applyType:1,items:[{orderItemId:'1',refundQuantity:q}]})
    assert.equal(rules.couponRefundPreview(items,[{orderItemId:'2',quantity:1}]),198)
    assert.equal(rules.couponRefundPreview(items,[{orderItemId:'1',quantity:1}]),298.99)
    assert.equal(rules.couponRefundPreview(items,[{orderItemId:'1',quantity:1}],[sale(1)]),299)
    assert.equal(rules.couponRefundPreview(items,[{orderItemId:'1',quantity:1}],[sale(2)]),299)
    assert.equal(rules.couponRefundPreview(items,[{orderItemId:'1',quantity:-1}]),0)
    assert.equal(rules.couponRefundPreview(items,[{orderItemId:'1',quantity:4}]),0)
    assert.equal(rules.couponRefundPreview(items,[{orderItemId:'1',quantity:1}],[{...sale(1),status:2}]),298.99)
  })
}
test('购物车标题采用与系统导航一致的字号和字重，两端同一规范', () => {
  const h5=readFileSync(new URL('../src/views/CartView.vue',import.meta.url),'utf8')
  const mini=readFileSync(new URL('../../mall-mini-program/pages/cart/index.wxss',import.meta.url),'utf8')
  for(const source of [h5,mini])assert.match(source,/font-size:\s*17px;[\s\S]*?font-weight:\s*600/)
})
test('明确拒绝可重新选券，但超时或幂等处理中保留原订单请求',()=>{
  for(const fn of [canReviseRejectedOrder,native.canReviseRejectedOrder]){
    assert.equal(fn({httpStatus:400,message:'已过期'},false),true)
    assert.equal(fn({response:{status:400},message:'已过期'},false),true)
    assert.equal(fn({httpStatus:400,message:'已过期'},true),false)
    assert.equal(fn({httpStatus:400,message:'订单正在提交，请勿重复操作'},false),false)
    assert.equal(fn({httpStatus:500},false),false)
    assert.equal(fn(Error('timeout'),false),false)
  }
})
