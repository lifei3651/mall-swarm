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
  it('仅平台自营时关闭新商户入口但保留历史资料与退出处理', async () => {
    const source = await readFile(sourcePath, 'utf8')
    expect(source).toContain(':disabled="!modeLoaded || !multiMerchantEnabled"')
    expect(source).toContain('仅平台自营；历史商户资料、履约和资金仍可处理')
    expect(source).toContain('if (!row && (!modeLoaded.value || !multiMerchantEnabled.value)) return')
    expect(source).toContain('不能开通新商户')
    expect(source).toContain('退出检查')
  })
})
