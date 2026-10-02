import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import crypto from 'node:crypto'
import { execFileSync, spawnSync } from 'node:child_process'
const root = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim()
const read = name => fs.readFileSync(`${root}/${name}`, 'utf8')
const backend = read('scripts/remote-deploy-20261002-v1.0.177-backend.sh')
const baseline = '6c44e3513bf54c7263cdea09471f3392251c4f5d9765d354c240f85ef841079b'
test('177 fixes the read-back 176 baseline and independent upload project', () => {
  for (const name of ['release-lingqi-177.mjs', 'prepare-lingqi-mini-release-177.mjs', 'upload-lingqi-mini-177.mjs']) {
    const body = read(`scripts/${name}`)
    assert.ok(body.includes('1.0.177') && body.includes('1.0.176') && body.includes(baseline))
    assert.ok(body.includes('20261002-closure-1.0.177'))
    execFileSync('node', ['--check', `${root}/scripts/${name}`])
  }
  for (const name of ['release-readiness-177.sh', 'run-mall-closure-regression-177.sh', 'remote-deploy-20261002-v1.0.177-backend.sh', 'remote-deploy-20261002-v1.0.177-static.sh']) execFileSync('bash', ['-n', `${root}/scripts/${name}`])
  assert.match(read('scripts/upload-lingqi-mini-177.mjs'), /wechat-mini-program-177/)
  assert.match(read('scripts/release-lingqi-177.mjs'), /'-pl', 'mall-common,mall-mbg,mall-distribution', 'clean', 'package'/)
})
test('only the 46th configuration migration is added; prior 45 SQL bytes remain fixed', () => {
  const names = fs.readdirSync(`${root}/document/db/migrations`).filter(n => /^V\d{12}__.*\.sql$/.test(n) && n <= 'V202610021000__optional_default_inviter.sql').sort()
  assert.equal(names.length, 46)
  assert.equal(names.at(-1), 'V202610021000__optional_default_inviter.sql')
  for (const name of names.slice(0, 45)) {
    const original = execFileSync('git', ['show', `4d6254a6ac243d1bf19ecfd4e749eb9fadfc17f1:document/db/migrations/${name}`], { cwd: root })
    assert.deepEqual(fs.readFileSync(`${root}/document/db/migrations/${name}`), original)
  }
  const sql = read(`document/db/migrations/${names.at(-1)}`)
  assert.doesNotMatch(sql, /\b(?:UPDATE|DELETE|INSERT|DROP)\b/i)
  assert.ok(backend.includes(crypto.createHash('sha256').update(sql).digest('hex')))
  assert.match(backend, /EXPECTED_MIGRATIONS_BEFORE=45/)
  assert.match(backend, /EXPECTED_MIGRATIONS_AFTER=46/)
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
test('the executed default-schema guard rejects missing columns and nondefault tenant values', () => {
  const start = backend.indexOf('verify_default_inviter_schema() {')
  const end = backend.indexOf('\napply_module_migrations()', start)
  const functionBody = backend.slice(start, end)
  for (const [schema, changed, success] of [[2,0,true], [1,0,false], [2,1,false]]) {
    const result = spawnSync('bash', ['-c', `set -e\nfail() { exit 1; }\nmysql_db() { if [[ "$1" == information_schema ]]; then echo ${schema}; else echo ${changed}; fi; }\n${functionBody}\nverify_default_inviter_schema fixture`], { encoding:'utf8' })
    assert.equal(result.status === 0, success)
  }
})
