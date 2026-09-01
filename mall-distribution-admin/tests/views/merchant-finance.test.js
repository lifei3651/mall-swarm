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

  it('覆盖付款异常、撤回、风控冻结和账本对账闭环', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('PAYMENT_PROCESSING')
    expect(source).toContain('PAYMENT_FAILED')
    expect(source).toContain('RISK_FROZEN')
    expect(source).toContain('cancelMerchantWithdrawal')
    expect(source).toContain('startMerchantWithdrawalPayment')
    expect(source).toContain('failMerchantWithdrawalPayment')
    expect(source).toContain('completeMerchantWithdrawal')
    expect(source).toContain('账本对账')
    expect(source).toContain('merchantCanManageFunds')
    expect(source).toContain('账户减总账差额')
  })

  it('确认实际打款展示金额和流水号，并用最终业务确认代替重复密码', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).not.toContain('payForm.adminPassword')
    expect(source).toContain('确认银行已经实际打款 ¥${money(payForm.value.actualPaidAmount)}')
    expect(source).toContain('payForm.value.paymentReference.trim()')
    expect(source).toContain("confirmButtonText: '确认已打款'")
    expect(source).toContain("cancelButtonText: '返回核对'")
    expect(source).toContain('runAction(`pay-${current.value.id}`')
  })

  it('商户货款敏感操作统一阻止重复提交', async () => {
    const source = await readFile(sourcePath, 'utf8')
    expect(source).toContain('const actionBusyKey = ref(\'\')')
    expect(source).toContain('if (actionBusyKey.value) return')
    expect(source).toContain(':disabled="Boolean(actionBusyKey)"')
  })
})
