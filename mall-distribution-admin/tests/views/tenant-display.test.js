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

  it('覆盖六项装修能力并让颜色微调真正作用于预览', async () => {
    const source = await readFile(sourcePath, 'utf8')
    for (const label of ['首页 Banner', '首页模块', '分类模块', '底部导航', '颜色微调']) {
      expect(source).toContain(label)
    }
    expect(source).toContain('openBannerDialog')
    expect(source).toContain('draggable="true"')
    expect(source).toContain('setCategoryDraft')
    expect(source).toContain('resetColors')
    for (const variable of ['--preview-header-bg', '--preview-price', '--preview-accent', '--preview-line', '--preview-button']) {
      expect(source).toContain(variable)
    }
    expect(source).toContain('保存发布')
    expect(source).toContain('extraConfigJson')
  })
})
