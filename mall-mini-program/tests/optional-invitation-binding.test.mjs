import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runMiniScript } from './helpers/run-mini-script.mjs'
const source=readFileSync(new URL('../utils/invitation-binding.js',import.meta.url),'utf8')
function harness(request,token='member-a') {
  let currentToken=token,state={selected:{code:'ABCD1234'}},cleared=0
  const module={exports:{}}
  runMiniScript(source,{module,require:name=>({
    './request':request,'./session':{getToken:()=>currentToken},'./feedback':{toast(){}},
    './invite':{getState:()=>state,getPendingInvite:()=>state.selected?.code||'',
      normalizeInviteCode:v=>/^[A-Z0-9]{8}$/.test(v||'')?v:'',clearPendingInvite:()=>{cleared++;state={}}}
  })[name]})
  return {bind:module.exports.bindPendingInvite,setToken:t=>currentToken=t,setState:s=>state=s,cleared:()=>cleared}
}
test('logged in account submits captured share once',async()=>{
  const calls=[];const h=harness(async o=>{calls.push(o);return 'BOUND'})
  await h.bind();assert.equal(calls.length,1);assert.equal(calls[0].url,'/shop/invite/bind');assert.equal(calls[0].data.inviteCode,'ABCD1234');assert.equal(h.cleared(),1)
})
test('guest waits for authentication; binding failure leaves login intact',async()=>{
  let calls=0;const h=harness(async()=>{calls++;throw Error('offline')},'')
  await h.bind();assert.equal(calls,0);h.setToken('member-a');await h.bind();assert.equal(calls,1);assert.equal(h.cleared(),0)
})
test('login binds verified submitted code despite newer guest invitation conflict',async()=>{
  const calls=[];const h=harness(async o=>{calls.push(o);return 'BOUND'})
  h.setState({selected:{code:'ABCD1234'},candidate:{code:'EFGH5678'}})
  await h.bind();assert.equal(calls.length,0);await h.bind('ABCD1234');assert.equal(calls[0].data.inviteCode,'ABCD1234');assert.equal(h.cleared(),1)
})
test('late response cannot clear another account invitation',async()=>{
  let resolve;const h=harness(()=>new Promise(r=>resolve=r));const pending=h.bind();h.setToken('member-b');resolve('BOUND');await pending;assert.equal(h.cleared(),0)
})
