import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import cp from 'node:child_process'
import crypto from 'node:crypto'

const [target, mode] = process.argv.slice(2)
if (target !== 'backend' || !['--package-only', '--authorize-release'].includes(mode)) {
  throw new Error('Usage: node scripts/release-lingqi-152.mjs backend --package-only|--authorize-release')
}
const git = (...args) => cp.execFileSync('git', args, { encoding: 'utf8' }).trim()
const root = git('rev-parse', '--show-toplevel')
if (path.basename(root) !== 'mall-swarm-app-h5') throw new Error('Wrong product repository')
process.chdir(root)
if (git('status', '--porcelain')) throw new Error('Commit reviewed changes before packaging')
if (fs.readFileSync('VERSION', 'utf8').trim() !== '1.0.152') throw new Error('Wrong version')
const commit = git('rev-parse', 'HEAD')
const sha = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')
const quote = value => "'" + value.replaceAll("'", "'\\''") + "'"
const jar = path.join(root, 'mall-distribution/target/mall-distribution-1.0-SNAPSHOT.jar')
if (!fs.existsSync(jar)) throw new Error('Missing built backend JAR')

const migrations = fs.readdirSync('document/db/migrations')
  .filter(name => /^V\d{12}__[a-z0-9_]+\.sql$/.test(name)).sort()
if (migrations.length !== 40 || migrations.at(-1) !== 'V202609202100__system_fund_accounts.sql') {
  throw new Error('Unexpected migration inventory')
}
const stage = fs.mkdtempSync(path.join(os.tmpdir(), 'lingqi-152-backend-candidate.'))
fs.mkdirSync(path.join(stage, 'scripts'), { recursive: true })
fs.mkdirSync(path.join(stage, 'document/db/migrations'), { recursive: true })
fs.copyFileSync(jar, path.join(stage, 'mall-distribution.jar'))
fs.copyFileSync('VERSION', path.join(stage, 'VERSION'))
fs.copyFileSync('scripts/remote-deploy-20260920-v1.0.152-backend.sh', path.join(stage, 'release.sh'))
fs.copyFileSync('scripts/production-backup.sh', path.join(stage, 'scripts/production-backup.sh'))
fs.copyFileSync('scripts/db-migrate.sh', path.join(stage, 'scripts/db-migrate.sh'))
for (const name of migrations) fs.copyFileSync(`document/db/migrations/${name}`, path.join(stage, 'document/db/migrations', name))

const manifest = {
  version: '1.0.152', scope: 'financial-accounts-risk-sse-hotfix', gitCommit: commit,
  buildId: '20260920-financial-hotfix-1.0.152', jarSha256: sha(jar), generatedAt: new Date().toISOString(),
  previousVersion: '1.0.149', previousJarSha256: 'fbbea526aef6ed69144ccf64b14642240cb187d4595de9bbf8cd22bebaffa2d1',
  databaseMigrations: migrations,
}
fs.writeFileSync(path.join(stage, 'RELEASE_MANIFEST.json'), JSON.stringify(manifest, null, 2) + '\n')
const files = []
function walk(directory, prefix = '') {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const relative = prefix + entry.name
    if (entry.isDirectory()) walk(path.join(directory, entry.name), relative + '/')
    else if (entry.isFile() && relative !== 'SHA256SUMS') files.push(relative)
    else if (!entry.isFile()) throw new Error('Unexpected candidate entry')
  }
}
walk(stage)
fs.writeFileSync(path.join(stage, 'SHA256SUMS'), files.sort().map(file => `${sha(path.join(stage, file))}  ${file}`).join('\n') + '\n')
const archive = stage + '.tar.gz'
cp.execFileSync('tar', ['--format=ustar', '--no-xattrs', '--no-mac-metadata', '-czf', archive, '-C', stage, '.'], {
  env: { ...process.env, COPYFILE_DISABLE: '1' },
})
const archiveSha = sha(archive)
console.log(JSON.stringify({ target, stage, archive, archiveSha, commit, jarSha256: manifest.jarSha256 }))
if (mode === '--package-only') process.exit(0)

const sshOptions = ['-i', '/Users/minmatemp/.ssh/lingqi_server_ed25519', '-o', 'BatchMode=yes', '-o', 'IdentitiesOnly=yes', '-o', 'ConnectTimeout=12', '-o', 'StrictHostKeyChecking=yes', '-o', 'ServerAliveInterval=10', '-o', 'ServerAliveCountMax=3']
const host = 'root@lingqimall.com'
const remote = cp.execFileSync('ssh', [...sshOptions, host, 'mktemp -d /tmp/lingqimall-financial-hotfix-152.XXXXXX'], { encoding: 'utf8' }).trim()
if (!/^\/tmp\/lingqimall-financial-hotfix-152\.[A-Za-z0-9]+$/.test(remote)) throw new Error('Unexpected remote directory')
cp.execFileSync('rsync', ['--partial', '--timeout=45', '-e', ['ssh', ...sshOptions].map(quote).join(' '), archive, host + ':' + remote + '.tar.gz'], { stdio: 'inherit' })
const command = `set -euo pipefail
echo ${quote(archiveSha + '  ' + remote + '.tar.gz')} | sha256sum -c -
tar -xzf ${quote(remote + '.tar.gz')} -C ${quote(remote)}
export LINGQIMALL_RELEASE_AUTHORIZATION=1.0.152
export LINGQIMALL_RELEASE_COMMIT=${quote(commit)}
bash ${quote(remote + '/release.sh')} ${quote(remote)} --preflight-only
bash ${quote(remote + '/release.sh')} ${quote(remote)} --authorize-release`
cp.execFileSync('ssh', [...sshOptions, host, 'bash -c ' + quote(command)], { stdio: 'inherit' })
