import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const readProjectFile = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8')

test('PWA manifest and service worker provide an installable shell without caching private APIs', async () => {
  const manifest = JSON.parse(await readProjectFile('public/manifest.webmanifest'))
  const serviceWorker = await readProjectFile('public/service-worker.js')
  const html = await readProjectFile('index.html')

  assert.equal(manifest.display, 'standalone')
  assert.deepEqual(manifest.icons.map((icon) => icon.sizes), ['192x192', '512x512'])
  assert.match(html, /rel="manifest" href="\/manifest\.webmanifest"/)
  assert.match(serviceWorker, /pathname\.startsWith\('\/api\/'\)/)
  assert.match(serviceWorker, /pathname\.includes\('\/pay\/'\)/)
  assert.match(serviceWorker, /request\.mode === 'navigate'/)
})

test('storefront installs global runtime error collection and registers PWA updates', async () => {
  const main = await readProjectFile('src/main.js')
  const runtimeErrors = await readProjectFile('src/utils/runtimeErrors.js')
  const pwa = await readProjectFile('src/utils/pwa.js')

  assert.match(main, /installGlobalErrorHandling\(app, router, appSurface === 'team' \? 'team-h5' : 'shop-public'\)/)
  assert.match(main, /registerPwa\(\)/)
  assert.match(runtimeErrors, /app\.config\.errorHandler/)
  assert.match(runtimeErrors, /unhandledrejection/)
  assert.match(runtimeErrors, /\/api\/shop\/client-errors/)
  assert.match(pwa, /navigator\.serviceWorker\.register/)
  assert.match(pwa, /import\.meta\.env\.PROD/)
  assert.match(pwa, /isTeamSurface/)
})

test('home and category pages share the reusable product skeleton component', async () => {
  const skeleton = await readProjectFile('src/components/ProductListSkeleton.vue')
  const home = await readProjectFile('src/views/HomeView.vue')
  const category = await readProjectFile('src/views/CategoryView.vue')

  assert.match(skeleton, /skeleton-card/)
  assert.match(skeleton, /skeleton-copy/)
  assert.match(home, /<ProductListSkeleton/)
  assert.match(category, /<ProductListSkeleton/)
})
