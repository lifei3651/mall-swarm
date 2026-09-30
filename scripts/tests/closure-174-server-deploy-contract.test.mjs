import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'
import crypto from 'node:crypto'
import { execFileSync, spawnSync } from 'node:child_process'
import test from 'node:test'

const root = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim()
const read = file => fs.readFileSync(path.join(root, file), 'utf8')
const backendFile = 'scripts/remote-deploy-20260930-v1.0.174-backend.sh'
const staticFile = 'scripts/remote-deploy-20260930-v1.0.174-static.sh'
const backend = read(backendFile)
const staticBody = read(staticFile)
const baselineJar = 'd3782cb36f1c8fd62822f7c7c79c7611ae53bacc150a2d13d5d9a8aac58d1b6e'
const baselineCommit = 'df9937c2fdb484e15c834d0d440de984da7adb1a'
const migration = 'V202609301800__direct_referral_immutable_rules.sql'
const migrationSha = 'c61638a9bcff44c09a3f549a8412172e0b067662269038dbf67fd48e044e3f03'

test('174 server scripts bind 173 baseline and pass shell syntax', () => {
  for (const file of [backendFile, staticFile]) execFileSync('bash', ['-n', path.join(root, file)])
  for (const body of [backend, staticBody]) {
    assert.ok(body.includes('EXPECTED_VERSION=1.0.174'))
    assert.ok(body.includes('EXPECTED_PREVIOUS_VERSION=1.0.173'))
    assert.ok(body.includes('20260930-closure-1.0.174'))
    assert.ok(body.includes(baselineJar))
    assert.match(body, /lingqimall-closure-174\\/)
  }
  assert.ok(staticBody.includes(baselineCommit))
  assert.match(staticBody, /PREVIOUS_BUILD_ID=20260930-closure-1\.0\.173/)
})

test('174 migration is exactly 44 to 45 with immutable historical prefix and hash', () => {
  assert.match(backend, /EXPECTED_MIGRATIONS_BEFORE=44/)
  assert.match(backend, /EXPECTED_MIGRATIONS_AFTER=45/)
  assert.match(backend, /len\(migrations\) == 45/)
  assert.ok(backend.includes(migration) && backend.includes(migrationSha))
  assert.equal(crypto.createHash('sha256').update(read('document/db/migrations/' + migration)).digest('hex'), migrationSha)
  assert.match(backend, /44:44\) verify_applied_migration_prefix/)
  assert.match(backend, /partial\/failed migration requires review/)
  assert.match(backend, /historical 44 migration records changed/)
  assert.match(backend, /migration-mode=apply-44-to-45/)
  assert.match(backend, /verify_direct_referral_schema_before "\$DB_NAME"/)
})

test('174 production mutation waits for retention, backup and isolated first/rerun checks', () => {
  const markers = [
    '[[ "$MODE" == --authorize-release ]] || exit 0',
    'P0-10 durable retention verification failed',
    'BACKUP_BEFORE=$(backup_and_verify',
    'capture_historical_business_data "$VERIFY_DB"',
    'isolated migration first run changed module switches',
    'ISOLATED_FIRST_PAYMENTS=$(first_payment_full_snapshot',
    'isolated migration rerun changed first payment markers',
    'STOPPED_DATABASE=$(business_snapshot',
    'capture_historical_business_data "$DB_NAME"',
    'apply_module_migrations "$DB_NAME"',
    'atomic_install "$RELEASE_DIR/mall-distribution.jar"',
  ].map(marker => { const index = backend.indexOf(marker); assert.ok(index > 0, marker); return index })
  assert.deepEqual(markers, [...markers].sort((a, b) => a - b))
  assert.match(backend, /verify_historical_business_data_unchanged "\$DB_NAME"/)
  assert.match(backend, /verify_historical_new_fields_empty "\$DB_NAME"/)
  assert.match(backend, /verify_first_payment_backfill "\$DB_NAME" .* exact/)
  assert.match(backend, /verify_first_payment_backfill "\$DB_NAME" .* preserved/)
})

test('174 protects old values and accepts only precise legal first-payment increment', () => {
  assert.match(backend, /HEX\(CAST\(\\`\$column\\` AS BINARY\)\)/)
  assert.match(backend, /pre-existing business data changed/)
  assert.match(backend, /unexpected business table addition or removal/)
  assert.match(backend, /IS_NULLABLE='YES'/)
  assert.match(backend, /migration populated a historical direct referral field/)
  assert.match(backend, /paid\.pay_time IS NOT NULL AND paid\.user_id > 0/)
  assert.match(backend, /earlier\.pay_time=paid\.pay_time AND earlier\.id < paid\.id/)
  assert.match(backend, /IS_NULLABLE='NO' AND COLUMN_DEFAULT='1'/)
})

test('174 recovery never restores or deletes production data and keeps protected-file checks', () => {
  const recovery = backend.slice(backend.indexOf('recover() {'), backend.indexOf('trap recover EXIT'))
  assert.match(recovery, /additive-migrations-retained=yes database-not-restored=yes/)
  assert.doesNotMatch(recovery, /mysql_db "\$DB_NAME"|DROP COLUMN|database\.sql\.gz/)
  assert.match(backend, /VERIFY_DB=m174v_/)
  assert.match(backend, /invalid verification database/)
  for (const marker of ['protected_hashes', 'runtime_snapshot', 'payment_snapshot', 'verify_unauthorized_contract']) assert.ok(backend.includes(marker))
  assert.match(staticBody, /candidate-tree-exact=passed/)
  assert.match(staticBody, /backend-other-sites-config-preserved=yes/)
  assert.match(staticBody, /recovery_result=failed/)
  assert.match(staticBody, /exit 70/)
  assert.doesNotMatch(backend + staticBody, /rm -rf|DROP DATABASE.*mall_distribution/)
})

const backfillVerifier = backend.slice(backend.indexOf('verify_first_payment_backfill() {'), backend.indexOf('\nbusiness_snapshot() {'))
function verifyMarkers(before, current, mode = 'exact', queryFails = false) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'closure174-markers-'))
  const beforeFile = path.join(directory, 'before.tsv')
  fs.writeFileSync(beforeFile, before)
  try {
    const script = 'set -euo pipefail\nfail() { echo "$*" >&2; exit 1; }\n' +
      'mysql_db() { if [[ "$QUERY_FAILS" == 1 ]]; then return 1; fi; printf "%s" "$MARKER_ROWS"; }\n' +
      backfillVerifier + '\nverify_first_payment_backfill "$1" "$2" "$3"\n'
    return spawnSync('bash', ['-c', script, 'qa', 'mall_distribution', beforeFile, mode], {
      encoding: 'utf8', env: { ...process.env, MARKER_ROWS: current, QUERY_FAILS: queryFails ? '1' : '0' },
    })
  } finally { fs.rmSync(directory, { recursive: true }) }
}

test('174 first-payment verifier accepts exact historical markers and empty history', () => {
  assert.equal(verifyMarkers('101\t501\t6101\n', '101\t501\t6101\n').status, 0)
  assert.equal(verifyMarkers('', '').status, 0)
})

test('174 first-payment verifier rejects missing or replaced historical first order', () => {
  assert.notEqual(verifyMarkers('101\t501\t6101\n', '').status, 0)
  assert.notEqual(verifyMarkers('101\t501\t6101\n', '101\t501\t6102\n').status, 0)
})

test('174 first-payment verifier rejects extra migration rows but allows new customers after restart', () => {
  const before = '101\t501\t6101\n'
  const after = before + '101\t502\t6201\n'
  assert.notEqual(verifyMarkers(before, after).status, 0)
  assert.equal(verifyMarkers(before, after, 'preserved').status, 0)
})

test('174 first-payment verifier rejects duplicate, malformed and failed reads', () => {
  const before = '101\t501\t6101\n'
  assert.notEqual(verifyMarkers(before, before + before).status, 0)
  assert.notEqual(verifyMarkers(before, '101\t501\tnull\n').status, 0)
  assert.notEqual(verifyMarkers(before, before, 'exact', true).status, 0)
})
