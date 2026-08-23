<template>
  <div class="page-container display-config-page">
    <div class="toolbar">
      <div>
        <h2>商城装修</h2>
        <p>配置首页模块顺序、颜色主题、底部导航和分类展示。</p>
      </div>
      <el-tag type="primary" effect="plain">前台实时生效</el-tag>
    </div>

    <el-card v-loading="loading" shadow="never" class="config-card">
      <el-empty v-if="!displayForm.id && !loading" description="暂未找到配置" />
      <el-form v-else :model="displayForm" label-width="130px">

        <!-- 首页模块排序 -->
        <section class="form-section">
          <h3>首页模块排序</h3>
          <p class="section-desc">拖拽调整模块顺序，控制哪些模块在首页展示。</p>
          <div class="module-list">
            <div v-for="(mod, index) in homeModules" :key="mod.type" class="module-item">
              <div class="module-handle">
                <el-button :icon="Top" circle size="small" :disabled="index === 0" @click="moveModule(index, -1)" />
                <el-button :icon="Bottom" circle size="small" :disabled="index === homeModules.length - 1" @click="moveModule(index, 1)" />
              </div>
              <span class="module-name">{{ moduleNames[mod.type] || mod.type }}</span>
              <el-switch v-model="mod.enabled" active-text="展示" inactive-text="隐藏" />
            </div>
          </div>
        </section>

        <!-- Banner管理入口 -->
        <section class="form-section">
          <h3>Banner轮播</h3>
          <p class="section-desc">管理首页顶部的轮播广告图。</p>
          <el-button type="primary" @click="$router.push('/tenant/banners')">管理Banner</el-button>
        </section>

        <!-- 分类展示 -->
        <section class="form-section">
          <h3>分类展示</h3>
          <el-form-item label="首页分类模块">
            <el-switch v-model="displayForm.showHomeCategories" :active-value="1" :inactive-value="0" active-text="展示" inactive-text="隐藏" />
          </el-form-item>
          <el-divider />
          <p class="section-desc">单独控制每个分类是否在首页展示：</p>
          <div v-for="cat in categories" :key="cat.id" class="category-toggle">
            <span>{{ cat.categoryName }}</span>
            <el-switch :model-value="cat.showOnHome ?? 1" :active-value="1" :inactive-value="0" @change="(val) => updateCategoryShowOnHome(cat.id, val)" />
          </div>
        </section>

        <!-- 颜色配置 -->
        <section class="form-section">
          <h3>颜色配置</h3>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="价格颜色">
                <el-color-picker v-model="colors.priceColor" show-alpha />
                <span class="color-hint">{{ colors.priceColor || '默认' }}</span>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="页面背景">
                <el-color-picker v-model="colors.pageBg" show-alpha />
                <span class="color-hint">{{ colors.pageBg || '默认' }}</span>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="顶部导航背景">
                <el-color-picker v-model="colors.headerBg" show-alpha />
                <span class="color-hint">{{ colors.headerBg || '默认' }}</span>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="卡片背景">
                <el-color-picker v-model="colors.cardBg" show-alpha />
                <span class="color-hint">{{ colors.cardBg || '默认' }}</span>
              </el-form-item>
            </el-col>
          </el-row>
        </section>

        <!-- 底部导航 -->
        <section class="form-section">
          <h3>底部导航</h3>
          <p class="section-desc">自定义手机端底部导航栏的入口。</p>
          <div v-for="nav in bottomNav" :key="nav.type" class="nav-item">
            <span>{{ navLabels[nav.type] || nav.type }}</span>
            <div class="nav-controls">
              <el-input v-model="nav.label" maxlength="6" style="width:100px" />
              <el-switch v-model="nav.enabled" active-text="展示" inactive-text="隐藏" />
            </div>
          </div>
        </section>

      </el-form>

      <template #footer>
        <div class="card-footer">
          <el-button type="primary" :loading="saving" :disabled="!displayForm.id" @click="submit">保存配置</el-button>
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Top, Bottom } from '@element-plus/icons-vue'
import { listTenants, saveTenant } from '@/api/tenant'
import { listShopCategories, updateCategoryShowOnHome as apiUpdateCategoryShowOnHome } from '@/api/shop'

const loading = ref(false)
const saving = ref(false)
const displayForm = ref({})
const categories = ref([])

const moduleNames = {
  banner: 'Banner轮播',
  notice: '商城公告',
  category: '商品分类',
  discovery: '直播广场 / 新品速递',
  trust: '服务保障',
  products: '商品列表',
}

const navLabels = {
  home: '首页',
  category: '分类',
  cart: '购物车',
  orders: '订单',
  profile: '我的',
}

const defaultModules = [
  { type: 'banner', enabled: true, sort: 1 },
  { type: 'notice', enabled: true, sort: 2 },
  { type: 'category', enabled: true, sort: 3 },
  { type: 'discovery', enabled: true, sort: 4 },
  { type: 'trust', enabled: true, sort: 5 },
  { type: 'products', enabled: true, sort: 6 },
]

const defaultNav = [
  { type: 'home', label: '首页', enabled: true },
  { type: 'category', label: '分类', enabled: true },
  { type: 'cart', label: '购物车', enabled: true },
  { type: 'orders', label: '订单', enabled: true },
  { type: 'profile', label: '我的', enabled: true },
]

const homeModules = ref([...defaultModules])
const colors = reactive({ priceColor: '', pageBg: '', headerBg: '', cardBg: '' })
const bottomNav = ref([...defaultNav])

const parseExtraConfig = (json) => {
  try {
    const config = JSON.parse(json || '{}')
    if (Array.isArray(config.homeModules) && config.homeModules.length) {
      homeModules.value = config.homeModules
    }
    if (config.colors) Object.assign(colors, config.colors)
    if (Array.isArray(config.bottomNav) && config.bottomNav.length) {
      bottomNav.value = config.bottomNav
    }
  } catch {}
}

const moveModule = (index, direction) => {
  const newIndex = index + direction
  if (newIndex < 0 || newIndex >= homeModules.value.length) return
  const temp = homeModules.value[index]
  homeModules.value[index] = homeModules.value[newIndex]
  homeModules.value[newIndex] = temp
  homeModules.value.forEach((m, i) => { m.sort = i + 1 })
}

const fetchData = async () => {
  loading.value = true
  try {
    const [tenantRes, catRes] = await Promise.all([
      listTenants({ pageNum: 1, pageSize: 100 }),
      listShopCategories({ status: null }),
    ])
    const rows = tenantRes.data?.list || []
    const tenant = rows.find((r) => Number(r.id) === 1) || rows[0] || {}
    displayForm.value = {
      id: tenant.id,
      showHomeCategories: tenant.showHomeCategories ?? 1,
      showBottomCategoryNav: tenant.showBottomCategoryNav ?? 1,
      extraConfigJson: tenant.extraConfigJson || '{}',
    }
    categories.value = catRes.data || []
    parseExtraConfig(displayForm.value.extraConfigJson)
  } finally {
    loading.value = false
  }
}

const updateCategoryShowOnHome = async (id, val) => {
  try {
    await apiUpdateCategoryShowOnHome(id, val)
    const cat = categories.value.find((c) => c.id === id)
    if (cat) cat.showOnHome = val
    ElMessage.success(val ? '已展示' : '已隐藏')
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

const submit = async () => {
  saving.value = true
  try {
    const extraConfig = {
      homeModules: homeModules.value,
      colors: { ...colors },
      bottomNav: bottomNav.value,
    }
    await saveTenant({
      id: displayForm.value.id,
      showHomeCategories: displayForm.value.showHomeCategories,
      showBottomCategoryNav: bottomNav.value.find((nav) => nav.type === 'category')?.enabled === false ? 0 : 1,
      extraConfigJson: JSON.stringify(extraConfig),
    })
    ElMessage.success('商城装修配置已保存，前台刷新后生效')
  } finally {
    saving.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.toolbar { display:flex; align-items:center; justify-content:space-between; gap:20px; margin-bottom:16px; }
.toolbar h2 { margin:0; color:#303133; font-size:20px; }
.toolbar p { margin:6px 0 0; color:#909399; font-size:13px; }
.config-card { border:1px solid #ebeef5; }
.form-section { padding:8px 0 16px; }
.form-section + .form-section { margin-top:14px; padding-top:20px; border-top:1px solid #ebeef5; }
.form-section h3 { margin:0 0 6px; padding-left:10px; color:#303133; font-size:16px; line-height:1.4; border-left:4px solid var(--el-color-primary); }
.section-desc { margin:0 0 14px; color:#909399; font-size:12px; }
.module-list { display:flex; flex-direction:column; gap:8px; }
.module-item { display:flex; align-items:center; gap:16px; padding:10px 14px; background:#f8f9fa; border-radius:8px; border:1px solid #ebeef5; }
.module-handle { display:flex; gap:4px; }
.module-name { flex:1; font-weight:600; color:#303133; }
.category-toggle { display:flex; align-items:center; justify-content:space-between; padding:8px 0; border-bottom:1px solid #f0f0f0; }
.category-toggle:last-child { border-bottom:0; }
.color-hint { margin-left:8px; color:#909399; font-size:12px; font-family:monospace; }
.nav-item { display:flex; align-items:center; justify-content:space-between; padding:8px 12px; margin-bottom:6px; background:#f8f9fa; border-radius:8px; }
.nav-controls { display:flex; align-items:center; gap:10px; }
.card-footer { display:flex; justify-content:flex-end; }
@media (max-width:700px) { .toolbar{align-items:flex-start;flex-direction:column;gap:10px} }
</style>
