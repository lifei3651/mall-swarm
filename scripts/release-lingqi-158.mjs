import cp from 'node:child_process'
import crypto from 'node:crypto'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

const VERSION = '1.0.158'
const BUILD_ID = '20260923-closure-1.0.158'
const PREVIOUS_BACKEND_VERSION = '1.0.154'
const PREVIOUS_BACKEND_JAR_SHA256 = '786aec477acb1deaf71c058bfbd55e9d57a9953c31bafa859ae2c9b43695dd96'
const PREVIOUS_STATIC_VERSION = '1.0.154'
const PREVIOUS_STATIC_COMMIT = 'a8f86f2ed3123c21082e7404d372812e11dae790'
const NEW_MIGRATION = 'V202609211530__order_item_service_tag_snapshot.sql'
const NEW_MIGRATION_SHA256 = 'bb5f0dbf8942db2f2c56c28bd1c6c16fa185b1087690a750affd8ac523962526'

if (process.argv.length !== 3 || process.argv[2] !== '--package-only') {
  throw new Error('Usage: node scripts/release-lingqi-158.mjs --package-only')
}

const git = (...args) => cp.execFileSync('git', args, { encoding: 'utf8' }).trim()
const root = git('rev-parse', '--show-toplevel')
if (path.basename(root) !== 'mall-swarm-app-h5') throw new Error('Wrong product repository')
process.chdir(root)
if (git('status', '--porcelain')) throw new Error('Commit and push reviewed changes before packaging')
if (git('rev-parse', '--abbrev-ref', 'HEAD') === 'HEAD') throw new Error('Detached HEAD is not a release source')
try {
  const divergence = git('rev-list', '--left-right', '--count', 'HEAD...@{upstream}')
  if (divergence !== '0\t0') throw new Error(`Release source is not synchronized with upstream: ${divergence}`)
} catch (error) {
  if (String(error?.message || error).includes('not synchronized')) throw error
  throw new Error('Release branch has no readable upstream')
}

const commit = git('rev-parse', 'HEAD')
const shortCommit = commit.slice(0, 8)
const sourceTree = git('rev-parse', 'HEAD^{tree}')
const assertSourceUnchanged = phase => {
  if (git('rev-parse', 'HEAD') !== commit || git('rev-parse', 'HEAD^{tree}') !== sourceTree
    || git('status', '--porcelain')) {
    throw new Error(`Release source changed during ${phase}; discard all generated artifacts`)
  }
}
const version = fs.readFileSync('VERSION', 'utf8').trim()
const miniPackage = JSON.parse(fs.readFileSync('mall-mini-program/package.json', 'utf8'))
const miniLock = JSON.parse(fs.readFileSync('mall-mini-program/package-lock.json', 'utf8'))
if (version !== VERSION || miniPackage.version !== VERSION || miniLock.version !== VERSION
  || miniLock.packages?.['']?.version !== VERSION) {
  throw new Error('Root and mini-program versions must all equal 1.0.158')
}

// 候选包不能信任工作区里可能遗留的 ignored 构建产物。打包器在已经确认
// 干净、已推送的不可变提交上重新 clean build，并把同一个提交/构建编号传给
// 四个静态构建；因此随后计算的哈希绑定的是本次进程从 HEAD 生成的产物。
// 同样不能继承发布终端里残留的 VITE_*：它们的优先级高于已提交的 mode env，
// 会在版本清单仍正确时悄悄改写 API、页面形态或管理后台 base 路径。
const sanitizedProcessEnvironment = Object.fromEntries(
  Object.entries(process.env).filter(([key]) => !key.startsWith('VITE_')),
)
const buildEnvironment = {
  ...sanitizedProcessEnvironment,
  RELEASE_GIT_COMMIT: commit,
  RELEASE_BUILD_ID: BUILD_ID,
}
const buildStartedAt = new Date().toISOString()
const runBuild = (command, args, cwd = root) => {
  const result = cp.spawnSync(command, args, {
    cwd,
    env: buildEnvironment,
    stdio: 'inherit',
  })
  if (result.error) throw result.error
  if (result.status !== 0) throw new Error(`Build failed (${result.status}): ${command} ${args.join(' ')}`)
}
runBuild('./mvnw', ['-DskipTests', 'clean', 'package'])
runBuild('npm', ['run', 'build'], path.join(root, 'mall-distribution-admin'))
runBuild('npm', ['run', 'build'], path.join(root, 'mall-shop-web'))
assertSourceUnchanged('clean build')
const buildFinishedAt = new Date().toISOString()

const sha = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')
const tar = (output, cwd, entries) => cp.execFileSync(
  'tar',
  ['--format=ustar', '--no-xattrs', '--no-mac-metadata', '-czf', output, '-C', cwd, ...entries],
  { env: { ...process.env, COPYFILE_DISABLE: '1' } },
)
const copy = (source, target, mode = null) => {
  fs.mkdirSync(path.dirname(target), { recursive: true })
  fs.copyFileSync(source, target)
  if (mode != null) fs.chmodSync(target, mode)
}

const outputs = {
  backend: 'mall-distribution/target/mall-distribution-1.0-SNAPSHOT.jar',
  admin: 'mall-distribution-admin/dist',
  shop: 'mall-shop-web/dist',
  team: 'mall-shop-web/dist-team',
  integrated: 'mall-shop-web/dist-integrated',
}
for (const [label, output] of Object.entries(outputs)) {
  if (!fs.existsSync(output)) throw new Error(`Missing built ${label} output: ${output}`)
}

const staticIdentity = {
  admin: ['admin', outputs.admin],
  shop: ['storefront-public', outputs.shop],
  team: ['team-h5', outputs.team],
  integrated: ['integrated-h5', outputs.integrated],
}
for (const [label, [application, directory]] of Object.entries(staticIdentity)) {
  const manifest = JSON.parse(fs.readFileSync(path.join(directory, 'version.json'), 'utf8'))
  const expected = { version: VERSION, edition: 'app-h5-split', application, gitCommit: commit, buildId: BUILD_ID }
  for (const [key, value] of Object.entries(expected)) {
    if (manifest[key] !== value) throw new Error(`Wrong ${label} build identity: ${key}`)
  }
}

const migrations = fs.readdirSync('document/db/migrations')
  .filter(name => /^V\d{12}__[a-z0-9_]+\.sql$/.test(name))
  .sort()
if (migrations.length !== 41 || migrations[0] !== 'V202608111205__tenant_config_versions_and_operation_log_index.sql'
  || migrations.at(-1) !== NEW_MIGRATION
  || sha(`document/db/migrations/${NEW_MIGRATION}`) !== NEW_MIGRATION_SHA256) {
  throw new Error('Unexpected migration inventory')
}

const stage = fs.mkdtempSync(path.join(os.tmpdir(), 'lingqi-158-unified-candidate.'))
const releaseDir = path.join(root, 'target/releases')
fs.mkdirSync(releaseDir, { recursive: true })

copy(outputs.backend, path.join(stage, 'mall-distribution.jar'))
copy('VERSION', path.join(stage, 'VERSION'))
copy('scripts/production-backup.sh', path.join(stage, 'production-backup.sh'), 0o755)
copy('scripts/db-migrate.sh', path.join(stage, 'db-migrate.sh'), 0o755)
copy('scripts/nginx/lingqimall.conf', path.join(stage, 'lingqimall.conf'))
copy('scripts/nginx/lingqimall-security.conf', path.join(stage, 'lingqimall-security.conf'))
copy('scripts/remote-deploy-20260923-v1.0.158-backend.sh', path.join(stage, 'release-backend.sh'), 0o755)
copy('scripts/remote-deploy-20260923-v1.0.158-static.sh', path.join(stage, 'release-static.sh'), 0o755)
copy('scripts/verify-release-artifact-retention.mjs', path.join(stage, 'verify-artifact-retention.mjs'), 0o755)
for (const name of migrations) {
  copy(`document/db/migrations/${name}`, path.join(stage, 'document/db/migrations', name))
}

const staticArtifacts = {}
function collectFiles(directory) {
  const files = []
  const walk = (current, prefix = '') => {
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const relative = prefix + entry.name
      if (entry.isSymbolicLink()) throw new Error(`Symlink is not allowed: ${relative}`)
      if (entry.name.startsWith('._') || entry.name === '__MACOSX' || entry.name.startsWith('.env')
        || /\.(?:map|pem|key)$/.test(entry.name)) throw new Error(`Unsafe release file: ${relative}`)
      if (entry.isDirectory()) walk(path.join(current, entry.name), relative + '/')
      else if (entry.isFile()) files.push(relative)
      else throw new Error(`Unexpected release entry: ${relative}`)
    }
  }
  walk(directory)
  return files.sort()
}

for (const [label, [, directory]] of Object.entries(staticIdentity)) {
  const files = collectFiles(directory)
  const archiveName = `${label}.tar.gz`
  const checksumsName = `${label.toUpperCase()}_SHA256SUMS`
  tar(path.join(stage, archiveName), directory, ['.'])
  fs.writeFileSync(
    path.join(stage, checksumsName),
    files.map(file => `${sha(path.join(directory, file))}  ./${file}`).join('\n') + '\n',
  )
  staticArtifacts[label] = {
    archive: archiveName,
    sha256: sha(path.join(stage, archiveName)),
    checksums: checksumsName,
    checksumsSha256: sha(path.join(stage, checksumsName)),
    fileCount: files.length,
  }
}

const miniStage = fs.mkdtempSync(path.join(os.tmpdir(), 'lingqi-158-mini-source.'))
const miniTarGz = cp.execFileSync(
  'git',
  ['archive', '--format=tar.gz', commit, 'mall-mini-program'],
  { maxBuffer: 64 * 1024 * 1024 },
)
fs.writeFileSync(path.join(stage, 'mini-program-source.tar.gz'), miniTarGz)
cp.execFileSync('tar', ['-xzf', path.join(stage, 'mini-program-source.tar.gz'), '-C', miniStage, '--strip-components=1'])
const miniFiles = collectFiles(miniStage)
const miniFileHashes = Object.fromEntries(
  miniFiles.map(file => [`mall-mini-program/${file}`, sha(path.join(miniStage, file))]),
)
const miniAggregateSha256 = crypto.createHash('sha256').update(
  Object.entries(miniFileHashes).map(([file, digest]) => `${digest}  ${file}\n`).join(''),
).digest('hex')
const miniManifest = {
  schemaVersion: 1,
  version: VERSION,
  gitCommit: commit,
  sourceArchive: 'mini-program-source.tar.gz',
  sourceArchiveSha256: sha(path.join(stage, 'mini-program-source.tar.gz')),
  sourceRoot: 'mall-mini-program',
  appid: 'wxd26e0a4e41df392b',
  api: 'https://lingqimall.com/api',
  urlCheck: true,
  plugins: {
    logisticsPlugin: { provider: 'wx9ad912bf20548d92', version: '2.1.12' },
  },
  aggregateSha256: miniAggregateSha256,
  fileCount: miniFiles.length,
  files: miniFileHashes,
}
fs.writeFileSync(path.join(stage, 'MINI_PROGRAM_MANIFEST.json'), JSON.stringify(miniManifest, null, 2) + '\n')

const releaseManifest = {
  version: VERSION,
  scope: 'mall-closure-candidate',
  gitCommit: commit,
  sourceTree,
  buildId: BUILD_ID,
  buildMethod: 'clean-build-in-release-process',
  buildStartedAt,
  buildFinishedAt,
  generatedAt: new Date().toISOString(),
  previousVersion: PREVIOUS_BACKEND_VERSION,
  previousJarSha256: PREVIOUS_BACKEND_JAR_SHA256,
  previousStaticVersion: PREVIOUS_STATIC_VERSION,
  previousStaticCommit: PREVIOUS_STATIC_COMMIT,
  jarSha256: sha(path.join(stage, 'mall-distribution.jar')),
  databaseMigrations: migrations,
  artifacts: staticArtifacts,
  miniProgram: {
    sourceArchive: 'mini-program-source.tar.gz',
    sourceArchiveSha256: sha(path.join(stage, 'mini-program-source.tar.gz')),
    manifest: 'MINI_PROGRAM_MANIFEST.json',
    manifestSha256: sha(path.join(stage, 'MINI_PROGRAM_MANIFEST.json')),
    fileCount: miniFiles.length,
  },
}
fs.writeFileSync(path.join(stage, 'RELEASE_MANIFEST.json'), JSON.stringify(releaseManifest, null, 2) + '\n')

const outerFiles = collectFiles(stage).filter(file => file !== 'SHA256SUMS')
fs.writeFileSync(
  path.join(stage, 'SHA256SUMS'),
  outerFiles.map(file => `${sha(path.join(stage, file))}  ${file}`).join('\n') + '\n',
)

assertSourceUnchanged('candidate assembly')

const archive = path.join(releaseDir, `lingqi-mall-${VERSION}-${shortCommit}.tar.gz`)
if (fs.existsSync(archive)) throw new Error(`Refusing to overwrite existing candidate: ${archive}`)
const resultFile = path.join(releaseDir, `lingqi-mall-${VERSION}-${shortCommit}.json`)
let result
try {
  tar(archive, stage, ['.'])
  result = {
    version: VERSION,
    buildId: BUILD_ID,
    gitCommit: commit,
    stage,
    archive,
    archiveSha256: sha(archive),
    jarSha256: releaseManifest.jarSha256,
    miniProgramFiles: miniFiles.length,
    staticFiles: Object.fromEntries(Object.entries(staticArtifacts).map(([key, value]) => [key, value.fileCount])),
  }
  fs.writeFileSync(resultFile, JSON.stringify(result, null, 2) + '\n')
  assertSourceUnchanged('candidate archive creation')
} catch (error) {
  fs.rmSync(archive, { force: true })
  fs.rmSync(resultFile, { force: true })
  throw error
}
console.log(JSON.stringify(result, null, 2))
