// Only pure, dependency-free modules are allowed. --check fails on source drift.
import { readFile, writeFile, mkdir } from 'node:fs/promises'
export const modules = ['quantityInput', 'quickCart', 'orderListRules', 'transportRetry', 'loginAccount']
export function compileRule(source, name) {
  if (/^\s*(?:import\s|export\s+(?:default|\{))/m.test(source)) throw new Error(`规则必须是无平台依赖的命名导出：${name}`)
  const names = [...source.matchAll(/^export (?:const|function) (\w+)/gm)].map(match => match[1])
  if (!names.length) throw new Error(`规则无导出：${name}`)
  return `// Generated from mall-shop-web/src/utils/${name}.js. Do not edit.\n${source.replace(/^export /gm, '')}\nmodule.exports = { ${names.join(', ')} }\n`
}
const check = process.argv.includes('--check')
if (!check) await mkdir(new URL('../utils/h5-rules/', import.meta.url), { recursive: true })
for (const name of modules) {
  const content = compileRule(await readFile(new URL(`../../mall-shop-web/src/utils/${name}.js`, import.meta.url), 'utf8'), name)
  const output = new URL(`../utils/h5-rules/${name}.js`, import.meta.url)
  if (check) {
    if (await readFile(output, 'utf8') !== content) throw new Error(`H5规则未同步：${name}；运行 npm run sync:h5-rules`)
  } else await writeFile(output, content)
}
console.log(check ? 'H5同源规则生成物一致' : '已同步H5纯业务规则')
