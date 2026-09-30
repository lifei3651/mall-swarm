import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import cp from 'node:child_process'
import crypto from 'node:crypto'

const root = cp.execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim()
if (path.basename(root) !== 'mall-swarm-app-h5') throw new Error('Wrong product repository')

const EXPECTED_APPID = 'wxd26e0a4e41df392b'
const EXPECTED_API = 'https://lingqimall.com/api'
const EXPECTED_VERSION = '1.0.174'
const EXPECTED_SCOPE = 'mall-closure-candidate'
const EXPECTED_BUILD_ID = '20260930-closure-1.0.174'
const EXPECTED_BUILD_METHOD = 'clean-build-in-release-process'
const EXPECTED_PREVIOUS_BACKEND_VERSION = '1.0.173'
const EXPECTED_PREVIOUS_BACKEND_JAR_SHA256 = 'd3782cb36f1c8fd62822f7c7c79c7611ae53bacc150a2d13d5d9a8aac58d1b6e'
const EXPECTED_PREVIOUS_STATIC_VERSION = '1.0.173'
const EXPECTED_PREVIOUS_STATIC_COMMIT = 'df9937c2fdb484e15c834d0d440de984da7adb1a'
const EXPECTED_MIGRATION_COUNT = 45
const EXPECTED_LAST_MIGRATION = 'V202609301800__direct_referral_immutable_rules.sql'
const EXPECTED_LAST_MIGRATION_SHA256 = 'c61638a9bcff44c09a3f549a8412172e0b067662269038dbf67fd48e044e3f03'
const EXPECTED_PLUGIN = Object.freeze({
  logisticsPlugin: Object.freeze({ provider: 'wx9ad912bf20548d92', version: '2.1.12' }),
})
const SOURCE_ARCHIVE = 'mini-program-source.tar.gz'
const MINI_MANIFEST = 'MINI_PROGRAM_MANIFEST.json'
const RELEASE_MANIFEST = 'RELEASE_MANIFEST.json'
const TARGET = path.join(root, 'dist', 'wechat-mini-program-174')
const TARGET_MANIFEST = `${TARGET}.release.json`
const SOURCE_ONLY_PATHS = [
  /^README\.md$/,
  /^project\.private\.config\.json(?:\.example)?$/,
  /^(?:document|scripts|tests)(?:\/|$)/,
]
const SENSITIVE_PATH = /(^|\/)(?:\.env(?:\..*)?|project\.private\.config\.json|LOCAL_QA_ONLY\.md|[^/]+\.(?:pem|key|p12|pfx|cer|crt))(?:$|\/)/i

function usage() {
  console.error('Usage: node scripts/prepare-lingqi-mini-release-174.mjs --candidate <candidate-dir|candidate.tar.gz> [--manifest <RELEASE_MANIFEST.json>] [--validate-only]')
}

function parseArgs(argv) {
  const result = { candidate: '', manifest: '', validateOnly: false }
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index]
    if (arg === '--candidate') result.candidate = argv[++index] || ''
    else if (arg === '--manifest') result.manifest = argv[++index] || ''
    else if (arg === '--validate-only') result.validateOnly = true
    else if (arg === '-h' || arg === '--help') {
      usage()
      process.exit(0)
    } else throw new Error(`Unknown argument: ${arg}`)
  }
  if (!result.candidate && !result.manifest) throw new Error('A unified candidate or explicit RELEASE_MANIFEST.json is required')
  return result
}

const sha256Buffer = value => crypto.createHash('sha256').update(value).digest('hex')
const sha256File = file => sha256Buffer(fs.readFileSync(file))
const readJson = file => JSON.parse(fs.readFileSync(file, 'utf8'))
const git = (root, ...args) => cp.execFileSync('git', ['-C', root, ...args], { encoding: 'utf8' }).trim()

function assertCleanAndSynchronized(root, expectedCommit = null) {
  if (git(root, 'status', '--porcelain')) throw new Error('Commit and push reviewed changes before preparing the upload project')
  if (git(root, 'rev-parse', '--abbrev-ref', 'HEAD') === 'HEAD') throw new Error('Detached HEAD is not a release source')
  let divergence
  try {
    divergence = git(root, 'rev-list', '--left-right', '--count', 'HEAD...@{upstream}')
  } catch {
    throw new Error('Release branch has no readable upstream')
  }
  if (divergence !== '0\t0') throw new Error(`Release source is not synchronized with upstream: ${divergence}`)
  const currentCommit = git(root, 'rev-parse', 'HEAD')
  if (expectedCommit && currentCommit !== expectedCommit) throw new Error('Release source changed during upload-project preparation')
  if (git(root, 'show', `${currentCommit}:VERSION`) !== EXPECTED_VERSION) throw new Error('Current source version is not 1.0.174; refusing a stale release')
  return currentCommit
}

function assertTargetAbsent() {
  if (fs.lstatSync(TARGET, { throwIfNoEntry: false }) || fs.lstatSync(TARGET_MANIFEST, { throwIfNoEntry: false })) {
    throw new Error('Fixed 1.0.174 upload target or manifest already exists; refusing to overwrite')
  }
}

function normalizedRelative(value) {
  const normalized = value.replaceAll('\\', '/').replace(/^\.\//, '')
  if (!normalized || normalized.startsWith('/') || normalized.split('/').includes('..')) {
    throw new Error(`Unsafe archive or manifest path: ${value}`)
  }
  return normalized
}

function listFiles(directory, prefix = '', result = {}) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true }).sort((left, right) => left.name.localeCompare(right.name))) {
    const relative = prefix ? `${prefix}/${entry.name}` : entry.name
    const absolute = path.join(directory, entry.name)
    if (entry.isSymbolicLink()) throw new Error(`Symlink is forbidden: ${relative}`)
    if (entry.isDirectory()) listFiles(absolute, relative, result)
    else if (entry.isFile()) result[relative] = sha256File(absolute)
    else throw new Error(`Unsupported file type: ${relative}`)
  }
  return result
}

function aggregateFiles(files) {
  const ledger = Object.entries(files).sort(([left], [right]) => left < right ? -1 : left > right ? 1 : 0)
    .map(([name, hash]) => `${hash}  ${name}\n`).join('')
  return sha256Buffer(ledger)
}

function canonicalJson(value) {
  if (Array.isArray(value)) return value.map(canonicalJson)
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonicalJson(value[key])]))
  }
  return value
}

function assertExactObject(actual, expected, label) {
  if (JSON.stringify(canonicalJson(actual)) !== JSON.stringify(canonicalJson(expected))) throw new Error(`${label} mismatch`)
}

function assertSafeTar(archive, label) {
  const script = String.raw`
import pathlib, sys, tarfile
archive_path = pathlib.Path(sys.argv[1])
with tarfile.open(archive_path, 'r:gz') as handle:
    headers = getattr(handle, 'pax_headers', {})
    if any('xattr' in key.lower() or 'com.apple' in key.lower() for key in headers):
        raise SystemExit('archive contains extended attributes')
    for member in handle.getmembers():
        name = member.name.replace('\\', '/')
        parts = pathlib.PurePosixPath(name).parts
        if not name or name.startswith('/') or '..' in parts:
            raise SystemExit('unsafe path: ' + name)
        if member.issym() or member.islnk() or member.isdev() or member.isfifo():
            raise SystemExit('unsafe member type: ' + name)
        headers = getattr(member, 'pax_headers', {})
        if any('xattr' in key.lower() or 'com.apple' in key.lower() for key in headers):
            raise SystemExit('member contains extended attributes: ' + name)
`
  const result = cp.spawnSync('python3', ['-c', script, archive], { encoding: 'utf8' })
  if (result.status !== 0) throw new Error(`${label} is unsafe: ${(result.stderr || result.stdout).trim()}`)
}

function extractTar(archive, destination, label) {
  assertSafeTar(archive, label)
  cp.execFileSync('tar', ['-xzf', archive, '-C', destination], {
    env: { ...process.env, COPYFILE_DISABLE: '1' },
  })
}

function locateCandidateRoot(directory) {
  if (fs.existsSync(path.join(directory, RELEASE_MANIFEST))) return directory
  const children = fs.readdirSync(directory, { withFileTypes: true }).filter(entry => entry.name !== '__MACOSX')
  if (children.length === 1 && children[0].isDirectory()) {
    const nested = path.join(directory, children[0].name)
    if (fs.existsSync(path.join(nested, RELEASE_MANIFEST))) return nested
  }
  throw new Error(`Candidate does not contain ${RELEASE_MANIFEST}`)
}

function parseSha256Sums(file) {
  const values = new Map()
  for (const [index, rawLine] of fs.readFileSync(file, 'utf8').split(/\r?\n/).entries()) {
    if (!rawLine) continue
    const match = rawLine.match(/^([a-f0-9]{64})  ([^\0]+)$/)
    if (!match) throw new Error(`Invalid SHA256SUMS line ${index + 1}`)
    const name = normalizedRelative(match[2])
    if (values.has(name)) throw new Error(`Duplicate SHA256SUMS entry: ${name}`)
    values.set(name, match[1])
  }
  return values
}

function verifyCandidateChecksums(candidateRoot) {
  const sumsPath = path.join(candidateRoot, 'SHA256SUMS')
  if (!fs.existsSync(sumsPath)) throw new Error('Candidate is missing SHA256SUMS')
  const sums = parseSha256Sums(sumsPath)
  const actual = listFiles(candidateRoot)
  delete actual.SHA256SUMS
  const expectedNames = Object.keys(actual).sort()
  const listedNames = [...sums.keys()].sort()
  if (JSON.stringify(expectedNames) !== JSON.stringify(listedNames)) {
    throw new Error('SHA256SUMS must cover every candidate file exactly once')
  }
  for (const [name, hash] of sums) {
    if (actual[name] !== hash) throw new Error(`Candidate checksum mismatch: ${name}`)
  }
}

function validateMiniManifest(candidateRoot, release, mini, root, sourceCommit) {
  if (mini.schemaVersion !== 1) throw new Error('Unsupported MINI_PROGRAM_MANIFEST schemaVersion')
  if (release.version !== EXPECTED_VERSION || release.scope !== EXPECTED_SCOPE || release.buildId !== EXPECTED_BUILD_ID
    || release.buildMethod !== EXPECTED_BUILD_METHOD) throw new Error('Unified closure candidate identity is incorrect')
  if (!/^[a-f0-9]{40}$/.test(release.gitCommit || '')) throw new Error('Release manifest must bind an immutable 40-character commit')
  if (!/^[a-f0-9]{40}$/.test(release.sourceTree || '')) throw new Error('Release manifest must bind the immutable source tree')
  if (release.previousVersion !== EXPECTED_PREVIOUS_BACKEND_VERSION
    || release.previousJarSha256 !== EXPECTED_PREVIOUS_BACKEND_JAR_SHA256
    || release.previousStaticVersion !== EXPECTED_PREVIOUS_STATIC_VERSION
    || release.previousStaticCommit !== EXPECTED_PREVIOUS_STATIC_COMMIT) {
    throw new Error('Unified closure candidate production baseline is incorrect')
  }
  if (mini.version !== release.version || mini.gitCommit !== release.gitCommit) throw new Error('Release and mini-program manifests disagree')
  if (mini.sourceArchive !== SOURCE_ARCHIVE || mini.sourceRoot !== 'mall-mini-program') throw new Error('Unexpected mini-program source binding')
  if (!/^[a-f0-9]{64}$/.test(mini.sourceArchiveSha256 || '')) throw new Error('Invalid mini-program source archive checksum')
  if (mini.appid !== EXPECTED_APPID || mini.api !== EXPECTED_API || mini.urlCheck !== true) throw new Error('Mini-program production identity is incorrect')
  assertExactObject(mini.plugins, EXPECTED_PLUGIN, 'Mini-program plugin inventory')
  const releaseMini = release.miniProgram
  if (!releaseMini || releaseMini.sourceArchive !== SOURCE_ARCHIVE || releaseMini.sourceArchiveSha256 !== mini.sourceArchiveSha256
    || releaseMini.manifest !== MINI_MANIFEST || releaseMini.fileCount !== mini.fileCount) {
    throw new Error('RELEASE_MANIFEST miniProgram binding is incomplete')
  }
  const miniManifestHash = sha256File(path.join(candidateRoot, MINI_MANIFEST))
  if (releaseMini.manifestSha256 !== miniManifestHash) throw new Error('RELEASE_MANIFEST mini-program manifest checksum mismatch')
  if (!mini.files || Array.isArray(mini.files) || typeof mini.files !== 'object') throw new Error('MINI_PROGRAM_MANIFEST files must be an object')
  if (mini.fileCount !== Object.keys(mini.files).length) throw new Error('MINI_PROGRAM_MANIFEST fileCount mismatch')
  if (mini.aggregateSha256 !== aggregateFiles(mini.files)) throw new Error('MINI_PROGRAM_MANIFEST aggregate checksum mismatch')
  const resolvedCommit = git(root, 'rev-parse', `${release.gitCommit}^{commit}`)
  if (resolvedCommit !== release.gitCommit) throw new Error('Release commit is not locally available as the exact immutable commit')
  const ancestry = cp.spawnSync('git', ['-C', root, 'merge-base', '--is-ancestor', release.gitCommit, sourceCommit])
  if (ancestry.error || ancestry.status !== 0) throw new Error('Candidate commit is not part of the synchronized release branch')
  if (git(root, 'rev-parse', `${release.gitCommit}^{tree}`) !== release.sourceTree) throw new Error('Release sourceTree does not match gitCommit')
  if (git(root, 'show', `${release.gitCommit}:VERSION`) !== release.version) throw new Error('Git VERSION does not match the unified candidate')
  const migrationPrefix = 'document/db/migrations/'
  const migrationFiles = git(root, 'ls-tree', '-r', '--name-only', release.gitCommit, migrationPrefix)
    .split('\n').filter(name => /^document\/db\/migrations\/V\d{12}__[a-z0-9_]+\.sql$/.test(name))
    .sort().map(name => name.slice(migrationPrefix.length))
  if (migrationFiles.length !== EXPECTED_MIGRATION_COUNT || migrationFiles.at(-1) !== EXPECTED_LAST_MIGRATION) {
    throw new Error('Candidate source does not contain the fixed 45-migration inventory')
  }
  assertExactObject(release.databaseMigrations, migrationFiles, 'Unified candidate migration inventory')
  for (const name of migrationFiles) {
    const candidateFile = path.join(candidateRoot, migrationPrefix, name)
    const committedFile = cp.execFileSync('git', ['-C', root, 'show', `${release.gitCommit}:${migrationPrefix}${name}`])
    if (sha256File(candidateFile) !== sha256Buffer(committedFile)) throw new Error(`Candidate migration differs from immutable source: ${name}`)
  }
  if (sha256File(path.join(candidateRoot, migrationPrefix, EXPECTED_LAST_MIGRATION)) !== EXPECTED_LAST_MIGRATION_SHA256) {
    throw new Error('Direct-referral migration checksum changed')
  }
}

function compareSourceToManifest(sourceRoot, mini) {
  const actual = Object.fromEntries(Object.entries(listFiles(sourceRoot)).map(([name, hash]) => [`mall-mini-program/${name}`, hash]))
  const normalizedManifest = {}
  for (const [rawName, hash] of Object.entries(mini.files)) {
    const name = normalizedRelative(rawName)
    if (!name.startsWith('mall-mini-program/')) throw new Error(`Mini-program manifest path is outside source root: ${name}`)
    if (!/^[a-f0-9]{64}$/.test(hash)) throw new Error(`Invalid mini-program file checksum: ${name}`)
    normalizedManifest[name] = hash
  }
  assertExactObject(actual, normalizedManifest, 'Mini-program source file manifest')
}

function compareSourceToGit(root, commit, extractedRoot, temporaryRoot) {
  const gitArchive = path.join(temporaryRoot, 'git-mini-program.tar')
  const gitExtracted = path.join(temporaryRoot, 'git-source')
  fs.mkdirSync(gitExtracted)
  cp.execFileSync('git', ['-C', root, 'archive', '--format=tar', '-o', gitArchive, commit, 'mall-mini-program'])
  cp.execFileSync('tar', ['-xf', gitArchive, '-C', gitExtracted])
  const fromCandidate = listFiles(extractedRoot)
  const fromGit = listFiles(path.join(gitExtracted, 'mall-mini-program'))
  assertExactObject(fromCandidate, fromGit, 'Candidate mini-program source and immutable git tree')
}

function validateSourceProject(sourceRoot, version) {
  const packageJson = readJson(path.join(sourceRoot, 'package.json'))
  const lock = readJson(path.join(sourceRoot, 'package-lock.json'))
  const lockRoot = lock.packages?.['']
  if (packageJson.version !== version || lock.version !== version || lockRoot?.version !== version) {
    throw new Error('Root, mini package, and lockfile versions are not identical')
  }
  const project = readJson(path.join(sourceRoot, 'project.config.json'))
  if (project.setting?.urlCheck !== true) throw new Error('Source project must keep legal-domain validation enabled')
  const runtime = fs.readFileSync(path.join(sourceRoot, 'config/runtime.js'), 'utf8')
  if (!runtime.includes(`API_BASE_URL: '${EXPECTED_API}'`)) throw new Error('Wrong mini-program production API target')
  const app = readJson(path.join(sourceRoot, 'app.json'))
  assertExactObject(app.plugins, EXPECTED_PLUGIN, 'Source plugin inventory')
}

function createRuntimeStage(sourceRoot, stage) {
  fs.cpSync(sourceRoot, stage, {
    recursive: true,
    filter: source => {
      if (source === sourceRoot) return true
      const relative = path.relative(sourceRoot, source).replaceAll(path.sep, '/')
      if (SENSITIVE_PATH.test(relative)) return false
      return !SOURCE_ONLY_PATHS.some(pattern => pattern.test(relative))
    },
  })
  const projectFile = path.join(stage, 'project.config.json')
  const project = readJson(projectFile)
  project.appid = EXPECTED_APPID
  project.projectname = '灵启商城-正式上传工程'
  project.setting = { ...project.setting, urlCheck: true }
  fs.writeFileSync(projectFile, `${JSON.stringify(project, null, 2)}\n`)
  return project
}

function validateRuntimeStage(stage, version) {
  const files = listFiles(stage)
  for (const name of Object.keys(files)) {
    if (SENSITIVE_PATH.test(name) || SOURCE_ONLY_PATHS.some(pattern => pattern.test(name))) {
      throw new Error(`Source-only, test, or sensitive file leaked into upload project: ${name}`)
    }
  }
  validateSourceProject(stage, version)
  const project = readJson(path.join(stage, 'project.config.json'))
  if (project.appid !== EXPECTED_APPID || project.setting?.urlCheck !== true) throw new Error('Generated upload project identity is incorrect')
  return files
}

const args = parseArgs(process.argv.slice(2))
const sourceCommit = assertCleanAndSynchronized(root)
if (!args.validateOnly) assertTargetAbsent()
const temporaryRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'lingqi-mini-release.'))
let stage = ''
try {
  let candidateRoot
  if (args.candidate) {
    const candidate = path.resolve(args.candidate)
    if (!fs.existsSync(candidate)) throw new Error(`Candidate does not exist: ${candidate}`)
    if (fs.statSync(candidate).isDirectory()) candidateRoot = locateCandidateRoot(candidate)
    else {
      const extracted = path.join(temporaryRoot, 'candidate')
      fs.mkdirSync(extracted)
      extractTar(candidate, extracted, 'Unified candidate')
      candidateRoot = locateCandidateRoot(extracted)
    }
  } else {
    const explicitManifest = path.resolve(args.manifest)
    if (path.basename(explicitManifest) !== RELEASE_MANIFEST || !fs.existsSync(explicitManifest)) {
      throw new Error('Explicit manifest must be an existing RELEASE_MANIFEST.json')
    }
    candidateRoot = path.dirname(explicitManifest)
  }

  const manifestPath = args.manifest ? path.resolve(args.manifest) : path.join(candidateRoot, RELEASE_MANIFEST)
  if (path.dirname(manifestPath) !== candidateRoot || path.basename(manifestPath) !== RELEASE_MANIFEST) {
    throw new Error('Explicit manifest must be the RELEASE_MANIFEST.json inside the unified candidate')
  }
  for (const required of ['VERSION', RELEASE_MANIFEST, MINI_MANIFEST, SOURCE_ARCHIVE, 'SHA256SUMS']) {
    if (!fs.statSync(path.join(candidateRoot, required), { throwIfNoEntry: false })?.isFile()) throw new Error(`Candidate is missing ${required}`)
  }
  verifyCandidateChecksums(candidateRoot)

  const release = readJson(manifestPath)
  const mini = readJson(path.join(candidateRoot, MINI_MANIFEST))
  const candidateVersion = fs.readFileSync(path.join(candidateRoot, 'VERSION'), 'utf8').trim()
  if (candidateVersion !== release.version) throw new Error('Candidate VERSION and RELEASE_MANIFEST disagree')
  validateMiniManifest(candidateRoot, release, mini, root, sourceCommit)

  const sourceArchive = path.join(candidateRoot, SOURCE_ARCHIVE)
  if (sha256File(sourceArchive) !== mini.sourceArchiveSha256) throw new Error('Mini-program source archive checksum mismatch')
  const sourceExtracted = path.join(temporaryRoot, 'source')
  fs.mkdirSync(sourceExtracted)
  extractTar(sourceArchive, sourceExtracted, 'Mini-program source archive')
  const sourceRoot = path.join(sourceExtracted, 'mall-mini-program')
  if (!fs.statSync(sourceRoot, { throwIfNoEntry: false })?.isDirectory()) throw new Error('Mini-program source archive has the wrong root')
  const sourceEntries = fs.readdirSync(sourceExtracted)
  if (sourceEntries.length !== 1 || sourceEntries[0] !== 'mall-mini-program') throw new Error('Mini-program source archive contains extra roots')
  compareSourceToManifest(sourceRoot, mini)
  compareSourceToGit(root, release.gitCommit, sourceRoot, temporaryRoot)
  validateSourceProject(sourceRoot, release.version)

  stage = path.join(temporaryRoot, 'upload-project')
  const project = createRuntimeStage(sourceRoot, stage)
  const files = validateRuntimeStage(stage, release.version)
  const aggregateSha256 = aggregateFiles(files)
  assertCleanAndSynchronized(root, sourceCommit)
  if (args.validateOnly) {
    console.log(JSON.stringify({
      result: 'validation-ok',
      version: release.version,
      gitCommit: release.gitCommit,
      sourceFiles: mini.fileCount,
      uploadFiles: Object.keys(files).length,
      aggregateSha256,
    }, null, 2))
  } else {
    assertTargetAbsent()
    fs.mkdirSync(path.dirname(TARGET), { recursive: true })
    const outputManifest = {
      schemaVersion: 2,
      target: TARGET,
      version: release.version,
      scope: release.scope,
      gitCommit: release.gitCommit,
      sourceTree: release.sourceTree,
      buildId: release.buildId,
      buildMethod: release.buildMethod,
      previousBackendVersion: release.previousVersion,
      previousBackendJarSha256: release.previousJarSha256,
      previousStaticVersion: release.previousStaticVersion,
      previousStaticCommit: release.previousStaticCommit,
      appid: project.appid,
      api: EXPECTED_API,
      urlCheck: project.setting.urlCheck,
      plugins: EXPECTED_PLUGIN,
      candidateManifestSha256: sha256File(manifestPath),
      miniProgramManifestSha256: sha256File(path.join(candidateRoot, MINI_MANIFEST)),
      sourceArchiveSha256: mini.sourceArchiveSha256,
      aggregateSha256,
      generatedAt: new Date().toISOString(),
      preservedPrevious: null,
      fileCount: Object.keys(files).length,
      files,
    }
    let newTargetInstalled = false
    let newManifestInstalled = false
    try {
      // Exclusive reservation prevents a concurrent preparer from replacing any existing project.
      fs.mkdirSync(TARGET)
      newTargetInstalled = true
      fs.cpSync(stage, TARGET, { recursive: true, force: false, errorOnExist: true })
      assertExactObject(listFiles(TARGET), files, 'Installed fixed 1.0.174 upload project')
      const manifestDescriptor = fs.openSync(TARGET_MANIFEST, 'wx')
      newManifestInstalled = true
      try {
        fs.writeFileSync(manifestDescriptor, `${JSON.stringify(outputManifest, null, 2)}\n`)
      } finally {
        fs.closeSync(manifestDescriptor)
      }
      assertCleanAndSynchronized(root, sourceCommit)
    } catch (error) {
      if (newManifestInstalled && fs.existsSync(TARGET_MANIFEST)) fs.rmSync(TARGET_MANIFEST, { force: true })
      if (newTargetInstalled && fs.existsSync(TARGET)) fs.rmSync(TARGET, { recursive: true, force: true })
      throw error
    }
    console.log(JSON.stringify({
      target: TARGET,
      version: release.version,
      gitCommit: release.gitCommit,
      files: outputManifest.fileCount,
      aggregateSha256,
      manifestPath: TARGET_MANIFEST,
      preservedPrevious: null,
    }, null, 2))
  }
} finally {
  if (stage && fs.existsSync(stage)) fs.rmSync(stage, { recursive: true, force: true })
  fs.rmSync(temporaryRoot, { recursive: true, force: true })
}
