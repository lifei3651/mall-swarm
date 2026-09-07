import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { commerceEnv } from './helpers/commerce-env.mjs'
const require = createRequire(import.meta.url)
const member = { id: '1', phone: '13800000000', username: 'tester' }
const wallet = { balance: 100, hasPaymentPassword: false, paymentPasswordLocked: false }
const event = type => ({ currentTarget: { dataset: { type } } })
const deferred = () => { let resolve; const promise = new Promise(ok => { resolve = ok }); return { promise, resolve } }
async function setup(section, respond = () => ({})) {
  const env = commerceEnv(options => options.url === '/shop/auth/me' ? member : options.url === '/shop/wallet/summary' ? wallet : options.url === '/shop/real-name/status' ? { verified: false, verificationAvailable: true } : respond(options))
  env.wx.redirectTo = ({url}) => env.routes.push(url)
  const page = env.page('account-settings'); page.onLoad({ section }); await page.onShow(); return { env, page }
}
test('支付密码首次设置使用登录密码+专用短信；修改使用原支付密码，提交字段与H5一致', async () => {
  const { env, page } = await setup('payment')
  page.setData({form:{ ...page.data.form,newPassword:'654321',confirmPassword:'654321',smsCode:'123456'}})
  await page.savePayment(); assert.equal(env.calls.some(c=>c.method==='PUT'),false)
  page.setData({'form.loginPassword':'ExamplePass123'}); await page.savePayment()
  assert.deepEqual(env.calls.at(-1).data,{oldPassword:'',loginPassword:'ExamplePass123',newPassword:'654321',smsCode:'123456'})
  assert.equal(page.data.form.loginPassword,''); assert.equal(page.data.wallet.hasPaymentPassword,true)
  page.setData({form:{...page.data.form,oldPassword:'654321',newPassword:'123789',confirmPassword:'123789',smsCode:'123456'}}); await page.savePayment()
  assert.deepEqual(env.calls.at(-1).data,{oldPassword:'654321',loginPassword:'',newPassword:'123789',smsCode:'123456'})
})
test('密码锁定或安全状态失败不允许提交；连续点击只写一次', async () => {
  const pending = deferred(), { env,page } = await setup('payment',()=>pending.promise)
  page.setData({'wallet.paymentPasswordLocked':true}); await page.savePayment(); assert.equal(env.calls.some(c=>c.method==='PUT'),false)
  page.setData({'wallet.paymentPasswordLocked':false,form:{...page.data.form,loginPassword:'ExamplePass123',newPassword:'654321',confirmPassword:'654321',smsCode:'123456'}})
  const task=page.savePayment(); await page.savePayment(); assert.equal(env.calls.filter(c=>c.method==='PUT').length,1)
  pending.resolve({}); await task
})
test('支付验证码不接受客户端收件人；换绑严格使用9/10业务与双验证码', async () => {
  const p=await setup('payment'); await p.page.sendCode(event('payment')); assert.deepEqual(p.env.calls.at(-1),{url:'/sms/send/payment-password',method:'POST'}); p.page.onHide()
  const {env,page}=await setup('phone'); page.setData({'form.newPhone':'13900000000'}); await page.sendCode(event('current')); await page.sendCode(event('new'))
  assert.deepEqual(env.calls.slice(-2).map(c=>c.data),[{phone:'13800000000',bizType:9},{phone:'13900000000',bizType:10}]); page.onHide()
})
test('换绑必须两个验证码；成功只清当前会话，晚到响应不能退出新账号', async () => {
  const pending=deferred(),{env,page}=await setup('phone',()=>pending.promise)
  page.setData({'form.newPhone':'13900000000','form.currentPhoneSmsCode':'123456'}); await page.savePhone(); assert.equal(env.calls.some(c=>c.method==='PUT'),false)
  page.setData({'form.newPhoneSmsCode':'654321'}); const task=page.savePhone(); env.token('new-owner'); pending.resolve({}); await task
  assert.equal(env.storage.get('mall_mini_access_token'),'new-owner'); assert.equal(env.routes.length,0)
})
test('实名未单独同意或通道关闭零写请求；姓名身份证仅提交加密层处理的正文', async () => {
  const {env,page}=await setup('identity',()=>({verified:true,maskedRealName:'张*',maskedIdCard:'110***********1234'}))
  page.setData({'form.realName':'张测试','form.idCard':'110101199001011234'}); await page.verifyIdentity(); assert.equal(env.calls.some(c=>c.method==='POST'),false)
  page.consent({detail:{value:['agree']}}); page.setData({'identity.verificationAvailable':false}); await page.verifyIdentity(); assert.equal(env.calls.some(c=>c.method==='POST'),false)
  page.setData({'identity.verificationAvailable':true}); await page.verifyIdentity()
  assert.deepEqual(env.calls.at(-1),{url:'/shop/real-name/verify',method:'POST',data:{realName:'张测试',idCard:'110101199001011234',sensitiveInfoConsent:true}})
  assert.equal(page.data.form.idCard,''); assert.equal(page.data.form.realName,''); assert.equal([...env.storage.values()].some(v=>JSON.stringify(v).includes('110101199001011234')),false)
})
test('离页、换号清空敏感字段并拒绝旧状态；未知页参数不能打开任意目标', async () => {
  const {page}=await setup('invalid'); assert.equal(page.data.section,'security'); page.setData({'form.smsCode':'123456','form.idCard':'110101199001011234'}); page.onHide(); assert.equal(page.data.form.smsCode,''); assert.equal(page.data.form.idCard,'')
})
test('公告列表使用H550条查询与类型筛选、详情兼容封装且拒绝错号', async () => {
  const env=commerceEnv(({url})=>url==='/shop/notices'?{list:[{id:'1',noticeType:1,createTime:'2026-09-07'},{id:'2',noticeType:2}]}:{notice:{id:'1',title:'测试'}}),page=env.page('notices'); await page.load()
  assert.deepEqual(env.calls[0].params,{status:1,pageSize:50}); page.filter({currentTarget:{dataset:{type:'2'}}}); assert.equal(page.data.filteredRows[0].id,'2')
  page.id='1'; await page.load(); assert.equal(page.data.notice.title,'测试'); page.id='2'; await page.load(); assert.match(page.data.error,/不存在/)
})
test('服务保障旧字符串说明与默认售后规则直接对照H5，不制造未配置保障', () => {
  const source=readFileSync(new URL('../../mall-shop-web/src/views/ProductDetailView.vue',import.meta.url),'utf8')
  const format=require('../utils/format'); const product=format.product({serviceTags:['七天无理由','正品保障','其他',{title:'关闭',enabled:false}]})
  assert.ok(source.includes(product.serviceTags[0].description)); assert.ok(source.includes(product.serviceTags[1].description))
  const literal=source.match(/const defaultAfterSalePolicy = ('[^']*')/)[1]; assert.equal(product.afterSalePolicy,Function('return '+literal)())
  assert.equal(format.product({}).serviceTags.length,0); assert.equal(product.serviceTags.length,3)
})
