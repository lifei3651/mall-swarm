import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'
const read = path => readFileSync(new URL('../' + path, import.meta.url), 'utf8')
const view = read('src/views/OrderReviewView.vue')
function harness(respond) {
  const calls = [], state = { member: 'member', alive: true }
  const context = {
    ref: value => ({ value }), computed: getter => ({ get value() { return getter() } }),
    watch() {}, onBeforeUnmount() {},
    useRoute: () => ({ params: { id: '7' }, query: { orderItemId: '700' } }), useRouter: () => ({ replace() {} }),
    localStorage: { getItem: () => state.member }, hasShopSession: () => state.alive,
    getProductReviews: async (id, params) => { calls.push({ id, params }); return respond('GET') },
    submitProductReview: async (id, data) => { calls.push({ id, data }); return respond('POST') }
  }
  const script = view.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*\n/gm, '')
  vm.runInNewContext(script + '\nthis.page = { load, submit, eligible, content, rating, completed, notice, submitting }', context)
  return { ...context, calls, state }
}
test('H5独立订单评价严格带订单项，失败保留草稿，成功禁重复', async () => {
  let fail = true
  const h = harness(method => { if (method === 'GET') return { data: { canReview: true } }; if (fail) throw Error('失败'); return {} })
  await h.page.load(); assert.equal(h.page.eligible.value, true)
  h.page.content.value = '真实体验'; await h.page.submit(); assert.equal(h.page.content.value, '真实体验')
  assert.equal(h.page.notice.value, '失败'); fail = false
  await h.page.submit(); assert.equal(h.page.completed.value, true)
  await h.page.submit(); assert.equal(h.calls.filter(c => c.data).length, 2)
  assert.equal(h.calls[1].data.orderItemId, '700')
})
test('H5订单不可评价不提交，资格核对后换号不能提交旧草稿', async () => {
  const denied = harness(() => ({ data: { canReview: false } }))
  await denied.page.load(); denied.page.content.value = '体验'; await denied.page.submit()
  assert.equal(denied.calls.length, 1)
  const h = harness(() => ({ data: { canReview: true } })); await h.page.load()
  h.page.content.value = '体验'; h.state.member = 'other'; await h.page.submit()
  assert.equal(h.calls.length, 1); assert.match(h.page.notice.value, /登录状态已变化/)
})
test('两种商城H5构建注册独立受保护评价路由，详情只读；后台已有隐藏恢复', () => {
  for (const path of ['src/router/index.js', 'src/surfaces/integrated/router.js']) assert.match(read(path), /path: '\/order-review\/:id'.*requiresAuth: true/)
  assert.doesNotMatch(read('src/views/ProductDetailView.vue'), /写评价|submitProductReview|review-form"/)
  const admin = read('../mall-distribution-admin/src/views/shop/reviews.vue')
  assert.match(admin, /hideReview\(row\)/); assert.match(admin, /restoreReview\(row\)/)
  assert.match(admin, /status: 0, reason: value.trim\(\)/); assert.match(admin, /status: 1, reason: ''/)
})
