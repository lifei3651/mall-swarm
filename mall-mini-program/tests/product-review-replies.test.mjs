import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { commerceEnv } from './helpers/commerce-env.mjs'

test('评价分页保留商家与平台回复及日期，旧数据无回复保持兼容', async () => {
  const first = { id: 1, content: '评价', merchantReply: '商家说明', merchantReplyTime: '2026-09-12T12:00:00',
    platformReply: '平台说明', platformReplyTime: '2026-09-12T13:00:00' }
  const env = commerceEnv(({ params }) => ({ page: { list: params.pageNum === 1 ? [first] : [{ id: 2, content: '旧评价' }], total: 2 } }))
  const page = env.page('product'); page.productId = 1
  await page.loadReviews()
  assert.equal(page.data.reviews[0].merchantReply, '商家说明')
  assert.equal(page.data.reviews[0].platformReply, '平台说明')
  assert.equal(page.data.reviews[0].merchantReplyDate, '2026-09-12')
  await page.loadMoreReviews()
  assert.equal(page.data.reviews.length, 2)
  assert.equal(page.data.reviews[1].platformReplyDate, '')
  assert.equal(env.calls.every(call => !call.method || call.method === 'GET'), true)
  assert.equal(env.notices.length, 0)
})

test('两端只读展示双方纯文本回复，不额外空占位，不添加写评价入口', () => {
  const mini = readFileSync(new URL('../pages/product/index.wxml', import.meta.url), 'utf8')
  const h5 = readFileSync(new URL('../../mall-shop-web/src/views/ProductDetailView.vue', import.meta.url), 'utf8')
  assert.match(mini, /wx:if="{{item.merchantReply \|\| item.platformReply}}"/)
  assert.match(h5, /v-if="review.merchantReply \|\| review.platformReply"/)
  for (const [source, prefix] of [[mini, 'item'], [h5, 'review']]) {
    assert.ok(source.includes('商家回复')); assert.ok(source.includes('平台回复'))
    assert.ok(source.includes(prefix + '.merchantReply'))
    assert.ok(source.includes(prefix + '.platformReply'))
  }
  assert.doesNotMatch(mini, /rich-text.*Reply|写评价|openReviewForm/)
  assert.doesNotMatch(h5, /v-html="review\.(merchantReply|platformReply)"/)
})
