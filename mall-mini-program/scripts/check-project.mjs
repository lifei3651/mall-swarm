import { execFileSync } from 'node:child_process'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..')
const required = ['app.js', 'app.json', 'app.wxss', 'project.config.json', 'sitemap.json', 'config/runtime.js']
for (const file of required) readFileSync(join(root, file))

const appConfig = JSON.parse(readFileSync(join(root, 'app.json'), 'utf8'))
for (const file of ['project.config.json', 'sitemap.json', 'package.json']) {
  JSON.parse(readFileSync(join(root, file), 'utf8'))
}
for (const page of appConfig.pages || []) {
  for (const extension of ['js', 'json', 'wxml', 'wxss']) readFileSync(join(root, `${page}.${extension}`))
}

const walk = (directory) => readdirSync(directory).flatMap((name) => {
  const file = join(directory, name)
  return statSync(file).isDirectory() ? walk(file) : [file]
})
for (const file of walk(root).filter((item) => item.endsWith('.js'))) {
  execFileSync(process.execPath, ['--check', file], { stdio: 'pipe' })
}

const runtime = readFileSync(join(root, 'config/runtime.js'), 'utf8')
if (!runtime.includes("API_BASE_URL: 'https://")) throw new Error('小程序 API 必须使用 HTTPS 域名')
if (runtime.includes('APP_SECRET')) throw new Error('小程序前端禁止保存 AppSecret')
const loginPage = readFileSync(join(root, 'pages/login/index.js'), 'utf8')
if (!loginPage.includes('runtime.privacyConsentVersion !== runtimeConfig.PRIVACY_CONSENT_VERSION')) {
  throw new Error('小程序登录必须阻止前后端隐私版本不一致')
}
for (const file of walk(join(root, 'pages')).filter((item) => item.endsWith('.wxml'))) {
  const view = readFileSync(file, 'utf8')
  if (/奖金|团队层级|余额互转/.test(view)) throw new Error(`公开小程序出现不允许展示的业务词：${file}`)
}
console.log('微信小程序工程结构、JSON、JavaScript 与 HTTPS 配置检查通过')
