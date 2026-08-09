import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const readSource = (path) => readFile(resolve(process.cwd(), path), 'utf8')

describe('商城协议与主体资料', () => {
  it('从后端读取统一模板并保留手工恢复入口', async () => {
    const source = await readSource('src/views/tenant/legal.vue')
    expect(source).toContain('getLegalTemplates')
    expect(source).toContain('恢复平台默认内容')
    expect(source).not.toContain('const defaultAgreement')
    expect(source).not.toContain('25%/30%/37%')
  })

  it('在商城资料维护协议所需的主体和第三方服务字段', async () => {
    const source = await readSource('src/views/tenant/profile.vue')
    expect(source).toContain('统一社会信用代码')
    expect(source).toContain('客服工作时间')
    expect(source).toContain('第三方服务清单')
    expect(source).toContain('normalizeCreditCode')
  })
})
