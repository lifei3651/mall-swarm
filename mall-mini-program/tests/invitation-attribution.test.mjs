import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'

const source = (file) => readFileSync(new URL(`../${file}`, import.meta.url), 'utf8')
function harness() {
  let time = 1000000
  const storage = new Map(), module = { exports: {} }
  vm.runInNewContext(source('utils/invite.js'), {
    module, require: () => ({ API_BASE_URL: 'https://shop.test/api' }), Date: { now: () => time },
    wx: { getStorageSync: (key) => storage.get(key), setStorageSync: (key, value) => storage.set(key, value), removeStorageSync: (key) => storage.delete(key) }
  })
  return { invite: module.exports, storage, advance: (ms) => { time += ms } }
}
test('不同分享不会静默覆盖，只有明确选择才能换用新邀请码', () => {
  const { invite } = harness()
  invite.captureLaunchInvite({ query: { inviteCode: 'abcd1234' } })
  invite.captureLaunchInvite({ query: { scene: 'EFGH5678' } })
  assert.equal(invite.getPendingInvite(), 'ABCD1234')
  assert.equal(invite.getState().candidate.code, 'EFGH5678')
  invite.chooseCandidate(true)
  assert.equal(invite.getPendingInvite(), 'EFGH5678')
  assert.equal(invite.getState().candidate, null)
})
test('重复回到同一分享不续期，过期与旧版无时间缓存都清理', () => {
  const { invite, storage, advance } = harness()
  storage.set('mall_pending_invite_code', 'OLD12345')
  assert.equal(invite.getPendingInvite(), '')
  invite.captureLaunchInvite({ query: { inviteCode: 'ABCD1234' } })
  advance(23 * 3600000)
  invite.captureLaunchInvite({ query: { inviteCode: 'ABCD1234' } })
  advance(2 * 3600000)
  assert.equal(invite.getPendingInvite(), '')
})
test('登录账号进入他人分享不吸收邀请码；退出后不会遗留给另一人', () => {
  const { invite, storage } = harness()
  invite.captureLaunchInvite({ query: { inviteCode: 'ABCD1234' } })
  storage.set('mall_mini_access_token', 'test-session')
  invite.captureLaunchInvite({ query: { inviteCode: 'EFGH5678' } })
  assert.equal(invite.getPendingInvite(), '')
})
test('解析分享链接与微信码仅接受完整八位码，不接受原型链值', () => {
  const { invite } = harness()
  assert.equal(invite.decodeScene(encodeURIComponent('inviteCode=ZX12CV34')), 'ZX12CV34')
  assert.equal(invite.decodeScene('__proto__=ABCD1234'), '')
  assert.equal(invite.normalizeInviteCode('ABCD1234&x=1'), '')
})
