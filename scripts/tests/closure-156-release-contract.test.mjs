import assert from 'node:assert/strict'
import cp from 'node:child_process'
import crypto from 'node:crypto'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const scripts = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const read = name => fs.readFileSync(path.join(scripts, name), 'utf8')

const previousJar = '786aec477acb1deaf71c058bfbd55e9d57a9953c31bafa859ae2c9b43695dd96'
const previousCommit = 'a8f86f2ed3123c21082e7404d372812e11dae790'
const migration = 'V202609211530__order_item_service_tag_snapshot.sql'
const migrationSha = 'bb5f0dbf8942db2f2c56c28bd1c6c16fa185b1087690a750affd8ac523962526'
const buildId = '20260922-closure-1.0.156'
const buildMethod = 'clean-build-in-release-process'

function git(cwd, ...args) {
  return cp.execFileSync('git', args, { cwd, encoding: 'utf8' }).trim()
}

function temporaryRepository(files) {
  const parent = fs.mkdtempSync(path.join(os.tmpdir(), 'closure-156-contract.'))
  const root = path.join(parent, 'mall-swarm-app-h5')
  fs.mkdirSync(root)
  for (const [relative, contents] of Object.entries(files)) {
    const target = path.join(root, relative)
    fs.mkdirSync(path.dirname(target), { recursive: true })
    fs.writeFileSync(target, contents)
  }
  git(root, 'init', '-q')
  git(root, 'config', 'user.name', 'Closure Contract Test')
  git(root, 'config', 'user.email', 'closure-contract@example.invalid')
  git(root, 'add', '.')
  git(root, 'commit', '-qm', 'fixture')
  return { parent, root, commit: git(root, 'rev-parse', 'HEAD') }
}

const sha256 = value => crypto.createHash('sha256').update(value).digest('hex')

function fileInventory(directory, prefix = '', result = {}) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true }).sort((left, right) => left.name.localeCompare(right.name))) {
    const relative = prefix ? `${prefix}/${entry.name}` : entry.name
    const absolute = path.join(directory, entry.name)
    if (entry.isDirectory()) fileInventory(absolute, relative, result)
    else if (entry.isFile()) result[relative] = sha256(fs.readFileSync(absolute))
  }
  return result
}

test('1.0.156 unified candidate is package-only and binds the 1.0.154 baseline', () => {
  const source = read('release-lingqi-156.mjs')
  assert.match(source, /const VERSION = '1\.0\.156'/)
  assert.match(source, /const BUILD_ID = '20260922-closure-1\.0\.156'/)
  assert.match(source, /process\.argv\.length !== 3 \|\| process\.argv\[2\] !== '--package-only'/)
  assert.match(source, new RegExp(previousJar))
  assert.match(source, new RegExp(previousCommit))
  assert.match(source, /migrations\.length !== 41/)
  assert.match(source, new RegExp(migration))
  assert.match(source, /remote-deploy-20260922-v1\.0\.156-backend\.sh/)
  assert.match(source, /remote-deploy-20260922-v1\.0\.156-static\.sh/)
  assert.match(source, /Object\.entries\(process\.env\)\.filter\(\(\[key\]\) => !key\.startsWith\('VITE_'\)\)/)
  assert.match(source, /const buildEnvironment = \{\n  \.\.\.sanitizedProcessEnvironment,/)
  assert.doesNotMatch(source, /const buildEnvironment = \{\n  \.\.\.process\.env,/)
})

test('1.0.156 WeChat wrapper can only preflight or upload a development version', () => {
  const source = read('upload-lingqi-mini-156.mjs')
  assert.match(source, /const VERSION = '1\.0\.156'/)
  assert.match(source, /policy: 'development-upload-only'/)
  assert.match(source, /experienceVersionChanged: false/)
  assert.match(source, /reviewSubmitted: false/)
  assert.match(source, /formalVersionPublished: false/)
  assert.match(source, /const commandArgs = \[\n  'upload'/)
  assert.match(source, /Choose exactly one of --preflight-only or --authorize-development-upload/)
  assert.doesNotMatch(source, /const commandArgs = \[\n  '(?:preview|submit|publish)'/)
  const preflightExit = source.indexOf('if (args.preflightOnly)')
  const uploadCommand = source.indexOf('const commandArgs = [')
  assert.ok(preflightExit >= 0 && uploadCommand > preflightExit,
    'the preflight-only branch must terminate before an upload command is assembled')
  assert.match(source.slice(preflightExit, uploadCommand), /process\.exit\(0\)/)
})

test('WeChat preflight executes only harmless CLI capability probes and never upload', () => {
  const parent = fs.mkdtempSync(path.join(os.tmpdir(), 'closure-156-wechat-preflight.'))
  const root = path.join(parent, 'mall-swarm-app-h5')
  const fakeCli = path.join(parent, 'fake-wechat-cli')
  const fakeIde = path.join(parent, 'fake-wechat-ide')
  const fakePackage = path.join(parent, 'fake-wechat-package.json')
  const commandLog = path.join(parent, 'cli-commands.log')
  const transformed = read('upload-lingqi-mini-156.mjs')
    .replace("const CLI = '/Applications/wechatwebdevtools.app/Contents/MacOS/cli'", `const CLI = ${JSON.stringify(fakeCli)}`)
    .replace("const IDE_BINARY = '/Applications/wechatwebdevtools.app/Contents/MacOS/wechatwebdevtools'", `const IDE_BINARY = ${JSON.stringify(fakeIde)}`)
    .replace("const IDE_PACKAGE = '/Applications/wechatwebdevtools.app/Contents/Resources/app.asar.unpacked/package.json'", `const IDE_PACKAGE = ${JSON.stringify(fakePackage)}`)
  fs.mkdirSync(path.join(root, 'scripts/lib'), { recursive: true })
  fs.writeFileSync(path.join(root, 'VERSION'), '1.0.156\n')
  fs.writeFileSync(path.join(root, 'scripts/upload-lingqi-mini-156.mjs'), transformed)
  fs.writeFileSync(path.join(root, 'scripts/lib/wechat-cli-output.mjs'), read('lib/wechat-cli-output.mjs'))
  git(root, 'init', '-q')
  git(root, 'config', 'user.name', 'Closure Contract Test')
  git(root, 'config', 'user.email', 'closure-contract@example.invalid')
  git(root, 'add', '.')
  git(root, 'commit', '-qm', 'fixture')
  const commit = git(root, 'rev-parse', 'HEAD')
  const sourceTree = git(root, 'rev-parse', 'HEAD^{tree}')
  const resolvedRoot = git(root, 'rev-parse', '--show-toplevel')

  const project = path.join(resolvedRoot, 'dist/wechat-mini-program')
  fs.mkdirSync(path.join(project, 'config'), { recursive: true })
  fs.writeFileSync(path.join(project, 'package.json'), JSON.stringify({ version: '1.0.156' }))
  fs.writeFileSync(path.join(project, 'package-lock.json'), JSON.stringify({
    version: '1.0.156', packages: { '': { version: '1.0.156' } },
  }))
  fs.writeFileSync(path.join(project, 'project.config.json'), JSON.stringify({
    appid: 'wxd26e0a4e41df392b', setting: { urlCheck: true },
  }))
  fs.writeFileSync(path.join(project, 'app.json'), JSON.stringify({
    plugins: { logisticsPlugin: { provider: 'wx9ad912bf20548d92', version: '2.1.12' } },
  }))
  fs.writeFileSync(path.join(project, 'config/runtime.js'), "module.exports = { API_BASE_URL: 'https://lingqimall.com/api' }\n")
  const files = fileInventory(project)
  const aggregate = sha256(Object.entries(files).sort(([left], [right]) => left < right ? -1 : left > right ? 1 : 0)
    .map(([name, hash]) => `${hash}  ${name}\n`).join(''))
  fs.writeFileSync(`${project}.release.json`, JSON.stringify({
    schemaVersion: 2,
    target: project,
    version: '1.0.156',
    gitCommit: commit,
    sourceTree,
    scope: 'mall-closure-candidate',
    buildId,
    buildMethod,
    previousBackendVersion: '1.0.154',
    previousBackendJarSha256: previousJar,
    previousStaticVersion: '1.0.154',
    previousStaticCommit: previousCommit,
    candidateManifestSha256: '1'.repeat(64),
    miniProgramManifestSha256: '2'.repeat(64),
    sourceArchiveSha256: '3'.repeat(64),
    aggregateSha256: aggregate,
    appid: 'wxd26e0a4e41df392b',
    api: 'https://lingqimall.com/api',
    urlCheck: true,
    plugins: { logisticsPlugin: { provider: 'wx9ad912bf20548d92', version: '2.1.12' } },
    fileCount: Object.keys(files).length,
    files,
  }))

  fs.writeFileSync(fakeCli, `#!/bin/sh
printf '%s\\n' "$*" >> ${JSON.stringify(commandLog)}
if [ "$1" = "upload" ] && [ "$2" = "--help" ]; then echo '--info-output'; exit 0; fi
if [ "$1" = "quit" ] && [ "$2" = "--help" ]; then echo 'Quit IDE'; exit 0; fi
echo 'MUTATING_UPLOAD' >> ${JSON.stringify(commandLog)}
exit 99
`)
  fs.chmodSync(fakeCli, 0o755)
  fs.writeFileSync(fakeIde, 'fixed fake IDE binary\n')
  fs.writeFileSync(fakePackage, JSON.stringify({ version: '9.9.9' }))

  try {
    const result = cp.spawnSync(process.execPath, ['scripts/upload-lingqi-mini-156.mjs', '--preflight-only', '--port', '47777'], {
      cwd: root,
      encoding: 'utf8',
    })
    assert.equal(result.status, 0, result.stderr)
    assert.equal(JSON.parse(result.stdout).result, 'preflight-ok')
    assert.deepEqual(fs.readFileSync(commandLog, 'utf8').trim().split('\n'), ['upload --help', 'quit --help'])
  } finally {
    fs.rmSync(parent, { recursive: true, force: true })
  }
})

test('1.0.156 backend allows only the fixed additive migration and never drops it on recovery', () => {
  const source = read('remote-deploy-20260922-v1.0.156-backend.sh')
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
  assert.match(source, new RegExp(`EXPECTED_BUILD_ID=${buildId.replaceAll('.', '\\.')}`))
  assert.match(source, new RegExp(`EXPECTED_BUILD_METHOD=${buildMethod}`))
  assert.match(source, /EXPECTED_SOURCE_TREE=\$\{LINGQIMALL_RELEASE_SOURCE_TREE:-\}/)
  assert.match(source, /\[\[ "\$EXPECTED_SOURCE_TREE" =~ \^\[a-f0-9\]\{40\}\$ \]\]/)
  assert.match(source, /manifest\['sourceTree'\] == source_tree/)
  assert.match(source, /manifest\.get\('buildId'\) == build_id == '20260922-closure-1\.0\.156'/)
  assert.match(source, /manifest\.get\('buildMethod'\) == build_method == 'clean-build-in-release-process'/)
  assert.doesNotMatch(source, /DROP\s+(?:COLUMN|TABLE)\s+service_tags/i)
})

test('1.0.156 static release binds the exact build method and independently supplied source tree', () => {
  const source = read('remote-deploy-20260922-v1.0.156-static.sh')
  assert.match(source, /EXPECTED_BUILD_ID=20260922-closure-1\.0\.156/)
  assert.match(source, /EXPECTED_BUILD_METHOD=clean-build-in-release-process/)
  assert.match(source, /EXPECTED_SOURCE_TREE=\$\{LINGQIMALL_RELEASE_SOURCE_TREE:-\}/)
  assert.match(source, /\[\[ "\$EXPECTED_SOURCE_TREE" =~ \^\[a-f0-9\]\{40\}\$ \]\]/)
  assert.match(source, /manifest\['sourceTree'\] == source_tree/)
  assert.match(source, /manifest\.get\('buildId'\) == build_id == '20260922-closure-1\.0\.156'/)
  assert.match(source, /manifest\.get\('buildMethod'\) == build_method == 'clean-build-in-release-process'/)
  assert.doesNotMatch(source, /EXPECTED_BUILD_ID=\$\{IDENTITY\[1\]\}/)
})

test('generic readiness and mini preparation point at the same 1.0.156 identity', () => {
  const prepare = read('prepare-lingqi-mini-release.mjs')
  const readiness = read('release-readiness.sh')
  const regression = read('run-mall-closure-regression.sh')
  for (const source of [prepare, readiness]) {
    assert.match(source, /1\.0\.156/)
    assert.match(source, /20260922-closure-1\.0\.156/)
    assert.match(source, new RegExp(previousJar))
    assert.match(source, new RegExp(previousCommit))
  }
  assert.match(readiness, /expected_migration_count = 41/)
  assert.match(readiness, new RegExp(migrationSha))
  assert.match(readiness, /UPSTREAM_REF=\$\(git -C "\$ROOT_DIR" rev-parse --abbrev-ref '@\{upstream\}'/)
  assert.match(readiness, /\|\| fail "当前分支没有可读取的 upstream"/)
  assert.match(readiness, /UPSTREAM_DIVERGENCE=\$\(git -C "\$ROOT_DIR" rev-list --left-right --count 'HEAD\.\.\.@\{upstream\}'/)
  assert.match(readiness, /\|\| fail "无法读取 upstream 同步状态"/)
  assert.match(regression, /EXPECTED_VERSION=1\.0\.156/)
  assert.match(regression, /EXPECTED_BUILD_ID=20260922-closure-1\.0\.156/)
  assert.match(regression, /"\$\{RELEASE_BUILD_ID:-\}" == "\$EXPECTED_BUILD_ID"/)
  assert.match(regression, /version !== expectedVersion/)
  assert.match(regression, /收口回归要求当前分支配置可读取的 upstream/)
  assert.match(regression, /== 41/)
  assert.match(regression, /release-lingqi-156\.mjs/)
  assert.match(regression, /upload-lingqi-mini-156\.mjs/)
})

test('regression gate rejects a wrong build id before running any build command', () => {
  const fixture = temporaryRepository({
    'VERSION': '1.0.156\n',
    'scripts/run-mall-closure-regression.sh': read('run-mall-closure-regression.sh'),
  })
  try {
    const result = cp.spawnSync('bash', ['scripts/run-mall-closure-regression.sh'], {
      cwd: fixture.root,
      encoding: 'utf8',
      env: {
        ...process.env,
        RELEASE_GIT_COMMIT: fixture.commit,
        RELEASE_BUILD_ID: 'not-the-reviewed-build',
      },
    })
    assert.notEqual(result.status, 0)
    assert.match(result.stderr, /RELEASE_BUILD_ID 必须精确等于 20260922-closure-1\.0\.156/)
    assert.doesNotMatch(`${result.stdout}${result.stderr}`, /\[1\/10\]/)
  } finally {
    fs.rmSync(fixture.parent, { recursive: true, force: true })
  }
})

test('regression and readiness gates both reject a branch without a readable upstream', () => {
  const fixture = temporaryRepository({
    'VERSION': '1.0.156\n',
    'scripts/run-mall-closure-regression.sh': read('run-mall-closure-regression.sh'),
    'scripts/release-readiness.sh': read('release-readiness.sh'),
    'scripts/production-targets.sh': read('production-targets.sh'),
  })
  try {
    const regression = cp.spawnSync('bash', ['scripts/run-mall-closure-regression.sh'], {
      cwd: fixture.root,
      encoding: 'utf8',
      env: {
        ...process.env,
        RELEASE_GIT_COMMIT: fixture.commit,
        RELEASE_BUILD_ID: buildId,
      },
    })
    assert.notEqual(regression.status, 0)
    assert.match(regression.stderr, /要求当前分支配置可读取的 upstream/)
    assert.doesNotMatch(`${regression.stdout}${regression.stderr}`, /\[1\/10\]/)

    const readiness = cp.spawnSync('bash', ['scripts/release-readiness.sh', '--local-only', '--allow-dirty'], {
      cwd: fixture.root,
      encoding: 'utf8',
    })
    assert.notEqual(readiness.status, 0)
    assert.match(readiness.stderr, /当前分支没有可读取的 upstream/)
    assert.doesNotMatch(`${readiness.stdout}${readiness.stderr}`, /本地预检完成/)
  } finally {
    fs.rmSync(fixture.parent, { recursive: true, force: true })
  }
})
