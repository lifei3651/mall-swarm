import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
test('public build allows only the new authenticated bind route and retains team isolation', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'invitation-public-boundary-'))
  const checker = new URL('../scripts/check-public-surface.mjs', import.meta.url)
  try {
    fs.mkdirSync(path.join(directory, 'dist'))
    for (const [api, allowed] of [['/shop/invite/bind', true], ['/shop/invite/my', false], ['/shop/invite/bind-other', false], ['/shop/team/members', false]]) {
      fs.writeFileSync(path.join(directory, 'dist/app.js'), `const endpoint = '${api}'`)
      const result = spawnSync('node', [checker.pathname], { cwd: directory, encoding: 'utf8' })
      assert.equal(result.status === 0, allowed, api)
    }
  } finally { fs.rmSync(directory, { recursive: true, force: true }) }
})
