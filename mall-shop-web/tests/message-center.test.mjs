import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const read=(path)=>readFileSync(new URL(`../${path}`,import.meta.url),'utf8')

test('all three H5 surfaces expose the same authenticated personal message center',()=>{
  for(const file of ['src/router/index.js','src/surfaces/team/router.js','src/surfaces/integrated/router.js']){
    const source=read(file);assert.match(source,/path: '\/messages'/);assert.match(source,/MessageCenterView/);assert.match(source,/MessageDetailView/);assert.match(source,/path: '\/support'/);assert.match(source,/ServiceTicketsView/);assert.match(source,/ServiceTicketDetailView/)
  }
})

test('message unread, explicit read and category/all-read use server APIs',()=>{
  const api=read('src/api/shop.js');assert.match(api,/\/shop\/messages\/unread/);assert.match(api,/read-category/);assert.match(api,/read-all/)
  const view=read('src/views/MessageCenterView.vue');assert.match(view,/message\.isRead!==1/);assert.doesNotMatch(view,/markMessageRead\(message/)
})

test('business navigation is an enum map and never executes a message supplied URL',()=>{
  const detail=read('src/views/MessageDetailView.vue');
  for(const target of ['ORDER','AFTER_SALE','SERVICE_TICKET','WALLET','WITHDRAWAL','ACCOUNT_SECURITY'])assert.match(detail,new RegExp(target))
  assert.doesNotMatch(detail,/message\.value\.(url|href)|window\.location|location\.href/)
})

test('customer service tickets are authenticated, idempotent and never become a visual-workbench switch',()=>{
  const api=read('src/api/shop.js');const list=read('src/views/ServiceTicketsView.vue');const detail=read('src/views/ServiceTicketDetailView.vue')
  assert.match(api,/\/shop\/service-tickets/);assert.match(api,/X-Idempotency-Key/)
  assert.match(list,/AFTER_SALE_DISPUTE/);assert.match(list,/请勿填写登录密码、支付密码、短信验证码或银行卡号/)
  assert.match(detail,/closeServiceTicket/);assert.match(detail,/setInterval\(\(\) => load\(true\), 30000\)/)
})

test('red dot uses 99 plus and realtime only refreshes server unread state',()=>{
  const entry=read('src/components/MessageCenterEntry.vue');assert.match(entry,/>99\?'99\+'/);assert.match(entry,/getMessageUnread/);assert.match(entry,/connectOrderRealtime/)
  const center=read('src/views/MessageCenterView.vue');assert.match(entry,/setInterval\(refresh,30000\)/);assert.match(center,/setInterval\(\(\)=>load\(true\),30000\)/)
})

test('service SMS is explicit, non-marketing and can still be revoked when the provider is unavailable',()=>{
  const api=read('src/api/shop.js');assert.match(api,/\/shop\/messages\/preferences\/sms/)
  const center=read('src/views/MessageCenterView.vue')
  assert.match(center,/订单发货、售后退款和账号安全变化/);assert.match(center,/不含营销内容/);assert.match(center,/role="switch"/)
  assert.match(center,/!smsPreference\.available&&!smsPreference\.enabled/);assert.match(center,/consent:enabled/)
})
