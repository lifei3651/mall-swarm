import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const sourcePath = resolve(process.cwd(), 'src/views/shop/orders.vue')

describe('商城订单取消入口', () => {
  it('待付款和待发货订单都显示取消操作，待发货明确提示退款', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('[0, 1].includes(Number(row.order?.status))')
    expect(source).toContain("'取消并退款'")
    expect(source).toContain('系统会原路全额退款、关闭订单并恢复库存')
  })
})
