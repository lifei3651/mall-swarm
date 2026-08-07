import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const sourcePath = resolve(process.cwd(), 'src/views/tenant/list.vue')
const layoutPath = resolve(process.cwd(), 'src/components/Layout.vue')

describe('商城视觉与页面工作台', () => {
  it('提供四个真实页面预览入口，而不是占位提示', async () => {
    const source = await readFile(sourcePath, 'utf8')
    for (const page of ['home', 'category', 'cart', 'profile']) {
      expect(source).toContain(`{ value: '${page}'`)
    }
    expect(source).toContain('previewCartTotal')
    expect(source).toContain('mobile-preview-profile-card')
    expect(source).not.toContain('下一阶段')
    expect(source).not.toContain('这里会接入对应的真实前台页面')
    const layout = await readFile(layoutPath, 'utf8')
    expect(layout).not.toContain("title: '首页Banner'")
  })
})
