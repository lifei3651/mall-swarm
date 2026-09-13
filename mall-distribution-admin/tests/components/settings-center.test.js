import { beforeEach, describe, expect, it, vi } from 'vitest'
vi.unmock('element-plus')
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import SettingsCenter from '../../src/views/settings/index.vue'
const state = vi.hoisted(() => ({ userInfo:{}, permissions:['*'] }))
vi.mock('@/store', () => ({ useAppStore:() => ({ get userInfo() { return state.userInfo }, hasPermission:permission => state.permissions.includes('*') || state.permissions.includes(permission) }) }))
const open = async (path) => {
  const router = createRouter({ history:createMemoryHistory(), routes:[{path:'/settings',component:SettingsCenter},{path:'/shop/coupons',component:{template:'<div>测试目标</div>'}}] })
  await router.push(path); await router.isReady()
  const wrapper = mount(SettingsCenter, { global:{ plugins:[router, ElementPlus] } }); await flushPromises()
  return wrapper
}
beforeEach(() => { state.userInfo = {}; state.permissions = ['*']; globalThis.ResizeObserver = class { observe(){} unobserve(){} disconnect(){} } })
describe('设置中心实际页面', () => {
  it('银行卡搜索只有待接入说明，没有假开关和保存按钮', async () => {
    const w = await open('/settings?q=银行卡')
    expect(w.text()).toContain('1 项待接入'); expect(w.text()).toContain('银行卡提现')
    expect(w.find('button').exists()).toBe(false); expect(w.find('[role="switch"]').exists()).toBe(false); w.unmount()
  })
  it('搜索列出归属分类与真实优惠券地址；无权限不显示资金能力', async () => {
    state.permissions = ['config:shop']
    const w = await open('/settings?q=优惠券')
    expect(w.text()).toContain('优惠与营销'); expect(w.find('a').attributes('href')).toBe('/shop/coupons'); w.unmount()
    const empty = await open('/settings?q=银行卡')
    expect(empty.text()).toContain('没有匹配的设置'); expect(empty.text()).not.toContain('银行卡提现'); empty.unmount()
  })
  it('商家不能从组件获取平台配置索引', async () => {
    state.userInfo = { merchantId:8 }
    const w = await open('/settings?group=finance')
    expect(w.text()).toContain('当前账号没有可访问的设置'); expect(w.find('a').exists()).toBe(false); w.unmount()
  })
})
