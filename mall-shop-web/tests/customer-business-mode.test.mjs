import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
const source = readFileSync(new URL('../src/utils/customerBusinessMode.js', import.meta.url), 'utf8').replace(/^import.*\n/, '').replaceAll('export ', '')
function policy(read) { return new Function('getBusinessConfig', source + '\nreturn { teamBusinessAllowed, checkTeamBusinessRoute }')(read) }
test('新模式直达团队入口拒绝，旧响应保持兼容，历史钱包不经过团队限制', async () => {
  for (const businessMode of ['NORMAL', 'AGENCY']) {
    const p = policy(async () => ({ data: { businessMode } }))
    assert.equal(await p.checkTeamBusinessRoute('/invite'), false)
    assert.equal(await p.checkTeamBusinessRoute('/profile/team'), false)
    assert.equal(await p.checkTeamBusinessRoute('/profile/wallet'), true)
  }
  const p = policy(async () => ({ data: { invitationEnabled: 1 } }))
  assert.equal(await p.checkTeamBusinessRoute('/invite'), true)
})
test('服务端能力读取失败不能放行受限页面', async () => {
  const p = policy(async () => { throw new Error('offline') })
  await assert.rejects(p.checkTeamBusinessRoute('/invite'), /offline/)
})
