import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'
import cp from 'node:child_process'
import crypto from 'node:crypto'

// A fixed, generated release artifact. Never modify the user's canonical checkout.
const root = cp.execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim()
if (path.basename(root) !== 'mall-swarm-app-h5') throw new Error('Wrong product repository')
const ref = cp.execFileSync('git', ['rev-parse', process.argv[2] || 'HEAD'], { encoding: 'utf8' }).trim()
if (!/^[a-f0-9]{40}$/.test(ref)) throw new Error('Invalid immutable source')
const target = '/Users/minmatemp/Documents/mall-swarm-app-h5/dist/wechat-mini-program'
const manifestPath = target + '.release.json'
const version = cp.execFileSync('git', ['show', ref + ':VERSION'], { encoding: 'utf8' }).trim()
const stage = fs.mkdtempSync(path.join(os.tmpdir(), 'lingqi-mini-release-stage.'))
const archive = cp.execFileSync('git', ['archive', ref, 'mall-mini-program'], { maxBuffer: 16 * 1024 * 1024 })
cp.execFileSync('tar', ['-xf', '-', '-C', stage, '--strip-components=1'], { input: archive })
const projectFile = path.join(stage, 'project.config.json')
const project = JSON.parse(fs.readFileSync(projectFile))
project.appid = 'wxd26e0a4e41df392b'
project.projectname = '灵启商城-正式上传工程'
project.setting.urlCheck = true
fs.writeFileSync(projectFile, JSON.stringify(project, null, 2) + '\n')
if (JSON.parse(fs.readFileSync(path.join(stage, 'package.json'))).version !== version) throw new Error('Version mismatch')
const runtime = fs.readFileSync(path.join(stage, 'config/runtime.js'), 'utf8')
if (!runtime.includes("API_BASE_URL: 'https://lingqimall.com/api'")) throw new Error('Wrong API target')
const files = {}
function walk(dir, prefix = '') {
  for (const item of fs.readdirSync(dir, { withFileTypes: true })) {
    const relative = prefix + item.name
    if (item.isSymbolicLink()) throw new Error('Symlink in release: ' + relative)
    if (item.isDirectory()) walk(path.join(dir, item.name), relative + '/')
    else {
      if (/(^|\/)(\.env|project\.private\.config\.json|LOCAL_QA_ONLY\.md)$/.test(relative)) throw new Error('Private/test project file in source')
      files[relative] = crypto.createHash('sha256').update(fs.readFileSync(path.join(dir, item.name))).digest('hex')
    }
  }
}
walk(stage)
fs.mkdirSync(path.dirname(target), { recursive: true })
let preservedPrevious = null
if (fs.existsSync(target)) {
  if (!fs.existsSync(manifestPath) || JSON.parse(fs.readFileSync(manifestPath)).target !== target) throw new Error('Unknown existing target; do not overwrite')
  preservedPrevious = target + '.previous-' + Date.now()
  fs.renameSync(target, preservedPrevious)
  fs.renameSync(manifestPath, preservedPrevious + '.release.json')
}
fs.renameSync(stage, target)
const manifest = { target, version, gitCommit: ref, appid: project.appid, api: 'https://lingqimall.com/api', generatedAt: new Date().toISOString(), preservedPrevious, files }
fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + '\n')
console.log(JSON.stringify({ target, version, gitCommit: ref, files: Object.keys(files).length, manifestPath, preservedPrevious }, null, 2))
