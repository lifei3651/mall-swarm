#!/usr/bin/env node

import cp from 'node:child_process'
import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'

const MIN_RETENTION_DAYS = 365
const SHA256 = /^[a-f0-9]{64}$/
const COMMIT = /^[a-f0-9]{40}$/

function fail(message) {
  throw new Error(`release-artifact-retention-invalid: ${message}`)
}

function usage() {
  console.error(`Usage:
  node scripts/verify-release-artifact-retention.mjs \\
    --candidate <exact-candidate.tar.gz> \\
    --receipt <artifact-retention.json> \\
    [--candidate-root <extracted-candidate-directory>]`)
}

function parseArgs(argv) {
  const result = { candidate: '', receipt: '', candidateRoot: '' }
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index]
    if (argument === '--candidate') result.candidate = argv[++index] || ''
    else if (argument === '--receipt') result.receipt = argv[++index] || ''
    else if (argument === '--candidate-root') result.candidateRoot = argv[++index] || ''
    else if (argument === '-h' || argument === '--help') {
      usage()
      process.exit(0)
    } else fail(`unknown argument: ${argument}`)
  }
  if (!result.candidate || !result.receipt) fail('candidate and receipt are required')
  return Object.fromEntries(Object.entries(result).map(([key, value]) => [key, value ? path.resolve(value) : '']))
}

function readJson(file, label) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'))
  } catch (error) {
    fail(`${label} is not valid JSON: ${error.message}`)
  }
}

function sha256Buffer(value) {
  return crypto.createHash('sha256').update(value).digest('hex')
}

function sha256File(file) {
  const hash = crypto.createHash('sha256')
  const descriptor = fs.openSync(file, 'r')
  const buffer = Buffer.allocUnsafe(1024 * 1024)
  try {
    for (;;) {
      const bytes = fs.readSync(descriptor, buffer, 0, buffer.length, null)
      if (bytes === 0) break
      hash.update(buffer.subarray(0, bytes))
    }
  } finally {
    fs.closeSync(descriptor)
  }
  return hash.digest('hex')
}

function manifestBytesFromArchive(archive) {
  for (const name of ['./RELEASE_MANIFEST.json', 'RELEASE_MANIFEST.json']) {
    const result = cp.spawnSync('tar', ['-xOf', archive, name], {
      encoding: null,
      maxBuffer: 4 * 1024 * 1024,
    })
    if (result.status === 0 && result.stdout?.length) return result.stdout
  }
  fail('candidate archive does not contain RELEASE_MANIFEST.json at its root')
}

function parseTimestamp(value, label) {
  if (typeof value !== 'string' || !value.trim()) fail(`${label} is required`)
  const milliseconds = Date.parse(value)
  if (!Number.isFinite(milliseconds)) fail(`${label} is not a valid timestamp`)
  return milliseconds
}

function utcDay(milliseconds) {
  const date = new Date(milliseconds)
  return Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate())
}

function assertIdentity(receipt, manifest, manifestSha256) {
  if (!COMMIT.test(manifest.gitCommit || '')) fail('candidate manifest gitCommit is invalid')
  if (!COMMIT.test(manifest.sourceTree || '')) fail('candidate manifest sourceTree is invalid')
  if (manifest.scope !== 'mall-closure-candidate') fail('candidate manifest scope is invalid')
  if (manifest.buildMethod !== 'clean-build-in-release-process') fail('candidate manifest buildMethod is invalid')
  for (const key of ['version', 'buildId', 'gitCommit', 'sourceTree']) {
    if (receipt[key] !== manifest[key]) fail(`receipt ${key} does not match candidate manifest`)
  }
  if (receipt.candidateManifestSha256 !== manifestSha256) {
    fail('receipt candidateManifestSha256 does not match candidate manifest bytes')
  }
}

const args = parseArgs(process.argv.slice(2))
const archiveStat = fs.statSync(args.candidate, { throwIfNoEntry: false })
if (!archiveStat?.isFile()) fail('candidate must be the exact regular tar.gz file')
const receiptStat = fs.statSync(args.receipt, { throwIfNoEntry: false })
if (!receiptStat?.isFile()) fail('receipt must be a regular file')

const manifestBytes = manifestBytesFromArchive(args.candidate)
let manifest
try {
  manifest = JSON.parse(manifestBytes.toString('utf8'))
} catch (error) {
  fail(`candidate RELEASE_MANIFEST.json is invalid: ${error.message}`)
}
const manifestSha256 = sha256Buffer(manifestBytes)

if (args.candidateRoot) {
  const rootStat = fs.statSync(args.candidateRoot, { throwIfNoEntry: false })
  if (!rootStat?.isDirectory()) fail('candidate-root must be an extracted candidate directory')
  const rootManifest = path.join(args.candidateRoot, 'RELEASE_MANIFEST.json')
  if (!fs.statSync(rootManifest, { throwIfNoEntry: false })?.isFile()) {
    fail('candidate-root is missing RELEASE_MANIFEST.json')
  }
  if (!fs.readFileSync(rootManifest).equals(manifestBytes)) {
    fail('candidate-root manifest differs from the exact candidate archive')
  }
}

const receipt = readJson(args.receipt, 'retention receipt')
if (receipt.schemaVersion !== 2) fail('receipt schemaVersion must be 2')
if (receipt.receiptStatus !== 'COMPLETED') fail('receiptStatus must be COMPLETED')
assertIdentity(receipt, manifest, manifestSha256)

const local = receipt.localArtifact
const durable = receipt.durableArtifact
if (!local || typeof local !== 'object') fail('localArtifact is required')
if (!durable || typeof durable !== 'object') fail('durableArtifact is required')
if (local.verified !== true) fail('localArtifact.verified must be true')
if (!Number.isSafeInteger(local.bytes) || local.bytes <= 0) fail('localArtifact.bytes is invalid')
if (!SHA256.test(local.sha256 || '')) fail('localArtifact.sha256 is invalid')
if (archiveStat.size !== local.bytes) fail('candidate archive byte count differs from receipt')
if (sha256File(args.candidate) !== local.sha256) fail('candidate archive SHA-256 differs from receipt')

if (durable.status !== 'COMPLETED') fail('durableArtifact.status must be COMPLETED')
if (durable.provider !== 'github-release') fail('durableArtifact.provider must be github-release')
if (durable.private !== true) fail('durableArtifact.private must be true')
if (durable.immutable !== true) fail('durableArtifact.immutable must be true')
if (typeof durable.objectId !== 'string' || !durable.objectId.trim()) fail('durableArtifact.objectId is required')
if (!Number.isSafeInteger(durable.readBackBytes) || durable.readBackBytes <= 0) {
  fail('durableArtifact.readBackBytes is invalid')
}
if (!SHA256.test(durable.readBackSha256 || '')) fail('durableArtifact.readBackSha256 is invalid')
if (durable.readBackBytes !== local.bytes) fail('candidate and read-back byte counts differ')
if (durable.readBackSha256 !== local.sha256) fail('candidate and read-back SHA-256 values differ')
if (durable.assetDigest != null && durable.assetDigest !== `sha256:${local.sha256}`) {
  fail('durableArtifact.assetDigest differs from the candidate SHA-256')
}

const uploadedAt = parseTimestamp(durable.uploadedAt, 'durableArtifact.uploadedAt')
const readBackAt = parseTimestamp(durable.readBackAt, 'durableArtifact.readBackAt')
if (readBackAt < uploadedAt) fail('read-back time precedes upload time')
const retentionUntil = parseTimestamp(durable.retentionUntil, 'durableArtifact.retentionUntil')
const minimumRetention = utcDay(readBackAt) + MIN_RETENTION_DAYS * 24 * 60 * 60 * 1000
if (retentionUntil < minimumRetention) {
  fail(`retentionUntil must cover at least ${MIN_RETENTION_DAYS} days after durable read-back`)
}

console.log(JSON.stringify({
  result: 'retention-verified',
  receipt: args.receipt,
  candidate: args.candidate,
  version: manifest.version,
  buildId: manifest.buildId,
  gitCommit: manifest.gitCommit,
  sourceTree: manifest.sourceTree,
  candidateBytes: local.bytes,
  candidateSha256: local.sha256,
  candidateManifestSha256: manifestSha256,
  provider: durable.provider,
  private: durable.private,
  immutable: durable.immutable,
  objectId: durable.objectId,
  retentionUntil: durable.retentionUntil,
  minimumRetentionDays: MIN_RETENTION_DAYS,
}, null, 2))
