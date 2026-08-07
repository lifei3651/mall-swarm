<template>
  <div class="page-container">
    <div class="toolbar">
      <div>
        <h2>商城视觉与页面</h2>
        <p>在这里预览并调整商城的品牌、主题色、首页样式和底部导航，保存后刷新前台即可查看。</p>
      </div>
      <div v-if="tableData[0]" class="toolbar-actions">
        <el-button @click="openTenantDialog(tableData[0])">品牌资料</el-button>
        <el-button @click="openBannerDialog">首页 Banner</el-button>
        <el-button type="primary" @click="openDisplayDialog(tableData[0])">视觉装修</el-button>
      </div>
    </div>

    <el-alert
      title="选择主题色后可在下方实时查看商城界面示意；经营主体、客服和协议内容已拆分到独立页面。"
      type="info"
      :closable="false"
      show-icon
      class="single-tenant-alert"
    />

    <el-table :data="tableData" v-loading="loading" style="width: 100%">
      <el-table-column prop="brandName" label="商城品牌名" width="180" />
      <el-table-column label="品牌LOGO" width="110" align="center">
        <template #default="{ row }">
          <el-image v-if="row.logoUrl" :src="row.logoUrl" class="table-logo" fit="contain" />
          <span v-else class="empty-logo">未上传</span>
        </template>
      </el-table-column>
      <el-table-column prop="themeColor" label="主题色" width="110">
        <template #default="{ row }">
          <div class="color-cell">
            <span class="swatch" :style="{ backgroundColor: row.themeColor || '#e7193f' }"></span>
            <span>{{ row.themeColor }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="productTemplate" label="前台样式" width="140">
        <template #default="{ row }">
          <el-tag>{{ getTemplateName(row.productTemplate) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="商城布局" width="150">
        <template #default>
          <el-tag type="success">{{ getLayoutTemplateName(currentDisplayConfig.layoutTemplate) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="280">
        <template #default="{ row }">
          <el-button type="primary" link @click="openTenantDialog(row)">品牌资料</el-button>
          <el-button type="info" link @click="openBannerDialog">首页 Banner</el-button>
          <el-button type="success" link @click="openDisplayDialog(row)">视觉装修</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="tenantDialogVisible" title="商城视觉与页面" width="900px" top="5vh">
      <el-form :model="tenantForm" label-width="110px">
        <el-form-item label="品牌名">
          <div class="field-with-help">
            <el-input v-model="tenantForm.brandName" placeholder="前端商城展示名称" />
            <span>同时用于商城头部、浏览器标签页、手机网页标题和管理后台登录页标题。</span>
          </div>
        </el-form-item>
        <el-form-item label="品牌LOGO">
          <div class="logo-upload-row">
            <el-upload action="#" :show-file-list="false" accept="image/*" :http-request="uploadLogo">
              <div class="logo-uploader">
                <el-image v-if="tenantForm.logoUrl" :src="tenantForm.logoUrl" fit="contain" />
                <div v-else class="logo-placeholder">点击上传<br />LOGO图片</div>
              </div>
            </el-upload>
            <div class="logo-help">支持 JPG、PNG、WEBP、GIF，单张不超过 5MB。<br />建议使用透明背景 PNG。</div>
          </div>
        </el-form-item>
        <el-form-item label="前台样式">
          <div class="theme-preset-grid">
            <button
              v-for="theme in themeOptions"
              :key="theme.value"
              type="button"
              class="theme-preset"
              :class="{ active: tenantForm.productTemplate === theme.value }"
              @click="applyTheme(theme)"
            >
              <span class="theme-preview" :style="{ '--preview-color': theme.color, '--preview-radius': theme.radius }">
                <i></i><b></b><em></em>
              </span>
              <strong>{{ theme.label }}</strong>
              <small>{{ theme.description }}</small>
            </button>
          </div>
        </el-form-item>
        <el-form-item label="主题色">
          <div class="color-editor">
            <el-color-picker v-model="tenantForm.themeColor" />
            <el-input v-model="tenantForm.themeColor" maxlength="7" placeholder="#e7193f" />
            <span>可在所选样式基础上自由改色。</span>
          </div>
        </el-form-item>
        <el-form-item label="首页装修预览">
          <div class="field-help">
            模块开关、顺序和底部导航请进入“首页布局”装修工作台调整。那里会用真实的 Banner、分类和商品数据实时预览，保存后才会发布到前台。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tenantDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTenant">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="displayDialogVisible" title="商城视觉装修工作台" width="1040px" top="1vh" class="display-workbench-dialog">
      <el-alert title="左侧调整模块，右侧手机实时预览。当前修改只保存在草稿中，点击“保存发布”后才会影响客户前台。" type="info" :closable="false" class="display-alert" />
      <section class="visual-design-panel">
        <div class="control-section-heading">
          <div><strong>品牌视觉</strong><small>模板和主题色实时作用于右侧手机预览，点击保存发布后客户前台生效</small></div>
          <el-tag size="small" type="success">实时预览</el-tag>
        </div>
        <div class="visual-design-grid">
          <div class="theme-preset-grid compact-theme-grid">
            <button
              v-for="theme in themeOptions"
              :key="theme.value"
              type="button"
              class="theme-preset"
              :class="{ active: displayForm.productTemplate === theme.value }"
              @click="applyDisplayTheme(theme)"
            >
              <span class="theme-preview" :style="{ '--preview-color': theme.color, '--preview-radius': theme.radius }"><i></i><b></b><em></em></span>
              <strong>{{ theme.label }}</strong>
              <small>{{ theme.description }}</small>
            </button>
          </div>
          <div class="visual-design-fields">
            <label><span>商城名称</span><el-input v-model="displayForm.brandName" maxlength="64" placeholder="客户前台展示名称" /></label>
            <label><span>主题色</span><div class="color-editor"><el-color-picker v-model="displayForm.themeColor" /><el-input v-model="displayForm.themeColor" maxlength="7" placeholder="#e7193f" /></div></label>
          </div>
        </div>
      </section>
      <div class="preview-page-tabs" role="tablist" aria-label="前台页面预览">
        <button v-for="page in previewPages" :key="page.value" type="button" :class="{ active: previewPage === page.value }" @click="previewPage = page.value">{{ page.label }}<small v-if="page.value !== 'home'">下一阶段</small></button>
      </div>
      <div class="display-workbench">
        <aside class="display-controls">
          <section class="control-section">
            <div class="control-section-heading"><div><strong>首页模块</strong><small>拖动调整前台显示顺序</small></div><el-tag size="small" type="info">实时预览</el-tag></div>
            <div class="module-list module-list-sortable">
              <div v-for="(module, index) in displayForm.homeModules" :key="module.type" class="module-item" draggable="true" @dragstart="startModuleDrag(index)" @dragover.prevent @drop="dropModule(index)">
                <span class="drag-handle" aria-hidden="true">⋮⋮</span>
                <strong>{{ moduleNames[module.type] || module.type }}</strong>
                <el-switch v-model="module.enabled" active-text="展示" inactive-text="隐藏" />
              </div>
            </div>
            <div class="section-note">Banner 图片、跳转和顺序统一在本页管理：<el-button type="primary" link @click="openBannerDialog">打开 Banner 管理</el-button>。</div>
          </section>

          <section class="control-section category-config-section">
            <div class="control-section-heading"><div><strong>分类模块</strong><small>先控制整体，再控制单个分类</small></div></div>
            <div class="control-switch-row"><span>首页显示分类</span><el-switch v-model="displayForm.showHomeCategories" :active-value="1" :inactive-value="0" /></div>
            <div class="category-list category-list-draft">
              <div v-for="category in categories" :key="category.id" class="category-row">
                <span>{{ category.categoryName }}</span>
                <el-switch :model-value="categoryDraft[category.id] ?? 1" :active-value="1" :inactive-value="0" active-text="展示" inactive-text="隐藏" @change="(value) => setCategoryDraft(category, value)" />
              </div>
              <el-empty v-if="!categories.length" :image-size="44" description="暂无商品分类，可直接展示精选商品" />
            </div>
          </section>

          <section class="control-section">
            <div class="control-section-heading"><div><strong>服务说明</strong><small>首页底部的说明性内容</small></div></div>
            <div class="control-switch-row"><span>安全支付、订单可查、售后无忧</span><el-switch v-model="displayForm.showTrustStrip" :active-value="1" :inactive-value="0" /></div>
          </section>

          <section class="control-section">
            <div class="control-section-heading"><div><strong>底部导航</strong><small>拖动排序、改名或隐藏</small></div></div>
            <div class="nav-config-list nav-list-sortable">
              <div v-for="(nav, index) in displayForm.bottomNav" :key="nav.type" class="nav-config-row" draggable="true" @dragstart="startNavDrag(index)" @dragover.prevent @drop="dropNav(index)">
                <span class="drag-handle" aria-hidden="true">⋮⋮</span>
                <span class="nav-type-name">{{ navNames[nav.type] || nav.type }}</span>
                <el-input v-model="nav.label" maxlength="6" style="width:100px" />
                <el-switch v-model="nav.enabled" active-text="展示" inactive-text="隐藏" />
              </div>
            </div>
            <div class="control-switch-row"><span>底部分类入口</span><el-switch v-model="displayForm.showBottomCategoryNav" :active-value="1" :inactive-value="0" /></div>
          </section>

          <section class="control-section">
            <div class="control-section-heading">
              <div><strong>颜色微调</strong><small>不改变模块结构；保存发布后客户前台生效，未保存仅影响右侧预览</small></div>
              <el-button type="primary" link @click="resetColors">恢复默认</el-button>
            </div>
            <div class="color-grid">
              <label v-for="color in colorFields" :key="color.key"><span>{{ color.label }}</span><el-color-picker v-model="displayForm.colors[color.key]" show-alpha /></label>
            </div>
          </section>
        </aside>

        <section class="preview-stage">
          <div class="preview-stage-heading"><div><strong>客户手机版预览</strong><span>{{ previewPage === 'home' ? '首页模块与前台保持同一套配置' : '此页面将在首页工作台稳定后接入真实页面' }}</span></div><el-tag type="success">草稿预览</el-tag></div>
          <div v-if="previewPage !== 'home'" class="preview-coming-soon"><strong>{{ previewPages.find((page) => page.value === previewPage)?.label }}预览</strong><span>首页装修完成后，这里会接入对应的真实前台页面。</span></div>
          <div v-else class="mobile-preview-shell live-mobile-preview" :style="previewStyle">
            <div class="mobile-preview-status"><span>9:41</span><span>● ● ●</span></div>
            <div class="mobile-preview-brand"><span class="mobile-preview-logo"><img v-if="currentTenant?.logoUrl" :src="currentTenant.logoUrl" alt="" /><span v-else>{{ (displayForm.brandName || '灵启').slice(0, 1) }}</span></span><strong>{{ displayForm.brandName || '灵启商城' }}</strong><span class="mobile-preview-share">分享</span></div>
            <div class="mobile-preview-search"><span>⌕</span><span>搜索商品</span><b>⌕</b></div>
            <template v-for="module in orderedPreviewModules" :key="module.type">
              <div v-if="module.type === 'banner' && module.enabled" class="mobile-preview-banner live-preview-banner">
                <img v-if="previewBanners.length" :src="previewBanners[0].imageUrl" :alt="previewBanners[0].title || '商城活动'" />
                <div v-else class="preview-empty-module"><strong>Banner轮播</strong><span>前往 Banner 管理上传图片</span></div>
                <i v-if="previewBanners.length > 1">● ○ ○</i>
              </div>
              <div v-else-if="module.type === 'notice' && module.enabled" class="mobile-preview-notice"><span>⌁</span><strong>商城公告</strong><small>欢迎来到{{ displayForm.brandName || '灵启商城' }}</small></div>
              <div v-else-if="module.type === 'category' && module.enabled && displayForm.showHomeCategories === 1" class="mobile-preview-categories live-preview-categories">
                <div v-for="category in visiblePreviewCategories" :key="category.id" class="mobile-preview-category"><span><img v-if="category.iconUrl" :src="category.iconUrl" alt="" /><b v-else>{{ category.categoryName?.slice(0, 1) }}</b></span><strong>{{ category.categoryName }}</strong></div>
                <div v-if="!visiblePreviewCategories.length" class="preview-empty-inline">暂无首页分类</div>
              </div>
              <div v-else-if="module.type === 'trust' && module.enabled && displayForm.showTrustStrip === 1" class="mobile-preview-trust"><span>安全支付</span><span>订单可查</span><span>售后无忧</span></div>
              <div v-else-if="module.type === 'products' && module.enabled" class="mobile-preview-product-section"><div class="mobile-preview-heading"><strong>精选商品</strong><span>商城好物，为你精选</span></div><div class="mobile-preview-products"><div v-for="product in previewProducts" :key="product.id" class="mobile-preview-product"><img v-if="product.coverUrl" :src="product.coverUrl" :alt="product.productName" /><i v-else></i><strong>{{ product.productName }}</strong><small>{{ product.subtitle || '精选商品，品质保障' }}</small><b>¥{{ Number(product.salePrice || 0).toFixed(2) }}</b></div><div v-if="!previewProducts.length" class="preview-empty-module">暂无上架商品</div></div></div>
            </template>
            <div class="mobile-preview-nav" :style="{ gridTemplateColumns: `repeat(${Math.max(visiblePreviewNav.length, 1)}, minmax(0, 1fr))` }"><span v-for="nav in visiblePreviewNav" :key="nav.type" :class="{ active: nav.type === 'home' }">{{ nav.label }}</span></div>
          </div>
        </section>
      </div>
      <template #footer>
        <el-button @click="displayDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDisplayConfig">保存发布</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bannerDialogVisible" title="首页 Banner 管理" width="1000px" top="3vh" append-to-body>
      <ShopBanners />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listShopBanners, listShopCategories, listShopProducts, updateCategoryShowOnHome, uploadShopImage } from '@/api/shop'
import ShopBanners from '@/views/shop/banners.vue'
import {
  getDisplayConfig,
  listTenants,
  saveDisplayConfig,
  saveTenant,
} from '@/api/tenant'

const loading = ref(false)
const tableData = ref([])
const tenantDialogVisible = ref(false)
const displayDialogVisible = ref(false)
const bannerDialogVisible = ref(false)
const currentTenant = ref(null)
const currentDisplayConfig = ref({ layoutTemplate: 'standard' })
const categories = ref([])
const previewProducts = ref([])
const previewBanners = ref([])
const categoryDraft = ref({})
const previewPage = ref('home')
const draggingModuleIndex = ref(null)
const draggingNavIndex = ref(null)

const tenantForm = ref({})
const displayForm = ref({})
const moduleNames = { banner: 'Banner轮播', notice: '商城公告', category: '商品分类', trust: '服务保障', products: '精选商品' }
const navNames = { home: '首页', category: '分类', cart: '购物车', orders: '订单', profile: '我的' }
const previewPages = [
  { value: 'home', label: '首页' },
  { value: 'category', label: '分类' },
  { value: 'cart', label: '购物车' },
  { value: 'profile', label: '我的' },
]
const defaultModules = () => [
  { type: 'banner', enabled: true, sort: 1 },
  { type: 'notice', enabled: true, sort: 2 },
  { type: 'category', enabled: true, sort: 3 },
  { type: 'trust', enabled: false, sort: 4 },
  { type: 'products', enabled: true, sort: 5 },
]
const defaultBottomNav = () => [
  { type: 'home', label: '首页', enabled: true },
  { type: 'category', label: '分类', enabled: true },
  { type: 'cart', label: '购物车', enabled: true },
  { type: 'orders', label: '订单', enabled: false },
  { type: 'profile', label: '我的', enabled: true },
]
const defaultColors = () => ({
  priceColor: '',
  pageBg: '',
  headerBg: '',
  cardBg: '',
  textColor: '',
  mutedColor: '',
  accentColor: '',
  lineColor: '',
  buttonBg: '',
})
const colorFields = [
  { key: 'priceColor', label: '价格色' },
  { key: 'pageBg', label: '页面背景' },
  { key: 'headerBg', label: '顶部背景' },
  { key: 'cardBg', label: '卡片背景' },
  { key: 'textColor', label: '主文字色' },
  { key: 'mutedColor', label: '辅助文字色' },
  { key: 'accentColor', label: '强调色' },
  { key: 'lineColor', label: '分割线色' },
  { key: 'buttonBg', label: '按钮背景' },
]
const themeOptions = [
  { value: 'retail-red', label: '热卖红', color: '#e7193f', radius: '12px', description: '醒目促销、适合大众零售' },
  { value: 'fresh-green', label: '清新绿', color: '#0f766e', radius: '18px', description: '自然清爽、适合健康生活' },
  { value: 'premium-gold', label: '轻奢金', color: '#9a6a22', radius: '6px', description: '稳重精致、适合高端商品' },
  { value: 'soft-purple', label: '雅致紫', color: '#7c3aed', radius: '20px', description: '柔和现代、适合美妆精品' },
]
const layoutTemplateOptions = [
  {
    value: 'standard',
    label: '标准零售版',
    description: '首页分类与底部四导航并存，适合综合商城',
    showHomeCategories: 1,
    showBottomCategoryNav: 1,
    showTrustStrip: 0,
  },
  {
    value: 'product-focus',
    label: '精简商品版',
    description: '弱化分类，底部三导航，适合商品较少的商城',
    showHomeCategories: 0,
    showBottomCategoryNav: 0,
  },
  {
    value: 'category-focus',
    label: '分类导购版',
    description: '突出分类入口，适合品类和商品较多的商城',
    showHomeCategories: 1,
    showBottomCategoryNav: 1,
  },
]
const legacyThemeMap = { standard: 'retail-red', beauty: 'soft-purple', food: 'fresh-green', health: 'fresh-green', course: 'premium-gold' }
const normalizeTheme = (value) => themeOptions.some((item) => item.value === value) ? value : (legacyThemeMap[value] || 'retail-red')

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listTenants({ pageNum: 1, pageSize: 100 })
    const rows = res.data?.list || []
    const current = rows.find((row) => Number(row.id) === 1) || rows[0]
    tableData.value = current ? [current] : []
    if (current) {
      const configRes = await getDisplayConfig(current.id)
      currentDisplayConfig.value = configRes.data || { layoutTemplate: 'standard' }
    }
  } finally {
    loading.value = false
  }
}

const openTenantDialog = (row) => {
  if (!row) return
  tenantForm.value = { ...row, productTemplate: normalizeTheme(row.productTemplate) }
  tenantDialogVisible.value = true
}

const openBannerDialog = () => {
  bannerDialogVisible.value = true
}

const applyTheme = (theme) => {
  tenantForm.value.productTemplate = theme.value
  tenantForm.value.themeColor = theme.color
}

const submitTenant = async () => {
  if (!tenantForm.value.tenantName) {
    ElMessage.warning('请输入运营主体')
    return
  }
  if (!/^#[0-9a-fA-F]{6}$/.test(tenantForm.value.themeColor || '')) {
    ElMessage.warning('主题色必须是完整的6位色值，例如 #e7193f')
    return
  }
  const res = await saveTenant(tenantForm.value)
  if (res.data) tenantForm.value = { ...res.data, productTemplate: normalizeTheme(res.data.productTemplate) }
  ElMessage.success('品牌和前台样式已保存，刷新商城即可看到效果')
  tenantDialogVisible.value = false
  await fetchData()
}

const uploadLogo = async ({ file }) => {
  const res = await uploadShopImage(file)
  tenantForm.value.logoUrl = res.data
  ElMessage.success('品牌LOGO上传成功')
}

const openDisplayDialog = async (row) => {
  currentTenant.value = row
  const [resResult, categoryResult, productResult, bannerResult] = await Promise.allSettled([
    getDisplayConfig(row.id),
    listShopCategories({ tenantId: row.id, status: 1 }),
    listShopProducts({ tenantId: row.id, status: 1, pageNum: 1, pageSize: 6 }),
    listShopBanners({ tenantId: row.id }),
  ])
  if (resResult.status === 'rejected') throw resResult.reason
  const res = resResult.value
  const categoryRes = categoryResult.status === 'fulfilled' ? categoryResult.value : { data: [] }
  const productRes = productResult.status === 'fulfilled' ? productResult.value : { data: [] }
  const bannerRes = bannerResult.status === 'fulfilled' ? bannerResult.value : { data: [] }
  categories.value = Array.isArray(categoryRes.data) ? categoryRes.data : (categoryRes.data?.list || [])
  previewProducts.value = Array.isArray(productRes.data) ? productRes.data : (productRes.data?.list || [])
  previewBanners.value = (Array.isArray(bannerRes.data) ? bannerRes.data : (bannerRes.data?.list || [])).filter((banner) => Number(banner.status ?? 1) === 1)
  categoryDraft.value = Object.fromEntries(categories.value.map((category) => [category.id, Number(category.showOnHome ?? 1)]))
  const raw = res.data?.extraConfigJson || '{}'
  let extra = {}
  try { extra = JSON.parse(raw) || {} } catch { extra = {} }
  displayForm.value = {
    tenantId: row.id,
    brandName: row.brandName || row.tenantName || '灵启商城',
    themeColor: row.themeColor || '#e7193f',
    productTemplate: normalizeTheme(row.productTemplate),
    layoutTemplate: 'standard',
    showHomeCategories: 1,
    showBottomCategoryNav: 1,
    ...(res.data || {}),
    homeModules: Array.isArray(extra.homeModules) && extra.homeModules.length ? extra.homeModules : defaultModules(),
    colors: { ...defaultColors(), ...(extra.colors || {}) },
    bottomNav: Array.isArray(extra.bottomNav) && extra.bottomNav.length ? extra.bottomNav : defaultBottomNav(),
    showTrustStrip: Number(extra.showTrustStrip ?? 0) === 1 ? 1 : 0,
  }
  displayDialogVisible.value = true
}

const applyDisplayTheme = (theme) => {
  displayForm.value.productTemplate = theme.value
  displayForm.value.themeColor = theme.color
}

const resetColors = () => {
  displayForm.value.colors = defaultColors()
  ElMessage.success('颜色已恢复默认，点击“保存发布”后客户前台生效')
}

const orderedPreviewModules = computed(() => [...(displayForm.value.homeModules || [])].sort((a, b) => (a.sort || 99) - (b.sort || 99)))
const visiblePreviewCategories = computed(() => categories.value.filter((category) => Number(categoryDraft.value[category.id] ?? 1) === 1))
const visiblePreviewNav = computed(() => (displayForm.value.bottomNav || []).filter((nav) => nav.enabled !== false && (nav.type !== 'category' || Number(displayForm.value.showBottomCategoryNav ?? 1) === 1)))
const previewStyle = computed(() => ({
  '--preview-color': displayForm.value.themeColor || currentTenant.value?.themeColor || '#e7193f',
  '--preview-page-bg': displayForm.value.colors?.pageBg || '#f5f6f8',
  '--preview-card-bg': displayForm.value.colors?.cardBg || '#fff',
  '--preview-text': displayForm.value.colors?.textColor || '#202735',
  '--preview-muted': displayForm.value.colors?.mutedColor || '#98a2b3',
}))

const applyLayoutTemplate = (template) => {
  displayForm.value.layoutTemplate = template.value
  displayForm.value.showHomeCategories = template.showHomeCategories
  displayForm.value.showBottomCategoryNav = template.showBottomCategoryNav
}

const moveModule = (index, direction) => {
  const next = index + direction
  if (next < 0 || next >= displayForm.value.homeModules.length) return
  const modules = displayForm.value.homeModules
  ;[modules[index], modules[next]] = [modules[next], modules[index]]
  modules.forEach((module, itemIndex) => { module.sort = itemIndex + 1 })
}

const reorderItems = (items, from, to) => {
  if (from === null || from === to || from < 0 || to < 0 || from >= items.length || to >= items.length) return
  const [item] = items.splice(from, 1)
  items.splice(to, 0, item)
}

const startModuleDrag = (index) => { draggingModuleIndex.value = index }
const dropModule = (index) => {
  const modules = displayForm.value.homeModules || []
  reorderItems(modules, draggingModuleIndex.value, index)
  modules.forEach((module, itemIndex) => { module.sort = itemIndex + 1 })
  draggingModuleIndex.value = null
}
const startNavDrag = (index) => { draggingNavIndex.value = index }
const dropNav = (index) => {
  const navs = displayForm.value.bottomNav || []
  reorderItems(navs, draggingNavIndex.value, index)
  draggingNavIndex.value = null
}
const setCategoryDraft = (category, value) => {
  categoryDraft.value = { ...categoryDraft.value, [category.id]: Number(value) }
}

const submitDisplayConfig = async () => {
  const payload = { ...displayForm.value }
  payload.extraConfigJson = JSON.stringify({ homeModules: payload.homeModules, colors: payload.colors, bottomNav: payload.bottomNav, showTrustStrip: payload.showTrustStrip })
  delete payload.homeModules
  delete payload.colors
  delete payload.bottomNav
  delete payload.brandName
  delete payload.themeColor
  delete payload.productTemplate
  const tenantPayload = {
    ...currentTenant.value,
    id: currentTenant.value.id,
    brandName: displayForm.value.brandName,
    themeColor: displayForm.value.themeColor,
    productTemplate: normalizeTheme(displayForm.value.productTemplate),
  }
  const [tenantResult] = await Promise.all([saveTenant(tenantPayload), saveDisplayConfig(payload)])
  if (tenantResult.data) {
    currentTenant.value = { ...currentTenant.value, ...tenantResult.data }
    tableData.value = tableData.value.map((row) => Number(row.id) === Number(currentTenant.value.id) ? { ...row, ...tenantResult.data } : row)
  }
  const categoryUpdates = categories.value
    .filter((category) => Number(categoryDraft.value[category.id] ?? 1) !== Number(category.showOnHome ?? 1))
    .map((category) => updateCategoryShowOnHome(category.id, categoryDraft.value[category.id]))
  const categoryResults = await Promise.allSettled(categoryUpdates)
  categories.value.forEach((category) => { category.showOnHome = categoryDraft.value[category.id] ?? category.showOnHome })
  currentDisplayConfig.value = { ...displayForm.value }
  if (categoryResults.some((result) => result.status === 'rejected')) {
    ElMessage.warning('页面配置已保存，但部分分类显示状态保存失败，请重试')
  } else {
    ElMessage.success('商城首页装修已发布，网页和 APP 刷新后生效')
  }
  displayDialogVisible.value = false
}

const getLayoutTemplateName = (value) => {
  return layoutTemplateOptions.find((item) => item.value === value)?.label || '标准零售版'
}

const getTemplateName = (value) => {
  const map = {
    'retail-red': '热卖红',
    'fresh-green': '清新绿',
    'premium-gold': '轻奢金',
    'soft-purple': '雅致紫',
  }
  return map[normalizeTheme(value)] || '热卖红'
}

onMounted(fetchData)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}
.toolbar h2 {
  margin: 0;
  color: #303133;
  font-size: 20px;
}
.toolbar p {
  margin: 6px 0 0;
  color: #909399;
  font-size: 13px;
}
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.single-tenant-alert {
  margin-bottom: 16px;
}
.color-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.swatch {
  width: 16px;
  height: 16px;
  border-radius: 3px;
  border: 1px solid #dcdfe6;
}
.ui-preview {
  width: 100%;
  max-width: 720px;
  overflow: hidden;
  color: #253044;
  background: #f6f7f9;
  border: 1px solid #e4e7ed;
  border-radius: 14px;
  box-shadow: 0 8px 24px rgba(31, 45, 61, .08);
}
.ui-preview-head {
  display: grid;
  grid-template-columns: 28px auto minmax(120px, 1fr) auto;
  align-items: center;
  gap: 9px;
  padding: 14px 16px;
  color: #fff;
  background: linear-gradient(135deg, color-mix(in srgb, var(--preview-color) 88%, #111 12%), var(--preview-color));
}
.ui-preview-logo {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  color: var(--preview-color);
  font-weight: 700;
  background: #fff;
  border-radius: 9px;
}
.ui-preview-head strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ui-preview-search { padding: 6px 10px; color: rgba(255,255,255,.75); font-size: 12px; background: rgba(255,255,255,.16); border-radius: 999px; }
.ui-preview-avatar { font-size: 12px; opacity: .86; }
.ui-preview-categories { display: flex; gap: 8px; padding: 12px 16px 4px; overflow: hidden; white-space: nowrap; }
.ui-preview-categories span { padding: 5px 10px; color: var(--preview-color); font-size: 12px; background: color-mix(in srgb, var(--preview-color) 12%, #fff 88%); border-radius: 999px; }
.ui-preview-products { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; padding: 10px 16px 16px; }
.ui-preview-product { display: grid; gap: 6px; min-width: 0; padding: 10px; background: #fff; border-radius: 10px; }
.ui-preview-product i { display: block; height: 62px; background: linear-gradient(135deg, color-mix(in srgb, var(--preview-color) 18%, #fff 82%), #eef1f4); border-radius: 8px; }
.ui-preview-product strong,.ui-preview-product small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ui-preview-product strong { font-size: 13px; }
.ui-preview-product small { color: #8a94a4; font-size: 11px; }
.ui-preview-product b { color: var(--preview-color); font-size: 14px; }
.ui-preview-nav { display: grid; grid-template-columns: repeat(4, 1fr); padding: 10px 16px; color: #8a94a4; font-size: 11px; text-align: center; background: #fff; border-top: 1px solid #eef0f3; }
.ui-preview-nav .active { color: var(--preview-color); font-weight: 700; }
.mobile-preview-shell {
  width: 340px;
  max-width: 100%;
  height: 560px;
  margin: 0 auto;
  overflow-y: auto;
  color: var(--preview-text, #202735);
  background: var(--preview-page-bg, #f5f6f8);
  border: 8px solid #1f2937;
  border-radius: 30px;
  box-shadow: 0 18px 40px rgba(31, 41, 55, .18);
}
.mobile-preview-status { display:flex; justify-content:space-between; padding:9px 18px 4px; color:#1f2937; font-size:11px; font-weight:700; background:#fff; }
.mobile-preview-brand { display:grid; grid-template-columns:28px 1fr auto; align-items:center; gap:8px; padding:9px 14px 10px; background:#fff; }
.mobile-preview-logo { display:grid; width:28px; height:28px; place-items:center; overflow:hidden; color:var(--preview-color); font-weight:800; background:#fff; border:2px solid var(--preview-color); border-radius:9px; }
.mobile-preview-logo img { width:100%; height:100%; object-fit:contain; }
.mobile-preview-brand strong { overflow:hidden; font-size:16px; text-overflow:ellipsis; white-space:nowrap; }
.mobile-preview-share { color:var(--preview-color); font-size:12px; }
.mobile-preview-search { display:grid; grid-template-columns:24px 1fr 26px; align-items:center; gap:6px; margin:0 12px 10px; padding:8px 10px; color:#98a2b3; background:#fff; border:1.5px solid var(--preview-color); border-radius:999px; }
.mobile-preview-search b { display:grid; width:26px; height:26px; place-items:center; color:#fff; background:var(--preview-color); border-radius:50%; }
.mobile-preview-banner { position:relative; display:grid; gap:4px; min-height:122px; margin:0 12px 10px; overflow:hidden; color:#fff; background:linear-gradient(135deg, color-mix(in srgb, var(--preview-color) 84%, #111 16%), var(--preview-color)); border-radius:16px; }
.live-preview-banner img { width:100%; height:122px; object-fit:cover; }
.live-preview-banner i { position:absolute; right:12px; bottom:8px; margin:0; }
.preview-empty-module { display:grid; place-items:center; align-content:center; gap:4px; min-height:100px; padding:14px; color:#667085; font-size:11px; text-align:center; }
.preview-empty-module strong { color:var(--preview-color); font-size:15px; }
.mobile-preview-banner .preview-empty-module { color:#fff; }
.mobile-preview-banner .preview-empty-module strong { color:#fff; }
.mobile-preview-banner span { font-size:20px; font-weight:800; }
.mobile-preview-banner small { opacity:.86; }
.mobile-preview-banner i { margin-top:8px; font-style:normal; font-size:11px; letter-spacing:3px; opacity:.85; }
.mobile-preview-notice { display:flex; align-items:center; gap:7px; margin:0 12px 10px; padding:9px 10px; overflow:hidden; color:var(--preview-color); background:#fff; border-radius:12px; }
.mobile-preview-notice small { overflow:hidden; color:var(--preview-muted, #98a2b3); text-overflow:ellipsis; white-space:nowrap; }
.mobile-preview-categories { display:flex; gap:7px; margin:0 12px 10px; padding:10px; overflow:hidden; background:var(--preview-card-bg, #fff); border-radius:14px; }
.mobile-preview-category { flex:0 0 58px; display:grid; justify-items:center; gap:4px; min-width:0; color:var(--preview-color); font-size:10px; text-align:center; }
.mobile-preview-category > span { display:grid; width:36px; height:36px; place-items:center; overflow:hidden; background:color-mix(in srgb, var(--preview-color) 10%, #fff 90%); border-radius:50%; }
.mobile-preview-category img { width:100%; height:100%; object-fit:cover; }
.mobile-preview-category b { font-size:14px; }
.mobile-preview-category strong { overflow:hidden; width:100%; text-overflow:ellipsis; white-space:nowrap; }
.preview-empty-inline { padding:12px; color:var(--preview-muted, #98a2b3); font-size:11px; }
.mobile-preview-heading { display:grid; gap:3px; padding:7px 14px; }
.mobile-preview-heading strong { font-size:20px; }
.mobile-preview-heading span { color:#98a2b3; font-size:12px; }
.mobile-preview-products { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:9px; padding:7px 12px 12px; }
.mobile-preview-product { display:grid; gap:5px; min-width:0; padding:8px; background:var(--preview-card-bg, #fff); border-radius:14px; }
.mobile-preview-product img { display:block; width:100%; height:95px; object-fit:cover; border-radius:10px; }
.mobile-preview-product i { display:block; height:95px; background:linear-gradient(135deg, color-mix(in srgb, var(--preview-color) 15%, #fff 85%), #e9edf2); border-radius:10px; }
.mobile-preview-product strong { overflow:hidden; font-size:13px; text-overflow:ellipsis; white-space:nowrap; }
.mobile-preview-product small { overflow:hidden; color:#98a2b3; font-size:11px; text-overflow:ellipsis; white-space:nowrap; }
.mobile-preview-product b { color:var(--preview-color); font-size:15px; }
.mobile-preview-trust { display:grid; grid-template-columns:repeat(3,1fr); gap:1px; margin:0 12px 12px; padding:9px 4px; color:#667085; font-size:11px; text-align:center; background:var(--preview-card-bg, #fff); border-radius:12px; }
.mobile-preview-nav { display:grid; grid-template-columns:repeat(4, minmax(0, 1fr)); padding:10px 8px 12px; color:#8a94a4; font-size:11px; text-align:center; background:#fff; border-top:1px solid #eef0f3; }
.mobile-preview-nav span:first-child { color:var(--preview-color); font-weight:800; }
.version-form {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}
.display-alert {
  margin-bottom: 16px;
}
.visual-design-panel {
  margin-bottom: 14px;
  padding: 14px;
  background: linear-gradient(135deg, #fbfdff, #f5f8fc);
  border: 1px solid #e4ebf3;
  border-radius: 12px;
}
.visual-design-panel .control-section-heading { margin-bottom: 10px; }
.visual-design-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  gap: 14px;
  align-items: start;
}
.compact-theme-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 7px; margin: 0; }
.compact-theme-grid .theme-preset { grid-template-columns: 42px minmax(0, 1fr); gap: 0 7px; padding: 7px; border-radius: 8px; }
.compact-theme-grid .theme-preview { width: 42px; height: 34px; padding: 5px; }
.compact-theme-grid .theme-preset strong { font-size: 12px; }
.compact-theme-grid .theme-preset small { min-height: 0; overflow: hidden; font-size: 10px; line-height: 14px; text-overflow: ellipsis; white-space: nowrap; }
.visual-design-fields { display: grid; gap: 10px; padding: 4px 0; }
.visual-design-fields label { display: grid; gap: 5px; color: #667085; font-size: 12px; }
.visual-design-fields .color-editor { grid-template-columns: 32px minmax(0, 1fr); width: 100%; gap: 7px; }
.visual-design-fields .color-editor .el-color-picker { width: 32px; }
.preview-page-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  padding: 4px;
  background: #f5f7fa;
  border-radius: 10px;
}
.preview-page-tabs button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 8px 14px;
  color: #606266;
  background: transparent;
  border: 0;
  border-radius: 7px;
  cursor: pointer;
}
.preview-page-tabs button.active {
  color: var(--el-color-primary);
  background: #fff;
  box-shadow: 0 1px 4px rgba(31, 45, 61, .08);
}
.preview-page-tabs small { color: #a8abb2; font-size: 10px; }
.display-workbench {
  display: grid;
  grid-template-columns: minmax(430px, 1fr) 410px;
  gap: 18px;
  min-height: 0;
}
.display-controls {
  max-height: 590px;
  padding-right: 6px;
  overflow-y: auto;
}
.control-section {
  margin-bottom: 14px;
  padding: 14px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
}
.control-section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 11px;
}
.control-section-heading div { display: grid; gap: 3px; }
.control-section-heading strong { color: #303133; font-size: 14px; }
.control-section-heading small { color: #909399; font-size: 12px; }
.control-switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  color: #606266;
  border-top: 1px solid #f2f3f5;
}
.drag-handle {
  flex: 0 0 18px;
  color: #a8abb2;
  font-size: 16px;
  line-height: 1;
  letter-spacing: -4px;
  cursor: grab;
}
.module-list-sortable .module-item,
.nav-list-sortable .nav-config-row { cursor: grab; }
.module-list-sortable .module-item:active,
.nav-list-sortable .nav-config-row:active { cursor: grabbing; }
.module-list-sortable .module-item { gap: 8px; min-height: 34px; padding: 7px 9px; }
.module-list-sortable .module-item strong { flex: 1; }
.category-config-section .control-section-heading { margin-bottom: 8px; }
.category-list.category-list-draft {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  max-height: 132px;
  padding: 6px;
  overflow-y: auto;
  background: #f7f9fc;
  border: 1px solid #eef1f5;
  border-radius: 9px;
}
.category-list-draft .category-row {
  min-width: 0;
  gap: 6px;
  padding: 6px 8px;
  color: #475467;
  font-size: 12px;
  background: #fff;
  border-color: #e8edf3;
  border-radius: 7px;
}
.category-list-draft .category-row > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.category-list-draft .el-switch { transform: scale(.9); transform-origin: right center; }
.nav-config-row { gap: 8px; }
.nav-config-row .nav-type-name { width: 54px; color: #303133; font-size: 12px; }
.nav-config-row .el-switch { margin-left: auto; }
.color-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.color-grid label { display: flex; align-items: center; justify-content: space-between; gap: 8px; color: #606266; font-size: 12px; }
.preview-stage {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 0;
  padding: 14px;
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 12px;
}
.preview-stage-heading { display: flex; align-items: center; justify-content: space-between; width: 100%; gap: 10px; margin-bottom: 12px; }
.preview-stage-heading div { display: grid; gap: 3px; }
.preview-stage-heading strong { color: #303133; font-size: 14px; }
.preview-stage-heading span { color: #909399; font-size: 11px; }
.preview-coming-soon { display: grid; place-items: center; align-content: center; gap: 8px; flex: 1; width: 100%; min-height: 520px; color: #909399; text-align: center; background: #fff; border: 1px dashed #dcdfe6; border-radius: 14px; }
.preview-coming-soon strong { color: #606266; font-size: 18px; }
.preview-coming-soon span { font-size: 12px; }
.module-list,.category-list,.nav-config-list { display: grid; gap: 8px; }
.module-item,.category-row,.nav-config-row { display:flex; align-items:center; gap:12px; padding:10px 12px; border:1px solid #ebeef5; border-radius:8px; background:#fafbfc; }
.module-item strong,.category-row span { flex:1; color:#303133; }
.module-actions { display:flex; gap:5px; }
.section-note { margin-top:8px; color:#909399; font-size:12px; }
.category-row { justify-content:space-between; background:#fff; }
.nav-config-row > span { width:90px; color:#303133; }
.nav-config-row .el-switch { margin-left:auto; }
.color-editor { display:flex; align-items:center; gap:8px; }
.color-editor span { color:#909399; font-size:12px; font-family:monospace; }
.layout-template-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}
.layout-template-card {
  min-width: 0;
  display: grid;
  grid-template-rows: 92px auto auto;
  gap: 7px;
  padding: 12px;
  color: #303133;
  text-align: left;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 10px;
  cursor: pointer;
  transition: border-color .2s ease, box-shadow .2s ease, transform .2s ease;
}
.layout-template-card:hover,
.layout-template-card.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
  transform: translateY(-1px);
}
.layout-template-card strong {
  font-size: 14px;
}
.layout-template-card small {
  min-height: 36px;
  color: #909399;
  line-height: 18px;
}
.layout-preview {
  position: relative;
  display: grid;
  grid-template-rows: 14px 23px 1fr 12px;
  gap: 5px;
  padding: 8px;
  overflow: hidden;
  background: #f3f5f7;
  border-radius: 8px;
}
.layout-preview i {
  display: block;
  border-radius: 4px;
}
.preview-search { width: 70%; background: #fff; border: 1px solid #d9dfe6; }
.preview-categories { background: repeating-linear-gradient(90deg, #dfe6ec 0 17%, transparent 17% 20%); }
.preview-products { background: repeating-linear-gradient(90deg, #fff 0 31%, transparent 31% 34%); }
.preview-nav { background: repeating-linear-gradient(90deg, #cdd6df 0 23%, transparent 23% 26%); }
.layout-preview--product-focus {
  grid-template-rows: 14px 0 1fr 12px;
}
.layout-preview--product-focus .preview-categories { display: none; }
.layout-preview--product-focus .preview-products { background: repeating-linear-gradient(90deg, #fff 0 48%, transparent 48% 52%); }
.layout-preview--product-focus .preview-nav { background: repeating-linear-gradient(90deg, #cdd6df 0 31%, transparent 31% 35%); }
.layout-preview--category-focus .preview-categories {
  height: 28px;
  background: repeating-linear-gradient(90deg, #b9d8f5 0 17%, transparent 17% 20%);
}
@media (max-width: 700px) {
  .ui-preview-head { grid-template-columns: 28px minmax(0, 1fr) auto; }
  .ui-preview-search { display: none; }
  .ui-preview-products { grid-template-columns: 1fr; }
  .ui-preview-product { grid-template-columns: 72px 1fr; align-items: center; column-gap: 10px; }
  .ui-preview-product i { grid-row: span 3; height: 72px; }
}
.switch-with-help {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  line-height: 1.4;
}
.switch-with-help span {
  color: #909399;
  font-size: 12px;
}
.license-uploader {
  width: 180px;
  height: 126px;
  border: 1px dashed #c0c4cc;
  border-radius: 8px;
  overflow: hidden;
  display: grid;
  place-items: center;
  background: #fafafa;
}
.license-uploader .el-image { width: 100%; height: 100%; }
.table-logo {
  width: 58px;
  height: 42px;
}
.empty-logo {
  color: #909399;
  font-size: 12px;
}
.logo-upload-row {
  display: flex;
  align-items: center;
  gap: 16px;
}
.logo-uploader {
  width: 120px;
  height: 80px;
  border: 1px dashed #c0ccda;
  border-radius: 7px;
  overflow: hidden;
  cursor: pointer;
  background: #fafafa;
}
.logo-uploader:hover {
  border-color: #409eff;
}
.logo-uploader :deep(.el-image) {
  width: 100%;
  height: 100%;
}
.logo-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: #909399;
  font-size: 13px;
}
.logo-help {
  color: #909399;
  font-size: 12px;
  line-height: 20px;
}
.field-with-help {
  width: 100%;
}
.field-with-help span,
.color-editor > span {
  display: block;
  margin-top: 5px;
  color: #909399;
  font-size: 12px;
}
.theme-preset-grid {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.theme-preset {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr);
  grid-template-rows: auto auto;
  gap: 1px 12px;
  padding: 10px;
  color: #303133;
  text-align: left;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
}
.theme-preset:hover,
.theme-preset.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
}
.theme-preview {
  grid-row: 1 / 3;
  width: 82px;
  height: 54px;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 4px;
  padding: 7px;
  background: #f4f5f7;
  border-radius: var(--preview-radius);
}
.theme-preview i {
  grid-column: 1 / 3;
  height: 8px;
  background: var(--preview-color);
  border-radius: 4px;
}
.theme-preview b,
.theme-preview em {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: calc(var(--preview-radius) / 2);
}
.theme-preset strong { align-self: end; font-size: 14px; }
.theme-preset small { color: #909399; line-height: 17px; }
.color-editor {
  width: 100%;
  display: grid;
  grid-template-columns: 42px 130px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}
.color-editor > span { margin: 0; }
@media (max-width: 760px) {
  .toolbar { align-items: flex-start; flex-direction: column; }
  .toolbar-actions { width: 100%; }
  .layout-template-grid { grid-template-columns: 1fr; }
  .theme-preset-grid { grid-template-columns: 1fr; }
  .color-editor { grid-template-columns: 42px minmax(0, 1fr); }
  .color-editor > span { grid-column: 1 / 3; }
  .display-workbench { grid-template-columns: 1fr; }
  .display-controls { max-height: none; overflow: visible; }
  .preview-stage { order: -1; }
  .visual-design-grid { grid-template-columns: 1fr; }
  .compact-theme-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 680px) {
  .category-list.category-list-draft { grid-template-columns: 1fr; max-height: 180px; }
}
</style>
