import { access, readdir, readFile } from 'node:fs/promises'
import path from 'node:path'

const root = path.resolve('dist-team')
await access(path.join(root, 'index.html'))
const manifest = JSON.parse(await readFile(path.join(root, 'version.json'), 'utf8'))
if (manifest.application !== 'team-h5') throw new Error(`团队 H5 构建身份错误：${manifest.application || '未设置'}`)

const files = []
const walk = async (directory) => {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const target = path.join(directory, entry.name)
    if (entry.isDirectory()) await walk(target)
    else if (/\.(?:js|html|json)$/.test(entry.name)) files.push(target)
  }
}
await walk(root)
const blocked = [
  { label: '余额互转页面', pattern: /BalanceTransferView|\/profile\/wallet\/transfer/ },
  { label: '余额互转接口', pattern: /\/shop\/wallet\/(?:transfers|recipient)/ },
]
const violations = []
for (const file of files) {
  const content = await readFile(file, 'utf8')
  for (const rule of blocked) if (rule.pattern.test(content)) violations.push(`${path.relative(root, file)}：${rule.label}`)
}
if (violations.length) {
  console.error(`团队 H5 资金边界检查失败：\n${violations.map((item) => `- ${item}`).join('\n')}`)
  process.exit(1)
}
console.log(`团队 H5 资金边界检查通过：未打包余额互转页面和接口，共扫描 ${files.length} 个构建文件。`)
