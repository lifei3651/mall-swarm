import assert from 'node:assert/strict'
import crypto from 'node:crypto'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import vm from 'node:vm'
import { execFileSync } from 'node:child_process'
import test from 'node:test'

const root = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim()
const read = file => fs.readFileSync(path.join(root, file), 'utf8')
const packageScript = read('scripts/release-lingqi-174.mjs')
const prepare = read('scripts/prepare-lingqi-mini-release-174.mjs')
const readiness = read('scripts/release-readiness-174.sh')
const upload = read('scripts/upload-lingqi-mini-174.mjs')
const regression = read('scripts/run-mall-closure-regression-174.sh')
const baselineJar = 'd3782cb36f1c8fd62822f7c7c79c7611ae53bacc150a2d13d5d9a8aac58d1b6e'
const baselineCommit = 'df9937c2fdb484e15c834d0d440de984da7adb1a'
const lastMigration = 'V202609301800__direct_referral_immutable_rules.sql'
const lastMigrationHash = 'c61638a9bcff44c09a3f549a8412172e0b067662269038dbf67fd48e044e3f03'
const sha = data => crypto.createHash('sha256').update(data).digest('hex')

function extractFunction(source, name) {
  const start = source.indexOf(`function ${name}(`)
  assert.ok(start >= 0, `Missing ${name}`)
  const end = source.indexOf('\n}\n', start)
  assert.ok(end > start, `Unterminated ${name}`)
  return source.slice(start, end + 2)
}

function freshnessContext(overrides = {}) {
  const state = { status: '', branch: 'codex/release', divergence: '0\t0', commit: 'a'.repeat(40), version: '1.0.174', ...overrides }
  const context = {
    EXPECTED_VERSION: '1.0.174',
    git: (_root, ...args) => {
      const command = args.join(' ')
      if (command === 'status --porcelain') return state.status
      if (command === 'rev-parse --abbrev-ref HEAD') return state.branch
      if (command === 'rev-list --left-right --count HEAD...@{upstream}') {
        if (state.missingUpstream) throw new Error('missing upstream')
        return state.divergence
      }
      if (command === 'rev-parse HEAD') return state.commit
      if (command === `show ${state.commit}:VERSION`) return state.version
      throw new Error(`Unexpected read-only Git command: ${command}`)
    },
  }
  vm.runInNewContext(extractFunction(prepare, 'assertCleanAndSynchronized'), context)
  return context
}

function withInstallationFixture(callback) {
  const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'lingqi-174-install-contract.'))
  try {
    const stage = path.join(temporary, 'stage')
    const target = path.join(temporary, 'wechat-mini-program-174')
    fs.mkdirSync(stage)
    fs.writeFileSync(path.join(stage, 'app.json'), '{"candidate":174}\n')
    const files = { 'app.json': sha(fs.readFileSync(path.join(stage, 'app.json'))) }
    const context = {
      fs,
      stage,
      TARGET: target,
      TARGET_MANIFEST: `${target}.release.json`,
      files,
      outputManifest: { version: '1.0.174', files },
      root: temporary,
      sourceCommit: 'a'.repeat(40),
      assertCleanAndSynchronized: () => {},
      listFiles: directory => Object.fromEntries(fs.readdirSync(directory).map(name => [name, sha(fs.readFileSync(path.join(directory, name)))])),
      assertExactObject: (actual, expected) => assert.deepEqual(actual, expected),
    }
    const begin = prepare.indexOf('    let newTargetInstalled = false')
    const end = prepare.indexOf('    console.log(JSON.stringify({\n      target: TARGET', begin)
    assert.ok(begin > 0 && end > begin)
    const install = () => vm.runInNewContext(`{\n${prepare.slice(begin, end)}\n}`, context)
    callback({ context, target, install })
  } finally {
    fs.rmSync(temporary, { recursive: true, force: true })
  }
}

test('174 候选入口统一固定 173 线上基线、构建编号与源码身份', () => {
  for (const [name, body] of Object.entries({ packageScript, prepare, readiness, upload })) {
    assert.ok(body.includes('1.0.174') && body.includes('1.0.173'), name)
    assert.ok(body.includes(baselineJar) && body.includes(baselineCommit), name)
    assert.ok(body.includes('20260930-closure-1.0.174'), name)
  }
  assert.match(packageScript, /git\('status', '--porcelain'\)/)
  assert.match(packageScript, /HEAD\.\.\.@\{upstream\}/)
  assert.match(packageScript, /assertSourceUnchanged\('clean build'\)/)
  assert.match(packageScript, /assertSourceUnchanged\('candidate archive creation'\)/)
  assert.match(packageScript, /fs\.openSync\(archive, 'wx'\)/)
  assert.match(packageScript, /fs\.openSync\(resultFile, 'wx'\)/)
  assert.match(packageScript, /remote-deploy-20260930-v1\.0\.174-backend\.sh/)
  assert.match(packageScript, /remote-deploy-20260930-v1\.0\.174-static\.sh/)
})

test('174 精确增加第 45 条直接推荐迁移，不沿用 44 条尾集合', () => {
  const prefix = fs.readdirSync(path.join(root, 'document/db/migrations'))
    .filter(name => /^V\d{12}__[a-z0-9_]+\.sql$/.test(name) && name <= lastMigration).sort()
  assert.equal(prefix.length, 45)
  assert.equal(prefix.at(-2), 'V202609262130__tenant_invitation_switch.sql')
  assert.equal(prefix.at(-1), lastMigration)
  assert.equal(sha(read(`document/db/migrations/${lastMigration}`)), lastMigrationHash)
  for (const body of [packageScript, prepare, readiness, regression]) {
    assert.ok(body.includes(lastMigration))
    assert.ok(body.includes(lastMigrationHash))
  }
  assert.match(packageScript, /migrations\.length !== 45/)
  assert.match(prepare, /EXPECTED_MIGRATION_COUNT = 45/)
  assert.match(readiness, /expected_migration_count = 45/)
  assert.match(prepare, /assertExactObject\(release\.databaseMigrations, migrationFiles/)
  assert.match(prepare, /Candidate migration differs from immutable source/)
  assert.match(readiness, /migration-mode=apply-44-to-45/)
  assert.match(readiness, /候选脚本与已验证源码不同/)
})

test('准备工程只接受干净、上游同步、非 detached 且仍为 174 的固定 HEAD', () => {
  const valid = freshnessContext()
  assert.equal(valid.assertCleanAndSynchronized(root), 'a'.repeat(40))
  for (const [overrides, message] of [
    [{ status: ' M VERSION' }, /Commit and push/],
    [{ branch: 'HEAD' }, /Detached HEAD/],
    [{ missingUpstream: true }, /no readable upstream/],
    [{ divergence: '1\t0' }, /not synchronized/],
    [{ divergence: '0\t1' }, /not synchronized/],
    [{ version: '1.0.175' }, /stale release/],
  ]) {
    assert.throws(() => freshnessContext(overrides).assertCleanAndSynchronized(root), message)
  }
  assert.throws(() => valid.assertCleanAndSynchronized(root, 'b'.repeat(40)), /source changed/)
  assert.match(prepare, /merge-base', '--is-ancestor', release\.gitCommit, sourceCommit/)
  assert.match(prepare, /assertCleanAndSynchronized\(root, sourceCommit\)/)
})

test('准备/上传固定独立 174 目录，不替换 173 历史工程', () => {
  assert.match(prepare, /const TARGET = path\.join\(root, 'dist', 'wechat-mini-program-174'\)/)
  assert.match(upload, /const PROJECT = path\.join\(root, 'dist', 'wechat-mini-program-174'\)/)
  assert.doesNotMatch(prepare, /verifyExistingTarget|verifyRegisteredBaseline|SUPERSEDED_MINI_TARGETS|fs\.renameSync/)
  assert.match(prepare, /if \(!args\.validateOnly\) assertTargetAbsent\(\)/)
  assert.match(upload, /2026-10-01-release-snapshot-174/)
})

test('新工程独占创建，逐文件回读一致并独占写入版本清单', () => {
  withInstallationFixture(({ context, target, install }) => {
    install()
    assert.deepEqual(JSON.parse(fs.readFileSync(context.TARGET_MANIFEST, 'utf8')), context.outputManifest)
    assert.equal(fs.readFileSync(path.join(target, 'app.json'), 'utf8'), '{"candidate":174}\n')
    assert.throws(install, /EEXIST/)
    assert.equal(fs.readFileSync(path.join(target, 'app.json'), 'utf8'), '{"candidate":174}\n')
  })
})

test('目录已存在或清单孤立存在时不覆盖也不删除先前内容', () => {
  withInstallationFixture(({ target, install }) => {
    fs.mkdirSync(target)
    fs.writeFileSync(path.join(target, 'original.txt'), 'previous upload project')
    assert.throws(install, /EEXIST/)
    assert.equal(fs.readFileSync(path.join(target, 'original.txt'), 'utf8'), 'previous upload project')
  })
  withInstallationFixture(({ context, target, install }) => {
    fs.writeFileSync(context.TARGET_MANIFEST, 'previous manifest')
    assert.throws(install, /EEXIST/)
    assert.equal(fs.readFileSync(context.TARGET_MANIFEST, 'utf8'), 'previous manifest')
    assert.equal(fs.existsSync(target), false)
  })
})

test('断开的符号链接也阻止覆盖；源提交变化只清理本次安装', () => {
  withInstallationFixture(({ context, target }) => {
    fs.symlinkSync(path.join(target, 'missing'), target)
    vm.runInNewContext(extractFunction(prepare, 'assertTargetAbsent'), context)
    assert.throws(() => context.assertTargetAbsent(), /already exists/)
    assert.equal(fs.lstatSync(target).isSymbolicLink(), true)
  })
  withInstallationFixture(({ context, target, install }) => {
    context.assertCleanAndSynchronized = () => { throw new Error('source changed') }
    assert.throws(install, /source changed/)
    assert.equal(fs.existsSync(target), false)
    assert.equal(fs.existsSync(context.TARGET_MANIFEST), false)
  })
})

test('小程序仅显式上传开发版，缺私有留档回执不得上传，也不自动提审发布', () => {
  assert.match(upload, /development-upload-only/)
  assert.match(upload, /Authorized upload requires --candidate and --retention-receipt/)
  assert.match(upload, /Retention receipt and fixed WeChat project refer to different candidate manifests/)
  assert.match(upload, /Evidence commit is not synchronized with upstream/)
  assert.match(upload, /portClosed: !portListening\(port\)/)
  for (const marker of ['experienceVersionChanged: false', 'reviewSubmitted: false', 'formalVersionPublished: false']) {
    assert.ok(upload.includes(marker))
  }
  assert.doesNotMatch(upload, /--authorize-review|--authorize-publish/)
})

test('回归门禁逐个检查所有 shell 脚本，不把后续文件误当作 bash 参数', () => {
  assert.match(regression, /for closure_script in scripts\/production-backup\.sh scripts\/db-migrate\.sh/)
  assert.match(regression, /bash -n "\$closure_script"/)
  assert.match(regression, /RELEASE_GIT_COMMIT 必须等于当前不可变提交/)
  assert.match(regression, /EXPECTED_BUILD_ID=20260930-closure-1\.0\.174/)
  assert.match(regression, /node --test scripts\/tests\/\*\.test\.mjs/)
})
