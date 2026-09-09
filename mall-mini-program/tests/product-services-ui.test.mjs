import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { commerceEnv } from './helpers/commerce-env.mjs'
const require = createRequire(import.meta.url)
const format = require('../utils/format')
const wxml = readFileSync(new URL('../pages/product/index.wxml', import.meta.url), 'utf8')
const css = readFileSync(new URL('../pages/product/index.wxss', import.meta.url), 'utf8')
const rules = selector => css.match(new RegExp('\\.' + selector + '\\s*\\{([^}]+)\\}'))?.[1] || ''

test('商品详情移除平台自营和商家身份标签，不留空占位', () => {
  assert.doesNotMatch(wxml, /平台自营|seller-badge|product\.merchantName/)
  assert.doesNotMatch(css, /seller-badge/)
  assert.match(wxml, /product\.productName/)
  assert.match(wxml, /product\.afterSalePolicy/)
})

test('商品分享为主图右上角小图标，保留原生分享与失败重试，不再占一整行', () => {
  const hero = wxml.slice(wxml.indexOf('class="hero-shell"'), wxml.indexOf('class="detail-card"'))
  assert.match(hero, /class="product-share-button" open-type="share" aria-label="分享商品"/)
  assert.match(hero, /bindtap="retryShare" disabled="\{\{!shareError\}\}"/)
  assert.match(hero, /wx:if="\{\{shareError\}\}" class="product-share-error"/)
  assert.match(readFileSync(new URL('../pages/product/index.js', import.meta.url), 'utf8'), /feedback\.notice\(this\.data\.shareError, '暂时无法分享'\)/)
  assert.doesNotMatch(wxml, />分享商品<|class="secondary-button" open-type="share"/)
  assert.match(rules('product-share-button'), /position: absolute/)
  assert.match(rules('product-share-button'), /width: 88rpx; height: 88rpx/)
  assert.match(rules('product-share-icon'), /width: 40rpx; height: 40rpx/)
})

test('服务保障为逐项图文列表，不再将标题和整段说明套成胶囊', () => {
  for (const name of ['service-heading', 'service-caption', 'service-list', 'service-item', 'service-symbol', 'service-title', 'service-description']) assert.ok(wxml.includes(name))
  assert.ok(wxml.includes('具体服务以本商品说明和商城规则为准'))
  assert.doesNotMatch(css, /\.service-panel\s*>\s*(?:view|text)/)
  assert.match(rules('service-title'), /color:\s*var\(--ink\)/)
  assert.match(rules('service-description'), /color:\s*var\(--muted\)/)
  assert.doesNotMatch(rules('service-title') + rules('service-description'), /background:|border-radius:/)
})

test('小屏长标题和多行说明自然换行，不裁剪服务条件；分隔线独立控制', () => {
  assert.match(rules('service-content'), /min-width:\s*0/)
  assert.match(rules('service-item'), /align-items:\s*flex-start/)
  assert.match(rules('service-description'), /white-space:\s*pre-wrap/)
  assert.match(rules('service-description'), /overflow-wrap:\s*anywhere/)
  assert.doesNotMatch(rules('service-description'), /line-clamp|overflow:\s*hidden|max-height|(?:^|;)\s*height:/)
  assert.match(rules('service-item-separated'), /border-top:\s*1rpx solid var\(--line\)/)
  assert.ok(wxml.includes("index > 0 ? 'service-item-separated' : ''"))
})

test('只展示已有启用保障，无配置不制造保障，缺少说明不补写承诺', () => {
  const tags = [{ title: '已配置', description: '按原规则执行。' }, { title: '关闭', enabled: false }, '仅标题']
  assert.deepEqual(format.product({ serviceTags: JSON.stringify(tags) }).serviceTags.map(tag => tag.title), ['已配置', '仅标题'])
  assert.equal(format.product({ serviceTags: null }).serviceTags.length, 0)
  assert.equal(format.product({ serviceTags: 'invalid' }).serviceTags.length, 0)
  assert.ok(wxml.includes('wx:if="{{product.serviceTags.length}}"'))
  assert.ok(wxml.includes('wx:if="{{item.description}}" class="service-description"'))
  assert.ok(wxml.includes('aria-hidden="true"'))
})

test('真实商品页加载保留后台保障顺序、长说明及售后政策，不改交易数据', async () => {
  const tags = Array.from({ length: 8 }, (_, index) => ({ title: `已配置服务${index + 1}`, description: '原始规则第一段\n第二段包含非常长的条件说明'.repeat(3) }))
  const product = { id: '27', productName: '服务区验收', status: 1, salePrice: 99, stock: 4, purchaseLimit: 2, serviceTags: JSON.stringify(tags), afterSalePolicy: '保留本商品原有售后规则' }
  const env = commerceEnv(({ url }) => url.endsWith('/reviews') ? { page: { list: [], total: 0 }, reviewCount: 0, canReview: false } : ({ product, skus: [] }))
  const page = env.page('product'); page.productId = '27'; await page.load()
  assert.deepEqual(page.data.product.serviceTags, tags)
  assert.equal(page.data.product.afterSalePolicy, product.afterSalePolicy)
  assert.equal(page.data.product.purchaseLimit, 2)
  assert.equal(page.data.priceText, '99.00')
  assert.equal(page.data.stock, 4)
  assert.deepEqual(env.calls, [{ url: '/shop/products/27' }, { url: '/shop/products/27/reviews', params: { pageNum: 1, pageSize: 5 } }])
})
