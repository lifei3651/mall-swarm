import test from 'node:test'
import assert from 'node:assert/strict'
import { commerceEnv } from './helpers/commerce-env.mjs'

test('小程序首页先展示装修再挂载接口自带商品，默认首屏不重复请求商品列表', async () => {
  const env = commerceEnv(({ url }) => {
    if (url === '/shop/home') return {
      brandName: '测试商城', displayConfig: {}, categoryList: [], banners: [], notices: [], newArrivals: [], liveRooms: [],
      featuredProducts: [{ id: '1', productName: '商品', coverUrl: '/api/shop/media/images/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.jpg', salePrice: 10, stock: 1, status: 1 }]
    }
    throw new Error(`不应请求 ${url}`)
  })
  const page = env.page('home'), updates = []
  const update = page.setData.bind(page)
  page.setData = (patch, done) => { updates.push(structuredClone(patch)); update(patch, done) }

  await page.loadHome()

  assert.deepEqual(env.calls.map(call => call.url), ['/shop/home'])
  const homeUpdate = updates.findIndex(patch => patch.home?.brandName === '测试商城')
  const productUpdate = updates.findIndex(patch => Array.isArray(patch.products) && patch.products.length === 1)
  assert.ok(homeUpdate >= 0 && productUpdate > homeUpdate)
  assert.match(page.data.products[0].coverUrl, /\?variant=card$/)
})

test('小程序首页已有筛选时仍按筛选条件单独取商品', async () => {
  const env = commerceEnv(({ url }) => url === '/shop/home'
    ? { displayConfig: {}, categoryList: [], banners: [], notices: [], newArrivals: [], liveRooms: [], featuredProducts: [{ id: '1' }] }
    : { list: [{ id: '2', productName: '筛选商品', salePrice: 20, stock: 1, status: 1 }] })
  const page = env.page('home')
  page.setData({ activeCategory: '护理套装' })

  await page.loadHome()

  assert.deepEqual(env.calls.map(call => call.url), ['/shop/home', '/shop/products'])
  assert.equal(env.calls[1].params.categoryName, '护理套装')
  assert.equal(page.data.products[0].id, '2')
})
