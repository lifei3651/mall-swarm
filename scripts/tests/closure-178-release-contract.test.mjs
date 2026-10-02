import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import crypto from 'node:crypto'
import { execFileSync, spawnSync } from 'node:child_process'
const root = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim()
const read = name => fs.readFileSync(`${root}/${name}`, 'utf8')
const backend = read('scripts/remote-deploy-20261002-v1.0.178-backend.sh')
const baseline = 'b7eb1333d667dbf525fd1bc007530bf43255850735bdaabbc61a5eb1726760b3'
test('178 fixes the read-back 177 baseline and independent upload project', () => {
  for (const name of ['release-lingqi-178.mjs', 'prepare-lingqi-mini-release-178.mjs', 'upload-lingqi-mini-178.mjs']) {
    const body = read(`scripts/${name}`)
    assert.ok(body.includes('1.0.178') && body.includes('1.0.177') && body.includes(baseline))
    assert.ok(body.includes('20261002-closure-1.0.178'))
    execFileSync('node', ['--check', `${root}/scripts/${name}`])
  }
  for (const name of ['release-readiness-178.sh', 'run-mall-closure-regression-178.sh', 'remote-deploy-20261002-v1.0.178-backend.sh', 'remote-deploy-20261002-v1.0.178-static.sh']) execFileSync('bash', ['-n', `${root}/scripts/${name}`])
  assert.match(read('scripts/upload-lingqi-mini-178.mjs'), /wechat-mini-program-178/)
  assert.match(read('scripts/release-lingqi-178.mjs'), /'-pl', 'mall-common,mall-mbg,mall-distribution', 'clean', 'package'/)
})
test('only the 47th configuration migration is added; prior 46 SQL bytes remain fixed', () => {
  const names = fs.readdirSync(`${root}/document/db/migrations`).filter(n => /^V\d{12}__.*\.sql$/.test(n)).sort()
  assert.equal(names.length, 47)
  assert.equal(names.at(-1), 'V202610022300__customer_business_mode_drafts.sql')
  for (const name of names.slice(0, 46)) {
    const original = execFileSync('git', ['show', `a4067dfc9deec999d2922bd792b72b3fbc6b5d29:document/db/migrations/${name}`], { cwd: root })
    assert.deepEqual(fs.readFileSync(`${root}/document/db/migrations/${name}`), original)
  }
  const sql = read(`document/db/migrations/${names.at(-1)}`)
  assert.doesNotMatch(sql.replace(/^--.*$/gm, ''), /\b(?:UPDATE|DELETE|INSERT|DROP)\b/i)
  assert.ok(backend.includes(crypto.createHash('sha256').update(sql).digest('hex')))
  assert.match(backend, /EXPECTED_MIGRATIONS_BEFORE=46/)
  assert.match(backend, /EXPECTED_MIGRATIONS_AFTER=47/)
})
test('production apply follows retention, backup, restored first/rerun and stopped snapshots', () => {
  const begin = backend.indexOf('[[ "$MODE" == --authorize-release ]] || exit 0')
  const body = backend.slice(begin)
  const markers = ['P0-10 durable retention verification failed', 'BACKUP_BEFORE=$(backup_and_verify', 'gzip -dc "$BACKUP_BEFORE/database.sql.gz"', 'for verify_pass in first rerun; do', 'apply_module_migrations "$VERIFY_DB"', 'MUTATED=1', 'systemctl stop "$SERVICE"', 'capture_historical_business_data "$DB_NAME"', 'apply_module_migrations "$DB_NAME"', 'atomic_install "$RELEASE_DIR/mall-distribution.jar"']
  const indexes = markers.map(value => { const index = body.indexOf(value); assert.ok(index >= 0, value); return index })
  assert.deepEqual(indexes, [...indexes].sort((a,b) => a-b))
  assert.match(backend, /additive-migrations-retained=yes database-not-restored=yes/)
  assert.match(backend, /old migration ledger changed during apply/)
  assert.match(backend, /default inviter must remain disabled and unassigned/)
})
test('the executed nullable-mode guard rejects missing columns and nondefault tenant values', () => {
  const start = backend.indexOf('verify_customer_mode_schema() {')
  const end = backend.indexOf('\napply_module_migrations()', start)
  const functionBody = backend.slice(start, end)
  for (const [schema, changed, success] of [[2,0,true], [1,0,false], [2,1,false]]) {
    const result = spawnSync('bash', ['-c', `set -e\nfail() { exit 1; }\nmysql_db() { if [[ "$1" == information_schema ]]; then echo ${schema}; else echo ${changed}; fi; }\n${functionBody}\nverify_customer_mode_schema fixture`], { encoding:'utf8' })
    assert.equal(result.status === 0, success)
  }
})


test('178 nullable mode fields do not backfill any customer and full business snapshot allows only these two columns', () => {
  const sql = read('document/db/migrations/V202610022300__customer_business_mode_drafts.sql').replace(/^--.*$/gm, '')
  assert.match(sql, /business_mode VARCHAR\(16\) NULL/)
  assert.match(sql, /agency_rule_draft TEXT NULL/)
  assert.doesNotMatch(sql, /\b(?:UPDATE|INSERT|DELETE|DROP)\b/i)
  assert.match(backend, /business_mode IS NOT NULL OR agency_rule_draft IS NOT NULL/)
  assert.match(backend, /expected_columns\+=",business_mode,agency_rule_draft"/)
  assert.match(backend, /COLUMN_NAME NOT IN \('business_mode','agency_rule_draft'\)/)
  assert.match(backend, /46:46\) verify_applied_migration_prefix/)
  assert.match(backend, /verify_customer_mode_absent "\$DB_NAME"/)
  assert.match(backend, /verify_default_inviter_schema "\$DB_NAME"/)
})
