import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const sourcePath = resolve(process.cwd(), 'src/views/dashboard/index.vue')

describe('工作台订单待办', () => {
  it('待处理事项优先展示待发货和待售后并跳转到对应队列', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('getAdminOrderWorkSummary')
    expect(source.indexOf("title: '待发货'")).toBeLessThan(source.indexOf("title: '待售后'"))
    expect(source.indexOf("title: '待售后'")).toBeLessThan(source.indexOf("title: '待审核提现'"))
    expect(source).toContain('/shop/orders?orderState=PENDING_SHIPMENT')
    expect(source).toContain('/shop/orders?orderState=AFTER_SALE')
    expect(source).toContain("window.addEventListener('admin-order-work-summary'")
  })

  it('支持将经营看板导出为可下载报表', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('exportDashboard')
    expect(source).toContain('导出报表')
    expect(source).toContain('商城经营报表-')
    expect(source).toContain('URL.createObjectURL')
  })
})
