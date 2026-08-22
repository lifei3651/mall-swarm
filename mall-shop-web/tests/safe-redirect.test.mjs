import test from 'node:test'
import assert from 'node:assert/strict'
import { safeShopRedirect } from '../src/utils/safeRedirect.js'

test('shop redirect keeps valid internal routes', () => {
  assert.equal(safeShopRedirect('/orders/12?tab=after-sale#detail', '/profile'), '/orders/12?tab=after-sale#detail')
})

test('shop redirect rejects external, encoded and authentication loops', () => {
  for (const value of [
    'https://evil.example',
    '//evil.example/path',
    '/\\evil.example',
    '/%2f%2fevil.example',
    '/%5cevil.example',
    'javascript:alert(1)',
    '/login',
    '/register',
    ['//evil.example'],
  ]) {
    assert.equal(safeShopRedirect(value, '/profile'), '/profile')
  }
})
