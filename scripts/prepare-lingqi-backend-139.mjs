import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import cp from 'node:child_process'
import crypto from 'node:crypto'

const git = (...args) => cp.execFileSync('git', args, { encoding: 'utf8' }).trim()
const root = git('rev-parse', '--show-toplevel')
if (path.basename(root) !== 'mall-swarm-app-h5') throw new Error('Wrong product repository')
process.chdir(root)
if (git('status', '--porcelain')) throw new Error('Commit the reviewed release changes before packaging')
const commit = git('rev-parse', 'HEAD')
cp.execFileSync('git', ['merge-base', '--is-ancestor', 'ba1a819d', commit])
const version = fs.readFileSync('VERSION', 'utf8').trim()
if (version !== '1.0.139') throw new Error('This assembler is for version 1.0.139 only')
const jar = path.join(root, 'mall-distribution/target/mall-distribution-1.0-SNAPSHOT.jar')
if (!fs.statSync(jar).isFile()) throw new Error('Missing built backend JAR')
const sha = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')
const stage = fs.mkdtempSync(path.join(os.tmpdir(), 'lingqi-139-backend-candidate.'))
fs.chmodSync(stage, 0o700)
for (const [source, target] of [
  [jar, 'mall-distribution.jar'],
  ['VERSION', 'VERSION'],
  ['scripts/remote-deploy-20260913-v1.0.139-mini-backend.sh', 'release.sh'],
  ['scripts/production-backup.sh', 'production-backup.sh'],
  ['scripts/db-migrate.sh', 'db-migrate.sh'],
  ['document/db/migrations/V202609121900__product_review_replies.sql', 'V202609121900__product_review_replies.sql'],
  ['document/db/migrations/V202609122000__shop_coupons.sql', 'V202609122000__shop_coupons.sql'],
]) {
  fs.copyFileSync(source, path.join(stage, target))
  fs.chmodSync(path.join(stage, target), 0o600)
}
const manifest = {
  version, scope: 'backend-only', gitCommit: commit,
  buildId: '20260913-mini-backend-1.0.139',
  jarSha256: sha(jar), generatedAt: new Date().toISOString(),
  previousVersion: '1.0.137',
  previousJarSha256: '80663849f6f43f45180390f75f407f0bcf0fef2a60aa4df848be2fb9e6791c8f',
  databaseMigrations: ['V202609121900__product_review_replies.sql', 'V202609122000__shop_coupons.sql'],
}
fs.writeFileSync(path.join(stage, 'RELEASE_MANIFEST.json'), JSON.stringify(manifest, null, 2) + '\n', { mode: 0o600 })
const files = fs.readdirSync(stage).sort()
fs.writeFileSync(path.join(stage, 'SHA256SUMS'), files.map(name => `${sha(path.join(stage, name))}  ${name}`).join('\n') + '\n', { mode: 0o600 })
const archive = stage + '.tar.gz'
const expectedFiles = fs.readdirSync(stage).sort()
cp.execFileSync('tar', ['--format=ustar', '--no-xattrs', '--no-mac-metadata', '-czf', archive, '-C', stage, ...expectedFiles], {
  env: { ...process.env, COPYFILE_DISABLE: '1' },
})
const archiveFiles = cp.execFileSync('tar', ['-tzf', archive], { encoding: 'utf8' }).trim().split('\n').sort()
if (JSON.stringify(archiveFiles) !== JSON.stringify(expectedFiles)) throw new Error('Unexpected archive entry; do not upload')
fs.chmodSync(archive, 0o600)
console.log(JSON.stringify({ stage, archive, archiveSha256: sha(archive), ...manifest }, null, 2))
