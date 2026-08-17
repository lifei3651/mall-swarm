import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const sourcePath = resolve(process.cwd(), 'src/views/audit/merchant-finance.vue')

describe('商户货款提现', () => {
  it('驳回必须填写原因并明确先抵退款欠款', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain("inputValidator:(v)=>Boolean(v?.trim())||'必须填写原因'")
    expect(source).toContain('冻结金额已先抵退款欠款')
    expect(source).toContain('剩余部分退回可提现余额')
  })
})
