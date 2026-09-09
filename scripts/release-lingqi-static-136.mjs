import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import cp from 'node:child_process'
import crypto from 'node:crypto'

// Bound to this reviewed candidate and the existing production host.
const [site, mode] = process.argv.slice(2)
const config = {
  shop: ['mall-shop-web/dist', 'public-h5', 'storefront-public'],
  team: ['mall-shop-web/dist-team', 'team-h5', 'team-h5'],
  admin: ['mall-distribution-admin/dist', 'admin', 'admin'],
}[site]
if (!config || !['--package-only', '--authorize-release'].includes(mode)) throw new Error('Invalid explicit scope/mode')
const git = (...args) => cp.execFileSync('git', args, { encoding: 'utf8' }).trim()
const root = git('rev-parse', '--show-toplevel')
if (path.basename(root) !== 'mall-swarm-app-h5') throw new Error('Wrong product repository')
process.chdir(root)
if (git('status', '--porcelain')) throw new Error('Commit reviewed changes before packaging')
const commit = git('rev-parse', 'HEAD')
const [dist, label, application] = config
const version = JSON.parse(fs.readFileSync(path.join(dist, 'version.json')))
const expected = { version: '1.0.136', edition: 'app-h5-split', application, gitCommit: commit, buildId: '20260909-' + label + '-1.0.136' }
for (const key of Object.keys(expected)) if (version[key] !== expected[key]) throw new Error('Wrong built identity: ' + key)
const sha = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')
const files = []
function walk(dir, prefix = '') {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.isSymbolicLink() || entry.name.startsWith('.env') || entry.name.startsWith('._')) throw new Error('Unsafe candidate entry')
    const relative = prefix + entry.name
    if (entry.isDirectory()) walk(path.join(dir, entry.name), relative + '/')
    else if (entry.isFile()) files.push(relative)
    else throw new Error('Unexpected file kind')
  }
}
walk(dist)
const stage = fs.mkdtempSync(path.join(os.tmpdir(), 'lingqi136-' + site + '-package.'))
const tar = (output, cwd, entries) => cp.execFileSync('tar', ['--format=ustar', '--no-xattrs', '--no-mac-metadata', '-czf', output, '-C', cwd, ...entries], { env: { ...process.env, COPYFILE_DISABLE: '1' } })
tar(path.join(stage, site + '.tar.gz'), dist, ['.'])
fs.writeFileSync(path.join(stage, site.toUpperCase() + '_SHA256SUMS'), files.sort().map(f => sha(path.join(dist, f)) + '  ./' + f).join('\n') + '\n')
fs.copyFileSync('scripts/remote-deploy-20260909-v1.0.136-' + label + '.sh', path.join(stage, 'release.sh'))
fs.copyFileSync('scripts/production-backup.sh', path.join(stage, 'production-backup.sh'))
fs.writeFileSync(path.join(stage, 'SHA256SUMS'), fs.readdirSync(stage).sort().map(f => sha(path.join(stage, f)) + '  ' + f).join('\n') + '\n')
const names = fs.readdirSync(stage).sort()
const archive = stage + '.tar.gz'
tar(archive, stage, names)
const entries = cp.execFileSync('tar', ['-tzf', archive], { encoding: 'utf8' }).trim().split('\n').sort()
if (JSON.stringify(names) !== JSON.stringify(entries)) throw new Error('Unexpected archive entries')
const hash = sha(archive)
console.log(JSON.stringify({ site, stage, archive, sha256: hash, commit, files: files.length }))
if (mode === '--package-only') process.exit(0)
const opts = ['-i', '/Users/minmatemp/.ssh/lingqi_server_ed25519', '-o', 'BatchMode=yes', '-o', 'IdentitiesOnly=yes', '-o', 'ConnectTimeout=12', '-o', 'StrictHostKeyChecking=yes']
const host = 'root@lingqimall.com'
const quote = s => "'" + s.replaceAll("'", "'\\''") + "'"
const remote = cp.execFileSync('ssh', [...opts, host, 'mktemp -d /tmp/lingqimall-' + label + '-136.XXXXXX'], { encoding: 'utf8' }).trim()
if (!new RegExp('^/tmp/lingqimall-' + label + '-136\\.[A-Za-z0-9]+$').test(remote)) throw new Error('Unexpected remote directory')
console.log('remote=' + remote)
cp.execFileSync('scp', [...opts, archive, host + ':' + remote + '.tar.gz'], { stdio: 'inherit' })
const command = [
  'set -euo pipefail',
  'echo ' + quote(hash + '  ' + remote + '.tar.gz') + ' | sha256sum -c -',
  "python3 - " + quote(remote + '.tar.gz') + " <<'PY'",
  'import sys, tarfile',
  'expected = ' + JSON.stringify(names),
  'with tarfile.open(sys.argv[1]) as a:',
  '    entries = a.getmembers()',
  '    assert len(entries) == len(expected) and sorted(m.name for m in entries) == expected',
  '    assert all(m.isfile() for m in entries)',
  'PY',
  'tar -xzf ' + quote(remote + '.tar.gz') + ' -C ' + quote(remote),
  'export LINGQIMALL_RELEASE_AUTHORIZATION=1.0.136',
  'bash ' + quote(remote + '/release.sh') + ' ' + quote(remote) + ' --preflight-only ' + quote(commit),
  'bash ' + quote(remote + '/release.sh') + ' ' + quote(remote) + ' --authorize-release ' + quote(commit),
].join('\n')
cp.execFileSync('ssh', [...opts, host, 'bash -c ' + quote(command)], { stdio: 'inherit' })
