import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const sourcePath = resolve(process.cwd(), 'src/views/audit/merchant-finance.vue')

describe('商户货款提现', () => {
  it('驳回必须填写原因并明确先抵退款欠款', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain("inputValidator: (v) => Boolean(v?.trim()) || '必须填写原因'")
    expect(source).toContain('冻结金额先抵退款欠款')
    expect(source).toContain('剩余退回可提现余额')
  })

  it('商户工作台展示结算时间并将保证金与提现冻结分开', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('商户货款工作台')
    expect(source).toContain('row.settlementDelayDays')
    expect(source).toContain('row.eligibleTime')
    expect(source).toContain('row.depositFrozenAmount')
    expect(source).toContain("openDeposit(row, 'FREEZE')")
    expect(source).toContain("openDeposit(row, 'RECEIVE')")
    expect(source).toContain("openDeposit(row, 'RELEASE')")
    expect(source).toContain('operationNo: operationNo()')
    expect(source).toContain('requestNo: withdrawalRequestNo()')
    expect(source).toContain('bankAccountNoSnapshot')
    expect(source).toContain('depositShortfallAmount')
  })
})
