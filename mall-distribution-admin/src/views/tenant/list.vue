<template>
  <div class="page-container">
    <div class="toolbar">
      <div>
        <h2>商城品牌与界面</h2>
        <p>前端 UI 的统一设置入口：运营主体、商城名称、LOGO、主题色、页面样式和首页布局。</p>
      </div>
      <div v-if="tableData[0]" class="toolbar-actions">
        <el-button @click="openTenantDialog(tableData[0])">编辑品牌资料</el-button>
        <el-button type="primary" @click="openDisplayDialog(tableData[0])">配置商城界面</el-button>
      </div>
    </div>

    <el-alert
      title="当前为单商城交付模式。后台不显示新增公司、租户编码等平台方功能，避免客户误建数据空间。"
      type="info"
      :closable="false"
      show-icon
      class="single-tenant-alert"
    />

    <el-table :data="tableData" v-loading="loading" style="width: 100%">
      <el-table-column prop="tenantName" label="运营主体" width="180" />
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
      <el-table-column prop="remark" label="备注" />
      <el-table-column label="操作" fixed="right" width="200">
        <template #default="{ row }">
          <el-button type="primary" link @click="openTenantDialog(row)">编辑</el-button>
          <el-button type="info" link @click="openDisplayDialog(row)">首页布局</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="tenantDialogVisible" title="商城资料与前台样式" width="860px" top="5vh">
      <el-form :model="tenantForm" label-width="110px">
        <el-form-item label="运营主体" required>
          <el-input v-model="tenantForm.tenantName" placeholder="请输入营业执照或实际运营主体名称" />
        </el-form-item>
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
        <el-form-item label="备注">
          <el-input v-model="tenantForm.remark" type="textarea" placeholder="仅后台可见的设置说明" />
        </el-form-item>
        <el-divider content-position="left">经营主体与客服</el-divider>
        <el-form-item label="经营地址">
          <el-input v-model="tenantForm.companyAddress" maxlength="255" placeholder="营业执照登记地址或实际经营地址" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="客服电话"><el-input v-model="tenantForm.servicePhone" maxlength="32" placeholder="前台对外客服电话" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="客服邮箱"><el-input v-model="tenantForm.serviceEmail" maxlength="128" placeholder="前台对外客服邮箱" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="营业执照">
          <div class="logo-upload-row">
            <el-upload action="#" :show-file-list="false" accept="image/*" :http-request="uploadBusinessLicense">
              <div class="license-uploader">
                <el-image v-if="tenantForm.businessLicenseUrl" :src="tenantForm.businessLicenseUrl" fit="contain" />
                <div v-else class="logo-placeholder">点击上传<br />营业执照</div>
              </div>
            </el-upload>
            <div class="logo-help">前台“经营资质”页面展示，单张不超过 5MB。请注意遮挡无需公开的个人信息。</div>
          </div>
        </el-form-item>
        <el-divider content-position="left">网站备案</el-divider>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="ICP备案号"><el-input v-model="tenantForm.icpNumber" maxlength="128" placeholder="例如：粤ICP备XXXXXXXX号" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="公安备案号"><el-input v-model="tenantForm.policeRecordNumber" maxlength="128" placeholder="完成公安备案后填写" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="公安备案链接">
          <el-input v-model="tenantForm.policeRecordUrl" maxlength="512" placeholder="仅允许 https:// 安全链接" />
        </el-form-item>
        <el-divider content-position="left">前台协议</el-divider>
        <el-alert title="以下内容会以纯文本公开展示。正式上线前请按实际经营范围、退换货规则和收集的个人信息交由专业人员审核。" type="warning" :closable="false" class="display-alert" />
        <el-form-item label="用户服务协议"><el-input v-model="tenantForm.userAgreement" type="textarea" :rows="7" maxlength="30000" show-word-limit placeholder="请输入经运营主体确认的完整用户服务协议" /></el-form-item>
        <el-form-item label="隐私政策"><el-input v-model="tenantForm.privacyPolicy" type="textarea" :rows="7" maxlength="30000" show-word-limit placeholder="请输入完整隐私政策" /></el-form-item>
        <el-form-item label="交易与售后规则"><el-input v-model="tenantForm.afterSalePolicy" type="textarea" :rows="7" maxlength="30000" show-word-limit placeholder="请输入完整交易、售后、退款、运费及不适用七天无理由的规则" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tenantDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTenant">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="displayDialogVisible" title="商城首页布局" width="820px" top="5vh">
      <el-alert
        title="这里只调整商城模板、首页分类和底部导航；奖金、业绩和账务规则在各自业务页面管理。"
        type="info"
        :closable="false"
        class="display-alert"
      />
      <el-form :model="displayForm" label-width="150px">
        <el-divider content-position="left">商城布局模板</el-divider>
        <div class="layout-template-grid">
          <button
            v-for="template in layoutTemplateOptions"
            :key="template.value"
            type="button"
            class="layout-template-card"
            :class="{ active: displayForm.layoutTemplate === template.value }"
            @click="applyLayoutTemplate(template)"
          >
            <span class="layout-preview" :class="`layout-preview--${template.value}`">
              <i class="preview-search"></i>
              <i class="preview-categories"></i>
              <i class="preview-products"></i>
              <i class="preview-nav"></i>
            </span>
            <strong>{{ template.label }}</strong>
            <small>{{ template.description }}</small>
          </button>
        </div>

        <el-divider content-position="left">首页与底部导航</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="首页商品分类">
              <div class="switch-with-help">
                <el-switch v-model="displayForm.showHomeCategories" :active-value="1" :inactive-value="0" />
                <span>关闭后首页直接展示精选商品。</span>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="底部分类入口">
              <div class="switch-with-help">
                <el-switch v-model="displayForm.showBottomCategoryNav" :active-value="1" :inactive-value="0" />
                <span>关闭后由四导航自动变为三导航。</span>
              </div>
            </el-form-item>
          </el-col>
        </el-row>

      </el-form>
      <template #footer>
        <el-button @click="displayDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDisplayConfig">保存商城界面</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadShopImage } from '@/api/shop'
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
const currentTenant = ref(null)
const currentDisplayConfig = ref({ layoutTemplate: 'standard' })

const tenantForm = ref({})
const displayForm = ref({})
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

const uploadBusinessLicense = async ({ file }) => {
  const res = await uploadShopImage(file)
  tenantForm.value.businessLicenseUrl = res.data
  ElMessage.success('营业执照图片上传成功')
}

const openDisplayDialog = async (row) => {
  currentTenant.value = row
  const res = await getDisplayConfig(row.id)
  displayForm.value = {
    tenantId: row.id,
    layoutTemplate: 'standard',
    showHomeCategories: 1,
    showBottomCategoryNav: 1,
    ...(res.data || {}),
  }
  displayDialogVisible.value = true
}

const applyLayoutTemplate = (template) => {
  displayForm.value.layoutTemplate = template.value
  displayForm.value.showHomeCategories = template.showHomeCategories
  displayForm.value.showBottomCategoryNav = template.showBottomCategoryNav
}

const submitDisplayConfig = async () => {
  await saveDisplayConfig(displayForm.value)
  currentDisplayConfig.value = { ...displayForm.value }
  ElMessage.success('商城界面已保存，网页和 APP 刷新后生效')
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
.version-form {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}
.display-alert {
  margin-bottom: 16px;
}
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
}
</style>
