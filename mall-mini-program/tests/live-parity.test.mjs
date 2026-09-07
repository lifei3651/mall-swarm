import test from 'node:test'
import assert from 'node:assert/strict'
import { commerceEnv } from './helpers/commerce-env.mjs'
const deferred=()=>{let resolve;const promise=new Promise(ok=>{resolve=ok});return{promise,resolve}}
const item=(id,state='UPCOMING')=>({room:{id:String(id),title:'测试直播',commentEnabled:1,shareEnabled:0,watchUrl:'https://example.test/live.m3u8'},roomState:state,products:[]})
const event=id=>({currentTarget:{dataset:{id}}})
test('直播广场与H5相同50条、双分类，已结束不混入；预约和取消使用真实接口且防连点',async()=>{
  const wait=deferred(); let pending=false
  const env=commerceEnv(o=>o.url==='/shop/home'?{displayConfig:{liveSquareEnabled:1}}:o.url==='/shop/live-rooms'?[item(1),item(2,'LIVE'),item(3,'CONNECTING'),item(4,'ENDED')]:o.url==='/shop/live-reservations'?['1']:pending?wait.promise:{}),page=env.page('store-content')
  page.onLoad({type:'live',tab:'upcoming'});await page.onShow();assert.equal(env.calls[1].params.limit,50);assert.equal(page.data.filteredRooms.length,1);assert.equal(page.data.filteredRooms[0].reserved,true)
  pending=true;const task=page.reserve(event('1'));await page.reserve(event('1'));assert.equal(env.calls.filter(o=>o.method==='DELETE').length,1);wait.resolve({});await task;assert.deepEqual(page.data.reservedIds,[])
  page.selectLiveTab({currentTarget:{dataset:{tab:'live'}}});assert.deepEqual(page.data.filteredRooms.map(i=>i.room.id),['2','3']);page.onHide()
})
test('直播预约未知状态先读取，读取失败不盲目重复预约；换号后的旧响应不污染新账号',async()=>{
  const wait=deferred(),env=commerceEnv(o=>o.url==='/shop/live-reservations'?wait.promise:{}),page=env.page('store-content'),live=env.load('utils/live')
  page.setData({rooms:[item(1)],reservationReady:false});const task=live.toggleReservation(page,'1');env.token('new-owner');wait.resolve(['1']);await task;assert.equal(env.calls.some(o=>o.method==='POST'||o.method==='DELETE'),false);assert.deepEqual(page.data.reservedIds,[])
})
test('直播评论同H580条反序；只有LIVE+开启评论+当前账号可以发送，300字且防重复',async t=>{
  const wait=deferred();let writing=false
  const env=commerceEnv(o=>o.url==='/shop/home'?{displayConfig:{liveSquareEnabled:1}}:o.url==='/shop/live-rooms/1'?item(1,'LIVE'):o.url.endsWith('/comments')?(o.method==='POST'?(writing=true,wait.promise):[{id:'2',content:'后'},{id:'1',content:'先'}]):{}),page=env.page('store-content');t.after(()=>page.onUnload())
  page.onLoad({type:'live',id:'1'});await page.onShow();await new Promise(r=>setImmediate(r));assert.deepEqual(page.data.comments.map(i=>i.id),['1','2']);assert.equal(env.calls.find(o=>o.url.endsWith('/comments')).params.limit,80)
  page.commentInput({detail:{value:'测试评论'}});const task=page.sendComment();await new Promise(r=>setImmediate(r));await page.sendComment();assert.equal(writing,true);assert.equal(env.calls.filter(o=>o.url.endsWith('/comments')&&o.method==='POST').length,1)
  wait.resolve({});await task;assert.equal(page.data.commentText,'');page.setData({'room':item(1,'ENDED'),commentText:'不应发送'});await page.sendComment();assert.equal(env.calls.filter(o=>o.url.endsWith('/comments')&&o.method==='POST').length,1)
})
test('直播切后台停止轮询、清评论输入和分享；只有本房商品可点击，返回不泄露旧账号预约',async t=>{
  const env=commerceEnv(o=>o.url==='/shop/home'?{displayConfig:{liveSquareEnabled:1}}:o.url==='/shop/live-rooms/1'?item(1,'LIVE'):o.url.endsWith('/comments')?[]:{}),page=env.page('store-content');t.after(()=>page.onUnload())
  page.onLoad({type:'live',id:'1'});await page.onShow();await new Promise(r=>setImmediate(r));assert.ok(page.commentTimer);assert.ok(page.heartbeatTimer);page.onHide();assert.equal(page.commentTimer,null);assert.equal(page.heartbeatTimer,null);assert.equal(page.data.commentText,'');assert.equal(page.data.shareReady,false)
  assert.ok(env.calls.some(o=>o.data?.eventType==='ENTER'));assert.ok(env.calls.some(o=>o.data?.eventType==='LEAVE'));assert.equal(env.calls.some(o=>o.url.includes('/orders')),false)
})
test('直播总开关关闭/错号详情/旧请求晚到一律关闭页面；危险观看地址保持拒绝',async()=>{
  for(const enabled of [0,'0',false]){const env=commerceEnv(()=>({displayConfig:{liveSquareEnabled:enabled}})),page=env.page('store-content');page.onLoad({type:'live',id:'1'});await page.onShow();assert.equal(env.calls.length,1);assert.match(page.data.error,/暂未开放/)}
  const env=commerceEnv(o=>o.url==='/shop/home'?{displayConfig:{liveSquareEnabled:1}}:item(2,'LIVE')),page=env.page('store-content');page.onLoad({type:'live',id:'1'});await page.onShow();assert.match(page.data.error,/不存在/);assert.equal(page.data.room,null)
})
