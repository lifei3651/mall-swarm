import { readdir, readFile } from 'node:fs/promises'
import path from 'node:path'

const root = path.resolve('dist')
const blocked = [
  { label: '团队关系接口', pattern: /\/shop\/(?:team|invite)(?:\/|`|"|')/i },
  { label: '团队资料接口', pattern: /\/shop\/profile\/performance/i },
  { label: '资金划转接口', pattern: /\/shop\/wallet\/(?:withdrawals|transfers|recipient)/i },
  { label: '团队业务文字', pattern: /团队|邀请|奖金|佣金|提现|复购|层级|代理等级/ },
  { label: '团队页面路由', pattern: /["'`]\/(?:invite|performance|wallet\/transfer)["'`]/i },
]

const files = []
const walk = async (directory) => {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const target = path.join(directory, entry.name)
    if (entry.isDirectory()) await walk(target)
    else if (/\.(?:js|html|json)$/.test(entry.name)) files.push(target)
  }
}

await walk(root)
const violations = []
for (const file of files) {
  const content = await readFile(file, 'utf8')
  for (const rule of blocked) {
    if (rule.pattern.test(content)) violations.push(`${path.relative(root, file)}：${rule.label}`)
  }
}

if (violations.length) {
  console.error(`公开商城包边界检查失败：\n${violations.map((item) => `- ${item}`).join('\n')}`)
  process.exit(1)
}
console.log(`公开商城包边界检查通过：已扫描 ${files.length} 个构建文件。`)
