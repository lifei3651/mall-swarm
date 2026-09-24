import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { execFileSync } from 'node:child_process'
import test from 'node:test'

const root = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim()
const read = file => fs.readFileSync(path.join(root, file), 'utf8')
const baselineJar = 'cbd249426c1dfcdad4de7dbf65f9780486b47a9807e35ae3eb27fb8252f1ae1f'
const baselineCommit = 'fcadf4099861a0a5359b12761cbf232c160635a7'

test('1.0.165 候选严格绑定线上 1.0.158 基线', () => {
  for (const file of [
    'scripts/release-lingqi-165.mjs',
    'scripts/prepare-lingqi-mini-release-165.mjs',
    'scripts/upload-lingqi-mini-165.mjs',
    'scripts/release-readiness-165.sh',
    'scripts/remote-deploy-20260924-v1.0.165-backend.sh',
    'scripts/remote-deploy-20260924-v1.0.165-static.sh',
  ]) {
    const body = read(file)
    assert.match(body, /1\.0\.165/)
    assert.match(body, /1\.0\.158/)
    assert.ok(body.includes(baselineJar), file)
  }
  for (const file of [
    'scripts/release-lingqi-165.mjs',
    'scripts/prepare-lingqi-mini-release-165.mjs',
    'scripts/upload-lingqi-mini-165.mjs',
    'scripts/release-readiness-165.sh',
    'scripts/remote-deploy-20260924-v1.0.165-static.sh',
  ]) {
    assert.ok(read(file).includes(baselineCommit), file)
  }
})

test('1.0.165 后端只验证已存在的 41 条迁移', () => {
  const script = read('scripts/remote-deploy-20260924-v1.0.165-backend.sh')
  assert.match(script, /\[\[ "\$MIGRATION_STATE" == 41:41 \]\]/)
  assert.match(script, /migration-mode=verify-only/)
  assert.doesNotMatch(script, /db-migrate\.sh[" ]+apply/)
  assert.doesNotMatch(script, /mysql_db "\$DB_NAME" </)
  assert.doesNotMatch(script, /apply_service_tag_migration/)
})

test('1.0.165 微信脚本仅上传开发版', () => {
  const script = read('scripts/upload-lingqi-mini-165.mjs')
  assert.match(script, /development-upload-only/)
  assert.match(script, /experienceVersionChanged: false/)
  assert.match(script, /reviewSubmitted: false/)
  assert.match(script, /formalVersionPublished: false/)
  assert.doesNotMatch(script, /--authorize-review|--authorize-publish/)
})
