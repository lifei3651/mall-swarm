import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { resolveDirectoryGuideLayout } from '../../src/utils/categoryGuideLayout.js'
import { normalizeBottomNav } from '../../src/utils/bottomNav.js'
import {
  DISPLAY_COLOR_KEYS,
  SHOP_THEME_OPTIONS,
  applyThemePresetToForm,
  hydrateThemeColors,
  isThemePresetActive,
  themePreviewVariables,
} from '../../src/utils/shopTheme.js'

const sourcePath = resolve(process.cwd(), 'src/views/tenant/list.vue')
const layoutPath = resolve(process.cwd(), 'src/components/Layout.vue')

describe('商城视觉与页面工作台', () => {
  it('四套主题点击后同步主色和颜色细节，保存重载后保持一致', () => {
    for (const theme of SHOP_THEME_OPTIONS) {
      const form = {
        productTemplate: 'retail-red',
        themeColor: '#123456',
        colors: Object.fromEntries(DISPLAY_COLOR_KEYS.map((key) => [key, '#1556A3'])),
      }
      applyThemePresetToForm(form, theme)
      const preview = themePreviewVariables(form)
      const savedExtra = JSON.parse(JSON.stringify({ colors: form.colors }))
      const reloadedColors = hydrateThemeColors(theme, form.themeColor, savedExtra.colors)

      expect(preview['--preview-color'].toLowerCase()).toBe(theme.color.toLowerCase())
      expect(preview['--preview-accent'].toLowerCase()).toBe(theme.color.toLowerCase())
      expect(preview['--preview-button'].toLowerCase()).toBe(theme.color.toLowerCase())
      expect(reloadedColors).toEqual(form.colors)
      expect(isThemePresetActive({ ...form, colors: reloadedColors }, theme)).toBe(true)
    }
    const retailRed = SHOP_THEME_OPTIONS.find((theme) => theme.value === 'retail-red')
    const form = applyThemePresetToForm({ colors: {} }, retailRed)
    expect(JSON.stringify(form).toLowerCase()).not.toContain('#1556a3')
  })

  it('自定义主题稳定保留，旧分类蓝色指纹仅在完整匹配时确定迁移', () => {
    const retailRed = SHOP_THEME_OPTIONS.find((theme) => theme.value === 'retail-red')
    const custom = {
      priceColor: '#aa2200', pageBg: '#fffaf5', headerBg: '#ffffff', cardBg: '#fffdfb',
      textColor: '#222222', mutedColor: '#777777', accentColor: '#123456',
      lineColor: '#eeeeee', buttonBg: '#654321',
    }
    expect(hydrateThemeColors(retailRed, '#345678', custom)).toEqual(custom)
    expect(themePreviewVariables({ themeColor: '#345678', colors: custom })['--preview-color']).toBe('#345678')

    const legacyCategoryBlue = {
      priceColor: '#E5484D', pageBg: '#F6F7F9', headerBg: '#FFFFFF', cardBg: '#FFFFFF',
      textColor: '#1B2430', mutedColor: '#6B7280', accentColor: '#1556A3',
      lineColor: '#E8ECF1', buttonBg: '#1556A3',
    }
    const migrated = hydrateThemeColors(retailRed, '#e7193f', legacyCategoryBlue)
    expect(migrated.accentColor).toBe('#e7193f')
    expect(migrated.buttonBg).toBe('#e7193f')
    const missingFields = hydrateThemeColors(retailRed, '#e7193f', {})
    expect(DISPLAY_COLOR_KEYS.every((key) => Boolean(missingFields[key]))).toBe(true)
  })

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

  it('外层只保留一张当前装修卡、一个主入口和版本记录', async () => {
    const source = await readFile(sourcePath, 'utf8')
    const outer = source.slice(0, source.indexOf('<el-dialog v-model="displayDialogVisible"'))
    expect(outer).toContain('class="current-decoration-card"')
    expect(outer).toContain('当前商城装修')
    expect(outer.match(/进入装修工作台/g)).toHaveLength(1)
    expect(outer.match(/版本记录/g)).toHaveLength(1)
    expect(outer).not.toContain('<el-table')
    expect(outer).not.toContain('装修模块')
    expect(outer).not.toContain('编辑商城视觉')
    expect(source).not.toContain('displaySectionRows')
    expect(source).toContain('currentLayoutSummary')
    expect(source).toContain('current-decoration-preview')
  })

  it('按整体版型、品牌、首页、独立页面和底部导航组织单一工作台', async () => {
    const source = await readFile(sourcePath, 'utf8')
    for (const label of ['整体版型', '品牌与主题', '首页模块', '独立页面', '底部导航']) expect(source).toContain(label)
    expect(source).toContain("const activeEditSection = ref('layout')")
    expect(source).toContain('v-for="(group, index) in workbenchGroups"')
    expect(source).toContain('@click="activeEditSection = group.key"')
    expect(source).toContain("v-if=\"activeEditSection === 'brand'\"")
    expect(source.match(/<span>品牌 LOGO<\/span>/g)).toHaveLength(1)
    expect(source).toContain("activeEditSection === 'pages' && independentPageTab === 'culture'")
    expect(source).toContain("activeEditSection === 'pages' && independentPageTab === 'live'")
    expect(source).toContain("activeEditSection === 'pages' && independentPageTab === 'newArrivals'")
    expect(source).toContain('管理轮播图片')
    expect(source).toContain('home-category-settings')
    expect(source).toContain('home-template-modules')
    expect(source).toContain("culture: 'pages', live: 'pages', newArrivals: 'pages', banner: 'home', category: 'home', colors: 'brand'")
    expect(source).toContain('确认放弃未保存修改？')
    expect(source).toContain('watch(displayForm')
  })

  it('保留全部装修能力并让颜色细节真正作用于预览和原有发布载荷', async () => {
    const source = await readFile(sourcePath, 'utf8')
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
    expect(source).toContain('建议750×320px、JPG/PNG/WebP、单张≤3MB')
    expect(source).toContain('建议宽750px，单张高度1000–3000px；JPG/PNG/WebP；单张≤5MB，合计≤30MB，最多10张')
    expect(source).toContain('beforeBrandCultureDetailUpload')
    expect(source).toContain('dropBrandCultureImage')
    expect(source).toContain('clearBrandCultureDetails')
    expect(source).toContain('brandCultureDetailImages: form.brandCultureDetailImages || []')
    expect(source).not.toContain('uploadBrandCultureCover')
    expect(source).not.toContain('页面封面</span>')
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
    expect(source).toContain('normalizeBottomNav(form.bottomNav)')
    expect(source).toContain('bottomNavIndependent: 1')
    for (const section of ['品牌视觉', '品牌文化页', '直播广场', '新品速递', '首页模块', '首页分类内容', '底部导航', '颜色细节']) {
      expect(source).toContain(section)
    }
    expect(source).not.toContain('直播广场 / 新品速递')
    expect(source).not.toContain('active-text="开放" inactive-text="关闭"')
    expect(source).toContain('legacyDiscovery')
    expect(source).not.toContain("activeEditSection === 'service'")
    expect(source).toContain('activeEditSection')
    expect(source).toContain('display-section-brand-only')
    expect(source).toContain('workbench-heading')
    expect(source).not.toContain('preview-page-tabs')
    expect(source).not.toContain('v-for="section in displaySections"')
    expect(source).toContain('先选择整体版型，再逐步配置其他内容')
  })

  it('品牌文化改用多图详情并保留旧文字作为不可编辑兜底', async () => {
    const source = await readFile(sourcePath, 'utf8')
    expect(source).toContain('uploadBrandCultureImage(displayForm.value.tenantId')
    expect(source).toContain("'detail'")
    expect(source).toContain('multiple')
    expect(source).toContain('详情图最多10张')
    expect(source).toContain('合计将超出30MB')
    expect(source).toContain('旧客户已有文字内容会继续作为兜底展示')
    expect(source).toContain('v-if="displayForm.brandCultureDetailImages?.length"')
    expect(source).toContain("<template v-else>")
    expect(source).toContain("'detail-first': displayForm.brandCultureDetailImages?.length")
    expect(source).toContain('v-if="!isCulturePreview" class="mobile-preview-nav"')
  })

  it('让服务保障开关同步控制右侧预览，并给搜索框保留品牌区间距', async () => {
    const source = await readFile(sourcePath, 'utf8')
    expect(source).toContain('setTrustEnabled')
    expect(source).toContain('Number(displayForm.showTrustStrip) === 1')
    expect(source).toContain('margin:10px 10px 8px')
  })

  it('工作台适配当前浏览器高度并为失效Logo提供可操作降级', async () => {
    const source = await readFile(sourcePath, 'utf8')
    expect(source).toContain('width="min(1100px, calc(100vw - 56px))"')
    expect(source).toContain('height: calc(100vh - 24px)')
    expect(source).toContain('height: 438px')
    expect(source).toContain('displayLogoLoadFailed')
    expect(source).toContain("displayForm.logoUrl ? '重传' : '上传'")
    expect(source).toContain('normalizeMediaUrl')
  })

  it('在首页模块组内保留轮播管理和分类内容入口且不限制内容高度', async () => {
    const source = await readFile(sourcePath, 'utf8')
    expect(source).toContain('当前 {{ previewBanners.length }} 条已启用')
    expect(source).toContain('@click="bannerDialogVisible = true">管理轮播图片')
    expect(source).toContain('首页分类内容')
    expect(source).not.toContain('>编辑{{ row.label }}</el-button>')
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
    expect(source).toContain("v-if=\"displayForm.layoutTemplate === 'category-focus'\" class=\"category-guide-config\"")
    expect(source).toContain('原有模块值会一直保留')
    expect(source).toContain('直播广场总开关已关闭，保留当前首页开关值')
    expect(source).toContain('新品速递总开关已关闭，保留当前首页开关值')
    expect(source).toContain('isRequiredNav(nav.type)')
    expect(source).toContain('requiredCapabilities')
    expect(source).toContain('guide-preview-directory')
    expect(source).toContain('preview-guide-showcase')
    expect(source).toContain('preview-guide-scenarios')
  })

  it('A版型八种开关组合实时切换为双栏、内容全宽、一级分类全宽或无效提示', async () => {
    const source = await readFile(sourcePath, 'utf8')
    const matrix = [
      [0, 0, 0, 'empty'],
      [1, 0, 0, 'primary-only'],
      [0, 1, 0, 'content-only'],
      [0, 0, 1, 'content-only'],
      [0, 1, 1, 'content-only'],
      [1, 1, 0, 'split'],
      [1, 0, 1, 'split'],
      [1, 1, 1, 'split'],
    ]
    for (const [primaryCategories, subcategories, hotProducts, expected] of matrix) {
      expect(resolveDirectoryGuideLayout({ primaryCategories, subcategories, hotProducts })).toBe(expected)
    }
    expect(source).toContain('directoryGuidePreviewMode === \'split\'')
    expect(source).toContain('directoryGuidePreviewMode === \'primary-only\'')
    expect(source).toContain('directoryGuidePreviewMode === \'empty\'')
    expect(source).toContain('v-if="directoryGuideInvalid" class="guide-module-error" role="alert">请至少开启一个模块，或切换其他首页版型</p>')
    expect(source).toContain(':disabled="savingDisplay || directoryGuideInvalid"')
    expect(source).toContain("displayForm.value.layoutTemplate === 'category-focus'")
    expect(source).toContain("displayForm.value.categoryGuideTemplate === 'directory'")
    expect(source).toContain('if (directoryGuideInvalid.value)')
    expect(source).toContain("ElMessage.warning('请至少开启一个分类导购模块')")
    expect(source).toContain('class="guide-preview-directory-body"')
    expect(source).not.toContain('class="guide-preview-hero"')
    expect(source).not.toMatch(/\.mobile-category-guide-preview\s*\{[^}]*min-height/)
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
    expect(source).toContain('v-for="nav in configurableBottomNav"')
    expect(source).toContain('切换首页版型不会改动这里')
    expect(source).toContain('<div class="mobile-preview-search"><span>⌕</span><span>搜索商品</span><b>⌕</b></div>')
    expect(source).toMatch(/\.mobile-category-guide-preview \.mobile-preview-search\s*\{[^}]*height:37px/)
    expect(source).toMatch(/\.guide-preview-directory-body\.is-split\s*\{[^}]*grid-template-columns:62px minmax\(0,1fr\)/)
    expect(source).toContain('requiredCapabilities')
  })

  it('四个首页版型不得改写独立底栏，历史缺失项按安全默认补齐', async () => {
    const source = await readFile(sourcePath, 'utf8')
    const applyLayout = source.slice(source.indexOf('const applyLayoutTemplate'), source.indexOf('const moveModule'))
    expect(applyLayout).not.toContain('bottomNav')
    expect(applyLayout).not.toContain('showBottomCategoryNav')

    const historical = normalizeBottomNav([
      { type: 'home', label: '商城', enabled: false, futureStyle: 'keep' },
      { type: 'cart', label: '购物车', enabled: false },
      { type: 'profile', label: '我的', enabled: false },
    ])
    expect(historical.map((item) => item.type)).toEqual(['home', 'category', 'cart', 'orders', 'profile'])
    expect(historical.find((item) => item.type === 'category').enabled).toBe(true)
    expect(historical.find((item) => item.type === 'orders').enabled).toBe(false)
    expect(historical.filter((item) => ['home', 'cart', 'profile'].includes(item.type)).every((item) => item.enabled)).toBe(true)
    expect(historical[0].futureStyle).toBe('keep')

    for (const categoryEnabled of [false, true]) {
      for (const ordersEnabled of [false, true]) {
        const selected = normalizeBottomNav([
          { type: 'home', enabled: true },
          { type: 'category', enabled: categoryEnabled },
          { type: 'cart', enabled: true },
          { type: 'orders', enabled: ordersEnabled },
          { type: 'profile', enabled: true },
        ])
        for (const layout of ['standard', 'product-focus', 'category-focus', 'campaign-feed']) {
          const refreshed = normalizeBottomNav(JSON.parse(JSON.stringify(selected)))
          expect({ layout, nav: refreshed }.nav).toEqual(selected)
        }
      }
    }
    expect(source).toContain("isEditableBottomNav(nav.type)")
    expect(source).toContain('...displayExtraBase.value')
    expect(source).toContain('分类页、首页分类模块和“我的”中的订单都会继续保留')
  })
})
