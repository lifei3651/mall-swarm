import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { resolveOrderAdminAccess } from '@/utils/orderAdminPermissions'

vi.mock('@/utils/adminSession', () => ({
  saveAdminSessionExpireTime: vi.fn(),
  clearAdminSessionStorage: vi.fn(),
  resetAdminLoginRedirect: vi.fn(),
}))

describe('订单后台动作权限', () => {
  let useAppStore

  beforeEach(async () => {
    localStorage.clear()
    setActivePinia(createPinia())
    ;({ useAppStore } = await import('@/store'))
  })

  const accessFor = (permissions) => {
    const store = useAppStore()
    store.setPermissions(permissions)
    return resolveOrderAdminAccess(store.hasPermission)
  }

  it('只有订单权限时可进入订单页，但不能处理售后或查看奖金去向', () => {
    expect(accessFor(['shop:order'])).toEqual({
      canViewOrders: true,
      canHandleAfterSale: false,
      canReadFinance: false,
    })
  })

  it('售后角色可进入订单页并处理售后，但不能查看奖金去向', () => {
    expect(accessFor(['shop:order', 'shop:aftersale'])).toEqual({
      canViewOrders: true,
      canHandleAfterSale: true,
      canReadFinance: false,
    })
  })

  it('财务角色可进入订单页并查看奖金去向，但不能处理售后', () => {
    expect(accessFor(['shop:order', 'finance:read'])).toEqual({
      canViewOrders: true,
      canHandleAfterSale: false,
      canReadFinance: true,
    })
  })

  it('超级管理员通配权限保留订单、售后和财务全部能力', () => {
    expect(accessFor(['*'])).toEqual({
      canViewOrders: true,
      canHandleAfterSale: true,
      canReadFinance: true,
    })
  })

  it('商户首页把售后待办与普通客服入口按权限拆开', async () => {
    const source = await readFile(resolve(process.cwd(), 'src/views/merchant/home.vue'), 'utf8')

    expect(source).toContain("store.hasPermission('shop:aftersale') && {")
    expect(source).toContain("path: '/shop/orders?orderState=AFTER_SALE'")
    expect(source).toContain("store.hasPermission('shop:order') && {")
    expect(source).toContain("path: '/shop/service-tickets'")
  })
})
