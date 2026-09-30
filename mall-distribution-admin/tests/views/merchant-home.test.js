import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import MerchantHome from '@/views/merchant/home.vue'

const api = vi.hoisted(() => ({
  push: vi.fn(),
  getAdminOrderWorkSummary: vi.fn(),
  listMerchantAccounts: vi.fn(),
}))
const auth = vi.hoisted(() => ({
  userInfo: { merchantId: 87, merchantName: '合成验收商户' },
  permissions: [],
}))

vi.mock('vue-router', () => ({ useRouter: () => ({ push: api.push }) }))
vi.mock('@/store', () => ({
  useAppStore: () => ({
    userInfo: auth.userInfo,
    hasPermission: (permission) => auth.permissions.includes(permission),
  }),
}))
vi.mock('@/api/shop', () => ({ getAdminOrderWorkSummary: api.getAdminOrderWorkSummary }))
vi.mock('@/api/merchant', () => ({ listMerchantAccounts: api.listMerchantAccounts }))

const stubs = {
  ElTag: { template: '<span><slot /></span>' },
  ElIcon: { template: '<span />' },
  ElAlert: {
    props: ['title', 'description'],
    template: '<div role="alert"><strong>{{ title }}</strong><p>{{ description }}</p></div>',
  },
}
let wrapper
const render = async (permissions) => {
  auth.permissions = permissions
  wrapper = mount(MerchantHome, { global: { stubs } })
  await flushPromises()
  return wrapper
}
const actionTitles = () => wrapper.findAll('.action-card strong').map((item) => item.text())

beforeEach(() => {
  vi.clearAllMocks()
  auth.permissions = []
  api.getAdminOrderWorkSummary.mockResolvedValue({ data: { pendingShipment: 1, afterSale: 0 } })
  api.listMerchantAccounts.mockResolvedValue({ data: [] })
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('商户首页岗位入口', () => {
  it('仅订单权限的员工只显示订单与客服，不显示入驻或子账号入口', async () => {
    await render(['admin:read', 'shop:order'])

    expect(actionTitles()).toEqual(['待发货订单', '客服工单'])
    expect(wrapper.text()).not.toContain('入驻资料与认证')
    expect(wrapper.text()).not.toContain('子账号与权限')
    expect(api.getAdminOrderWorkSummary).toHaveBeenCalledTimes(1)
    expect(api.listMerchantAccounts).not.toHaveBeenCalled()
    await wrapper.findAll('.action-card')[0].trigger('click')
    expect(api.push).toHaveBeenCalledWith('/shop/orders')
    await wrapper.findAll('.action-card')[1].trigger('click')
    expect(api.push).toHaveBeenLastCalledWith('/shop/service-tickets')
  })

  it('有子账号管理权限的负责人显示入驻和子账号卡并导航到对应页面', async () => {
    await render(['admin:read', 'merchant:staff-manage'])

    expect(actionTitles()).toEqual(['入驻资料与认证', '子账号与权限'])
    await wrapper.findAll('.action-card')[0].trigger('click')
    expect(api.push).toHaveBeenLastCalledWith('/merchant/profile')
    await wrapper.findAll('.action-card')[1].trigger('click')
    expect(api.push).toHaveBeenLastCalledWith('/merchant/staff')
    expect(api.getAdminOrderWorkSummary).not.toHaveBeenCalled()
    expect(api.listMerchantAccounts).not.toHaveBeenCalled()
  })

  it('未分配业务权限时显示空态，不显示快捷卡或查询订单和货款', async () => {
    await render(['admin:read'])

    expect(api.getAdminOrderWorkSummary).not.toHaveBeenCalled()
    expect(api.listMerchantAccounts).not.toHaveBeenCalled()
    expect(wrapper.findAll('.action-card')).toHaveLength(0)
    expect(wrapper.get('[role="alert"]').text()).toContain('账号已开通，暂未分配业务权限')
    expect(wrapper.text()).not.toContain('今日工作')
    expect(api.push).not.toHaveBeenCalled()
  })
})
