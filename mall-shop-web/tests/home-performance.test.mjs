import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { productCardImage } from '../src/utils/productMedia.js'

const home = readFileSync(new URL('../src/views/HomeView.vue', import.meta.url), 'utf8')
const category = readFileSync(new URL('../src/views/CategoryView.vue', import.meta.url), 'utf8')
const api = readFileSync(new URL('../src/api/shop.js', import.meta.url), 'utf8')

test('商品列表使用服务器卡片缩略图，外部图片和版型地址保持不变', () => {
  assert.equal(productCardImage('/api/shop/media/images/abc.jpg'), '/api/shop/media/images/abc.jpg?variant=card')
  assert.equal(productCardImage('/api/shop/media/images/abc.jpg?variant=card'), '/api/shop/media/images/abc.jpg?variant=card')
  assert.equal(productCardImage('https://images.example.com/abc.jpg'), 'https://images.example.com/abc.jpg')
  assert.match(home, /coverUrl: productCardImage\(product\.coverUrl \|\| product\.picUrl \|\| ''\)/)
  assert.match(category, /coverUrl: productCardImage\(product\.coverUrl \|\| product\.picUrl \|\| ''\)/)
})

test('H5首页先渲染装修再使用首页已返回商品，默认首屏不重复查询列表', () => {
  assert.match(home, /const loadedHome = await fetchHome\(\)[\s\S]*await nextTick\(\)[\s\S]*loadedHome\.featuredProducts\.map\(normalizeProduct\)/)
  assert.match(home, /void refreshCampaigns\(\)/)
  assert.ok(home.indexOf('loadedHome.featuredProducts.map(normalizeProduct)') < home.indexOf('void refreshCampaigns()'))
})

test('H5外壳与首页同时读取首页时合并同一在途请求', () => {
  assert.match(api, /let pendingHomeRequest = null/)
  assert.match(api, /if \(!pendingHomeRequest\)[\s\S]*pendingHomeRequest = request\(\{ url: '\/shop\/home', method: 'get' \}\)/)
  assert.match(api, /\.finally\(\(\) => \{ pendingHomeRequest = null \}\)/)
})
