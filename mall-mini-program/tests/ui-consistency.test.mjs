import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { commerceEnv } from './helpers/commerce-env.mjs'
const read = path => readFileSync(new URL('../'+path, import.meta.url),'utf8')
test('所有已注册页面选择器具备选择箭头及可理解标签',()=>{
  for(const path of JSON.parse(read('app.json')).pages){
    for(const picker of read(path+'.wxml').matchAll(/<picker\b[\s\S]*?<\/picker>/g)){
      assert.match(picker[0],/chevron-right\.png/,path)
      assert.match(picker[0],/aria-label=/,path)
    }
  }
})
test('无订单、加载中、失败三种状态分别呈现，不打开单项空菜单',async()=>{
  let resolve
  const env=commerceEnv(({url})=>url==='/shop/orders'?new Promise(r=>{resolve=r}):{}),page=env.page('support')
  const loading=page.loadContext();assert.equal(page.data.contextLoading,true)
  resolve({list:[]});await loading
  assert.equal(page.data.contextLoading,false);assert.equal(page.data.orderChoices.length,1);assert.equal(page.data.contextError,'')
  const failed=commerceEnv(({url})=>{if(url==='/shop/orders')throw Error('unavailable');return {}}).page('support')
  await failed.loadContext();assert.equal(failed.data.contextLoading,false);assert.match(failed.data.contextError,/加载失败/)
  const view=read('pages/support/index.wxml')
  assert.match(view,/orderChoices.length > 1 && !contextLoading/)
  assert.match(view,/暂无可关联订单/);assert.match(view,/暂不可用/)
})
test('取消只收起草稿；提交中禁止取消和改选',()=>{
  const env=commerceEnv(()=>({})),page=env.page('support')
  page.setData({creating:true,'form.subject':'保留草稿',submitting:true})
  page.cancelCreate();page.chooseType({detail:{value:1}})
  assert.equal(page.data.creating,true);assert.equal(page.data.form.type,'CONSULTATION')
  page.setData({submitting:false});page.cancelCreate()
  assert.equal(page.data.creating,false);assert.equal(page.data.form.subject,'保留草稿');assert.equal(env.calls.length,0)
})
test('原生紧凑按钮与通栏按钮显式覆盖平台默认宽度',()=>{
  assert.match(read('pages/product/index.wxss'),/\.product-page \.product-share-button/)
  assert.match(read('styles/support.wxss'),/\.support-page \.section-head > \.ui-text-action[^}]*width:auto;[^}]*min-width:88rpx/)
  assert.match(read('pages/subscriptions/index.wxss'),/\.subscription-page \.subscribe-button[^}]*width:100%/)
  assert.match(read('pages/message-detail/index.wxss'),/\.detail-page \.detail-card \.primary-button[^}]*width:100%/)
  assert.match(read('pages/order-review/index.wxss'),/\.review-page \.review-card > \.primary-button[^}]*width:100%/)
  assert.match(read('pages/address/index.wxss'),/\.address-page \.paste-address \.secondary-button[^}]*width:100%/)
})
