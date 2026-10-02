import { describe, it, expect, vi } from 'vitest'
const read = vi.hoisted(() => vi.fn())
vi.mock('@/api/shop', () => ({ getStorefrontBusinessConfig: read }))
import { modeMenuAllowed, isTeamBusinessPath, refreshCustomerBusinessMode } from '../../src/utils/customerBusinessMode'
describe('模式菜单及重新读取', () => {
  it('存量保留团队菜单，新模式精简团队与奖金菜单但保留历史资金入口', () => {
    expect(modeMenuAllowed('/members/tree', null)).toBe(true)
    expect(modeMenuAllowed('/members/tree', null, false)).toBe(false)
    expect(modeMenuAllowed('/account/flows', null, false)).toBe(true)
    for (const mode of ['NORMAL', 'AGENCY']) {
      expect(modeMenuAllowed('/members/tree', mode)).toBe(false)
      expect(modeMenuAllowed('/commission/records', mode)).toBe(false)
      expect(modeMenuAllowed('/withdraw/list', mode)).toBe(true)
      expect(modeMenuAllowed('/account/flows', mode)).toBe(true)
      expect(modeMenuAllowed('/settings', mode)).toBe(true)
    }
    expect(isTeamBusinessPath('/performance/overview')).toBe(true)
  })
  it('读取失败不能把旧值误当新读取结果，成功可切回存量兼容', async () => {
    const store = { setBusinessMode: vi.fn() }
    read.mockResolvedValue({ data: {} })
    await expect(refreshCustomerBusinessMode(store)).rejects.toThrow()
    expect(store.setBusinessMode).not.toHaveBeenCalled()
    read.mockResolvedValue({ data: { invitationEnabled: 0, businessMode: 'NORMAL' } })
    await refreshCustomerBusinessMode(store); expect(store.setBusinessMode).toHaveBeenLastCalledWith('NORMAL')
    read.mockResolvedValue({ data: { invitationEnabled: 1 } })
    await refreshCustomerBusinessMode(store); expect(store.setBusinessMode).toHaveBeenLastCalledWith(null)
  })
})
