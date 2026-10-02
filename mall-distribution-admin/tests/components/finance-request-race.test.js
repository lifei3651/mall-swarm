import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import Finance from '../../src/views/audit/finance.vue'

const api = vi.hoisted(() => ({
  getFinanceSummary: vi.fn(), getFinanceDailySummary: vi.fn(), getCompanyShareSummary: vi.fn(),
  getRiskAlerts: vi.fn(), listRiskRules: vi.fn(), exportFinanceDailySummary: vi.fn(), saveRiskRule: vi.fn(),
}))
const chart = vi.hoisted(() => ({ init: vi.fn(), setOption: vi.fn(), clear: vi.fn(), dispose: vi.fn() }))
vi.mock('@/api/audit', () => api)
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
vi.mock('@/utils/echarts', () => ({ default: { init: chart.init } }))

const deferred = () => {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
let requests, wrapper
const start = () => (wrapper = mount(Finance, { global: {
  directives: { loading: () => {} },
  stubs: { ElRadioButton: true, ElRadioGroup: true, ElDatePicker: true, ElButton: true, ElTable: true, ElAlert: true, ElInputNumber: true, ElSwitch: true, ElTableColumn: true, ElTabs: { template: '<div><slot /></div>' }, ElTabPane: { template: '<div><slot /></div>' } },
} }))
beforeEach(() => {
  vi.clearAllMocks()
  requests = []
  api.getFinanceSummary.mockImplementation(params => {
    const pending = deferred()
    requests.push({ ...pending, params: { ...params } })
    return pending.promise
  })
  api.getFinanceDailySummary.mockImplementation(params => Promise.resolve({ data: [{ statDate: params.range, payAmount: 10 }] }))
  api.getCompanyShareSummary.mockImplementation(params => Promise.resolve({ data: [{ range: params.range }] }))
  api.getRiskAlerts.mockImplementation(params => Promise.resolve({ data: [{ range: params.range }] }))
  api.listRiskRules.mockResolvedValue({ data: [{ id: 1 }] })
  chart.init.mockReturnValue(chart)
})
afterEach(() => { wrapper?.unmount(); wrapper = undefined })

describe('财务筛选异步响应', () => {
  it('后发查询先返回时，旧汇总、明细、分账和预警不能覆盖新筛选', async () => {
    const w = start()
    w.vm.range = 'month'
    const latest = w.vm.fetchSummary()
    requests[1].resolve({ data: { orderCount: 20 } })
    await latest
    requests[0].resolve({ data: { orderCount: 1 } })
    await flushPromises()
    expect(w.vm.summary.orderCount).toBe(20)
    expect(w.vm.dailyRows[0].statDate).toBe('month')
    expect(w.vm.shareRows[0].range).toBe('month')
    expect(w.vm.riskAlerts[0].range).toBe('month')
    expect(chart.setOption).toHaveBeenCalledTimes(1)
    expect(chart.setOption.mock.calls[0][0].xAxis.data).toEqual(['month'])
  })

  it('旧请求先结束，最新查询仍显示加载中并使用请求时的自定义日期', async () => {
    const w = start()
    w.vm.range = 'custom'
    w.vm.customDates = ['2026-09-01', '2026-09-30']
    const latest = w.vm.fetchSummary()
    w.vm.customDates[0] = '2026-10-01'
    requests[0].resolve({ data: { orderCount: 1 } })
    await flushPromises()
    expect(w.vm.loading).toBe(true)
    expect(requests[1].params).toEqual({ range: 'custom', startDate: '2026-09-01', endDate: '2026-09-30' })
    requests[1].resolve({ data: { orderCount: 2 } })
    await latest
    expect(w.vm.loading).toBe(false)
  })

  it('切到未填写日期的自定义筛选会清空旧数据并使在途响应失效', async () => {
    const w = start()
    w.vm.range = 'custom'
    w.vm.handleRangeChange()
    requests[0].resolve({ data: { orderCount: 1 } })
    await flushPromises()
    expect(requests).toHaveLength(1)
    expect(w.vm.summary).toEqual({})
    expect(w.vm.dailyRows).toEqual([])
    expect(w.vm.loading).toBe(false)
    expect(chart.init).not.toHaveBeenCalled()
  })

  it('旧请求失败不终止新查询；最新请求失败后可重新刷新', async () => {
    const w = start()
    w.vm.range = 'month'
    const latest = w.vm.fetchSummary()
    requests[0].reject(new Error('old failed'))
    await flushPromises()
    expect(w.vm.loading).toBe(true)
    const failure = expect(latest).rejects.toThrow('latest failed')
    requests[1].reject(new Error('latest failed'))
    await failure
    expect(w.vm.loading).toBe(false)
    const retry = w.vm.fetchSummary()
    requests[2].resolve({ data: { orderCount: 3 } })
    await retry
    expect(w.vm.summary.orderCount).toBe(3)
  })

  it('离开页面后的响应不能再初始化图表或更新汇总', async () => {
    const w = start()
    const page = w.vm
    w.unmount()
    requests[0].resolve({ data: { orderCount: 8 } })
    await flushPromises()
    expect(page.summary).toEqual({})
    expect(chart.init).not.toHaveBeenCalled()
  })
})
