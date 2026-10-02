import { beforeEach, describe, expect, it, vi } from 'vitest'
vi.unmock('element-plus')
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElMessageBox } from 'element-plus'
import Modes from '../../src/views/tenant/business-modes.vue'
const api = vi.hoisted(() => ({ getTenantBusinessModes:vi.fn(), saveTenantBusinessModes:vi.fn(), leaveGuard:vi.fn() }))
const permissionState = vi.hoisted(() => ({ canShop:true }))
vi.mock('@/api/tenant', () => api)
vi.mock('@/composables/useUnsavedChanges', () => ({ useUnsavedChanges:api.leaveGuard }))
vi.mock('@/store', () => ({ useAppStore:() => ({ hasPermission:(permission) => permission !== 'config:shop' || permissionState.canShop }) }))
const row = () => ({ id:1, invitationEnabled:1, balanceTransactionsEnabled:1, multiMerchantEnabled:1, promotionJoinMode:'MANUAL_REVIEW', flashSaleEnabled:0, flashSaleBonusMode:'NONE', repurchaseMallEnabled:0, repurchaseEligibilityMode:'PAID_MEMBER', repurchaseBonusMode:'NONE' })
const mounted = () => mount(Modes, { global:{ plugins:[ElementPlus] } })
beforeEach(() => {
  vi.restoreAllMocks(); vi.clearAllMocks()
  permissionState.canShop = true
  globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} }
  api.getTenantBusinessModes.mockResolvedValue({ data:row() })
  api.saveTenantBusinessModes.mockResolvedValue({ data:{} })
})
describe('业务模式设置保存保护', () => {
  it('仅两种新模式，代理草稿无默认金额或资格；保存回读版本，撤销恢复原配置', async () => {
    const r = { ...row(), businessMode: 'NORMAL', modeRevision: 'v1', modeChangeAllowed: true }
    api.getTenantBusinessModes.mockResolvedValue({ data: r })
    const w = mounted(); await flushPromises()
    expect(w.text()).toContain('普通商城')
    expect(w.text()).toContain('直销／代理模式')
    expect(w.text()).not.toContain('受邀即开通')
    w.vm.form.businessMode = 'AGENCY'; await flushPromises()
    expect(w.text()).toContain('填满也不会启用')
    expect(w.vm.form.agencyRuleDraft).toBeUndefined()
    w.vm.form.agencyRuleDraft = { attributionRule: '客户草稿' }
    const confirm = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm')
    api.saveTenantBusinessModes.mockResolvedValue({ data: { ...r, businessMode: 'AGENCY', modeRevision: 'v2', agencyRuleDraft: { attributionRule: '客户草稿' }, agencyConfigStatus: { state: 'INCOMPLETE', missingItems: ['购买门槛种类'] } } })
    await w.vm.save(); await flushPromises()
    expect(confirm.mock.calls[0][0]).toContain('经营模式：普通商城 → 直销／代理模式')
    expect(api.saveTenantBusinessModes).toHaveBeenCalledWith(1, expect.objectContaining({ expectedModeRevision: 'v1' }))
    expect(w.vm.form.modeRevision).toBe('v2'); expect(w.vm.changes).toHaveLength(0)
    expect(w.text()).toContain('购买门槛种类')
    w.vm.form.businessMode = 'NORMAL'; w.vm.reset(); expect(w.vm.form.businessMode).toBe('AGENCY')
    w.unmount()
  })
  it('存量有业务时不允许直接切换；缺商城设置权限只能看规则草稿', async () => {
    api.getTenantBusinessModes.mockResolvedValue({ data: { ...row(), modeChangeAllowed: false } })
    permissionState.canShop = false
    const w = mounted(); await flushPromises()
    expect(w.text()).toContain('不能直接切换模式')
    const modeGroup = w.findComponent({ name: 'CustomerModeSettings' }).findComponent({ name: 'ElRadioGroup' })
    expect(modeGroup.props('disabled')).toBe(true)
    w.unmount()
  })
  it('普通商城与邀请模式清晰区分，切换邀请不改推广资格规则', async () => {
    const w = mounted(); await flushPromises()
    expect(w.text()).toContain('邀请开启（邀请码选填）')
    expect(w.text()).toContain('已注册购物账号可邀请他人')
    expect(w.text()).toContain('推广资格开通')
    w.vm.form.invitationEnabled = 0; await flushPromises()
    expect(w.text()).toContain('普通商城（无邀请）')
    expect(w.text()).toContain('历史关系、订单和账务保留')
    expect(w.vm.form.promotionJoinMode).toBe('MANUAL_REVIEW')
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm')
    await w.vm.save()
    expect(api.saveTenantBusinessModes).toHaveBeenCalledWith(1,
      expect.objectContaining({ invitationEnabled: 0, promotionJoinMode: 'MANUAL_REVIEW' }))
    w.unmount()
  })
  it('未修改不提交；修改后撤销恢复原值和离页保护状态', async () => {
    const w = mounted(); await flushPromises()
    await w.vm.save(); expect(api.saveTenantBusinessModes).not.toHaveBeenCalled()
    w.vm.form.flashSaleEnabled = 1
    expect(api.leaveGuard.mock.calls[0][0].value).toBe(true)
    w.vm.reset(); expect(w.vm.form.flashSaleEnabled).toBe(0)
    expect(api.leaveGuard.mock.calls[0][0].value).toBe(false); w.unmount()
  })
  it('取消影响确认不提交并保留草稿', async () => {
    const w = mounted(); await flushPromises(); w.vm.form.flashSaleEnabled = 1
    vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue('cancel')
    await w.vm.save()
    expect(api.saveTenantBusinessModes).not.toHaveBeenCalled(); expect(w.vm.changes).toHaveLength(1)
    expect(w.vm.saving).toBe(false); w.unmount()
  })
  it('确认绑定当时配置、禁止重复提交，且只提交专用业务模式合同', async () => {
    const w = mounted(); await flushPromises(); w.vm.form.flashSaleEnabled = 1
    let confirm; vi.spyOn(ElMessageBox, 'confirm').mockImplementation(() => new Promise(resolve => { confirm = resolve }))
    const pending = w.vm.save(); expect(w.vm.saving).toBe(true)
    w.vm.form.flashSaleEnabled = 0; await w.vm.save(); confirm(); await pending
    expect(api.saveTenantBusinessModes).toHaveBeenCalledTimes(1)
    expect(api.saveTenantBusinessModes).toHaveBeenCalledWith(1, expect.objectContaining({ id:1, flashSaleEnabled:1 }))
    expect(api.saveTenantBusinessModes.mock.calls[0][1]).not.toHaveProperty('tenantName')
    expect(w.vm.changes).toHaveLength(1); w.unmount()
  })
  it('保存失败保留草稿，允许重试；成功后清除未保存状态', async () => {
    const w = mounted(); await flushPromises(); w.vm.form.flashSaleEnabled = 1
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm')
    api.saveTenantBusinessModes.mockRejectedValueOnce(new Error('模拟服务端失败'))
    await expect(w.vm.save()).rejects.toThrow('模拟服务端失败')
    expect(w.vm.changes).toHaveLength(1); expect(w.vm.saving).toBe(false)
    await w.vm.save(); expect(w.vm.changes).toHaveLength(0); w.unmount()
  })
  it('关闭新余额交易和多商户时同时展示影响并提交独立开关', async () => {
    const w = mounted(); await flushPromises()
    w.vm.form.balanceTransactionsEnabled = 0
    w.vm.form.multiMerchantEnabled = 0
    expect(w.vm.changes.map(change => change.key)).toEqual(['balanceTransactionsEnabled', 'multiMerchantEnabled'])
    const confirm = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm')
    await w.vm.save()
    expect(confirm.mock.calls[0][0]).toContain('余额新交易：开启 → 关闭')
    expect(confirm.mock.calls[0][0]).toContain('多商户新业务：开启 → 关闭')
    expect(api.saveTenantBusinessModes).toHaveBeenCalledWith(1, expect.objectContaining({ balanceTransactionsEnabled:0, multiMerchantEnabled:0 }))
    w.unmount()
  })
  it('默认主账号独立配置，保存列出主账号及开关而不更改推广资格', async () => {
    const w=mounted(); await flushPromises()
    w.vm.form.defaultInviterEnabled=1; w.vm.form.defaultInviterCode='MASTER01'; await flushPromises()
    expect(w.text()).toContain('主账号邀请码')
    expect(w.vm.form.promotionJoinMode).toBe('MANUAL_REVIEW')
    const confirm=vi.spyOn(ElMessageBox,'confirm').mockResolvedValue('confirm')
    await w.vm.save()
    expect(confirm.mock.calls[0][0]).toContain('默认绑定主账号：关闭 → 开启')
    expect(confirm.mock.calls[0][0]).toContain('MASTER01')
    expect(api.saveTenantBusinessModes).toHaveBeenCalledWith(1,expect.objectContaining({defaultInviterEnabled:1,defaultInviterCode:'MASTER01'}))
    w.unmount()
  })
  it('无商城设置权限时邀请开关只读，避免可点后必然被接口拒绝', async () => {
    permissionState.canShop = false
    const w = mounted(); await flushPromises()
    expect(w.find('[aria-label="启用邀请关系，注册无需邀请码"]').attributes('aria-disabled')).toBe('true')
    expect(w.text()).toContain('调整邀请功能需要商城设置权限')
    w.unmount()
  })
})
