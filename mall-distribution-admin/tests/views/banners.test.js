import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const sourcePath = resolve(process.cwd(), 'src/views/shop/banners.vue')

describe('首页轮播图管理', () => {
  it('不再提供有效期和展示时间配置，并默认不跳转', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).not.toContain('label="有效期"')
    expect(source).not.toContain('label="展示时间"')
    expect(source).not.toContain('type="datetimerange"')
    expect(source).toContain("linkType: 'none'")
    expect(source).toContain('不跳转')
  })

  it('兼容历史接口返回的大写点击动作，并保留历史时间字段', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('normalizeLinkType')
    expect(source).toContain('linkType: normalizeLinkType(row.linkType)')
    expect(source).toContain('const payload = { ...form.value, linkType: normalizeLinkType(form.value.linkType) }')
    expect(source).toContain('delete payload.timeRange')
    expect(source).not.toContain('startTime: form.value.timeRange?.[0] || null')
  })

  it('明确提示模块已开但没有启用图片，并统一状态类型', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('activeBannerCount')
    expect(source).toContain('当前没有启用的图片')
    expect(source).toContain('status: Number(row.status ?? 0)')
    expect(source).toContain('Number(row.status) === 1')
  })
})
