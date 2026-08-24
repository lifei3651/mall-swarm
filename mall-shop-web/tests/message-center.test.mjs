import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const read=(path)=>readFileSync(new URL(`../${path}`,import.meta.url),'utf8')

test('all three H5 surfaces expose the same authenticated personal message center',()=>{
  for(const file of ['src/router/index.js','src/surfaces/team/router.js','src/surfaces/integrated/router.js']){
    const source=read(file);assert.match(source,/path: '\/messages'/);assert.match(source,/MessageCenterView/);assert.match(source,/MessageDetailView/)
  }
})

test('message unread, explicit read and category/all-read use server APIs',()=>{
  const api=read('src/api/shop.js');assert.match(api,/\/shop\/messages\/unread/);assert.match(api,/read-category/);assert.match(api,/read-all/)
  const view=read('src/views/MessageCenterView.vue');assert.match(view,/message\.isRead!==1/);assert.doesNotMatch(view,/markMessageRead\(message/)
})

test('business navigation is an enum map and never executes a message supplied URL',()=>{
  const detail=read('src/views/MessageDetailView.vue');
  for(const target of ['ORDER','AFTER_SALE','WALLET','WITHDRAWAL','ACCOUNT_SECURITY'])assert.match(detail,new RegExp(target))
  assert.doesNotMatch(detail,/message\.value\.(url|href)|window\.location|location\.href/)
})

test('red dot uses 99 plus and realtime only refreshes server unread state',()=>{
  const entry=read('src/components/MessageCenterEntry.vue');assert.match(entry,/>99\?'99\+'/);assert.match(entry,/getMessageUnread/);assert.match(entry,/connectOrderRealtime/)
  const center=read('src/views/MessageCenterView.vue');assert.match(entry,/setInterval\(refresh,30000\)/);assert.match(center,/setInterval\(\(\)=>load\(true\),30000\)/)
})
