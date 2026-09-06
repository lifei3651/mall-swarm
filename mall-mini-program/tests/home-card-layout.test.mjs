import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8')

test('首页单列活动版限制图片高度并取消双列卡片的高度下限', () => {
  const style = read('../pages/home/index.wxss')
  assert.match(style, /\.layout-campaign-feed \.product-media,\s*\.layout-campaign-feed \.product-cover\s*\{\s*height:\s*320rpx;/)
  assert.match(style, /\.layout-campaign-feed \.product-card\s*\{\s*min-height:\s*0;/)
  assert.match(style, /\.layout-campaign-feed \.product-grid\s*\{[^}]*grid-template-columns:\s*minmax\(0, 1fr\);[^}]*gap:\s*20rpx;/)
  assert.match(style, /\.layout-campaign-feed \.product-price\s*\{\s*margin-top:\s*12rpx;/)
  // Other templates keep their existing two-column thumbnail size.
  assert.match(style, /\.product-media\s*\{[^}]*height:\s*296rpx;/)
  assert.doesNotMatch(style, /height:\s*580rpx/)
})

test('缩短列表缩略图不拉伸图片，也不删除详情入口及详情完整图片', () => {
  const home = read('../pages/home/index.wxml')
  assert.match(home, /class="product-card"[^>]*bindtap="openProduct"/)
  assert.match(home, /class="product-cover"[^>]*mode="aspectFill"/)
  const detail = read('../pages/product/index.wxml')
  assert.match(detail, /class="hero"[^>]*mode="aspectFit"/)
  assert.match(detail, /class="detail-image"[^>]*mode="widthFix"/)
})
