import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const sourcePath = resolve(process.cwd(), 'src/views/shop/merchants.vue')

describe('商户退出检查', () => {
  it('在确认已退出前展示后端检查结果并关闭全部经营能力', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('退出检查')
    expect(source).toContain('getMerchantExitReadiness')
    expect(source).toContain("status === 'EXITED'")
    expect(source).toContain("accountStatus = 'DISABLED'")
    expect(source).toContain("businessStatus = 'CLOSED'")
    expect(source).toContain("fulfillmentStatus = 'DISABLED'")
    expect(source).toContain('尚有未完成业务或未清资金')
  })
})
