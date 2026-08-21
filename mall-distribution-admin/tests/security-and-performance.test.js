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

  it('代理和商户确认打款都提交管理员二次验证密码', async () => {
    const withdrawApi = await source('src/api/withdraw.js')
    const withdrawView = await source('src/views/withdraw/list.vue')
    const merchantFinance = await source('src/views/audit/merchant-finance.vue')

    expect(withdrawApi).toContain('data,')
    expect(withdrawApi).not.toContain('params: { payNo }')
    expect(withdrawView).toContain('adminPassword: payForm.value.adminPassword')
    expect(merchantFinance).toContain('v-model="payForm.adminPassword"')
  })
})
