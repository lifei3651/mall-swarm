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

  it('覆盖独立装修能力并让颜色微调真正作用于预览', async () => {
    const source = await readFile(sourcePath, 'utf8')
    for (const label of ['品牌文化页', '首页轮播图', '首页版型', '直播广场', '新品速递', '首页模块', '分类模块', '底部导航', '颜色微调']) {
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
    expect(source).toContain('savingDisplay')
    expect(source).toContain('showTrustStrip: form.showTrustStrip')
    expect(source).toContain("value: 'campaign-feed'")
    expect(source).toContain('直播广场完整页面总开关')
    expect(source).toContain('独立页面默认独立开关')
    expect(source).toContain('新品完整页面总开关')
    expect(source).toContain('liveSquareEnabled: form.liveSquareEnabled')
    expect(source).toContain('newArrivalsEnabled: form.newArrivalsEnabled')
    expect(source).toContain('newArrivalWindowDays: form.newArrivalWindowDays')
    expect(source).toContain('新品完整页面总开关')
    expect(source).toContain('30～365天')
    expect(source).toContain("key: 'culture'")
    expect(source).toContain('brandCultureEnabled: form.brandCultureEnabled')
    expect(source).not.toContain('<span>品牌文化正文</span>')
    expect(source).not.toContain('type="textarea"')
    expect(source).toContain('品牌文化详情图')
    expect(source).toContain('建议 750×420px（约16:9），JPG/PNG/WebP，单张≤3MB')
    expect(source).toContain('建议宽750px，单张高度1000–3000px；JPG/PNG/WebP；单张≤5MB，合计≤30MB，最多10张')
    expect(source).toContain('beforeBrandCultureDetailUpload')
    expect(source).toContain('dropBrandCultureImage')
    expect(source).toContain('clearBrandCultureDetails')
    expect(source).toContain('brandCultureDetailImages: form.brandCultureDetailImages || []')
    expect(source).toContain('uploadBrandCultureCover')
    expect(source).toContain('class="mobile-preview-feature-row"')
    expect(source).toContain('previewFeatureModules')
    expect(source).toContain("previewFeatureOrder('live')")
    expect(source).toContain("previewFeatureOrder('newArrivals')")
    expect(source).toContain('直播与新品固定横排')
    expect(source).toContain('只提交后端实体字段')
    expect(source).toContain('saveDisplayConfig(payload, { silentError: true })')
    expect(source).toContain('商城视觉装修发布失败')
    expect(source).toContain('@click.stop.prevent="applyDisplayTheme(theme)"')
    expect(source).toContain('class="visual-design-field"')
    expect(source).not.toContain('<label><span>品牌 LOGO</span>')
    expect(source).toContain('extraConfigJson')
    expect(source).toContain('确认放弃未保存修改？')
    expect(source).toContain('moveNav')
    for (const section of ['品牌视觉', '品牌文化页', '首页轮播图', '首页版型', '直播广场', '新品速递', '首页模块', '分类模块', '底部导航', '颜色微调']) {
      expect(source).toContain(section)
    }
    expect(source).not.toContain('直播广场 / 新品速递')
    expect(source).not.toContain('active-text="开放" inactive-text="关闭"')
    expect(source).toContain('legacyDiscovery')
    expect(source).not.toContain("activeEditSection === 'service'")
    expect(source).toContain('displaySectionRows')
    expect(source).toContain('activeEditSection')
    expect(source).toContain('display-section-brand-only')
    expect(source).toContain('workbench-heading')
    expect(source).not.toContain('preview-page-tabs')
    expect(source).not.toContain('v-for="section in displaySections"')
    expect(source).toContain('主题色可选')
    expect(source).toContain('未选择时沿用系统默认主题')
    expect(source).toContain('保存发布即可生效')
  })

  it('品牌文化改用多图详情并保留旧文字作为不可编辑兜底', async () => {
    const source = await readFile(sourcePath, 'utf8')
    expect(source).toContain('uploadBrandCultureImage(displayForm.value.tenantId')
    expect(source).toContain("'cover'")
    expect(source).toContain("'detail'")
    expect(source).toContain('multiple')
    expect(source).toContain('详情图最多10张')
    expect(source).toContain('合计将超出30MB')
    expect(source).toContain('旧客户已有文字内容会继续作为兜底展示')
    expect(source).toContain('v-if="displayForm.brandCultureDetailImages?.length"')
    expect(source).toContain('v-else>{{ displayForm.brandCultureContent')
  })

  it('让服务保障开关同步控制右侧预览，并给搜索框保留品牌区间距', async () => {
    const source = await readFile(sourcePath, 'utf8')
    expect(source).toContain('setTrustEnabled')
    expect(source).toContain('Number(displayForm.showTrustStrip) === 1')
    expect(source).toContain('margin:10px 10px 8px')
  })

  it('工作台适配当前浏览器高度并为失效Logo提供可操作降级', async () => {
    const source = await readFile(sourcePath, 'utf8')
    expect(source).toContain('width="min(1120px, calc(100vw - 28px))"')
    expect(source).toContain('height: calc(100vh - 24px)')
    expect(source).toContain('height: 438px')
    expect(source).toContain('displayLogoLoadFailed')
    expect(source).toContain("displayForm.logoUrl ? '重传' : '上传'")
    expect(source).toContain('normalizeMediaUrl')
  })

  it('让首页轮播图总开关状态同步到装修模块列表', async () => {
    const source = await readFile(sourcePath, 'utf8')
    expect(source).toContain('bannerModuleVisible')
    expect(source).toContain("status: bannerModuleVisible ? '展示中' : '已隐藏'")
    expect(source).toContain('首页模块总开关已隐藏，图片不会在前台展示')
    expect(source).toContain('>编辑{{ row.label }}</el-button>')
    expect(source).not.toContain("row.key === 'banner' ? '进入管理'")
    expect(source.indexOf("key: 'category'")).toBeLessThan(source.indexOf("key: 'banner'"))
    expect(source).toContain('模块的“编辑首页轮播图”即可直接维护')
    expect(source).toContain('.category-list.category-list-draft {')
    expect(source).toContain('overflow: visible;')
    expect(source).not.toContain('max-height: 132px')
    expect(source).not.toContain('max-height: 180px')
  })

  it('支持查看和恢复商城客户配置历史版本', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('版本记录')
    expect(source).toContain('listTenantConfigVersions')
    expect(source).toContain('restoreTenantConfigVersion')
    expect(source).toContain('恢复前的当前配置会自动保存为历史版本')
    expect(source.indexOf('await saveTenant(tenantPayload')).toBeLessThan(source.indexOf('await saveDisplayConfig(payload'))
  })

  it('支持从客户资料待完善项直接打开品牌视觉编辑', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('useRoute')
    expect(source).toContain('route.query.editSection')
    expect(source).toContain('await openDisplayDialog(tableData.value[0], editSection)')
  })

  it('支持四种主版型、三种分类导购子版型、三层开关与底层保护', async () => {
    const source = await readFile(sourcePath, 'utf8')

    for (const label of ['标准零售版', '紧凑商品版', '活动信息流版', '分类导购版']) {
      expect(source).toContain(label)
    }
    for (const label of ['A 双栏目录导航', 'B 视觉品类橱窗', 'C 需求场景导购']) {
      expect(source).toContain(label)
    }
    for (const label of ['一级分类', '子分类', '热销商品', '大型视觉品类', '品类货架', '推荐商品', '购物场景', '分类快捷入口', '人气商品']) {
      expect(source).toContain(label)
    }
    expect(source).toContain("displayForm.layoutTemplate !== 'category-focus'")
    expect(source).toContain('父版型关闭时配置保留但不可操作')
    expect(source).toContain('直播广场总开关已关闭，保留当前首页开关值')
    expect(source).toContain('新品速递总开关已关闭，保留当前首页开关值')
    expect(source).toContain('isRequiredNav(nav.type)')
    expect(source).toContain('requiredCapabilities')
    expect(source).toContain('guide-preview-directory')
    expect(source).toContain('preview-guide-showcase')
    expect(source).toContain('preview-guide-scenarios')
  })

  it('不把核心交易与合规能力展示为装修配置项', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).not.toContain("activeEditSection === 'system'")
    expect(source).not.toContain("key: 'system'")
    expect(source).not.toContain('coreCapabilityRows')
    expect(source).not.toContain('系统必需 / 合规锁定')
    expect(source).not.toContain('后端强制锁定')
    expect(source).not.toContain('>系统必需</el-tag>')
    expect(source).not.toContain(':disabled="isRequiredNav(nav.type)"')
    expect(source).toContain('v-for="(item, index) in configurableBottomNav"')
    expect(source).toContain('核心交易与合规能力不受装修配置影响。')
    expect(source).toContain('<div class="mobile-preview-search"><span>⌕</span><span>搜索商品</span><b>⌕</b></div>')
    expect(source).toMatch(/\.guide-preview-directory\s*\{[^}]*align-content:start/)
    expect(source).toContain('requiredCapabilities')
  })
})
