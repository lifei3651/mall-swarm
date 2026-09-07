import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { canApplyAfterSale, afterSaleDeadline, isTradeActionOwner } from '../src/utils/orderListRules.js'
import { retryDelay } from '../src/utils/transportRetry.js'
const require = createRequire(import.meta.url)
const native = require('../../mall-mini-program/utils/h5-rules/orderListRules.js')
const nativeRetry = require('../../mall-mini-program/utils/h5-rules/transportRetry.js')

test('H5原资格规则提取后保持期限、状态、已退数量和联合单归属；原生同源输出一致', () => {
  const now = Date.parse('2026-09-07T00:00:00')
  const base = { order: { id: '9', status: 2, createTime: '2026-09-01 00:00:00' }, items: [{ quantity: 2 }], afterSales: [] }
  for (const [patch, expected] of [
    [{}, true], [{ afterSaleDeadline: '2026-09-07 00:00:00' }, false],
    [{ afterSaleSelfServiceEnabled: false }, false], [{ afterSaleWindowDays: 1 }, false],
    [{ order: { ...base.order, status: 0 } }, false], [{ order: { ...base.order, status: 4 } }, false],
    [{ afterSales: [{ status: 4 }] }, false], [{ afterSales: [{ status: 2 }] }, true],
    [{ afterSales: [{ status: 1, applyType: 1, items: [{ refundQuantity: 2 }] }] }, false],
    [{ afterSales: [{ status: 1, applyType: 1, items: [{ refundQuantity: 1 }] }] }, true],
    [{ afterSales: [{ status: 1, applyType: 3, items: [{ refundQuantity: 2 }] }] }, true],
    [{ afterSaleWindowMode: 'RECEIVED', order: { ...base.order, createTime: '2000-01-01' } }, true]
  ]) {
    const item = { ...base, ...patch }
    assert.equal(canApplyAfterSale(item, now), expected)
    assert.equal(native.canApplyAfterSale(item, now), expected)
    assert.equal(native.afterSaleDeadline(item), afterSaleDeadline(item))
  }
  const rows = [{ order: { id: '9', tradeId: '8' } }, { order: { id: '10', tradeId: '8' } }, { order: { id: '11' } }]
  assert.deepEqual(rows.map(row => isTradeActionOwner(row, rows)), [true, false, true])
  assert.deepEqual(rows.map(row => native.isTradeActionOwner(row, rows)), [true, false, true])
})

test('H5只读恢复策略与原生同源：所有方法/故障/重试次数组合一致', () => {
  for (const method of ['GET', 'HEAD', 'OPTIONS', 'POST', 'PUT', 'DELETE', 'PATCH']) {
    for (const error of [{ code: 'ERR_NETWORK' }, { code: 'ECONNABORTED' }, { message: 'Network Error' }, {}, ...[200, 400, 401, 403, 404, 429, 500, 502, 503, 504].map(status => ({ response: { status } }))]) {
      for (const count of [0, 1, 2]) {
        const expected = count === 0 && ['GET', 'HEAD', 'OPTIONS'].includes(method)
          ? ([502, 503, 504].includes(error.response?.status) ? 600 : !error.response && (['ERR_NETWORK', 'ECONNABORTED'].includes(error.code) || error.message === 'Network Error') ? 250 : null) : null
        assert.equal(retryDelay(method, error, count), expected)
        assert.equal(nativeRetry.retryDelay(method, error, count), expected)
      }
    }
  }
})
