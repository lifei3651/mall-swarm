import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import cp from 'node:child_process'
import crypto from 'node:crypto'

const [target, mode] = process.argv.slice(2)
const config = {
  admin: ['mall-distribution-admin/dist', 'admin', 'admin'],
  shop: ['mall-shop-web/dist', 'public-h5', 'storefront-public'],
  team: ['mall-shop-web/dist-team', 'team-h5', 'team-h5'],
}
if (!config[target] || !['--package-only', '--authorize-release'].includes(mode)) {
  throw new Error('Usage: node scripts/release-lingqi-151-static.mjs admin|shop|team --package-only|--authorize-release')
}

const git = (...args) => cp.execFileSync('git', args, { encoding: 'utf8' }).trim()
const root = git('rev-parse', '--show-toplevel')
if (path.basename(root) !== 'mall-swarm-app-h5') throw new Error('Wrong product repository')
process.chdir(root)
if (git('status', '--porcelain')) throw new Error('Commit reviewed changes before packaging')
if (fs.readFileSync('VERSION', 'utf8').trim() !== '1.0.151') throw new Error('Wrong version')

const commit = git('rev-parse', 'HEAD')
const [dist, label, application] = config[target]
const expectedVersion = {
  version: '1.0.151',
  edition: 'app-h5-split',
  application,
  gitCommit: commit,
  buildId: `20260920-${label}-1.0.151`,
}
const version = JSON.parse(fs.readFileSync(path.join(dist, 'version.json')))
for (const [key, expected] of Object.entries(expectedVersion)) {
  if (version[key] !== expected) throw new Error(`Wrong built identity: ${key}`)
}

const sha = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')
const quote = value => "'" + value.replaceAll("'", "'\\''") + "'"
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

const stage = fs.mkdtempSync(path.join(os.tmpdir(), `lingqi-151-${target}-candidate.`))
const tar = (output, cwd, entries) => cp.execFileSync('tar', ['--format=ustar', '--no-xattrs', '--no-mac-metadata', '-czf', output, '-C', cwd, ...entries], {
  env: { ...process.env, COPYFILE_DISABLE: '1' },
})
tar(path.join(stage, `${target}.tar.gz`), dist, ['.'])
fs.writeFileSync(path.join(stage, `${target.toUpperCase()}_SHA256SUMS`), files.sort().map(file => `${sha(path.join(dist, file))}  ./${file}`).join('\n') + '\n')
fs.copyFileSync('scripts/remote-deploy-20260920-v1.0.151-static.sh', path.join(stage, 'release.sh'))
fs.copyFileSync('scripts/production-backup.sh', path.join(stage, 'production-backup.sh'))
const payload = fs.readdirSync(stage).sort()
fs.writeFileSync(path.join(stage, 'SHA256SUMS'), payload.map(name => `${sha(path.join(stage, name))}  ${name}`).join('\n') + '\n')
const names = fs.readdirSync(stage).sort()
const archive = `${stage}.tar.gz`
tar(archive, stage, names)
const archiveEntries = cp.execFileSync('tar', ['-tzf', archive], { encoding: 'utf8' }).trim().split('\n').sort()
if (JSON.stringify(names) !== JSON.stringify(archiveEntries)) throw new Error('Unexpected archive entries')
const archiveSha = sha(archive)
console.log(JSON.stringify({ target, stage, archive, archiveSha, commit, files: files.length }))
if (mode === '--package-only') process.exit(0)

const sshOptions = [
  '-i', '/Users/minmatemp/.ssh/lingqi_server_ed25519',
  '-o', 'BatchMode=yes', '-o', 'IdentitiesOnly=yes', '-o', 'ConnectTimeout=12',
  '-o', 'StrictHostKeyChecking=yes', '-o', 'ServerAliveInterval=10', '-o', 'ServerAliveCountMax=3',
]
const host = 'root@lingqimall.com'
const remote = cp.execFileSync('ssh', [...sshOptions, host, `mktemp -d /tmp/lingqimall-${label}-151.XXXXXX`], { encoding: 'utf8' }).trim()
if (!new RegExp(`^/tmp/lingqimall-${label}-151\\.[A-Za-z0-9]+$`).test(remote)) throw new Error('Unexpected remote directory')
cp.execFileSync('scp', [...sshOptions, archive, `${host}:${remote}.tar.gz`], { stdio: 'inherit' })
const command = `set -euo pipefail
echo ${quote(`${archiveSha}  ${remote}.tar.gz`)} | sha256sum -c -
tar -xzf ${quote(`${remote}.tar.gz`)} -C ${quote(remote)}
export LINGQIMALL_RELEASE_AUTHORIZATION=1.0.151
export LINGQIMALL_STATIC_SITE=${quote(target)}
bash ${quote(`${remote}/release.sh`)} ${quote(remote)} --preflight-only ${quote(commit)}
bash ${quote(`${remote}/release.sh`)} ${quote(remote)} --authorize-release ${quote(commit)}`
cp.execFileSync('ssh', [...sshOptions, host, 'bash -c ' + quote(command)], { stdio: 'inherit' })
