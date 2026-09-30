import { beforeEach, describe, expect, it, vi } from 'vitest'
vi.unmock('element-plus')
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElMessageBox } from 'element-plus'
import BonusConfig from '../../src/views/tenant/bonus-config.vue'
import { directReferralForm, directReferralPayload, directReferralValidation } from '../../src/utils/directReferralConfig.js'
import { normalizeProductBonusMode } from '../../src/utils/productBonusMode.js'

const api = vi.hoisted(() => ({ getDirectReferralConfig: vi.fn(), saveDirectReferralConfig: vi.fn(), leaveGuard: vi.fn(), push: vi.fn() }))
const auth = vi.hoisted(() => ({ merchantId: null, permissions: ['config:bonus', 'finance:manage', 'shop:product', 'config:shop'] }))
vi.mock('@/api/bonusConfig', () => api)
vi.mock('vue-router', () => ({ useRouter: () => ({ push: api.push }) }))
vi.mock('@/composables/useUnsavedChanges', () => ({ useUnsavedChanges: api.leaveGuard }))
vi.mock('@/store', () => ({ useAppStore: () => ({ userInfo: auth, hasPermission: permission => auth.permissions.includes(permission) }) }))

const row = (overrides = {}) => ({ enabled: false, commissionRate: 0, purchaseScope: 'ALL_ORDERS', settlementDelayDays: 7, versionId: 17, currentPolicyCode: 'CUSTOMER_BONUS_DISABLED', versionName: '安全关闭', readOnly: false, ...overrides })
const mounted = () => mount(BonusConfig, { global: { plugins: [ElementPlus] } })
beforeEach(() => {
  vi.restoreAllMocks(); vi.clearAllMocks()
  auth.merchantId = null; auth.permissions = ['config:bonus', 'finance:manage', 'shop:product', 'config:shop']
  globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} }
  api.getDirectReferralConfig.mockResolvedValue({ data: row() })
  api.saveDirectReferralConfig.mockImplementation(async payload => ({ data: row({ ...payload, versionId: 18, currentPolicyCode: 'DIRECT_REFERRAL_V1' }) }))
})

describe('基座直接推荐佣金设置', () => {
  it('初始关闭不提交，百分比精确转换并携带版本，首笔文案不把退款作为新资格', async () => {
    const w = mounted(); await flushPromises()
    expect(w.vm.form).toEqual({ enabled: false, commissionPercent: 0, purchaseScope: 'ALL_ORDERS', settlementDelayDays: 7 })
    await w.vm.save(); expect(api.saveDirectReferralConfig).not.toHaveBeenCalled()
    Object.assign(w.vm.form, { enabled: true, commissionPercent: 6.25, purchaseScope: 'FIRST_PAID_ORDER', settlementDelayDays: 14 })
    await flushPromises()
    expect(w.text()).toContain('退款、关闭或重开佣金规则均不会重置首笔资格')
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm')
    await w.vm.save()
    expect(api.saveDirectReferralConfig).toHaveBeenCalledWith({ enabled: true, commissionRate: 0.0625, purchaseScope: 'FIRST_PAID_ORDER', settlementDelayDays: 14, expectedVersionId: 17, confirmPolicySwitch: false })
    expect(w.vm.dirty).toBe(false)
    expect(w.text()).toContain('直接推荐佣金 · 已开启'); w.unmount()
  })

  it('历史定制程序不静默启用，取消替换保留草稿且确认后显式发送切换标记', async () => {
    api.getDirectReferralConfig.mockResolvedValue({ data: row({ currentPolicyCode: 'CUSTOM_BONUS', versionName: '客户程序', readOnly: true }) })
    const w = mounted(); await flushPromises()
    expect(w.find('.direct-form').exists()).toBe(false)
    expect(w.text()).toContain('客户独立程序')
    w.vm.switchRequested = true
    Object.assign(w.vm.form, { enabled: true, commissionPercent: 10 })
    const confirm = vi.spyOn(ElMessageBox, 'confirm').mockRejectedValueOnce('cancel').mockResolvedValueOnce('confirm')
    await w.vm.save()
    expect(api.saveDirectReferralConfig).not.toHaveBeenCalled(); expect(w.vm.dirty).toBe(true)
    await w.vm.save()
    expect(confirm.mock.calls[1][0]).toContain('将替换当前“客户程序”程序')
    expect(confirm.mock.calls[1][2].confirmButtonText).toBe('确认替换并保存')
    expect(api.saveDirectReferralConfig).toHaveBeenCalledWith(expect.objectContaining({ expectedVersionId: 17, confirmPolicySwitch: true }))
    w.unmount()
  })

  it('无财务权限只读，商户即使持有权限也不查询、不保存', async () => {
    auth.permissions = ['config:bonus']
    const w = mounted(); await flushPromises()
    expect(w.vm.canView).toBe(true); expect(w.vm.canEdit).toBe(false)
    w.vm.form.commissionPercent = 10; await w.vm.save()
    expect(api.saveDirectReferralConfig).not.toHaveBeenCalled(); w.unmount()
    auth.merchantId = 99
    api.getDirectReferralConfig.mockClear()
    const merchant = mounted(); await flushPromises()
    expect(api.getDirectReferralConfig).not.toHaveBeenCalled()
    expect(merchant.find('.direct-card').exists()).toBe(false); merchant.unmount()
  })

  it('读取失败不冒充已关闭，重试后才允许编辑，保存失败保留草稿和原版本', async () => {
    api.getDirectReferralConfig.mockRejectedValueOnce(new Error('读取失败'))
    const w = mounted(); await flushPromises()
    expect(w.vm.loaded).toBe(false); expect(w.text()).toContain('状态未核实')
    w.vm.form.commissionPercent = 10; await w.vm.save(); expect(api.saveDirectReferralConfig).not.toHaveBeenCalled()
    await w.vm.load(); Object.assign(w.vm.form, { enabled: true, commissionPercent: 10 })
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm')
    api.saveDirectReferralConfig.mockRejectedValueOnce(new Error('版本已变更'))
    await w.vm.save()
    expect(w.vm.form.commissionPercent).toBe(10); expect(w.vm.config.versionId).toBe(17)
    expect(w.vm.dirty).toBe(true); expect(w.text()).toContain('草稿已保留')
    w.vm.reset(); await w.vm.load(); expect(w.vm.dirty).toBe(false); w.unmount()
  })

  it('确认绑定当时草稿、防重复保存，保存后不覆盖迟到修改', async () => {
    const w = mounted(); await flushPromises(); Object.assign(w.vm.form, { enabled: true, commissionPercent: 10 })
    let confirm; vi.spyOn(ElMessageBox, 'confirm').mockImplementation(() => new Promise(resolve => { confirm = resolve }))
    const pending = w.vm.save(); w.vm.form.commissionPercent = 20; await w.vm.save(); confirm(); await pending
    expect(api.saveDirectReferralConfig).toHaveBeenCalledTimes(1)
    expect(api.saveDirectReferralConfig.mock.calls[0][0].commissionRate).toBe(0.1)
    expect(w.vm.form.commissionPercent).toBe(20); expect(w.vm.snapshot.commissionPercent).toBe(10)
    expect(w.vm.dirty).toBe(true); w.unmount()
  })

  it('非法比例、保护期和未知范围不提交，关闭允许零比例', () => {
    expect(directReferralValidation(directReferralForm())).toBe('')
    for (const commissionPercent of [null, '', -1, 100.01, 1.001, NaN]) {
      expect(directReferralValidation({ ...directReferralForm(), commissionPercent })).not.toBe('')
    }
    expect(directReferralValidation({ ...directReferralForm(), enabled: true })).toContain('大于 0')
    for (const settlementDelayDays of [null, '', -1, 366, 1.5]) expect(directReferralValidation({ ...directReferralForm(), settlementDelayDays })).not.toBe('')
    expect(directReferralValidation({ ...directReferralForm(), purchaseScope: 'UNKNOWN' })).not.toBe('')
    expect(directReferralPayload({ ...directReferralForm(), commissionPercent: 0.01 }, {})).toEqual(expect.objectContaining({ commissionRate: 0.0001, expectedVersionId: null }))
  })

  it('编辑和保存自营商品保留不计佣及历史指定值，商户商品仍不计佣', () => {
    expect(normalizeProductBonusMode(null, 'NONE')).toBe('NONE')
    expect(normalizeProductBonusMode(null, 'STANDARD')).toBe('STANDARD')
    expect(normalizeProductBonusMode(null, undefined)).toBe('INHERIT')
    expect(normalizeProductBonusMode(1, 'INHERIT')).toBe('NONE')
  })
})
