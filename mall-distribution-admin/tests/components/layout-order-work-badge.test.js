import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const sourcePath = resolve(process.cwd(), 'src/components/Layout.vue')

describe('后台订单待办提醒', () => {
  it('侧边菜单汇总待发货和售后数量并定时刷新', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('getAdminOrderWorkSummary')
    expect(source).toContain('orderWorkSummary.pendingShipment + orderWorkSummary.afterSale')
    expect(source).toContain('menu-work-badge')
    expect(source).toContain('window.setInterval(loadOrderWorkSummary, 30000)')
    expect(source).toContain("new CustomEvent('admin-order-work-summary'")
  })
})
