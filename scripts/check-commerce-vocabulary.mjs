import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative, resolve } from 'node:path'
import process from 'node:process'

const root = resolve(import.meta.dirname, '..')
const forbidden = [
  { text: ['报', '单'].join(''), replacement: '普通商城' },
  { text: ['客户', '定制奖金'].join(''), replacement: '对应奖金规则' },
  { text: ['客户奖金', '处理'].join(''), replacement: '奖金规则' },
]
const extensions = new Set(['.java', '.js', '.json', '.properties', '.ts', '.vue', '.wxml', '.xml', '.yaml', '.yml'])
const roots = [
  'mall-common/src/main',
  'mall-distribution/src/main',
  'mall-distribution-admin/src',
  'mall-mini-program/app.js',
  'mall-mini-program/app.json',
  'mall-mini-program/components',
  'mall-mini-program/custom-tab-bar',
  'mall-mini-program/pages',
  'mall-mini-program/templates',
  'mall-mini-program/utils',
  'mall-shop-web/src',
]

const files = []
const walk = (path) => {
  if (!existsSync(path)) return
  const stat = statSync(path)
  if (stat.isDirectory()) {
    for (const name of readdirSync(path)) walk(join(path, name))
    return
  }
  const suffix = path.slice(path.lastIndexOf('.'))
  if (extensions.has(suffix)) files.push(path)
}
for (const path of roots) walk(join(root, path))

const failures = []
for (const path of files) {
  const lines = readFileSync(path, 'utf8').split(/\r?\n/)
  lines.forEach((line, index) => {
    for (const item of forbidden) {
      if (line.includes(item.text)) failures.push(`${relative(root, path)}:${index + 1} 请改为“${item.replacement}”口径`)
    }
  })
}

if (failures.length) {
  console.error(`商城业务术语检查失败（${failures.length}项）：`)
  failures.forEach((failure) => console.error(`- ${failure}`))
  process.exit(1)
}

console.log('商城业务术语检查通过：用户可见统一为“普通商城 / 复购区”，旧销售渠道与未开放奖金文案未进入产品源码。')
