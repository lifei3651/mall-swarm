import { beforeEach, describe, expect, it, vi } from 'vitest'
vi.unmock('element-plus')
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElMessageBox } from 'element-plus'
import Modes from '../../src/views/tenant/business-modes.vue'
const api = vi.hoisted(() => ({ listTenants:vi.fn(), saveTenant:vi.fn(), leaveGuard:vi.fn() }))
vi.mock('@/api/tenant', () => api)
vi.mock('@/composables/useUnsavedChanges', () => ({ useUnsavedChanges:api.leaveGuard }))
const row = () => ({ id:1, tenantName:'本地测试', promotionJoinMode:'MANUAL_REVIEW', flashSaleEnabled:0, flashSaleBonusMode:'NONE', repurchaseMallEnabled:0, repurchaseEligibilityMode:'PAID_MEMBER', repurchaseBonusMode:'NONE' })
const mounted = () => mount(Modes, { global:{ plugins:[ElementPlus] } })
beforeEach(() => {
  vi.restoreAllMocks(); vi.clearAllMocks()
  globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} }
  api.listTenants.mockResolvedValue({ data:{ list:[row()] } })
  api.saveTenant.mockResolvedValue({ data:{} })
})
describe('业务模式设置保存保护', () => {
  it('未修改不提交；修改后撤销恢复原值和离页保护状态', async () => {
    const w = mounted(); await flushPromises()
    await w.vm.save(); expect(api.saveTenant).not.toHaveBeenCalled()
    w.vm.form.flashSaleEnabled = 1
    expect(api.leaveGuard.mock.calls[0][0].value).toBe(true)
    w.vm.reset(); expect(w.vm.form.flashSaleEnabled).toBe(0)
    expect(api.leaveGuard.mock.calls[0][0].value).toBe(false); w.unmount()
  })
  it('取消影响确认不提交并保留草稿', async () => {
    const w = mounted(); await flushPromises(); w.vm.form.flashSaleEnabled = 1
    vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue('cancel')
    await w.vm.save()
    expect(api.saveTenant).not.toHaveBeenCalled(); expect(w.vm.changes).toHaveLength(1)
    expect(w.vm.saving).toBe(false); w.unmount()
  })
  it('确认绑定当时配置、禁止重复提交、保留租户其他字段', async () => {
    const w = mounted(); await flushPromises(); w.vm.form.flashSaleEnabled = 1
    let confirm; vi.spyOn(ElMessageBox, 'confirm').mockImplementation(() => new Promise(resolve => { confirm = resolve }))
    const pending = w.vm.save(); expect(w.vm.saving).toBe(true)
    w.vm.form.flashSaleEnabled = 0; await w.vm.save(); confirm(); await pending
    expect(api.saveTenant).toHaveBeenCalledTimes(1)
    expect(api.saveTenant).toHaveBeenCalledWith(expect.objectContaining({ id:1, tenantName:'本地测试', flashSaleEnabled:1 }))
    expect(w.vm.changes).toHaveLength(1); w.unmount()
  })
  it('保存失败保留草稿，允许重试；成功后清除未保存状态', async () => {
    const w = mounted(); await flushPromises(); w.vm.form.flashSaleEnabled = 1
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm')
    api.saveTenant.mockRejectedValueOnce(new Error('模拟服务端失败'))
    await expect(w.vm.save()).rejects.toThrow('模拟服务端失败')
    expect(w.vm.changes).toHaveLength(1); expect(w.vm.saving).toBe(false)
    await w.vm.save(); expect(w.vm.changes).toHaveLength(0); w.unmount()
  })
})
