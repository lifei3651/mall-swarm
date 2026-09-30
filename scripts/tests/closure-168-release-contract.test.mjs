import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import { execFileSync } from 'node:child_process'
import test from 'node:test'

const root = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim()
const read = file => fs.readFileSync(path.join(root, file), 'utf8')
const backend = read('scripts/remote-deploy-20260927-v1.0.168-backend.sh')
const baselineJar = 'a1e978931d69ffae226860e32b660eaac4a7bcae5e6bfe4b0410a2e5a728d9b2'
const baselineCommit = 'c40a0e8cea82b8c5081ba0438dc3ced0db0daf37'

test('1.0.168 所有候选入口固定线上 1.0.167 基线', () => {
  for (const file of [
    'scripts/release-lingqi-168.mjs', 'scripts/prepare-lingqi-mini-release-168.mjs',
    'scripts/upload-lingqi-mini-168.mjs', 'scripts/release-readiness-168.sh',
    'scripts/remote-deploy-20260927-v1.0.168-static.sh',
  ]) {
    const body = read(file)
    assert.ok(body.includes('1.0.168') && body.includes('1.0.167'), file)
    assert.ok(body.includes(baselineJar) && body.includes(baselineCommit), file)
    assert.ok(body.includes('20260927-closure-1.0.168'), file)
  }
  assert.ok(backend.includes(baselineJar))
  assert.match(backend, /EXPECTED_PREVIOUS_VERSION=1\.0\.167/)
  assert.match(read('scripts/remote-deploy-20260927-v1.0.168-static.sh'), /PREVIOUS_BUILD_ID=20260924-closure-1\.0\.167/)
})

test('41→44 只追加已验证的三条开关迁移，固定名称与摘要', () => {
  // 历史候选只校验当时冻结的迁移前缀，后续迁移不能改写旧包的 44 条合同。
  const names = fs.readdirSync(path.join(root, 'document/db/migrations'))
    .filter(n => /^V.*\.sql$/.test(n) && n <= 'V202609262130__tenant_invitation_switch.sql').sort()
  assert.equal(names.length, 44)
  assert.deepEqual(names.slice(41), [
    'V202609261800__tenant_coupon_module_switch.sql',
    'V202609262000__tenant_balance_and_merchant_mode_switches.sql',
    'V202609262130__tenant_invitation_switch.sql',
  ])
  for (const name of names.slice(41)) {
    assert.ok(backend.includes(name))
    assert.ok(backend.includes(crypto.createHash('sha256').update(read(`document/db/migrations/${name}`)).digest('hex')))
  }
  assert.match(backend, /EXPECTED_MIGRATIONS_BEFORE=41/)
  assert.match(backend, /EXPECTED_MIGRATIONS_AFTER=44/)
  assert.match(backend, /partial\/failed migration requires review/)
  assert.match(backend, /verify_applied_migration_prefix "\$DB_NAME" "\$EXPECTED_MIGRATIONS_BEFORE"/)
  assert.doesNotMatch(backend, /mysql_db "\$DB_NAME" </)
})

test('正式迁移必须晚于显式授权、留档验证、完整备份、隔离首跑和重跑', () => {
  const checkpoints = [
    '[[ "$MODE" == --authorize-release ]] || exit 0',
    'P0-10 durable retention verification failed',
    'BACKUP_BEFORE=$(backup_and_verify',
    'ISOLATED_BEFORE=$(business_snapshot',
    'ISOLATED_SWITCHES=$(module_switch_snapshot',
    'isolated migration rerun changed module switches',
    'STOPPED_DATABASE=$(business_snapshot',
    'apply_module_migrations "$DB_NAME"',
    'atomic_install "$RELEASE_DIR/mall-distribution.jar"',
  ].map(marker => { const at = backend.indexOf(marker); assert.ok(at > 0, marker); return at })
  assert.deepEqual(checkpoints, [...checkpoints].sort((a, b) => a - b))
  assert.match(backend, /IS_NULLABLE='NO' AND COLUMN_DEFAULT='1'/)
  assert.match(backend, /business_snapshot "\$DB_NAME"\)" == "\$STOPPED_DATABASE"/)
})

test('回退只恢复旧应用，不假称迁移回退，不覆盖业务库', () => {
  assert.match(backend, /additive-migrations-retained=yes database-not-restored=yes/)
  assert.doesNotMatch(backend, /migration-ledger-unchanged=yes/)
  assert.match(backend, /44:44\) verify_applied_migration_prefix/)
  assert.match(backend, /retry changed module switches/)
  const recovery = backend.slice(backend.indexOf('recover() {'), backend.indexOf('trap recover EXIT'))
  assert.doesNotMatch(recovery, /mysql_db "\$DB_NAME"|DROP COLUMN|database\.sql\.gz/)
})

test('本地准入递归绑定候选和已验证部署脚本，缺留档不能正式准入', () => {
  const body = read('scripts/release-readiness-168.sh')
  assert.match(body, /expected_migration_count = 44/)
  assert.match(body, /候选脚本与已验证源码不同/)
  assert.match(body, /正式准入缺少 --retention-receipt/)
  assert.match(read('scripts/release-lingqi-168.mjs'), /migrations\.length !== 44/)
})

test('小程序仍仅允许显式开发版上传，不含提审或正式发布', () => {
  const body = read('scripts/upload-lingqi-mini-168.mjs')
  assert.match(body, /development-upload-only/)
  for (const marker of ['experienceVersionChanged: false', 'reviewSubmitted: false', 'formalVersionPublished: false']) assert.ok(body.includes(marker))
  assert.doesNotMatch(body, /--authorize-review|--authorize-publish/)
})
