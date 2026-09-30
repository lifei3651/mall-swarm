import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'
import crypto from 'node:crypto'
import { execFileSync, spawnSync } from 'node:child_process'
import test from 'node:test'

const root = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim()
const read = file => fs.readFileSync(path.join(root, file), 'utf8')
const backendFile = 'scripts/remote-deploy-20261001-v1.0.175-backend.sh'
const staticFile = 'scripts/remote-deploy-20261001-v1.0.175-static.sh'
const backend = read(backendFile)
const staticBody = read(staticFile)
const baselineJar = 'd4f0a0fb6e0aa4e4902e01f97c236155bc1e52bd33885fe319738b375ffce1c6'
const baselineCommit = '72f4db846f0307c65bb2ea9cb75093e1968621e4'
const lastMigration = 'V202609301800__direct_referral_immutable_rules.sql'
const lastMigrationSha = 'c61638a9bcff44c09a3f549a8412172e0b067662269038dbf67fd48e044e3f03'

test('175 scripts bind the fixed 174 baseline and pass shell syntax', () => {
  for (const file of [backendFile, staticFile]) execFileSync('bash', ['-n', path.join(root, file)])
  for (const body of [backend, staticBody]) {
    assert.ok(body.includes('EXPECTED_VERSION=1.0.175'))
    assert.ok(body.includes('EXPECTED_PREVIOUS_VERSION=1.0.174'))
    assert.ok(body.includes('20261001-closure-1.0.175'))
    assert.ok(body.includes(baselineJar))
    assert.match(body, /lingqimall-closure-175\\/)
    assert.match(body, /LINGQIMALL_RELEASE_AUTHORIZATION/)
  }
  assert.ok(staticBody.includes(baselineCommit))
  assert.match(staticBody, /PREVIOUS_BUILD_ID=20260930-closure-1\.0\.174/)
  assert.doesNotMatch(backend + staticBody, /1\.0\.173|release-173/)
})

test('175 requires exactly 45 successful unchanged migrations and never applies SQL', () => {
  assert.match(backend, /EXPECTED_MIGRATIONS_BEFORE=45/)
  assert.match(backend, /EXPECTED_MIGRATIONS_AFTER=45/)
  assert.match(backend, /len\(migrations\) == 45/)
  assert.ok(backend.includes(lastMigration) && backend.includes(lastMigrationSha))
  assert.equal(crypto.createHash('sha256').update(read('document/db/migrations/' + lastMigration)).digest('hex'), lastMigrationSha)
  assert.match(backend, /45:45\) verify_applied_migration_prefix/)
  assert.match(backend, /partial\/failed migration requires review/)
  assert.match(backend, /existing ==|\$existing.*\$base:\$digest:1/)
  assert.match(backend, /execution_time_ms,HEX\(CAST\(installed_at AS BINARY\)\)/)
  assert.match(backend, /migration-mode=verify-45/)
  assert.doesNotMatch(backend, /apply_module_migrations|db-migrate\.sh" apply|apply-44-to-45|44:44\)/)
})

test('175 mutation follows retention, backup, isolated verify and exact stopped snapshots', () => {
  const markers = [
    '[[ "$MODE" == --authorize-release ]] || exit 0',
    'P0-10 durable retention verification failed',
    'BACKUP_BEFORE=$(backup_and_verify',
    'gzip -dc "$BACKUP_BEFORE/database.sql.gz"',
    'capture_historical_business_data "$VERIFY_DB"',
    'for verify_pass in first rerun; do',
    'isolated verification changed migration ledger',
    'isolated-verification=$verify_pass migration-mode=verify-45',
    'MUTATED=1',
    'systemctl stop "$SERVICE"',
    'STOPPED_DATABASE=$(business_snapshot',
    'capture_historical_business_data "$DB_NAME"',
    'atomic_install "$RELEASE_DIR/mall-distribution.jar"',
  ].map(marker => {
    const index = backend.indexOf(marker, marker === 'systemctl stop "$SERVICE"' ? backend.indexOf('MUTATED=1') : 0)
    assert.ok(index > 0, marker)
    return index
  })
  assert.deepEqual(markers, [...markers].sort((a, b) => a - b))
  assert.match(backend, /verify_first_payment_snapshot "\$VERIFY_DB" .* exact/)
  assert.match(backend, /verify_first_payment_snapshot "\$DB_NAME" .* exact/)
  assert.match(backend, /verify_first_payment_snapshot "\$DB_NAME" .* preserved/)
})

test('175 preserves full existing inventory without inventing NULL or empty first-payment requirements', () => {
  assert.match(backend, /HEX\(CAST\(\\`\$column\\` AS BINARY\)\)/)
  assert.match(backend, /business column inventory changed/)
  assert.match(backend, /pre-existing business data changed/)
  assert.match(backend, /unexpected business table addition or removal/)
  assert.match(backend, /TABLE_NAME <> 'dms_schema_migration_history'/)
  assert.doesNotMatch(backend, /TABLE_NAME NOT IN|sed '\/\^dms_member_first_payment/)
  assert.match(backend, /IS_NULLABLE='YES'/)
  assert.match(backend, /tenant_id,user_id/)
  assert.match(backend, /SELECT tenant_id,user_id,first_order_id,HEX\(CAST\(create_time AS BINARY\)\)/)
  assert.doesNotMatch(backend, /verify_direct_referral_schema_before|verify_historical_new_fields_empty|0:0:0|first_payment_expected_snapshot|paid\.pay_time|earlier\.pay_time/)
})

test('175 existing direct-referral schema guard has three balanced read-only statements', () => {
  const guards = backend.slice(backend.indexOf('verify_direct_referral_schema() {'), backend.indexOf('\n# Hash every existing'))
  const statements = [...guards.matchAll(/mysql_db information_schema -NBe "([^"]+)"/g)]
  assert.equal(statements.length, 3)
  for (const [, sql] of statements) {
    assert.match(sql.trim(), /^SELECT /)
    const tokens = sql.replace(/'(?:[^']|'')*'/g, '')
    let depth = 0
    for (const token of tokens) {
      if (token === '(') depth += 1
      if (token === ')') depth -= 1
      assert.ok(depth >= 0, sql)
    }
    assert.equal(depth, 0, sql)
  }
})

test('175 protects complete tenant and rule rows before replacement and after restart/backup', () => {
  const policy = backend.slice(backend.indexOf('policy_snapshot() {'), backend.indexOf('\nbusiness_snapshot() {'))
  assert.match(policy, /for table in dms_tenant dms_commission_rule_version/)
  assert.match(policy, /SELECT COLUMN_NAME FROM COLUMNS/)
  assert.match(policy, /historical_table_digest/)
  assert.match(policy, /snapshot query failed/)
  assert.match(backend, /BEFORE_POLICY=\$\(policy_snapshot "\$DB_NAME"\)/)
  assert.match(backend, /ISOLATED_POLICY=\$\(policy_snapshot "\$VERIFY_DB"\)/)
  for (const marker of ['tenant or rules changed before replacement', 'verification changed tenant or rules', 'tenant or rules changed during file replacement', 'tenant or rules changed after restart', 'tenant or rules changed after backup']) assert.ok(backend.includes(marker))
  assert.match(backend, /IS_NULLABLE='NO' AND COLUMN_DEFAULT='1'/)
  assert.match(backend, /STOPPED_SWITCHES=\$\(module_switch_snapshot/)
})

test('175 bounded recovery restores only JAR/VERSION, never production database', () => {
  const recovery = backend.slice(backend.indexOf('recover() {'), backend.indexOf('trap recover EXIT'))
  assert.match(recovery, /existing-migrations-retained=yes database-not-restored=yes/)
  assert.match(recovery, /EXPECTED_PREVIOUS_JAR_SHA/)
  assert.doesNotMatch(recovery, /mysql_db "\$DB_NAME"|DROP COLUMN|database\.sql\.gz/)
  assert.match(backend, /VERIFY_DB=m175v_/)
  assert.match(backend, /invalid verification database/)
  for (const marker of ['protected_hashes', 'runtime_snapshot', 'payment_snapshot', 'verify_unauthorized_contract']) assert.ok(backend.includes(marker))
  assert.match(backend, /gzip -t database\.sql\.gz/)
  assert.match(backend, /backup VERSION mismatch/)
  assert.match(backend, /post-release backup missing/)
  assert.doesNotMatch(backend + staticBody, /rm -rf|DROP DATABASE.*mall_distribution/)
})

test('175 static deployment retains atomic exchange, complete tree and bounded recovery checks', () => {
  for (const marker of ['candidate-tree-exact=passed', 'backend-other-sites-config-preserved=yes', 'recovery_result=failed', 'exit 70', 'renameat2', 'BACKUP_BEFORE', 'BACKUP_AFTER', 'P0-10 durable retention verification failed']) assert.ok(staticBody.includes(marker), marker)
  assert.match(staticBody, /previousStaticVersion.*1\.0\.174/)
  assert.match(staticBody, /previousStaticCommit.*72f4db846f0307c65bb2ea9cb75093e1968621e4/)
  assert.match(staticBody, /backend version is not 1\.0\.175/)
  assert.match(staticBody, /LINGQIMALL_RELEASE_SOURCE_TREE/)
})

const firstVerifier = backend.slice(backend.indexOf('first_payment_full_snapshot() {'), backend.indexOf('\n# Preserve every tenant'))
function verifyMarkers(before, current, mode = 'exact', queryFails = false) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'closure175-markers-'))
  const beforeFile = path.join(directory, 'before.tsv')
  fs.writeFileSync(beforeFile, before)
  try {
    const script = 'set -euo pipefail\nfail() { echo "$*" >&2; exit 1; }\n' +
      'mysql_db() { if [[ "$QUERY_FAILS" == 1 ]]; then return 1; fi; printf "%s" "$MARKER_ROWS"; }\n' +
      firstVerifier + '\nverify_first_payment_snapshot "$1" "$2" "$3"\n'
    return spawnSync('bash', ['-c', script, 'qa', 'mall_distribution', beforeFile, mode], {
      encoding: 'utf8', env: { ...process.env, MARKER_ROWS: current, QUERY_FAILS: queryFails ? '1' : '0' },
    })
  } finally { fs.rmSync(directory, { recursive: true }) }
}

test('175 actual first-payment verifier accepts nonempty historical and empty snapshots', () => {
  assert.equal(verifyMarkers('101\t501\t6101\t32303236\n', '101\t501\t6101\t32303236\n').status, 0)
  assert.equal(verifyMarkers('', '').status, 0)
})

test('175 first-payment verifier rejects missing/replaced order and changed timestamp', () => {
  const before = '101\t501\t6101\t32303236\n'
  assert.notEqual(verifyMarkers(before, '').status, 0)
  assert.notEqual(verifyMarkers(before, '101\t501\t6102\t32303236\n').status, 0)
  assert.notEqual(verifyMarkers(before, '101\t501\t6101\t32303237\n').status, 0)
})

test('175 first-payment exact mode rejects extra rows, restart mode allows only new keys', () => {
  const before = '101\t501\t6101\t32303236\n'
  const after = before + '101\t502\t6201\t32303237\n'
  assert.notEqual(verifyMarkers(before, after).status, 0)
  assert.equal(verifyMarkers(before, after, 'preserved').status, 0)
  assert.notEqual(verifyMarkers(before, '101\t501\t6102\t32303236\n', 'preserved').status, 0)
})

test('175 first-payment verifier rejects duplicates, malformed timestamp, bad mode and failed read', () => {
  const before = '101\t501\t6101\t32303236\n'
  for (const current of [before + before, '101\t501\tnull\t32303236\n', '101\t501\t6101\tnull\n', '101\t501\t6101\tABC\n', '101\t501\t6101\n']) assert.notEqual(verifyMarkers(before, current).status, 0)
  assert.notEqual(verifyMarkers(before, before, 'bad').status, 0)
  assert.notEqual(verifyMarkers(before, before, 'exact', true).status, 0)
})
