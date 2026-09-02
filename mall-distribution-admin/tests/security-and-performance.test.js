import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const source = (path) => readFile(resolve(process.cwd(), path), 'utf8')

describe('后台安全与只读查询', () => {
  it('佣金记录使用GET查询，不再产生写操作日志', async () => {
    const api = await source('src/api/commission.js')
    expect(api).toMatch(/url: '\/distribution\/commission\/records',[\s\S]*method: 'get',[\s\S]*params/)
  })

  it('后台浏览器会话使用HttpOnly Cookie标识而非持久化Bearer token', async () => {
    const request = await source('src/utils/request.js')
    const store = await source('src/store/index.js')
    expect(request).toContain("config.headers['X-Admin-Client'] = 'admin-web'")
    expect(store).toContain("localStorage.setItem('admin_session_present', '1')")
    expect(store).toContain("localStorage.removeItem('token')")
    expect(request).not.toContain("config.headers['Authorization']")
    expect(store).not.toContain("localStorage.setItem('permissions'")
    expect(store).not.toContain("localStorage.setItem('userInfo'")
  })

  it('ECharts只在图表页面实际渲染时异步加载', async () => {
    for (const path of ['src/views/dashboard/index.vue', 'src/views/performance/overview.vue', 'src/views/audit/finance.vue']) {
      const view = await source(path)
      expect(view).toContain("await import('@/utils/echarts')")
      expect(view).not.toContain("import echarts from '@/utils/echarts'")
    }
  })

  it('会员提现只使用官方渠道核对，商户人工打款仍使用准确业务确认且不重复提交登录密码', async () => {
    const withdrawApi = await source('src/api/withdraw.js')
    const withdrawView = await source('src/views/withdraw/list.vue')
    const merchantFinance = await source('src/views/audit/merchant-finance.vue')

    expect(withdrawApi).toContain('/payout/start')
    expect(withdrawApi).toContain('/payout/reconcile')
    expect(withdrawApi).not.toContain('confirm-pay')
    expect(withdrawApi).not.toContain('payNo')
    expect(withdrawView).toContain('渠道受理不等于成功，系统只在官方结果核对通过后记为打款成功')
    expect(withdrawView).not.toContain('payForm')
    expect(merchantFinance).toContain('确认银行已经实际打款 ¥${money(payForm.value.actualPaidAmount)}')
    expect(merchantFinance).not.toContain('payForm.adminPassword')
  })

  it('提现列表默认脱敏，完整收款资料只通过财务处理详情接口读取', async () => {
    const list = await source('src/views/withdraw/list.vue')
    const audit = await source('src/views/withdraw/audit.vue')
    expect(list).toContain("store.hasPermission('finance:manage')")
    expect(list).toContain('await getWithdrawById(row.id)')
    expect(audit).toContain('await getWithdrawById(row.id)')
  })

  it('订单实时通道遇到认证或权限错误时停止无效重连', async () => {
    const realtime = await source('src/utils/orderRealtime.js')
    expect(realtime).toMatch(/\[400, 401, 403, 404\]\.includes\(response\.status\)\) return/)
    expect(realtime).toContain('retry = Math.min(retry * 2, 30000)')
  })
})
