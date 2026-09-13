import fs from 'node:fs'
import cp from 'node:child_process'
import crypto from 'node:crypto'
import path from 'node:path'

const [archive, expectedSha, commit, mode] = process.argv.slice(2)
const root = cp.execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim()
if (path.basename(root) !== 'mall-swarm-app-h5') throw new Error('Wrong product repository')
process.chdir(root)
if (mode !== '--authorize-release') throw new Error('Explicit release mode is required')
if (!/^[a-f0-9]{64}$/.test(expectedSha || '') || !/^[a-f0-9]{40}$/.test(commit || '')) throw new Error('Invalid immutable identity')
if (!path.isAbsolute(archive || '') || !fs.statSync(archive).isFile()) throw new Error('Invalid candidate path')
if (cp.execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim() !== commit) throw new Error('Unexpected source commit')
if (fs.readFileSync('VERSION', 'utf8').trim() !== '1.0.139') throw new Error('Wrong version')
if (crypto.createHash('sha256').update(fs.readFileSync(archive)).digest('hex') !== expectedSha) throw new Error('Archive hash mismatch')
const expectedFiles = ['mall-distribution.jar', 'VERSION', 'RELEASE_MANIFEST.json', 'SHA256SUMS', 'release.sh', 'production-backup.sh', 'db-migrate.sh', 'V202609121900__product_review_replies.sql', 'V202609122000__shop_coupons.sql'].sort()
const archiveFiles = cp.execFileSync('tar', ['-tzf', archive], { encoding: 'utf8' }).trim().split('\n').sort()
if (JSON.stringify(archiveFiles) !== JSON.stringify(expectedFiles)) throw new Error('Unexpected archive entry')
const identity = '/Users/minmatemp/.ssh/lingqi_server_ed25519'
const options = ['-i', identity, '-o', 'BatchMode=yes', '-o', 'IdentitiesOnly=yes', '-o', 'ConnectTimeout=12', '-o', 'StrictHostKeyChecking=yes', '-o', 'ServerAliveInterval=10', '-o', 'ServerAliveCountMax=3']
const host = 'root@lingqimall.com'
const quote = value => "'" + value.replaceAll("'", "'\\''") + "'"
const remote = cp.execFileSync('ssh', [...options, host, 'mktemp -d /tmp/lingqimall-mini-backend-139.XXXXXX'], { encoding: 'utf8' }).trim()
if (!/^\/tmp\/lingqimall-mini-backend-139\.[A-Za-z0-9]+$/.test(remote)) throw new Error('Unexpected remote staging directory')
const remoteArchive = remote + '.tar.gz'
console.log('release-directory=' + remote)
cp.execFileSync('rsync', ['--partial', '--timeout=45', '-e', ['ssh', ...options].map(quote).join(' '), archive, host + ':' + remoteArchive], { stdio: 'inherit' })
const command = `set -euo pipefail
archive=${quote(remoteArchive)}
release_dir=${quote(remote)}
[[ "$(sha256sum "$archive" | awk '{print $1}')" == ${quote(expectedSha)} ]]
python3 - "$archive" <<'PY'
import sys, tarfile
expected = {'mall-distribution.jar', 'VERSION', 'RELEASE_MANIFEST.json', 'SHA256SUMS', 'release.sh', 'production-backup.sh', 'db-migrate.sh', 'V202609121900__product_review_replies.sql', 'V202609122000__shop_coupons.sql'}
with tarfile.open(sys.argv[1], 'r:gz') as archive:
    members = archive.getmembers()
    assert len(members) == 9 and {m.name for m in members} == expected
    assert all(m.isfile() for m in members)
print('archive_hash_and_nine_regular_files=passed')
PY
tar -xzf "$archive" -C "$release_dir"
export LINGQIMALL_RELEASE_AUTHORIZATION=1.0.139
export LINGQIMALL_RELEASE_COMMIT=${quote(commit)}
bash "$release_dir/release.sh" "$release_dir" --preflight-only
bash "$release_dir/release.sh" "$release_dir" --authorize-release
`
cp.execFileSync('ssh', [...options, host, 'bash -c ' + quote(command)], { stdio: 'inherit' })
console.log('remote-release-command-completed=yes')
