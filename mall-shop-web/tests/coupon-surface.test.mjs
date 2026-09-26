import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { canClaimCouponOnSurface } from '../src/utils/couponVisibility.js'

test('公开商城不提供复购专用券领取入口，一体化商城不受影响', () => {
  const repurchase = { businessTypes: ['REPURCHASE'] }
  const normal = { businessTypes: ['NORMAL'] }
  const shared = { businessTypes: ['NORMAL', 'REPURCHASE'] }
  assert.equal(canClaimCouponOnSurface(repurchase, true), false)
  assert.equal(canClaimCouponOnSurface(normal, true), true)
  assert.equal(canClaimCouponOnSurface(shared, true), true)
  assert.equal(canClaimCouponOnSurface(repurchase, false), true)
  assert.equal(canClaimCouponOnSurface({}, true), false)
  const view = readFileSync(new URL('../src/views/CouponsView.vue', import.meta.url), 'utf8')
  assert.match(view, /v-if="tab==='catalog' && claimVisible\(c\)"/)
  assert.match(view, /!claimVisible\(c\)\)return/)
})
