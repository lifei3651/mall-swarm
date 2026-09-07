// Bundle the exact H5 parser and its region labels for native offline parsing.
// Generated files are verified against H5 by full-parity.test.mjs.
import { createRequire } from 'node:module'
import { readFile, writeFile } from 'node:fs/promises'
const requireWeb = createRequire(new URL('../../mall-shop-web/package.json', import.meta.url))
const { pcaTextArr } = requireWeb('element-china-area-data')
const source = await readFile(new URL('../../mall-shop-web/src/utils/addressParser.js', import.meta.url), 'utf8')
const native = '// Generated from H5; run scripts/sync-address-parser.mjs.\n' + source
  .replace("import { pcaTextArr } from 'element-china-area-data'", "const pcaTextArr = require('./address-regions')")
  .replace('export function parseChineseAddress', 'function parseChineseAddress') + '\nmodule.exports = { parseChineseAddress }\n'
await writeFile(new URL('../utils/address-parser.js', import.meta.url), native)
await writeFile(new URL('../utils/address-regions.js', import.meta.url), '// Generated from H5 region labels. WeChat CommonJS does not import JSON.\nmodule.exports = ' + JSON.stringify(pcaTextArr) + '\n')
