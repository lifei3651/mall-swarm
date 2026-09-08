// Creates an isolated native simulator project. Never writes the formal upload directory.
import { cp, readFile, writeFile, mkdtemp, realpath } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, basename, dirname } from 'node:path'
const source = new URL('../', import.meta.url)
const output = process.argv[2] ? await realpath(process.argv[2]) : await mkdtemp(join(tmpdir(), 'lingqi133-completion-ui-'))
let previousProject
if (process.argv[2]) {
  if (await realpath(dirname(output)) !== await realpath(tmpdir()) || !basename(output).startsWith('lingqi133-completion-ui-')) throw Error('Only this script’s temporary QA project can be refreshed')
  if (!(await readFile(join(output, 'LOCAL_QA_ONLY.md'), 'utf8')).includes('禁止上传')) throw Error('Missing QA marker')
  previousProject = JSON.parse(await readFile(join(output, 'project.config.json'), 'utf8'))
}
await cp(source, output, { recursive: true, filter: path => !['node_modules', 'tests', 'scripts'].includes(basename(path)) })
await cp(new URL('../tests/fixtures/ui-request.js', import.meta.url), join(output, 'utils/request.js'))
const project = JSON.parse(await readFile(new URL('../project.config.json', import.meta.url), 'utf8'))
project.appid = previousProject?.appid || 'touristappid'; project.projectname = 'UI133-全页面验收-禁止上传'
await writeFile(join(output, 'project.config.json'), JSON.stringify(project, null, 2))
await writeFile(join(output, 'LOCAL_QA_ONLY.md'), '# 禁止上传\n仅本地假数据与界面验收；无有效服务器令牌，所有网络写入/授权/支付已拦截。\n')
const guard = `const blocked = options => { const error = {errMsg:'本地界面验收已拦截，未发起真实操作'}; options?.fail?.(error); options?.complete?.(error) }
for (const name of ['request','uploadFile','login','requestPayment','requestMerchantTransfer','requestSubscribeMessage','chooseAddress','getUserProfile']) wx[name] = blocked
wx.setStorageSync('mall_mini_access_token','LOCAL-UI-ONLY-NO-SERVER-AUTHORITY')
wx.setStorageSync('mall_mini_member',{id:'132',nickname:'本地界面验收',username:'PreviewOnly',phone:'13800000000'})
wx.setStorageSync('qa-settings',{})
`
await writeFile(join(output, 'qa-guard.js'), guard)
await writeFile(join(output, 'app.js'), `require('./qa-guard')\n${await readFile(new URL('../app.js', import.meta.url), 'utf8')}`)
console.log(output)
