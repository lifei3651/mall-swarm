import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const script = 'scripts/run-mall-functional-regression.sh'

test('daily regression has a version-independent, read-only preflight', () => {
  const result = spawnSync('bash', [script, '--preflight-only'], {
    cwd: root,
    env: { ...process.env, RELEASE_BUILD_ID: 'not-a-release', RELEASE_GIT_COMMIT: 'not-a-commit' },
    encoding: 'utf8',
  })
  assert.equal(result.status, 0, result.stderr)
  assert.match(result.stdout, /日常功能回归预检通过/)
  assert.doesNotMatch(result.stdout, /\[1\/9\]/)
})

test('daily regression does not inherit immutable candidate identity gates', () => {
  const source = readFileSync(path.join(root, script), 'utf8')
  assert.doesNotMatch(source, /EXPECTED_VERSION|EXPECTED_BUILD_ID|RELEASE_GIT_COMMIT|RELEASE_BUILD_ID/)
  assert.ok(source.includes("miniLock.packages?.['']?.version"))
  assert.match(source, /db-migrate\.sh plan/)
  assert.match(source, /node --test scripts\/tests\/\*\.test\.mjs/)
})

test('daily regression rejects unknown arguments before running tests', () => {
  const result = spawnSync('bash', [script, '--deploy'], { cwd: root, encoding: 'utf8' })
  assert.equal(result.status, 2)
  assert.match(result.stderr, /用法/)
  assert.doesNotMatch(result.stdout, /\[1\/9\]/)
})
