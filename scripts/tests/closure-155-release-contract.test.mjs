import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const scripts = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const read = name => fs.readFileSync(path.join(scripts, name), 'utf8')

const previousJar = '786aec477acb1deaf71c058bfbd55e9d57a9953c31bafa859ae2c9b43695dd96'
const previousCommit = 'a8f86f2ed3123c21082e7404d372812e11dae790'
const migration = 'V202609211530__order_item_service_tag_snapshot.sql'
const migrationSha = 'bb5f0dbf8942db2f2c56c28bd1c6c16fa185b1087690a750affd8ac523962526'

test('1.0.155 unified candidate is package-only and binds the 1.0.154 baseline', () => {
  const source = read('release-lingqi-155.mjs')
  assert.match(source, /const VERSION = '1\.0\.155'/)
  assert.match(source, /const BUILD_ID = '20260922-closure-1\.0\.155'/)
  assert.match(source, /process\.argv\.length !== 3 \|\| process\.argv\[2\] !== '--package-only'/)
  assert.match(source, new RegExp(previousJar))
  assert.match(source, new RegExp(previousCommit))
  assert.match(source, /migrations\.length !== 41/)
  assert.match(source, new RegExp(migration))
  assert.match(source, /remote-deploy-20260922-v1\.0\.155-backend\.sh/)
  assert.match(source, /remote-deploy-20260922-v1\.0\.155-static\.sh/)
  assert.match(source, /Object\.entries\(process\.env\)\.filter\(\(\[key\]\) => !key\.startsWith\('VITE_'\)\)/)
  assert.match(source, /const buildEnvironment = \{\n  \.\.\.sanitizedProcessEnvironment,/)
  assert.doesNotMatch(source, /const buildEnvironment = \{\n  \.\.\.process\.env,/)
})

test('1.0.155 WeChat wrapper can only preflight or upload a development version', () => {
  const source = read('upload-lingqi-mini-155.mjs')
  assert.match(source, /const VERSION = '1\.0\.155'/)
  assert.match(source, /policy: 'development-upload-only'/)
  assert.match(source, /experienceVersionChanged: false/)
  assert.match(source, /reviewSubmitted: false/)
  assert.match(source, /formalVersionPublished: false/)
  assert.match(source, /const commandArgs = \[\n  'upload'/)
  assert.match(source, /Choose exactly one of --preflight-only or --authorize-development-upload/)
  assert.doesNotMatch(source, /const commandArgs = \[\n  '(?:preview|submit|publish)'/)
})

test('1.0.155 backend allows only the fixed additive migration and never drops it on recovery', () => {
  const source = read('remote-deploy-20260922-v1.0.155-backend.sh')
  assert.match(source, /EXPECTED_MIGRATIONS_BEFORE=40/)
  assert.match(source, /EXPECTED_MIGRATIONS_AFTER=41/)
  assert.match(source, new RegExp(`NEW_MIGRATION=${migration}`))
  assert.match(source, new RegExp(`NEW_MIGRATION_SHA=${migrationSha}`))
  assert.match(source, /--preflight-only \|\| "\$MODE" == --authorize-release/)
  assert.match(source, /apply_service_tag_migration "\$VERIFY_DB"/)
  assert.match(source, /apply_service_tag_migration "\$DB_NAME"/)
  assert.match(source, /additive-service-tag-migration-retained=yes/)
  assert.match(source, /VERIFY_DB_CREATED=0/)
  assert.match(source, /\[\[ -n "\$VERIFY_DB" && "\$VERIFY_DB_CREATED" == 1 \]\]/)
  assert.match(source, /CREATE DATABASE[\s\S]*VERIFY_DB_CREATED=1[\s\S]*gzip -dc/)
  assert.match(source, /DROP DATABASE \\`\$VERIFY_DB\\`;"\nVERIFY_DB_CREATED=0/)
  assert.doesNotMatch(source, /DROP\s+(?:COLUMN|TABLE)\s+service_tags/i)
})

test('generic readiness and mini preparation point at the same 1.0.155 identity', () => {
  const prepare = read('prepare-lingqi-mini-release.mjs')
  const readiness = read('release-readiness.sh')
  const regression = read('run-mall-closure-regression.sh')
  for (const source of [prepare, readiness]) {
    assert.match(source, /1\.0\.155/)
    assert.match(source, /20260922-closure-1\.0\.155/)
    assert.match(source, new RegExp(previousJar))
    assert.match(source, new RegExp(previousCommit))
  }
  assert.match(readiness, /expected_migration_count = 41/)
  assert.match(readiness, new RegExp(migrationSha))
  assert.match(regression, /== 41/)
  assert.match(regression, /release-lingqi-155\.mjs/)
  assert.match(regression, /upload-lingqi-mini-155\.mjs/)
})
