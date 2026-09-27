import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const read = relative => readFileSync(new URL(relative, import.meta.url), 'utf8')

test('fresh catalog schema includes historical runtime columns and postflight probes them', () => {
  const baseline = read('../../../mall-distribution/document/sql/distribution.sql')
  const probes = read('../scripts/security-postflight.sh')
  for (const [table, fields] of Object.entries({ dms_shop_product: ['safety_stock', 'purchase_limit'], dms_shop_sku: ['safety_stock'], dms_shop_category: ['show_on_home'] })) {
    const schema = baseline.split('CREATE TABLE `' + table + '` (')[1].split('ENGINE=InnoDB')[0]
    for (const field of fields) assert.ok(schema.includes('`' + field + '`'), table + '.' + field)
    assert.ok(probes.includes(`SELECT ${fields.join(', ')} FROM ${table} LIMIT 0;`))
  }
  assert.ok(probes.includes('SELECT return_address, return_received_at FROM dms_shop_after_sale LIMIT 0;'))
})

test('fresh installation runs explicitly mounted prerequisites in dependency order', () => {
  const init = read('../initdb/00_run_project_sql.sh')
  const compose = read('../docker-compose.private.yml')
  const files = [
    '20260714_erp_integration_upgrade.sql',
    '20260808_add_shop_service_addresses.sql',
    '20260808_add_after_sale_return_workflow.sql'
  ]
  const calls = [...init.matchAll(/^run_sql_file \/init-sql\/prerequisites\/(.+)$/gm)].map(m => m[1])
  assert.deepEqual(calls, files)
  for (const file of files) assert.ok(compose.includes(`/init-sql/prerequisites/${file}:ro`))
  assert.ok(init.includes('missing required init sql:'))
  assert.doesNotMatch(init, /run_sql_dir|find .*\.sql/)
})

test('fresh customer defaults do not inherit legacy invitation and merchant opt-ins', () => {
  const baseline = read('../../../mall-distribution/document/sql/distribution.sql')
  const tenant = baseline.split('CREATE TABLE `dms_tenant` (')[1].split('ENGINE=InnoDB')[0]
  for (const field of ['invitation_enabled', 'multi_merchant_enabled']) {
    assert.match(tenant, new RegExp('`' + field + '` tinyint NOT NULL DEFAULT 0'))
  }
  // Upgrade migrations preserve existing clients. Never rewrite them to close live modules.
  for (const file of ['V202609262000__tenant_balance_and_merchant_mode_switches.sql', 'V202609262130__tenant_invitation_switch.sql']) {
    assert.match(read(`../../db/migrations/${file}`), /DEFAULT 1/)
  }
})
