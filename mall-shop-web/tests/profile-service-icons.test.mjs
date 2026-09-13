import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('公开和一体化个人中心使用同系列票券与工单图标，保留原路由', () => {
  for (const file of ['src/surfaces/public/PublicProfileView.vue', 'src/views/ProfileView.vue']) {
    const source = readFileSync(new URL(`../${file}`, import.meta.url), 'utf8')
    const coupon = source.match(/<RouterLink to="\/profile\/coupons"[\s\S]*?<\/RouterLink>/)?.[0]
    const support = source.match(/<RouterLink to="\/support"[\s\S]*?<\/RouterLink>/)?.[0]
    assert.match(coupon, /<TicketPercent /)
    assert.match(coupon, /优惠券/)
    assert.doesNotMatch(coupon, />券</)
    assert.match(support, /<ClipboardList /)
    assert.match(support, /客服工单/)
  }
})
