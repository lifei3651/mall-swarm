// Rasterize the existing Lucide library for native mini-program image support.
// Usage: node scripts/generate-profile-icons.mjs [absolute path to sharp package]
// Shapes are supplied by lucide-vue-next (ISC); see assets/LICENSE-lucide.txt.
import { createRequire } from 'node:module'
import { mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
const requireWeb = createRequire(new URL('../../mall-shop-web/package.json', import.meta.url))
const { createSSRApp } = requireWeb('vue')
const { renderToString } = requireWeb('@vue/server-renderer')
const icons = requireWeb('lucide-vue-next')
const sharp = requireWeb(process.argv[2] || 'sharp')
const output = new URL('../assets/profile/', import.meta.url)
await mkdir(output, { recursive: true })
for (const [file, name] of Object.entries({
  'user-round': 'UserRound', 'credit-card': 'CreditCard', package: 'Package',
  truck: 'Truck', 'rotate-ccw': 'RotateCcw', bell: 'Bell', 'map-pin': 'MapPin',
  headset: 'Headset', wallet: 'Wallet', 'chevron-right': 'ChevronRight'
})) {
  const svg = await renderToString(createSSRApp(icons[name], { size: 96, color: '#30394a', strokeWidth: 1.6 }))
  await sharp(Buffer.from(svg)).png().toFile(fileURLToPath(new URL(`${file}.png`, output)))
}
