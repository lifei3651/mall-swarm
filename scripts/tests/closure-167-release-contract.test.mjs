import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { execFileSync } from 'node:child_process'
import test from 'node:test'

const root = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim()
const read = file => fs.readFileSync(path.join(root, file), 'utf8')
const baselineJar = '934d345dee88ea7b9d12f8aeff5998e97c1f8d447caff5945e41d25cafbbd7ef'
const baselineCommit = '60a6e5e3fe1255bace2156f0f59089c9a341db0f'

test('1.0.167 候选严格绑定线上 1.0.165 基线', () => {
  for (const file of [
    'scripts/release-lingqi-167.mjs',
    'scripts/prepare-lingqi-mini-release-167.mjs',
    'scripts/upload-lingqi-mini-167.mjs',
    'scripts/release-readiness-167.sh',
    'scripts/remote-deploy-20260924-v1.0.167-backend.sh',
    'scripts/remote-deploy-20260924-v1.0.167-static.sh',
  ]) {
    const body = read(file)
    assert.match(body, /1\.0\.167/)
    assert.match(body, /1\.0\.165/)
    assert.ok(body.includes(baselineJar), file)
  }
  for (const file of [
    'scripts/release-lingqi-167.mjs',
    'scripts/prepare-lingqi-mini-release-167.mjs',
    'scripts/upload-lingqi-mini-167.mjs',
    'scripts/release-readiness-167.sh',
    'scripts/remote-deploy-20260924-v1.0.167-static.sh',
  ]) {
    assert.ok(read(file).includes(baselineCommit), file)
  }
})

test('1.0.167 后端只验证已存在的 41 条迁移', () => {
  const script = read('scripts/remote-deploy-20260924-v1.0.167-backend.sh')
  assert.match(script, /\[\[ "\$MIGRATION_STATE" == 41:41 \]\]/)
  assert.match(script, /migration-mode=verify-only/)
  assert.doesNotMatch(script, /db-migrate\.sh[" ]+apply/)
  assert.doesNotMatch(script, /mysql_db "\$DB_NAME" </)
  assert.doesNotMatch(script, /apply_service_tag_migration/)
})

test('1.0.167 微信脚本仅上传开发版', () => {
  const script = read('scripts/upload-lingqi-mini-167.mjs')
  assert.match(script, /development-upload-only/)
  assert.match(script, /experienceVersionChanged: false/)
  assert.match(script, /reviewSubmitted: false/)
  assert.match(script, /formalVersionPublished: false/)
  assert.doesNotMatch(script, /--authorize-review|--authorize-publish/)
})

test('1.0.167 在只读准入阶段检查正式主机 Node 运行时', () => {
  const readiness = read('scripts/release-readiness-167.sh')
  assert.ok(readiness.indexOf('正式主机缺少 Node 20+') > 0)
  assert.ok(readiness.indexOf('正式主机缺少 Node 20+') < readiness.indexOf('pass "正式主机身份'))
  for (const [file, marker] of [
    ['scripts/remote-deploy-20260924-v1.0.167-backend.sh', 'release-preflight=passed'],
    ['scripts/remote-deploy-20260924-v1.0.167-static.sh', '-preflight=passed'],
  ]) {
    const script = read(file)
    assert.ok(script.indexOf('node runtime is required before release preflight passes') > 0, file)
    assert.ok(script.indexOf('node runtime is required before release preflight passes') < script.indexOf(marker), file)
    assert.ok(script.indexOf('node --check') < script.indexOf(marker), file)
  }
})
