import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { normalizePageLayouts, resolvePageLayouts, hasEmptyDirectoryLayout, PAGE_LAYOUT_OPTIONS } from '../src/utils/pageLayouts.js'
import { resolveCategoryGuideConfig } from '../src/utils/displayConfig.js'
import { resolveBrandCssVariables } from '../src/utils/brand.js'
import { SHOP_THEME_OPTIONS, themePalette } from '../../mall-distribution-admin/src/utils/shopTheme.js'
const native = createRequire(import.meta.url)('../../mall-mini-program/utils/display-config.js')
const nativeRules = createRequire(import.meta.url)('../../mall-mini-program/utils/h5-rules/pageLayouts.js')
const config = (shared = {}, platforms = {}) => ({ extraConfigJson: JSON.stringify({ pageLayouts: { version: 1, shared, platforms }, opaqueExtension: { keep: true } }) })
test('legacy config retains prior home/category behavior without mutations', () => {
  for (const home of PAGE_LAYOUT_OPTIONS.home) for (const category of ['directory', 'showcase', 'scenario']) {
    const source = { layoutTemplate: home, categoryGuideTemplate: category }
    const before = JSON.stringify(source)
    assert.equal(resolvePageLayouts(source).home, home)
    assert.equal(resolvePageLayouts(source).category, home === 'category-focus' ? category : 'list')
    assert.equal(JSON.stringify(source), before)
  }
})
test('each page is independent; overrides are sparse and follow shared changes', () => {
  const source = config({ home: 'campaign-feed', category: 'showcase', product: 'inset' }, { mini: { category: 'list' }, app: { product: 'standard' } })
  assert.deepEqual(resolvePageLayouts(source, 'mini'), { home: 'campaign-feed', category: 'list', product: 'inset' })
  assert.equal(resolvePageLayouts(source, 'app').product, 'standard')
  assert.equal(resolvePageLayouts(source, 'h5').category, 'showcase')
  const edited = normalizePageLayouts(source)
  edited.shared.home = 'standard'
  assert.equal(resolvePageLayouts({ pageLayouts: edited }, 'mini').home, 'standard')
  delete edited.platforms.mini.category
  assert.equal(resolvePageLayouts({ pageLayouts: edited }, 'mini').category, 'showcase')
  assert.equal(JSON.parse(source.extraConfigJson).pageLayouts.shared.home, 'campaign-feed')
})
test('all combinations have identical H5/native rule outputs and active category templates', () => {
  for (const home of PAGE_LAYOUT_OPTIONS.home) for (const category of PAGE_LAYOUT_OPTIONS.category) for (const product of PAGE_LAYOUT_OPTIONS.product) {
    const source = config({ home, category, product })
    for (const platform of ['h5', 'mini', 'app', 'shared']) assert.deepEqual(resolvePageLayouts(source, platform), nativeRules.resolvePageLayouts(source, platform))
    assert.equal(native.home(source).layoutTemplate, home)
    assert.equal(native.category(source).guideEnabled, category !== 'list')
    assert.equal(resolveCategoryGuideConfig(source).enabled, category !== 'list')
    if (category !== 'list') assert.equal(resolveCategoryGuideConfig(source).template, native.category(source).guideTemplate)
    assert.equal(native.productLayout(source), product)
  }
})
test('malformed and future values safely fall back, not arbitrary CSS or business flags', () => {
  for (const source of [null, [], { extraConfigJson: '{' }, config({ home: 'evil', category: false }, { mini: { product: 'url(x)', checkout: 'off' } })]) {
    assert.equal(resolvePageLayouts(source, 'mini').home, 'standard')
    assert.equal(resolvePageLayouts(source, 'mini').product, 'standard')
  }
  assert.equal(resolvePageLayouts({ layoutTemplate: 'campaign-feed', pageLayouts: { version: 2, shared: { home: 'standard' } } }).home, 'campaign-feed')
})
test('directory modules must be checked across every configured platform', () => {
  const source = { ...config({ category: 'list' }, { mini: { category: 'directory' } }), categoryGuidePrimaryCategoriesEnabled: 0, categoryGuideSubcategoriesEnabled: 0, categoryGuideHotProductsEnabled: 0 }
  assert.equal(hasEmptyDirectoryLayout(source), true)
  source.categoryGuideHotProductsEnabled = 1
  assert.equal(hasEmptyDirectoryLayout(source), false)
})
test('opt-in green theme uses consistent color roles and preserves old defaults/customizations', () => {
  const brand = { productTemplate: 'lingqi-green' }
  const css = resolveBrandCssVariables(brand)
  const mini = native.palette(brand)
  const admin = themePalette(SHOP_THEME_OPTIONS.find(theme => theme.value === brand.productTemplate))
  for (const [role, variable] of [['priceColor', '--price-color'], ['pageBg', '--shop-page-bg'], ['textColor', '--text-color'], ['mutedColor', '--muted-color']]) {
    assert.equal(mini[role], admin[role])
    assert.equal(css[variable], admin[role])
  }
  assert.equal(css['--brand-primary'], '#16734b')
  assert.equal(mini.primary, '#16734b')
  assert.equal(resolveBrandCssVariables({})['--brand-primary'], '#e7193f')
  assert.equal(native.palette({}).primary, '#e7193f')
  const customized = { ...brand, themeColor: '#123456', displayConfig: { extraConfigJson: JSON.stringify({ colors: { priceColor: '#654321' } }) } }
  assert.equal(resolveBrandCssVariables(customized)['--price-color'], '#654321')
  assert.equal(native.palette(customized).priceColor, '#654321')
})
