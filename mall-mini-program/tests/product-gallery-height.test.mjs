import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'
const read = path => readFileSync(new URL('../' + path, import.meta.url), 'utf8')
function page() {
  let definition
  vm.runInNewContext(read('pages/product/index.js'), {
    require: name => name.endsWith('/theme') ? { pageData: () => ({}) } : {},
    Page: value => { definition = value }
  })
  definition.data = { ...definition.data, product: { gallery: ['wide', 'square', 'portrait'] } }
  definition.setData = function(values) { Object.assign(this.data, values) }
  return definition
}
const loaded = (p, src, width, height) => p.galleryImageLoaded({ currentTarget: { dataset: { src } }, detail: { width, height } })
test('横图按真实比例收起上下留白，方图和竖图不超过一屏宽', () => {
  const p = page()
  loaded(p, 'wide', 1500, 1000)
  assert.equal(p.data.galleryHeight, 500)
  loaded(p, 'portrait', 600, 1200)
  assert.equal(p.data.galleryHeight, 500, '非当前图片加载不能改变高度')
  p.galleryChanged({ detail: { current: 2 } })
  assert.equal(p.data.galleryHeight, 750)
  p.galleryChanged({ detail: { current: 0 } })
  assert.equal(p.data.galleryHeight, 500)
  loaded(p, 'wide', 0, 1000)
  loaded(p, 'wide', 1000, NaN)
  loaded(p, 'other-product', 1000, 10)
  assert.equal(p.data.galleryHeight, 500, '无效和其他商品回调忽略')
  p.galleryChanged({ detail: { current: 1 } })
  loaded(p, 'square', 1000, 1000)
  assert.equal(p.data.galleryHeight, 750)
})
test('主图容器按加载比例调整，内部图片完整展示且填满容器尺寸', () => {
  assert.match(read('pages/product/index.wxml'), /height: \{\{galleryHeight\}\}rpx/)
  assert.match(read('pages/product/index.wxml'), /class="gallery-image"[^>]*mode="aspectFit"[^>]*bindload="galleryImageLoaded"/)
  assert.match(read('pages/product/index.wxss'), /\.gallery-image\s*\{[^}]*width: 100%;[^}]*height: 100%/)
})
