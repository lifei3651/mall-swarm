import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const sourcePath = resolve(process.cwd(), 'src/views/tenant/list.vue')
const layoutPath = resolve(process.cwd(), 'src/components/Layout.vue')

describe('商城视觉与页面工作台', () => {
  it('仅提供首页预览入口，分类、我的和购物车不作为装修模板', async () => {
    const source = await readFile(sourcePath, 'utf8')
    expect(source).toContain("{ value: 'home'")
    expect(source).not.toContain("{ value: 'category'")
    expect(source).not.toContain("{ value: 'profile'")
    expect(source).not.toContain("{ value: 'cart'")
    expect(source).not.toContain("previewPage === 'cart'")
    expect(source).not.toContain('下一阶段')
    expect(source).not.toContain('这里会接入对应的真实前台页面')
    const layout = await readFile(layoutPath, 'utf8')
    expect(layout).not.toContain("title: '首页Banner'")
  })

  it('覆盖六项装修能力并让颜色微调真正作用于预览', async () => {
    const source = await readFile(sourcePath, 'utf8')
    for (const label of ['首页轮播图', '首页模块', '分类模块', '底部导航', '颜色微调']) {
      expect(source).toContain(label)
    }
    expect(source).toContain("if (section === 'banner')")
    expect(source).toContain('bannerDialogVisible.value = true')
    expect(source).not.toContain('openBannerDialog')
    expect(source).toContain('draggable="true"')
    expect(source).toContain('setCategoryDraft')
    expect(source).toContain('resetColors')
    for (const variable of ['--preview-header-bg', '--preview-price', '--preview-accent', '--preview-line', '--preview-button']) {
      expect(source).toContain(variable)
    }
    expect(source).toContain('保存发布')
    expect(source).toContain('extraConfigJson')
    expect(source).toContain('确认放弃未保存修改？')
    expect(source).toContain('moveNav')
    for (const section of ['品牌视觉', '首页轮播图', '首页模块', '分类模块', '服务说明', '底部导航', '颜色微调']) {
      expect(source).toContain(section)
    }
    expect(source).toContain('displaySectionRows')
    expect(source).toContain('activeEditSection')
    expect(source).toContain('display-section-brand-only')
    expect(source).not.toContain('v-for="section in displaySections"')
    expect(source).toContain('主题色可选')
    expect(source).toContain('未选择时使用当前默认主题')
  })

  it('让服务保障开关同步控制右侧预览，并给搜索框保留品牌区间距', async () => {
    const source = await readFile(sourcePath, 'utf8')
    expect(source).toContain('setTrustEnabled')
    expect(source).toContain('Number(displayForm.showTrustStrip) === 1')
    expect(source).toContain('margin:14px 12px 10px')
  })
})
