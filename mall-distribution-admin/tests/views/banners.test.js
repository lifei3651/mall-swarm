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

  it('识别商城视觉里的轮播图总开关并解释两级开关关系', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('bannerModuleEnabled')
    expect(source).toContain('resolveBannerModuleEnabled')
    expect(source).toContain('首页轮播图总开关当前为“隐藏”')
    expect(source).toContain('getDisplayConfig(1)')
  })

  it('品牌文化横幅使用受控动作、专用上传和页面开关联动', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('label="品牌文化页" value="brand_culture"')
    expect(source).toContain("uploadBrandCultureImage(1, 'banner', file)")
    expect(source).toContain('750×320px')
    expect(source).toContain('单张≤3MB')
    expect(source).toContain('publishedBrandCultureEnabled')
    expect(source).toContain('品牌文化页当前已关闭')
    expect(source).toContain('Number(row.status) !== 1')
    expect(source).toContain("!['none', 'brand_culture'].includes(form.linkType)")
    expect(source).toContain("payload.linkType === 'brand_culture'")
    expect(source).not.toContain("linkValue = '/brand-culture'")
  })
})
