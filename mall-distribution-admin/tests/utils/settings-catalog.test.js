import { describe, expect, it } from 'vitest'
import {
  SETTINGS_ENTRIES,
  SETTINGS_GROUPS,
  settingsEntriesFor,
  settingsGroupFor,
  canAccessSettings,
  isSettingsEditor,
  isSettingsContext,
  normalizeSettingsPath,
  settingsAwareMenuPath,
  withoutSettingsEditors,
} from '../../src/utils/settingsCatalog'
import { businessModeChanges } from '../../src/utils/businessModeChanges'
const account = (permissions, merchantId) => ({ userInfo: { merchantId }, hasPermission: (permission) => permissions.includes('*') || permissions.includes(permission) })
describe('集中设置入口权限与搜索', () => {
  it('未授权账号没有入口，商家即使带通配符也不进入平台设置', () => {
    expect(canAccessSettings(account([]))).toBe(false)
    expect(settingsEntriesFor(account(['*'], 1))).toEqual([])
  })
  it('搜索不会展示无权限的入口', () => {
    const store = account(['config:shop'])
    expect(settingsEntriesFor(store, '提现')).toEqual([])
    expect(settingsEntriesFor(store, '优惠券')[0].path).toBe('/shop/coupons')
    expect(settingsEntriesFor(store, '平台 指定商品')).toHaveLength(1)
    expect(settingsEntriesFor(store, '<script>')).toEqual([])
  })
  it('所有入口都有现存页面权限和合法分类，虚构资金开关不进入配置索引', () => {
    for (const item of SETTINGS_ENTRIES) {
      expect(item.path.startsWith('/')).toBe(true)
      expect(item.permission).toBeTruthy()
      expect(SETTINGS_GROUPS.some(group => group.key === item.group)).toBe(true)
    }
    expect(isSettingsEditor('/withdraw/audit')).toBe(false)
    expect(isSettingsEditor('/tenant/profile')).toBe(true)
    expect(SETTINGS_ENTRIES.some(item => item.path.includes('bank-card'))).toBe(false)
  })
  it('设置编辑页只有设置中心一个导航归属，旧地址也统一高亮设置中心', () => {
    const businessItems = [
      { title: '订单管理', path: '/shop/orders' },
      { title: 'ERP订单对接', path: '/tenant/erp' },
      { title: '余额与提现规则', path: '/withdraw/settings' },
    ]
    expect(withoutSettingsEditors(businessItems)).toEqual([{ title: '订单管理', path: '/shop/orders' }])
    expect(isSettingsContext('/settings')).toBe(true)
    expect(isSettingsContext('/tenant/erp')).toBe(true)
    expect(settingsAwareMenuPath('/tenant/erp')).toBe('/settings')
    expect(settingsAwareMenuPath('/withdraw/settings')).toBe('/settings')
    expect(settingsAwareMenuPath('/shop/orders')).toBe('/shop/orders')
    expect(settingsAwareMenuPath('/tenant/erp', 9)).toBe('/tenant/erp')
    expect(normalizeSettingsPath('/tenant/erp///')).toBe('/tenant/erp')
    expect(isSettingsContext('/settings/')).toBe(true)
    expect(isSettingsContext('/tenant/erp/')).toBe(true)
    expect(settingsAwareMenuPath('/withdraw/settings/')).toBe('/settings')
  })
  it('旧编辑地址与客服锚点正确定位，未知或无权限分类回退', () => {
    const entries = settingsEntriesFor(account(['config:shop']))
    expect(settingsGroupFor({ path:'/tenant/profile', hash:'#customer-service', query:{} }, entries)).toBe('service')
    expect(settingsGroupFor({ path:'/settings', query:{ group:'finance' } }, entries)).toBe('base')
    expect(settingsGroupFor({ path:'/settings', query:{ group:'appearance' } }, entries)).toBe('appearance')
    expect(settingsGroupFor({ path:'/tenant/profile/', hash:'#customer-service', query:{} }, entries)).toBe('service')
  })
})
describe('业务模式影响摘要', () => {
  it('只对实际业务字段生成逐项原值与新值，忽略完整租户的其他字段', () => {
    expect(businessModeChanges({ id:1, tenantName:'甲', flashSaleEnabled:0 }, { id:1, tenantName:'乙', flashSaleEnabled:1 })).toEqual([
      { key:'flashSaleEnabled', title:'秒杀专区', before:'关闭', after:'开启' },
    ])
  })
  it('兼容数字字符串并按渠道展示奖金规则', () => {
    expect(businessModeChanges({ flashSaleEnabled:0 }, { flashSaleEnabled:'0' })).toEqual([])
    expect(businessModeChanges({ repurchaseBonusMode:'NONE' }, { repurchaseBonusMode:'STANDARD' })[0].after).toBe('按渠道奖金规则')
  })
  it('优惠券开关进入变更确认摘要', () => {
    expect(businessModeChanges({ couponEnabled:1 }, { couponEnabled:0 })).toEqual([
      { key:'couponEnabled', title:'优惠券模块', before:'开启', after:'关闭' },
    ])
  })
})
