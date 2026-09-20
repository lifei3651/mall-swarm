import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const sourcePath = resolve(process.cwd(), 'src/views/audit/orders.vue')

describe('订单奖金与利润追溯口径', () => {
  it('区分预计利润与已实现利润，不再把预估值直接称为公司利润', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('label="实付金额"')
    expect(source).toContain('label="利润状态"')
    expect(source).toContain('label="利润金额"')
    expect(source).toContain("row.profitStage === 'REALIZED'")
    expect(source).toContain("row.profitStageName || '预计利润'")
    expect(source).not.toContain('label="公司利润"')
    expect(source).not.toContain('label="支付金额"')
  })

  it('明确关闭、退款及售后订单只保留底层审计记录', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('关闭、取消、退款及已进入售后的订单保留审计记录，但不在本页展示')
  })
})
