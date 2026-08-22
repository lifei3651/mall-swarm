import { access, readdir, readFile } from 'node:fs/promises'
import path from 'node:path'

const root = path.resolve('dist-integrated')
await access(path.join(root, 'index.html'))

const manifest = JSON.parse(await readFile(path.join(root, 'version.json'), 'utf8'))
if (manifest.application !== 'integrated-h5') {
  throw new Error(`一体化 H5 构建身份错误：${manifest.application || '未设置'}`)
}

const files = []
const walk = async (directory) => {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const target = path.join(directory, entry.name)
    if (entry.isDirectory()) await walk(target)
    else files.push(path.relative(root, target))
  }
}
await walk(root)

const requiredChunks = [
  'HomeView',
  'CartView',
  'OrdersView',
  'InviteView',
  'TeamPerformanceView',
  'WalletView',
  'BalanceTransferView',
  'RepurchaseView',
]
const missing = requiredChunks.filter((name) => !files.some((file) => file.includes(name)))
if (missing.length) {
  throw new Error(`一体化 H5 缺少功能分块：${missing.join('、')}`)
}

console.log(`一体化 H5 边界检查通过：商城交易与团队结算分块齐全，共扫描 ${files.length} 个构建文件。`)
