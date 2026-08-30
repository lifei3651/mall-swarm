import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const invite = require('../utils/invite')
const { buildQuery } = require('../utils/request')

test('邀请码只接受八位字母数字并统一大写', () => {
  assert.equal(invite.normalizeInviteCode('ab12cd34'), 'AB12CD34')
  assert.equal(invite.normalizeInviteCode('bad-code'), '')
  assert.equal(invite.normalizeInviteCode('<script>'), '')
})

test('二维码 scene 可以承载直接邀请码或查询参数', () => {
  assert.equal(invite.decodeScene('ab12cd34'), 'AB12CD34')
  assert.equal(invite.decodeScene(encodeURIComponent('inviteCode=ZX12CV34')), 'ZX12CV34')
})

test('请求查询参数过滤空值并编码', () => {
  assert.equal(buildQuery({ keyword: '护肤 套装', page: 1, empty: '' }), 'keyword=%E6%8A%A4%E8%82%A4%20%E5%A5%97%E8%A3%85&page=1')
})
