import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const sourcePath = resolve(process.cwd(), 'src/components/Layout.vue')

describe('后台订单待办提醒', () => {
  it('侧边父菜单不显示汇总数字，但继续向订单页和工作台同步待办数量', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('getAdminOrderWorkSummary')
    expect(source).not.toContain('orderWorkTotal')
    expect(source).not.toContain('menu-work-badge')
    expect(source).toContain('window.setInterval(loadOrderWorkSummary, 30000)')
    expect(source).toContain("new CustomEvent('admin-order-work-summary'")
  })
})
