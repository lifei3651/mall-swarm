import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import cp from 'node:child_process'
import crypto from 'node:crypto'

const [target, mode] = process.argv.slice(2)
if (!['backend', 'admin', 'shop', 'team'].includes(target) || !['--package-only', '--authorize-release'].includes(mode)) {
  throw new Error('Usage: node scripts/release-lingqi-149.mjs backend|admin|shop|team --package-only|--authorize-release')
}
const git = (...args) => cp.execFileSync('git', args, { encoding: 'utf8' }).trim()
const root = git('rev-parse', '--show-toplevel')
if (path.basename(root) !== 'mall-swarm-app-h5') throw new Error('Wrong product repository')
process.chdir(root)
if (git('status', '--porcelain')) throw new Error('Commit reviewed changes before packaging')
if (fs.readFileSync('VERSION', 'utf8').trim() !== '1.0.149') throw new Error('Wrong version')
const commit = git('rev-parse', 'HEAD')
const sha = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')
const tar = (output, cwd, entries) => cp.execFileSync('tar', ['--format=ustar', '--no-xattrs', '--no-mac-metadata', '-czf', output, '-C', cwd, ...entries], { env: { ...process.env, COPYFILE_DISABLE: '1' } })
const quote = value => "'" + value.replaceAll("'", "'\\''") + "'"
const sshOptions = ['-i', '/Users/minmatemp/.ssh/lingqi_server_ed25519', '-o', 'BatchMode=yes', '-o', 'IdentitiesOnly=yes', '-o', 'ConnectTimeout=12', '-o', 'StrictHostKeyChecking=yes', '-o', 'ServerAliveInterval=10', '-o', 'ServerAliveCountMax=3']
const host = 'root@lingqimall.com'

function verifyArchive(archive, names) {
  const entries = cp.execFileSync('tar', ['-tzf', archive], { encoding: 'utf8' }).trim().split('\n').sort()
  if (JSON.stringify(names.slice().sort()) !== JSON.stringify(entries)) throw new Error('Unexpected archive entries')
}

if (target === 'backend') {
  const jar = path.join(root, 'mall-distribution/target/mall-distribution-1.0-SNAPSHOT.jar')
  if (!fs.existsSync(jar)) throw new Error('Missing built backend JAR')
  const stage = fs.mkdtempSync(path.join(os.tmpdir(), 'lingqi-149-backend-candidate.'))
  const migrations = ['V202609201430__wechat_logistics_follow_tasks.sql', 'V202609201500__wechat_express_orders.sql']
  const copies = [
    [jar, 'mall-distribution.jar'], ['VERSION', 'VERSION'],
    ['scripts/remote-deploy-20260920-v1.0.149-backend.sh', 'release.sh'],
    ['scripts/production-backup.sh', 'production-backup.sh'], ['scripts/db-migrate.sh', 'db-migrate.sh'],
    ...migrations.map(name => ['document/db/migrations/' + name, name]),
  ]
  for (const [source, name] of copies) fs.copyFileSync(source, path.join(stage, name))
  const manifest = {
    version: '1.0.149', scope: 'closure-backend-with-logistics-migrations', gitCommit: commit,
    buildId: '20260920-closure-backend-1.0.149', jarSha256: sha(jar), generatedAt: new Date().toISOString(),
    previousVersion: '1.0.146', previousJarSha256: '014df28fa9123b1c00d51e2a4aa075dd5ea8b56e95ac4ce7f4528c6ca844015e',
    databaseMigrations: migrations,
  }
  fs.writeFileSync(path.join(stage, 'RELEASE_MANIFEST.json'), JSON.stringify(manifest, null, 2) + '\n')
  const payload = fs.readdirSync(stage).sort()
  fs.writeFileSync(path.join(stage, 'SHA256SUMS'), payload.map(name => `${sha(path.join(stage, name))}  ${name}`).join('\n') + '\n')
  const names = fs.readdirSync(stage).sort()
  const archive = stage + '.tar.gz'
  tar(archive, stage, names)
  verifyArchive(archive, names)
  const archiveSha = sha(archive)
  console.log(JSON.stringify({ target, stage, archive, archiveSha, commit, jarSha256: manifest.jarSha256 }))
  if (mode === '--package-only') process.exit(0)
  const remote = cp.execFileSync('ssh', [...sshOptions, host, 'mktemp -d /tmp/lingqimall-closure-backend-149.XXXXXX'], { encoding: 'utf8' }).trim()
  if (!/^\/tmp\/lingqimall-closure-backend-149\.[A-Za-z0-9]+$/.test(remote)) throw new Error('Unexpected remote directory')
  cp.execFileSync('rsync', ['--partial', '--timeout=45', '-e', ['ssh', ...sshOptions].map(quote).join(' '), archive, host + ':' + remote + '.tar.gz'], { stdio: 'inherit' })
  const command = `set -euo pipefail
echo ${quote(archiveSha + '  ' + remote + '.tar.gz')} | sha256sum -c -
tar -xzf ${quote(remote + '.tar.gz')} -C ${quote(remote)}
export LINGQIMALL_RELEASE_AUTHORIZATION=1.0.149
export LINGQIMALL_RELEASE_COMMIT=${quote(commit)}
bash ${quote(remote + '/release.sh')} ${quote(remote)} --preflight-only
bash ${quote(remote + '/release.sh')} ${quote(remote)} --authorize-release`
  cp.execFileSync('ssh', [...sshOptions, host, 'bash -c ' + quote(command)], { stdio: 'inherit' })
  process.exit(0)
}

const config = {
  admin: ['mall-distribution-admin/dist', 'admin', 'admin'],
  shop: ['mall-shop-web/dist', 'public-h5', 'storefront-public'],
  team: ['mall-shop-web/dist-team', 'team-h5', 'team-h5'],
}[target]
const [dist, label, application] = config
const version = JSON.parse(fs.readFileSync(path.join(dist, 'version.json')))
const expectedVersion = { version: '1.0.149', edition: 'app-h5-split', application, gitCommit: commit, buildId: `20260920-${label}-1.0.149` }
for (const [key, expected] of Object.entries(expectedVersion)) if (version[key] !== expected) throw new Error('Wrong built identity: ' + key)
const files = []
function walk(directory, prefix = '') {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    if (entry.isSymbolicLink() || entry.name.startsWith('.env') || entry.name.startsWith('._')) throw new Error('Unsafe static entry')
    const relative = prefix + entry.name
    if (entry.isDirectory()) walk(path.join(directory, entry.name), relative + '/')
    else if (entry.isFile()) files.push(relative)
    else throw new Error('Unexpected static entry')
  }
}
walk(dist)
const stage = fs.mkdtempSync(path.join(os.tmpdir(), `lingqi-149-${target}-candidate.`))
tar(path.join(stage, target + '.tar.gz'), dist, ['.'])
fs.writeFileSync(path.join(stage, target.toUpperCase() + '_SHA256SUMS'), files.sort().map(file => `${sha(path.join(dist, file))}  ./${file}`).join('\n') + '\n')
fs.copyFileSync('scripts/remote-deploy-20260920-v1.0.149-static.sh', path.join(stage, 'release.sh'))
fs.copyFileSync('scripts/production-backup.sh', path.join(stage, 'production-backup.sh'))
const payload = fs.readdirSync(stage).sort()
fs.writeFileSync(path.join(stage, 'SHA256SUMS'), payload.map(name => `${sha(path.join(stage, name))}  ${name}`).join('\n') + '\n')
const names = fs.readdirSync(stage).sort()
const archive = stage + '.tar.gz'
tar(archive, stage, names)
verifyArchive(archive, names)
const archiveSha = sha(archive)
console.log(JSON.stringify({ target, stage, archive, archiveSha, commit, files: files.length }))
if (mode === '--package-only') process.exit(0)
const remote = cp.execFileSync('ssh', [...sshOptions, host, `mktemp -d /tmp/lingqimall-${label}-149.XXXXXX`], { encoding: 'utf8' }).trim()
if (!new RegExp(`^/tmp/lingqimall-${label}-149\\.[A-Za-z0-9]+$`).test(remote)) throw new Error('Unexpected remote directory')
cp.execFileSync('scp', [...sshOptions, archive, host + ':' + remote + '.tar.gz'], { stdio: 'inherit' })
const command = `set -euo pipefail
echo ${quote(archiveSha + '  ' + remote + '.tar.gz')} | sha256sum -c -
tar -xzf ${quote(remote + '.tar.gz')} -C ${quote(remote)}
export LINGQIMALL_RELEASE_AUTHORIZATION=1.0.149
export LINGQIMALL_STATIC_SITE=${quote(target)}
bash ${quote(remote + '/release.sh')} ${quote(remote)} --preflight-only ${quote(commit)}
bash ${quote(remote + '/release.sh')} ${quote(remote)} --authorize-release ${quote(commit)}`
cp.execFileSync('ssh', [...sshOptions, host, 'bash -c ' + quote(command)], { stdio: 'inherit' })
