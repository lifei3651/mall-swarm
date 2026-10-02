import test from 'node:test'
import assert from 'node:assert/strict'
import { captureInvitation, pendingInvitation, bindPendingInvitation } from '../src/utils/invitationBinding.js'
const store=()=>{const map=new Map();return {getItem:k=>map.get(k),setItem:(k,v)=>map.set(k,v),removeItem:k=>map.delete(k)}}
test('share survives login redirect and expires after one day',()=>{
  const storage=store(),now=Date.now();captureInvitation({inviteCode:'abcd1234'},storage,now);
  assert.equal(captureInvitation({},storage,now),'ABCD1234');
  assert.equal(pendingInvitation(storage,now+24*3600000+1),'');
})
test('authenticated account binds received share once and clears only processed code',async()=>{
  const storage=store();captureInvitation({inviteCode:'ABCD1234'},storage);
  const calls=[];await bindPendingInvitation({storage,hasSession:()=>true,owner:()=>1,bind:async code=>{calls.push(code);return 'BOUND'}});
  assert.deepEqual(calls,['ABCD1234']);assert.equal(pendingInvitation(storage),'');
})
test('guest waits for authentication and binding failures do not block login',async()=>{
  const storage=store();captureInvitation({inviteCode:'ABCD1234'},storage);let called=0;
  await bindPendingInvitation({storage,hasSession:()=>false,restoreSession:async()=>false,owner:()=>0,bind:async()=>called++});
  assert.equal(called,0);assert.equal(pendingInvitation(storage),'ABCD1234');
  await bindPendingInvitation({storage,hasSession:()=>true,owner:()=>1,bind:async()=>{throw Error('offline')}});
  assert.equal(pendingInvitation(storage),'ABCD1234');
})
test('older binding response cannot clear a newer share or another account',async()=>{
  const storage=store();captureInvitation({inviteCode:'ABCD1234'},storage);let owner=1;
  await bindPendingInvitation({storage,hasSession:()=>true,owner:()=>owner,bind:async()=>{owner=2;captureInvitation({inviteCode:'EFGH5678'},storage)}});
  assert.equal(pendingInvitation(storage),'EFGH5678');
})
