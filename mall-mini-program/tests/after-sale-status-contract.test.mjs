import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const read = relative => readFileSync(new URL(relative, import.meta.url), 'utf8')

const frontends = [
  ['小程序订单列表', '../pages/orders/index.js'],
  ['小程序订单详情', '../pages/order-detail/index.js'],
  ['公开商城订单列表', '../../mall-shop-web/src/views/OrdersView.vue'],
  ['公开商城订单详情', '../../mall-shop-web/src/views/OrderDetailView.vue'],
  ['公开商城客服工单', '../../mall-shop-web/src/views/ServiceTicketsView.vue'],
  ['管理后台订单', '../../mall-distribution-admin/src/views/shop/orders.vue']
]

const statusMeanings = new Map([
  [0, /待审核/],
  [1, /退款完成/],
  [2, /拒绝/],
  [3, /取消/],
  [4, /寄回/],
  [5, /商家收货|收货/],
  [6, /退款.*中|退款处理中/],
  [7, /换货.*发出/],
  [8, /换货.*发出/]
])

const extractNineStateMap = (source, name) => {
  const match = source.match(/\{\s*0\s*:\s*['"]待审核['"][\s\S]{0,700}?8\s*:\s*['"][^'"]+['"]\s*\}/)
  assert.ok(match, `${name} 没有完整的售后状态映射`)
  return match[0]
}

test('小程序、H5与管理后台统一识别后端实际九种售后状态', () => {
  for (const [name, path] of frontends) {
    const map = extractNineStateMap(read(path), name)
    const keys = [...map.matchAll(/(?:\{|,)\s*(\d+)\s*:/g)].map(item => Number(item[1]))
    assert.deepEqual(keys, [0, 1, 2, 3, 4, 5, 6, 7, 8], `${name} 状态编号漂移`)
    for (const [status, meaning] of statusMeanings) {
      const label = map.match(new RegExp(`(?:\\{|,)\\s*${status}\\s*:\\s*['\"]([^'\"]+)['\"]`))?.[1] || ''
      assert.match(label, meaning, `${name} 状态 ${status} 的含义漂移：${label}`)
    }
  }
})

test('后端工作流覆盖九态且终态与各前端处理中集合一致', () => {
  const service = read('../../mall-distribution/src/main/java/com/macro/mall/distribution/service/impl/ShopAfterSaleServiceImpl.java')
  const mapper = read('../../mall-distribution/src/main/resources/mapper/DmsShopAfterSaleMapper.xml')
  const orderService = read('../../mall-distribution/src/main/java/com/macro/mall/distribution/service/impl/ShopServiceImpl.java')
  const miniDetail = read('../pages/order-detail/index.js')
  const h5List = read('../../mall-shop-web/src/views/OrdersView.vue')
  const adminOrders = read('../../mall-distribution-admin/src/views/shop/orders.vue')

  for (const token of [
    'afterSale.setStatus(0)',
    'afterSale.setStatus(1)',
    'Integer.valueOf(2).equals(status)',
    'afterSale.setStatus(3)',
    'afterSale.setStatus(4)',
    'afterSale.setStatus(5)',
    'afterSale.setStatus(6)',
    'exchange ? 7 : 6',
    'afterSale.setStatus(8)'
  ]) assert.ok(service.includes(token), `后端售后工作流缺少状态证据：${token}`)

  for (const token of [
    'status IN (4, 5)',
    '#{afterSale.status} = 5',
    'apply_type = 2 AND #{afterSale.status} = 6',
    'apply_type = 3 AND #{afterSale.status} = 7',
    'SET status = 8',
    'apply_type = 3 AND status = 7',
    'apply_type = 3 AND status = 8',
    'WHERE id = #{id} AND status = 6'
  ]) assert.ok(mapper.includes(token), `后端售后状态迁移合同缺失：${token}`)

  assert.match(orderService, /TERMINAL_AFTER_SALE_STATUSES\s*=\s*Set\.of\(1, 2, 3\)/)
  for (const source of [miniDetail, h5List, adminOrders]) {
    assert.match(source, /\[0, 4, 5, 6, 7, 8\]\.includes\(/,
      '前端处理中售后集合必须等于九态中的非终态集合')
  }
})
