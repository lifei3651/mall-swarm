import { beforeEach, describe, expect, it, vi } from 'vitest'
vi.unmock('element-plus')
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import Audit from '../../src/views/withdraw/audit.vue'
const api = vi.hoisted(() => ({ auditWithdraw:vi.fn(), getPendingAuditWithdraws:vi.fn(), getWithdrawById:vi.fn() }))
vi.mock('@/api/withdraw', () => api)
beforeEach(() => { vi.clearAllMocks(); globalThis.ResizeObserver = class { observe(){} unobserve(){} disconnect(){} }; api.getPendingAuditWithdraws.mockResolvedValue({data:{list:[]}}) })
describe('提现列表与详情展示', () => {
  it('中文空状态，加载失败明确提示且能够重试', async () => {
    api.getPendingAuditWithdraws.mockRejectedValueOnce(new Error('本地模拟读取失败'))
    const w = mount(Audit,{global:{plugins:[ElementPlus]}}); await flushPromises()
    expect(w.text()).toContain('提现列表读取失败')
    await w.vm.fetchData(); expect(w.vm.loadError).toBe(false)
    expect(w.text()).toContain('暂无待审核的提现申请'); expect(api.auditWithdraw).not.toHaveBeenCalled(); w.unmount()
  })
  it('收款资料在详情完整保留，查看不触发审核', async () => {
    const row = { id:9, withdrawNo:'DEMO9', withdrawAmount:25, bankName:'演示渠道', bankAccount:'仅本地演示' }
    api.getWithdrawById.mockResolvedValue({data:row})
    const w = mount(Audit,{global:{plugins:[ElementPlus]}}); await flushPromises(); await w.vm.handleDetail(row)
    expect(w.vm.detailVisible).toBe(true); expect(w.vm.detail.bankAccount).toBe('仅本地演示')
    expect(api.getWithdrawById).toHaveBeenCalledWith(9); expect(api.auditWithdraw).not.toHaveBeenCalled(); w.unmount()
  })
})
