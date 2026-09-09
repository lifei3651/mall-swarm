import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { commerceEnv } from './helpers/commerce-env.mjs'
const summary = canReview => ({ canReview, reviewHint: '订单暂不可评价', page: { list: [], total: 0 } })
test('商品详情只读评价，订单评价页必须带具体订单项并核对资格', async () => {
  const view = readFileSync(new URL('../pages/product/index.wxml', import.meta.url), 'utf8')
  assert.doesNotMatch(view, /写评价|openReviewForm|submitReview|review-editor/)
  const env = commerceEnv(() => summary(true)), invalid = env.page('order-review')
  invalid.onLoad({ id: '7' }); await invalid.onShow(); await invalid.submitReview()
  assert.equal(env.calls.length, 0)
  const page = env.page('order-review')
  page.onLoad({ id: '7', orderItemId: '700' }); await page.onShow()
  assert.equal(page.data.reviewFormVisible, true)
  assert.equal(env.calls[0].params.orderItemId, '700')
})
test('订单资格拒绝不展示表单，成功后不重复评价，失败保留内容', async () => {
  let allowed = false, fail = true
  const env = commerceEnv(({ method }) => { if (method === 'POST') { if (fail) throw Error('保存失败'); return {} } return summary(allowed) })
  const page = env.page('order-review'); page.onLoad({ id: '7', orderItemId: '700' })
  await page.onShow(); assert.equal(page.data.reviewFormVisible, false)
  allowed = true; await page.openReviewForm(); page.reviewInput({ detail: { value: '真实体验' } })
  await page.submitReview(); assert.equal(page.data.reviewContent, '真实体验'); assert.equal(page.data.completed, false)
  fail = false; await page.submitReview(); assert.equal(page.data.completed, true); assert.equal(page.data.reviewFormVisible, false)
  await page.onShow(); assert.equal(page.data.reviewFormVisible, false)
  assert.equal(env.calls.find(call => call.method === 'POST').data.orderItemId, '700')
})
test('订单评价核对时离页、换号不打开旧表单', async () => {
  let finish
  const env = commerceEnv(() => new Promise(resolve => { finish = resolve })), page = env.page('order-review')
  page.onLoad({ id: '7', orderItemId: '700' }); const pending = page.onShow()
  page.onHide(); env.token('other'); finish(summary(true)); await pending
  assert.equal(page.data.reviewFormVisible, false)
  page.setData({ reviewContent: '旧账号草稿' }); env.token('third')
  const next = page.onShow(); finish(summary(false)); await next
  assert.equal(page.data.reviewContent, '')
})
