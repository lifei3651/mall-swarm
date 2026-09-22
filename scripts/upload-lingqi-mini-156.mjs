import fs from 'node:fs'
import path from 'node:path'
import cp from 'node:child_process'
import crypto from 'node:crypto'

import { hasSuccessfulUploadMarker } from './lib/wechat-cli-output.mjs'

const root = cp.execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim()
if (path.basename(root) !== 'mall-swarm-app-h5') throw new Error('Wrong product repository')

const VERSION = '1.0.156'
const EXPECTED_SCOPE = 'mall-closure-candidate'
const EXPECTED_BUILD_ID = '20260922-closure-1.0.156'
const EXPECTED_BUILD_METHOD = 'clean-build-in-release-process'
const EXPECTED_PREVIOUS_BACKEND_VERSION = '1.0.154'
const EXPECTED_PREVIOUS_BACKEND_JAR_SHA256 = '786aec477acb1deaf71c058bfbd55e9d57a9953c31bafa859ae2c9b43695dd96'
const EXPECTED_PREVIOUS_STATIC_VERSION = '1.0.154'
const EXPECTED_PREVIOUS_STATIC_COMMIT = 'a8f86f2ed3123c21082e7404d372812e11dae790'
const EXPECTED_APPID = 'wxd26e0a4e41df392b'
const EXPECTED_API = 'https://lingqimall.com/api'
const EXPECTED_PLUGIN = Object.freeze({
  logisticsPlugin: Object.freeze({ provider: 'wx9ad912bf20548d92', version: '2.1.12' }),
})
const PROJECT = path.join(root, 'dist', 'wechat-mini-program')
const PROJECT_MANIFEST = `${PROJECT}.release.json`
const CLI = '/Applications/wechatwebdevtools.app/Contents/MacOS/cli'
const IDE_BINARY = '/Applications/wechatwebdevtools.app/Contents/MacOS/wechatwebdevtools'
const IDE_PACKAGE = '/Applications/wechatwebdevtools.app/Contents/Resources/app.asar.unpacked/package.json'
const DEFAULT_PORT = 32227
const SOURCE_ONLY_PATHS = [
  /^README\.md$/,
  /^project\.private\.config\.json(?:\.example)?$/,
  /^(?:document|scripts|tests)(?:\/|$)/,
]
const SENSITIVE_PATH = /(^|\/)(?:\.env(?:\..*)?|project\.private\.config\.json|LOCAL_QA_ONLY\.md|[^/]+\.(?:pem|key|p12|pfx|cer|crt))(?:$|\/)/i

function usage() {
  console.error(`Usage:
  node scripts/upload-lingqi-mini-156.mjs --preflight-only [--port ${DEFAULT_PORT}]
  node scripts/upload-lingqi-mini-156.mjs --authorize-development-upload \
    --candidate <exact-candidate.tar.gz> --retention-receipt <artifact-retention.json> \
    [--description <text>] [--port ${DEFAULT_PORT}] [--qa-root <repo/document/qa/path>]

This wrapper uploads only a WeChat development version. It cannot set an experience version,
submit for review, or publish a formal version.`)
}

function parseArgs(argv) {
  const result = {
    authorize: false,
    preflightOnly: false,
    description: '商城收口统一候选版：界面规范、账号关系与交易链路回归',
    port: DEFAULT_PORT,
    qaRoot: '',
    candidate: '',
    retentionReceipt: '',
  }
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index]
    if (arg === '--authorize-development-upload') result.authorize = true
    else if (arg === '--preflight-only') result.preflightOnly = true
    else if (arg === '--description') result.description = argv[++index] || ''
    else if (arg === '--port') result.port = Number(argv[++index])
    else if (arg === '--qa-root') result.qaRoot = argv[++index] || ''
    else if (arg === '--candidate') result.candidate = argv[++index] || ''
    else if (arg === '--retention-receipt') result.retentionReceipt = argv[++index] || ''
    else if (arg === '-h' || arg === '--help') {
      usage()
      process.exit(0)
    } else throw new Error(`Unknown argument: ${arg}`)
  }
  if (result.authorize === result.preflightOnly) throw new Error('Choose exactly one of --preflight-only or --authorize-development-upload')
  if (!Number.isInteger(result.port) || result.port < 1024 || result.port > 65535) throw new Error('Invalid temporary service port')
  if (!result.description || result.description.length > 80 || /[\r\n\0]/.test(result.description)) throw new Error('Description must be 1-80 characters on one line')
  if (result.authorize && (!result.candidate || !result.retentionReceipt)) {
    throw new Error('Authorized upload requires --candidate and --retention-receipt')
  }
  return result
}

const sha256Buffer = value => crypto.createHash('sha256').update(value).digest('hex')
const sha256File = file => sha256Buffer(fs.readFileSync(file))
const readJson = file => JSON.parse(fs.readFileSync(file, 'utf8'))

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

function listFiles(directory, prefix = '', result = {}) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true }).sort((left, right) => left.name.localeCompare(right.name))) {
    const relative = prefix ? `${prefix}/${entry.name}` : entry.name
    const absolute = path.join(directory, entry.name)
    if (entry.isSymbolicLink()) throw new Error(`Upload project contains a symlink: ${relative}`)
    if (entry.isDirectory()) listFiles(absolute, relative, result)
    else if (entry.isFile()) result[relative] = sha256File(absolute)
    else throw new Error(`Upload project contains an unsupported file: ${relative}`)
  }
  return result
}

function aggregateFiles(files) {
  const ledger = Object.entries(files).sort(([left], [right]) => left < right ? -1 : left > right ? 1 : 0)
    .map(([name, hash]) => `${hash}  ${name}\n`).join('')
  return sha256Buffer(ledger)
}

function portListening(port) {
  const result = cp.spawnSync('lsof', ['-nP', `-iTCP:${port}`, '-sTCP:LISTEN'], { encoding: 'utf8' })
  if (result.error?.code === 'ENOENT') throw new Error('lsof is required to guarantee temporary port shutdown')
  if (result.status === 0) return true
  if (result.status === 1) return false
  throw new Error(`Unable to inspect temporary port ${port}: ${(result.stderr || '').trim()}`)
}

function wait(milliseconds) {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, milliseconds)
}

function sanitized(value) {
  let output = String(value || '')
  const explicitToken = process.env.WECHAT_DEVTOOLS_CLI_TOKEN
  if (explicitToken) output = output.split(explicitToken).join('[REDACTED]')
  return output
    .replace(/(WECHAT_DEVTOOLS_CLI_TOKEN\s*[=:]\s*)\S+/gi, '$1[REDACTED]')
    .replace(/([?&](?:access_)?token=)[^&\s]+/gi, '$1[REDACTED]')
}

function verifyCliPreflight() {
  for (const file of [CLI, IDE_BINARY, IDE_PACKAGE]) {
    if (!fs.statSync(file, { throwIfNoEntry: false })?.isFile()) throw new Error(`Missing WeChat developer tool component: ${file}`)
  }
  fs.accessSync(CLI, fs.constants.X_OK)
  const uploadHelp = cp.spawnSync(CLI, ['upload', '--help'], { encoding: 'utf8' })
  const quitHelp = cp.spawnSync(CLI, ['quit', '--help'], { encoding: 'utf8' })
  if (uploadHelp.status !== 0 || !`${uploadHelp.stdout}${uploadHelp.stderr}`.includes('--info-output')) throw new Error('WeChat CLI upload capability is unavailable')
  if (quitHelp.status !== 0 || !`${quitHelp.stdout}${quitHelp.stderr}`.includes('Quit IDE')) throw new Error('WeChat CLI quit capability is unavailable')
  const ide = readJson(IDE_PACKAGE)
  if (!/^\d+\.\d+\.\d+$/.test(ide.version || '')) throw new Error('Unable to determine WeChat developer tool version')
  return {
    version: ide.version,
    cliPath: CLI,
    cliSha256: sha256File(CLI),
    ideBinaryPath: IDE_BINARY,
    ideBinarySha256: sha256File(IDE_BINARY),
  }
}

function verifyProject(root) {
  if (!fs.statSync(PROJECT, { throwIfNoEntry: false })?.isDirectory()) throw new Error(`Missing fixed upload project: ${PROJECT}`)
  if (!fs.statSync(PROJECT_MANIFEST, { throwIfNoEntry: false })?.isFile()) throw new Error(`Missing upload manifest: ${PROJECT_MANIFEST}`)
  const manifest = readJson(PROJECT_MANIFEST)
  if (manifest.schemaVersion !== 2 || manifest.target !== PROJECT || manifest.version !== VERSION) throw new Error('Upload manifest has the wrong target or version')
  if (!/^[a-f0-9]{40}$/.test(manifest.gitCommit || '')) throw new Error('Upload manifest is not bound to an immutable commit')
  if (!/^[a-f0-9]{40}$/.test(manifest.sourceTree || '') || manifest.scope !== EXPECTED_SCOPE
    || manifest.buildId !== EXPECTED_BUILD_ID || manifest.buildMethod !== EXPECTED_BUILD_METHOD) {
    throw new Error('Upload manifest is not bound to the reviewed closure candidate identity')
  }
  if (manifest.previousBackendVersion !== EXPECTED_PREVIOUS_BACKEND_VERSION
    || manifest.previousBackendJarSha256 !== EXPECTED_PREVIOUS_BACKEND_JAR_SHA256
    || manifest.previousStaticVersion !== EXPECTED_PREVIOUS_STATIC_VERSION
    || manifest.previousStaticCommit !== EXPECTED_PREVIOUS_STATIC_COMMIT) {
    throw new Error('Upload manifest production baseline is incorrect')
  }
  for (const field of ['candidateManifestSha256', 'miniProgramManifestSha256', 'sourceArchiveSha256', 'aggregateSha256']) {
    if (!/^[a-f0-9]{64}$/.test(manifest[field] || '')) throw new Error(`Upload manifest is missing ${field}`)
  }
  if (manifest.appid !== EXPECTED_APPID || manifest.api !== EXPECTED_API || manifest.urlCheck !== true) throw new Error('Upload manifest production identity is incorrect')
  assertExactObject(manifest.plugins, EXPECTED_PLUGIN, 'Upload manifest plugin inventory')
  const actual = listFiles(PROJECT)
  if (manifest.fileCount !== Object.keys(actual).length) throw new Error('Upload project fileCount mismatch')
  assertExactObject(actual, manifest.files, 'Upload project file manifest')
  if (aggregateFiles(actual) !== manifest.aggregateSha256) throw new Error('Upload project aggregate checksum mismatch')
  for (const name of Object.keys(actual)) {
    if (SENSITIVE_PATH.test(name) || SOURCE_ONLY_PATHS.some(pattern => pattern.test(name))) {
      throw new Error(`Upload project contains a source-only, test, or sensitive file: ${name}`)
    }
  }
  const packageJson = readJson(path.join(PROJECT, 'package.json'))
  const lock = readJson(path.join(PROJECT, 'package-lock.json'))
  if (packageJson.version !== VERSION || lock.version !== VERSION || lock.packages?.['']?.version !== VERSION) {
    throw new Error('Upload project package and lock versions do not match the release')
  }
  const project = readJson(path.join(PROJECT, 'project.config.json'))
  const app = readJson(path.join(PROJECT, 'app.json'))
  const runtime = fs.readFileSync(path.join(PROJECT, 'config/runtime.js'), 'utf8')
  if (project.appid !== EXPECTED_APPID || project.setting?.urlCheck !== true) throw new Error('Upload project AppID or urlCheck is incorrect')
  if (!runtime.includes(`API_BASE_URL: '${EXPECTED_API}'`)) throw new Error('Upload project API target is incorrect')
  assertExactObject(app.plugins, EXPECTED_PLUGIN, 'Upload project plugin inventory')
  const resolved = cp.execFileSync('git', ['-C', root, 'rev-parse', `${manifest.gitCommit}^{commit}`], { encoding: 'utf8' }).trim()
  if (resolved !== manifest.gitCommit) throw new Error('Upload project commit is not locally available')
  const sourceTree = cp.execFileSync('git', ['-C', root, 'rev-parse', `${manifest.gitCommit}^{tree}`], { encoding: 'utf8' }).trim()
  if (sourceTree !== manifest.sourceTree) throw new Error('Upload project sourceTree does not match gitCommit')
  const gitVersion = cp.execFileSync('git', ['-C', root, 'show', `${manifest.gitCommit}:VERSION`], { encoding: 'utf8' }).trim()
  if (gitVersion !== VERSION) throw new Error('Upload project commit has a different VERSION')
  return manifest
}

function resolveQaRoot(root, requested) {
  const base = path.join(root, 'document', 'qa')
  const resolved = requested ? path.resolve(requested) : path.join(base, '2026-09-22-mini-156-upload')
  const relative = path.relative(base, resolved)
  if (relative.startsWith('..') || path.isAbsolute(relative)) throw new Error('QA receipt directory must stay under document/qa')
  return resolved
}

function verifyRetentionReceipt(root, manifest, candidate, receipt) {
  const qaBase = path.join(root, 'document', 'qa')
  const resolvedReceipt = fs.realpathSync(receipt)
  const receiptRelative = path.relative(qaBase, resolvedReceipt)
  if (receiptRelative.startsWith('..') || path.isAbsolute(receiptRelative)) {
    throw new Error('Retention receipt must stay under document/qa')
  }
  const gitRelative = receiptRelative.split(path.sep).join('/')
  const tracked = cp.spawnSync('git', ['-C', root, 'ls-files', '--error-unmatch', `document/qa/${gitRelative}`], { encoding: 'utf8' })
  if (tracked.status !== 0) throw new Error('Retention receipt has not been committed to Git')
  const committed = cp.execFileSync('git', ['-C', root, 'show', `HEAD:document/qa/${gitRelative}`])
  if (!committed.equals(fs.readFileSync(resolvedReceipt))) {
    throw new Error('Retention receipt differs from the current evidence commit')
  }
  const upstream = cp.spawnSync('git', ['-C', root, 'rev-parse', '--abbrev-ref', '@{upstream}'], { encoding: 'utf8' })
  if (upstream.status !== 0 || !upstream.stdout.trim()) throw new Error('Evidence commit has no readable upstream')
  const divergence = cp.execFileSync('git', ['-C', root, 'rev-list', '--left-right', '--count', 'HEAD...@{upstream}'], { encoding: 'utf8' }).trim()
  if (divergence !== '0\t0') throw new Error(`Evidence commit is not synchronized with upstream: ${divergence}`)
  const verifier = path.join(root, 'scripts', 'verify-release-artifact-retention.mjs')
  const result = cp.spawnSync(process.execPath, [
    verifier,
    '--candidate', path.resolve(candidate),
    '--receipt', resolvedReceipt,
  ], { encoding: 'utf8' })
  if (result.status !== 0) {
    throw new Error(`P0-10 durable retention verification failed: ${(result.stderr || '').trim()}`)
  }
  const verified = JSON.parse(result.stdout)
  if (verified.candidateManifestSha256 !== manifest.candidateManifestSha256) {
    throw new Error('Retention receipt and fixed WeChat project refer to different candidate manifests')
  }
  return verified
}

function shutdownCli(port) {
  let quit = { status: null, stdout: '', stderr: '', error: null }
  if (portListening(port)) {
    const result = cp.spawnSync(CLI, ['quit', '--port', String(port), '--lang', 'zh'], { encoding: 'utf8', timeout: 60_000 })
    quit = {
      status: result.status,
      stdout: sanitized(result.stdout),
      stderr: sanitized(result.stderr),
      error: result.error ? sanitized(result.error.message) : null,
    }
  }
  for (let count = 0; count < 20 && portListening(port); count += 1) wait(500)
  return { ...quit, portClosed: !portListening(port) }
}

const args = parseArgs(process.argv.slice(2))
const manifest = verifyProject(root)
const cli = verifyCliPreflight()
if (portListening(args.port)) throw new Error(`Temporary port ${args.port} is already in use; refusing to touch an existing listener`)

const common = {
  policy: 'development-upload-only',
  experienceVersionChanged: false,
  reviewSubmitted: false,
  formalVersionPublished: false,
  version: VERSION,
  scope: manifest.scope,
  gitCommit: manifest.gitCommit,
  sourceTree: manifest.sourceTree,
  buildId: manifest.buildId,
  buildMethod: manifest.buildMethod,
  previousBackendVersion: manifest.previousBackendVersion,
  previousBackendJarSha256: manifest.previousBackendJarSha256,
  previousStaticVersion: manifest.previousStaticVersion,
  previousStaticCommit: manifest.previousStaticCommit,
  project: PROJECT,
  projectManifest: PROJECT_MANIFEST,
  projectManifestSha256: sha256File(PROJECT_MANIFEST),
  candidateManifestSha256: manifest.candidateManifestSha256,
  miniProgramManifestSha256: manifest.miniProgramManifestSha256,
  sourceArchiveSha256: manifest.sourceArchiveSha256,
  aggregateSha256: manifest.aggregateSha256,
  fileCount: manifest.fileCount,
  appid: EXPECTED_APPID,
  api: EXPECTED_API,
  urlCheck: true,
  plugins: EXPECTED_PLUGIN,
  cli,
  temporaryPort: args.port,
}

if (args.preflightOnly) {
  console.log(JSON.stringify({ ...common, result: 'preflight-ok', portListening: false }, null, 2))
  process.exit(0)
}

const retention = verifyRetentionReceipt(root, manifest, args.candidate, args.retentionReceipt)

const qaRoot = resolveQaRoot(root, args.qaRoot)
const runId = new Date().toISOString().replace(/[:.]/g, '-').replace('T', '_').replace('Z', '')
const receiptDirectory = path.join(qaRoot, runId)
fs.mkdirSync(qaRoot, { recursive: true })
fs.mkdirSync(receiptDirectory, { recursive: false })
const infoPath = path.join(receiptDirectory, 'upload-info.json')
const commandArgs = [
  'upload', '--project', PROJECT, '--version', VERSION, '--desc', args.description,
  '--info-output', infoPath, '--port', String(args.port), '--lang', 'zh',
]
const startedAt = new Date().toISOString()
let uploadResult
let cleanupResult
try {
  uploadResult = cp.spawnSync(CLI, commandArgs, { encoding: 'utf8', timeout: 10 * 60_000 })
} finally {
  cleanupResult = shutdownCli(args.port)
}
const finishedAt = new Date().toISOString()
const stdout = sanitized(uploadResult?.stdout)
const stderr = sanitized(uploadResult?.stderr)
fs.writeFileSync(path.join(receiptDirectory, 'stdout.log'), stdout)
fs.writeFileSync(path.join(receiptDirectory, 'stderr.log'), stderr)

let packageSize = null
let uploadInfoSha256 = null
if (fs.statSync(infoPath, { throwIfNoEntry: false })?.isFile()) {
  uploadInfoSha256 = sha256File(infoPath)
  try {
    const info = readJson(infoPath)
    packageSize = Number.isSafeInteger(info?.size?.total) ? info.size.total : null
  } catch {
    packageSize = null
  }
}

const receipt = {
  ...common,
  durableRetention: retention,
  startedAt,
  finishedAt,
  description: args.description,
  command: {
    executable: CLI,
    operation: 'upload development version',
    arguments: commandArgs,
    tokenArgumentUsed: false,
  },
  exitCode: uploadResult?.status ?? null,
  signal: uploadResult?.signal ?? null,
  processError: uploadResult?.error ? sanitized(uploadResult.error.message) : null,
  stdout,
  stderr,
  packageSizeBytes: packageSize,
  uploadInfoSha256,
  cleanup: cleanupResult,
  success: uploadResult?.status === 0 && hasSuccessfulUploadMarker(stdout, stderr)
    && Number.isSafeInteger(packageSize) && cleanupResult.portClosed,
}
fs.writeFileSync(path.join(receiptDirectory, 'receipt.json'), `${JSON.stringify(receipt, null, 2)}\n`)
console.log(JSON.stringify({
  receiptDirectory,
  success: receipt.success,
  exitCode: receipt.exitCode,
  packageSizeBytes: packageSize,
  portClosed: cleanupResult.portClosed,
}, null, 2))

if (!cleanupResult.portClosed) throw new Error(`Temporary service port ${args.port} is still listening after cleanup`)
if (!receipt.success) throw new Error(`WeChat development upload failed; evidence preserved at ${receiptDirectory}`)
