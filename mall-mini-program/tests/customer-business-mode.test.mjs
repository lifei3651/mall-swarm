import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runMiniScript } from './helpers/run-mini-script.mjs'
function capabilities(data) {
  const module = { exports: {} }
  runMiniScript(readFileSync(new URL('../utils/member-capabilities.js', import.meta.url), 'utf8'), {
    module, require: name => name === './request' ? async () => data : name === './session' ? { getToken: () => 'test' } : { normalizeInviteCode: s => s || '' }
  })
  return module.exports.load()
}
test('新模式不因旧接口邀请码字段开启分享，钱包能力保留', async () => {
  for (const businessMode of ['NORMAL', 'AGENCY']) {
    const r = await capabilities({ businessMode, membershipActive: false, canInvite: true, inviteCode: 'ABCD1234', canViewWallet: true, canViewPayoutRecords: true })
    assert.equal(r.canInvite, false); assert.equal(r.inviteCode, '')
    assert.equal(r.canViewWallet, true); assert.equal(r.canViewPayoutRecords, true)
  }
})
test('缺新增字段的177响应保留现有顾客邀请能力', async () => {
  const r = await capabilities({ membershipActive: false, canInvite: true, inviteCode: 'ABCD1234', canViewWallet: true, canViewPayoutRecords: true })
  assert.equal(r.canInvite, true); assert.equal(r.inviteCode, 'ABCD1234')
})
