import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { execFileSync, spawnSync } from 'node:child_process'
import test from 'node:test'

const root = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim()
const source = fs.readFileSync(path.join(root, 'scripts/remote-deploy-20260927-v1.0.169-backend.sh'), 'utf8')
const start = source.indexOf('verify_service_tag_snapshot_unchanged() {')
const end = source.indexOf('\nmigration_ledger() {', start)
assert.ok(start > 0 && end > start)
const verifyFunction = source.slice(start, end)
const json = value => 'JSON_HEX:' + Buffer.from(JSON.stringify(value)).toString('hex').toUpperCase()
const rows = entries => entries.map(([id, tags]) => `${id}\t${tags}\n`).join('')

function verify(before, after, queryFailure = false) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'closure-169-tags-test.'))
  try {
    fs.writeFileSync(path.join(directory, 'before.tsv'), before)
    fs.writeFileSync(path.join(directory, 'after.tsv'), after)
    // Execute the exact shell/Python verifier embedded in the release script.
    return spawnSync('bash', ['-c', `set -Eeuo pipefail
SNAPSHOT_AFTER=$2
QUERY_FAILURE=$3
fail() { echo "$*" >&2; exit 1; }
service_tag_snapshot() { if [[ "$QUERY_FAILURE" == yes ]]; then return 1; fi; cat "$SNAPSHOT_AFTER"; }
${verifyFunction}
verify_service_tag_snapshot_unchanged fixture "$1"
`, 'snapshot-test', path.join(directory, 'before.tsv'), path.join(directory, 'after.tsv'), queryFailure ? 'yes' : 'no'], { encoding: 'utf8' })
  } finally {
    fs.rmSync(directory, { recursive: true, force: true })
  }
}

test('SQL snapshot is read-only, independent of live product settings, and encodes NULL/JSON exactly', () => {
  const sqlFunction = source.slice(source.indexOf('service_tag_snapshot() {'), start)
  assert.match(sqlFunction, /FROM dms_shop_order_item ORDER BY id/)
  assert.match(sqlFunction, /service_tags IS NULL, 'SQL_NULL'/)
  assert.match(sqlFunction, /HEX\(CAST\(service_tags AS CHAR CHARACTER SET utf8mb4\)\)/)
  assert.doesNotMatch(sqlFunction, /dms_shop_product|UPDATE |INSERT |DELETE /)
  assert.doesNotMatch(source, /service_tag_backfill_gap|backfill incomplete/)
})

test('migration first run/rerun, production migration/restart/backup all preserve historical tags', () => {
  assert.equal((source.match(/verify_service_tag_snapshot_unchanged "\$VERIFY_DB"/g) || []).length, 2)
  assert.equal((source.match(/verify_service_tag_snapshot_unchanged "\$DB_NAME"/g) || []).length, 3)
  assert.ok(source.indexOf('service_tag_snapshot "$VERIFY_DB" >') < source.indexOf('apply_module_migrations "$VERIFY_DB"'))
  const stop = source.indexOf('systemctl stop "$SERVICE"', source.indexOf('MUTATED=1\nDEPLOY_START_TIME='))
  assert.ok(stop < source.indexOf('service_tag_snapshot "$DB_NAME" >'))
})

for (const [label, value] of [['SQL NULL', 'SQL_NULL'], ['empty array', json([])], ['JSON null', json(null)], ['empty object', json({})], ['real labels', json(['七天无理由', '晚发赔'])]]) {
  test(`unchanged historical ${label} is accepted even if product has since changed`, () => {
    const result = verify(rows([['9007199254740993', value]]), rows([['9007199254740993', value]]))
    assert.equal(result.status, 0, result.stderr)
  })
}

test('new rows after restart do not invalidate existing immutable snapshots', () => {
  const result = verify(rows([['2', 'SQL_NULL']]), rows([['1', json([])], ['2', 'SQL_NULL'], ['3', json(['新标签'])]]))
  assert.equal(result.status, 0, result.stderr)
})

test('empty order inventory is valid', () => {
  assert.equal(verify('', '').status, 0)
})

for (const [label, after] of [
  ['NULL backfilled to empty array', rows([['2', json([])]])],
  ['NULL backfilled to current product labels', rows([['2', json(['七天无理由'])]])],
  ['historical row deleted', ''],
  ['duplicate identifier', rows([['2', 'SQL_NULL'], ['2', 'SQL_NULL']])],
  ['malformed snapshot', '2\tJSON_HEX:ZZ\n'],
]) {
  test(`rejects ${label}`, () => {
    assert.notEqual(verify(rows([['2', 'SQL_NULL']]), after).status, 0)
  })
}

test('existing nonempty label mutation is rejected', () => {
  assert.notEqual(verify(rows([['2', json(['七天无理由'])]]), rows([['2', json(['晚发赔'])]])).status, 0)
})

test('query failure is not treated as an empty successful snapshot', () => {
  assert.notEqual(verify('', '', true).status, 0)
})

test('isolated MySQL: actual NULL/empty/current-product drift stays unchanged; historical overwrite is rejected', {
  skip: process.env.CLOSURE_169_MYSQL !== '1',
}, () => {
  const container = `closure169-tags-${process.pid}-${Date.now()}`
  const docker = (...args) => execFileSync('docker', args, { encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] })
  let created = false
  const sql = input => execFileSync('docker', ['exec', '-i', container, 'mysql', '-uroot', '--batch', '--skip-column-names'], { input, encoding: 'utf8' })
  try {
    docker('run', '--detach', '--name', container, '--network', 'none', '--tmpfs', '/var/lib/mysql',
      '--env', 'MYSQL_ALLOW_EMPTY_PASSWORD=yes', 'mysql:8.4.10')
    created = true
    let ready = false
    for (let attempt = 0; attempt < 90; attempt += 1) {
      const status = spawnSync('docker', ['exec', container, 'mysql', '-uroot', '-e', 'SELECT 1'], { encoding: 'utf8' })
      if (status.status === 0) { ready = true; break }
      Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 1000)
    }
    assert.ok(ready, 'isolated MySQL did not start')
    sql(`CREATE DATABASE closure169;
      CREATE TABLE closure169.dms_shop_order_item (id BIGINT PRIMARY KEY, product_id BIGINT, service_tags JSON NULL);
      CREATE TABLE closure169.dms_shop_product (id BIGINT PRIMARY KEY, service_tags JSON NULL);
      INSERT INTO closure169.dms_shop_product VALUES (1,NULL),(2,JSON_ARRAY());
      INSERT INTO closure169.dms_shop_order_item VALUES
        (9007199254740993,1,NULL),(9007199254740994,2,NULL),
        (3,1,JSON_ARRAY()),(4,1,CAST('null' AS JSON)),(5,1,JSON_ARRAY('first','second'));
      UPDATE closure169.dms_shop_product SET service_tags=JSON_ARRAY() WHERE id=1;`)
    const snapshotFunction = source.slice(source.indexOf('service_tag_snapshot() {'), start)
    const snapshot = () => execFileSync('bash', ['-c', `set -Eeuo pipefail
TAG_MYSQL_CONTAINER=$1
mysql_db() { docker exec -i "$TAG_MYSQL_CONTAINER" mysql -uroot "$@"; }
${snapshotFunction}
service_tag_snapshot closure169
`, 'mysql-snapshot', container], { encoding: 'utf8' })
    const before = snapshot()
    assert.equal(sql(`SELECT COUNT(*) FROM closure169.dms_shop_order_item item
      JOIN closure169.dms_shop_product product ON product.id=item.product_id
      WHERE item.service_tags IS NULL AND product.service_tags IS NOT NULL;`).trim(), '2')
    assert.equal(verify(before, snapshot()).status, 0)
    sql(`UPDATE closure169.dms_shop_product SET service_tags=JSON_ARRAY('later-product-label');
      INSERT INTO closure169.dms_shop_order_item VALUES (6,1,JSON_ARRAY('new-order'));`)
    assert.equal(verify(before, snapshot()).status, 0)
    sql('UPDATE closure169.dms_shop_order_item SET service_tags=JSON_ARRAY() WHERE id=9007199254740993;')
    assert.notEqual(verify(before, snapshot()).status, 0)
  } finally {
    if (created) docker('rm', '--force', container)
  }
})
