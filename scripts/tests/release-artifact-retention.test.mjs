import assert from 'node:assert/strict'
import cp from 'node:child_process'
import crypto from 'node:crypto'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const scripts = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const verifier = path.join(scripts, 'verify-release-artifact-retention.mjs')
const read = name => fs.readFileSync(path.join(scripts, name), 'utf8')
const sha256 = value => crypto.createHash('sha256').update(value).digest('hex')

function fixture() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'retention-contract.'))
  const candidateRoot = path.join(root, 'candidate')
  fs.mkdirSync(candidateRoot)
  const manifest = {
    version: '1.0.156',
    scope: 'mall-closure-candidate',
    gitCommit: '1'.repeat(40),
    sourceTree: '2'.repeat(40),
    buildId: '20260922-closure-1.0.156',
    buildMethod: 'clean-build-in-release-process',
  }
  const manifestBytes = Buffer.from(`${JSON.stringify(manifest, null, 2)}\n`)
  fs.writeFileSync(path.join(candidateRoot, 'RELEASE_MANIFEST.json'), manifestBytes)
  fs.writeFileSync(path.join(candidateRoot, 'VERSION'), '1.0.156\n')
  const archive = path.join(root, 'lingqi-mall-1.0.156-11111111.tar.gz')
  cp.execFileSync('tar', ['-czf', archive, '-C', candidateRoot, '.'])
  const bytes = fs.statSync(archive).size
  const archiveSha256 = sha256(fs.readFileSync(archive))
  const receipt = {
    schemaVersion: 2,
    receiptStatus: 'COMPLETED',
    version: manifest.version,
    buildId: manifest.buildId,
    gitCommit: manifest.gitCommit,
    sourceTree: manifest.sourceTree,
    candidateManifestSha256: sha256(manifestBytes),
    localArtifact: {
      path: `target/releases/${path.basename(archive)}`,
      bytes,
      sha256: archiveSha256,
      verified: true,
    },
    durableArtifact: {
      status: 'COMPLETED',
      provider: 'github-release',
      private: true,
      immutable: true,
      objectId: 'github-release:123:asset:456',
      uploadedAt: '2026-09-22T08:00:00.000Z',
      retentionUntil: '2027-09-22T00:00:00.000Z',
      readBackAt: '2026-09-22T08:10:00.000Z',
      readBackBytes: bytes,
      readBackSha256: archiveSha256,
      assetDigest: `sha256:${archiveSha256}`,
    },
  }
  const receiptFile = path.join(root, 'artifact-retention.json')
  fs.writeFileSync(receiptFile, `${JSON.stringify(receipt, null, 2)}\n`)
  return { root, candidateRoot, archive, receipt, receiptFile }
}

function run(item, extra = []) {
  return cp.spawnSync(process.execPath, [
    verifier,
    '--candidate', item.archive,
    '--receipt', item.receiptFile,
    ...extra,
  ], {
    cwd: os.tmpdir(),
    encoding: 'utf8',
  })
}

function rewrite(item, mutate) {
  const value = structuredClone(item.receipt)
  mutate(value)
  fs.writeFileSync(item.receiptFile, `${JSON.stringify(value, null, 2)}\n`)
}

test('complete private immutable GitHub receipt verifies without consulting current Git HEAD', () => {
  const item = fixture()
  try {
    const result = run(item, ['--candidate-root', item.candidateRoot])
    assert.equal(result.status, 0, result.stderr)
    const output = JSON.parse(result.stdout)
    assert.equal(output.result, 'retention-verified')
    assert.equal(output.version, '1.0.156')
    assert.equal(output.candidateSha256, item.receipt.localArtifact.sha256)
    assert.equal(output.candidateManifestSha256, item.receipt.candidateManifestSha256)
    assert.equal(output.private, true)
    assert.equal(output.immutable, true)
  } finally {
    fs.rmSync(item.root, { recursive: true, force: true })
  }
})

for (const [name, mutate, message] of [
  ['incomplete receipt', value => { value.receiptStatus = 'PENDING' }, /receiptStatus must be COMPLETED/],
  ['wrong provider', value => { value.durableArtifact.provider = 'local-copy' }, /provider must be github-release/],
  ['public asset', value => { value.durableArtifact.private = false }, /private must be true/],
  ['mutable asset', value => { value.durableArtifact.immutable = false }, /immutable must be true/],
  ['read-back byte mismatch', value => { value.durableArtifact.readBackBytes += 1 }, /byte counts differ/],
  ['read-back digest mismatch', value => { value.durableArtifact.readBackSha256 = '3'.repeat(64) }, /SHA-256 values differ/],
  ['candidate identity mismatch', value => { value.sourceTree = '4'.repeat(40) }, /sourceTree does not match/],
  ['manifest digest mismatch', value => { value.candidateManifestSha256 = '5'.repeat(64) }, /candidateManifestSha256/],
  ['short retention', value => { value.durableArtifact.retentionUntil = '2027-09-21T00:00:00.000Z' }, /at least 365 days/],
]) {
  test(`rejects ${name}`, () => {
    const item = fixture()
    try {
      rewrite(item, mutate)
      const result = run(item)
      assert.notEqual(result.status, 0)
      assert.match(result.stderr, message)
    } finally {
      fs.rmSync(item.root, { recursive: true, force: true })
    }
  })
}

test('rejects a receipt whose matching candidate hash does not match the actual archive', () => {
  const item = fixture()
  try {
    rewrite(item, value => {
      value.localArtifact.sha256 = '6'.repeat(64)
      value.durableArtifact.readBackSha256 = '6'.repeat(64)
      value.durableArtifact.assetDigest = `sha256:${'6'.repeat(64)}`
    })
    const result = run(item)
    assert.notEqual(result.status, 0)
    assert.match(result.stderr, /candidate archive SHA-256 differs from receipt/)
  } finally {
    fs.rmSync(item.root, { recursive: true, force: true })
  }
})

test('release entry points enforce retention only after their read-only preflight exits', () => {
  const readiness = read('release-readiness.sh')
  const backend = read('remote-deploy-20260922-v1.0.156-backend.sh')
  const staticRelease = read('remote-deploy-20260922-v1.0.156-static.sh')
  const wechat = read('upload-lingqi-mini-156.mjs')
  const builder = read('release-lingqi-156.mjs')

  assert.match(builder, /verify-release-artifact-retention\.mjs'.*verify-artifact-retention\.mjs/s)
  assert.match(readiness, /--preflight-only/)
  assert.match(readiness, /verify-release-artifact-retention\.mjs/)
  assert.ok(readiness.indexOf('if [[ "$PREFLIGHT_ONLY" == 1 ]]') < readiness.indexOf('P0-10 独立耐久留存回执校验失败'))

  for (const source of [backend, staticRelease]) {
    const preflightExit = source.indexOf('[[ "$MODE" == --authorize-release ]] || exit 0')
    const retentionGate = source.indexOf('P0-10 durable retention verification failed')
    assert.ok(preflightExit >= 0 && retentionGate > preflightExit)
    assert.match(source.slice(preflightExit, retentionGate), /candidate\.tar\.gz/)
    assert.match(source.slice(preflightExit, retentionGate), /artifact-retention\.json/)
  }

  const wechatPreflightExit = wechat.indexOf("if (args.preflightOnly)")
  const wechatRetentionGate = wechat.indexOf('const retention = verifyRetentionReceipt')
  const wechatUploadCommand = wechat.indexOf('const commandArgs = [')
  assert.ok(wechatPreflightExit >= 0 && wechatRetentionGate > wechatPreflightExit)
  assert.ok(wechatUploadCommand > wechatRetentionGate)
})
