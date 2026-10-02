import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { invitationShareUrl } from '../src/utils/invitationShare.js'

const setup = (changes = {}) => ({
  toUrl: path => `https://lingqimall.com${path}`,
  hasSession: () => true, restoreSession: async () => true, owner: () => 12,
  loadInviteInfo: async () => ({ data: { invitationEnabled: true, inviteCode: 'SELF1234', inviterId: 99 } }),
  ...changes,
})
test('product sharing carries the sender code and preserves product identity', async () => {
  assert.equal(await invitationShareUrl('/product/42', setup()), 'https://lingqimall.com/product/42?inviteCode=SELF1234')
  const view = readFileSync(new URL('../src/views/ProductDetailView.vue', import.meta.url), 'utf8')
  assert.match(view, /await invitationShareUrl\(`\/product\/\$\{route\.params\.id\}`\)/)
})
test('guests, invitation disabled, invalid and rejected system accounts share plain product links', async () => {
  for (const deps of [
    setup({ hasSession: () => false, restoreSession: async () => false, loadInviteInfo: async () => { throw Error('must not query guest') } }),
    setup({ loadInviteInfo: async () => ({ data: { invitationEnabled: false, inviteCode: 'SELF1234' } }) }),
    setup({ loadInviteInfo: async () => ({ data: { invitationEnabled: true, inviteCode: 'bad' } }) }),
    setup({ loadInviteInfo: async () => { throw Error('system account rejected') } }),
  ]) assert.equal(await invitationShareUrl('/product/42', deps), 'https://lingqimall.com/product/42')
})
test('session restore and account changes cannot attach another sender code', async () => {
  let authenticated = false
  const restored = setup({ hasSession: () => authenticated, restoreSession: async () => { authenticated = true; return true } })
  assert.match(await invitationShareUrl('/product/42', restored), /inviteCode=SELF1234$/)
  let owner = 12
  const switched = setup({ owner: () => owner, loadInviteInfo: async () => { owner = 13; return { data: { invitationEnabled: true, inviteCode: 'SELF1234' } } } })
  assert.equal(await invitationShareUrl('/product/42', switched), 'https://lingqimall.com/product/42')
})
