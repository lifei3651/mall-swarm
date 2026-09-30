import { beforeEach, describe, expect, it, vi } from 'vitest'
vi.unmock('element-plus')
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import SettingsCenter from '../../src/views/settings/index.vue'
const state = vi.hoisted(() => ({ userInfo:{}, permissions:['*'] }))
vi.mock('@/store', () => ({ useAppStore:() => ({ get userInfo() { return state.userInfo }, hasPermission:permission => state.permissions.includes('*') || state.permissions.includes(permission) }) }))
const open = async (path) => {
  const router = createRouter({ history:createMemoryHistory(), routes:[{path:'/settings',component:SettingsCenter},{path:'/tenant/business-modes',component:{template:'<div>业务开关</div>'}},{path:'/withdraw/settings',component:{template:'<div>提现规则</div>'}}] })
  await router.push(path); await router.isReady()
  const wrapper = mount(SettingsCenter, { global:{ plugins:[router, ElementPlus] } }); await flushPromises()
  return wrapper
}
beforeEach(() => { state.userInfo = {}; state.permissions = ['*']; globalThis.ResizeObserver = class { observe(){} unobserve(){} disconnect(){} } })
describe('设置中心实际页面', () => {
  it('银行卡搜索进入真实提现规则页面，不再显示待接入占位', async () => {
    const w = await open('/settings?q=银行卡')
    expect(w.text()).toContain('1 个可用入口'); expect(w.text()).toContain('余额与提现规则')
    expect(w.find('a').attributes('href')).toBe('/withdraw/settings'); w.unmount()
  })
  it('优惠券发行不混入设置中心，业务开关仍可搜索；无权限不显示资金规则', async () => {
    state.permissions = ['config:shop']
    const w = await open('/settings?q=优惠券')
    expect(w.text()).toContain('没有匹配的设置'); expect(w.find('a').exists()).toBe(false); w.unmount()
    state.permissions = ['config:bonus']
    const modes = await open('/settings?q=优惠券')
    expect(modes.text()).toContain('业务模块与奖金'); expect(modes.find('a').attributes('href')).toBe('/tenant/business-modes'); modes.unmount()
    const empty = await open('/settings?q=银行卡')
    expect(empty.text()).toContain('没有匹配的设置'); expect(empty.text()).not.toContain('银行卡提现'); empty.unmount()
  })
  it('商家不能从组件获取平台配置索引', async () => {
    state.userInfo = { merchantId:8 }
    const w = await open('/settings?group=finance')
    expect(w.text()).toContain('当前账号没有可访问的设置'); expect(w.find('a').exists()).toBe(false); w.unmount()
  })
})
