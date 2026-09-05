import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

const templates = ['发货', '售后', '退款', '到账'].map((title,index)=>({title,templateId:`template${index}`,availableGrants:0}))
function setup({ result = {}, failGrant = false, loggedIn = true } = {}) {
  let definition, fail = failGrant, token = loggedIn ? 'session-1' : ''
  const requested=[], grants=[], toasts=[], loginRequests=[], loads=[]
  vm.runInNewContext(readFileSync(new URL('../pages/subscriptions/index.js',import.meta.url),'utf8'),{
    Page:value=>{definition=value},
    require:id=>id.endsWith('/theme')?{pageData:()=>({}),apply(){},sync(){}}:id.endsWith('/session')?{getToken:()=>token}:id.endsWith('/auth')?{requireLogin:()=>{if(token)return true;loginRequests.push(true);return false}}:async({url,data})=>{
      loads.push(url)
      if(url.endsWith('/grants')){grants.push(structuredClone(data));if(fail)throw new Error('offline')}
      return templates
    },
    wx:{requestSubscribeMessage:({tmplIds,success})=>{requested.push([...tmplIds]);success(result)},showToast:({title})=>toasts.push(title)}
  })
  const page={...definition,data:structuredClone(definition.data),setData(patch){Object.assign(this.data,patch)}}
  return {page,requested,grants,toasts,loginRequests,loads,recover:()=>{fail=false},setToken:value=>{token=value}}
}
test('四模板拆成3+1两组；每次用户点击只弹一组，第四种不会遗漏',async()=>{
  const state=setup({result:{template0:'accept',template1:'reject',template2:'accept',template3:'accept'}})
  await state.page.load()
  assert.deepEqual(Array.from(state.page.data.groups,g=>g.templateIds.length),[3,1])
  await state.page.subscribe({currentTarget:{dataset:{group:'0'}}})
  assert.equal(state.requested.length,1)
  assert.deepEqual(state.grants[0].acceptedTemplateIds,['template0','template2'])
  await state.page.subscribe({currentTarget:{dataset:{group:'1'}}})
  assert.deepEqual(state.requested[1],['template3'])
  assert.deepEqual(state.grants[1].acceptedTemplateIds,['template3'])
})
test('用户拒绝/被封禁/同名过滤的模板不记录授权',async()=>{
  const state=setup({result:{template0:'reject',template1:'ban',template2:'filter'}})
  await state.page.load(); await state.page.subscribe()
  assert.equal(state.grants.length,0)
  assert.equal(state.page.data.pendingGrant,false)
})
test('授权落库失败保留同一幂等请求重试，不重新弹授权或双记次数',async()=>{
  const state=setup({result:{template0:'accept'},failGrant:true})
  await state.page.load();await state.page.subscribe()
  assert.equal(state.page.data.pendingGrant,true)
  state.recover();await state.page.syncGrant()
  assert.equal(state.requested.length,1)
  assert.deepEqual(state.grants[0],state.grants[1])
  assert.equal(state.page.data.pendingGrant,false)
})
test('伪造分组编号不会调起微信授权',async()=>{
  const state=setup();await state.page.load();await state.page.subscribe({currentTarget:{dataset:{group:'999'}}})
  assert.equal(state.requested.length,0)
})
test('游客只跳一次登录，登录回跳onShow重新读取而不是永久停留加载中',async()=>{
  const state=setup({loggedIn:false});state.page.onLoad();await state.page.onShow()
  assert.equal(state.loginRequests.length,1);assert.equal(state.loads.length,0)
  state.setToken('session-1');await state.page.onShow()
  assert.equal(state.loads.length,1);assert.equal(state.page.data.templates.length,4)
})
test('授权同步失败后切换账号，不能把旧授权记到新账号',async()=>{
  const state=setup({result:{template0:'accept'},failGrant:true})
  await state.page.load();await state.page.subscribe();assert.equal(state.grants.length,1)
  state.setToken('session-2');state.recover();await state.page.syncGrant()
  assert.equal(state.grants.length,1);assert.equal(state.page.data.pendingGrant,false)
})
