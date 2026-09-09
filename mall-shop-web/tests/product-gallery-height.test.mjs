import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
const source = readFileSync(new URL('../src/views/ProductDetailView.vue', import.meta.url), 'utf8')
test('H5主图按真实比例调整高度并保留完整图片，与小程序一致', () => {
  assert.match(source, /:style="\{ aspectRatio: galleryAspectRatio \}"/)
  assert.match(source, /@load="galleryImageLoaded\(image, \$event\)"/)
  assert.match(source, /galleryRatios.value\[mainImages.value\[activeImageIndex.value\]\]/)
  assert.match(source, /object-fit:contain/)
  const handler = source.match(/const galleryImageLoaded = \(image, event\) => \{[\s\S]*?\n\}/)[0]
  const ratios = { value: {} }
  const context = { galleryRatios: ratios }
  vm.runInNewContext(handler + '\nthis.loadImage = galleryImageLoaded', context)
  context.loadImage('wide', { target: { naturalWidth: 1500, naturalHeight: 1000 } })
  context.loadImage('tall', { target: { naturalWidth: 600, naturalHeight: 1200 } })
  context.loadImage('bad', { target: { naturalWidth: 0, naturalHeight: 100 } })
  assert.equal(ratios.value.wide, 1.5)
  assert.equal(ratios.value.tall, 1)
  assert.equal(ratios.value.bad, undefined)
})
