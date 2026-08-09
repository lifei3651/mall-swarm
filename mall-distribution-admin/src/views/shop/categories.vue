<template>
  <div class="page-container category-page">
    <div class="page-head">
      <div>
        <h2>商品分类管理</h2>
        <p>商品发布时只能选择这里维护的分类；停用后前台不再展示，未被商品使用的分类可以删除。</p>
      </div>
      <el-button type="primary" :icon="Plus" size="large" @click="openDialog()">新增分类</el-button>
    </div>

    <div class="filter-card">
      <el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索分类名称或备注" style="width:300px" />
      <el-radio-group v-model="statusFilter">
        <el-radio-button label="all">全部</el-radio-button>
        <el-radio-button :label="1">已启用</el-radio-button>
        <el-radio-button :label="0">已停用</el-radio-button>
      </el-radio-group>
      <span class="category-count">共 {{ filteredRows.length }} 个分类</span>
    </div>

    <el-alert v-if="!keywordValidation.valid" :title="keywordValidation.message" type="warning" :closable="false" show-icon class="search-feedback" />

    <el-table :data="filteredRows" v-loading="loading" :empty-text="categoryEmptyText" border>
      <el-table-column label="分类信息" min-width="260">
        <template #default="{ row }">
          <div class="category-cell">
            <el-image v-if="row.iconUrl" class="category-icon" :src="row.iconUrl" fit="cover" />
            <div v-else class="category-icon fallback"><Picture :size="20" /></div>
            <div><strong>{{ row.categoryName }}</strong><small>ID：{{ row.id }}</small></div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="展示排序" width="100" align="center" />
      <el-table-column label="启用状态" width="110" align="center">
        <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '已启用' : '已停用' }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="240"><template #default="{ row }">{{ row.remark || '-' }}</template></el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="180" :formatter="formatDateTimeCell" />
      <el-table-column label="操作" fixed="right" width="250">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
          <el-button link type="danger" @click="removeCategory(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑商品分类' : '新增商品分类'" width="560px" destroy-on-close>
      <el-form :model="form" label-width="92px">
        <el-form-item label="分类名称" required><el-input v-model="form.categoryName" maxlength="64" show-word-limit placeholder="例如：护理套装" /></el-form-item>
        <el-form-item label="分类图标">
          <div class="icon-uploader-wrap">
            <el-upload action="#" :show-file-list="false" accept=".jpg,.jpeg,.png,.webp,.gif,image/jpeg,image/png,image/webp,image/gif" :http-request="uploadIcon">
              <el-image v-if="form.iconUrl" class="icon-preview" :src="form.iconUrl" fit="cover">
                <template #error><div class="icon-load-error"><Picture :size="18" /><span>图片加载失败</span><small>点击重新上传</small></div></template>
              </el-image>
              <div v-else class="icon-uploader"><el-icon><Plus /></el-icon><span>上传图标</span></div>
            </el-upload>
            <div class="field-help">支持 JPG、PNG、WEBP、GIF，单张不超过5MB；会显示在商城分类页。</div>
          </div>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" :max="999999" /><span class="inline-help">数值越大越靠前</span></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="256" show-word-limit /></el-form-item>
      </el-form>
      <el-alert v-if="form.id" title="修改分类名称后，该分类下现有商品会同步改为新名称。" type="info" :closable="false" show-icon />
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveCategory">保存分类</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Picture, Plus, Search } from '@element-plus/icons-vue'
import { createShopCategory, deleteShopCategory, listShopCategories, updateShopCategory, updateShopCategoryStatus, uploadShopImage } from '@/api/shop'
import { validateSearchKeyword } from '@/utils/searchFeedback'
import { formatDateTimeCell } from '@/utils/dateTime'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const keyword = ref('')
const statusFilter = ref('all')
const dialogVisible = ref(false)
const defaultForm = () => ({ id: null, tenantId: 1, categoryName: '', iconUrl: '', sort: 0, status: 1, remark: '' })
const form = ref(defaultForm())
const keywordValidation = computed(() => validateSearchKeyword(keyword.value, { label: '分类关键词' }))
const categoryEmptyText = computed(() => {
  if (!keywordValidation.value.valid) return '请修改搜索内容后重新查询'
  const value = keywordValidation.value.keyword
  return value ? `未找到与“${value}”匹配的商品分类` : '暂无商品分类'
})

const filteredRows = computed(() => {
  if (!keywordValidation.value.valid) return []
  const search = keyword.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    const matchesStatus = statusFilter.value === 'all' || Number(row.status) === Number(statusFilter.value)
    const matchesKeyword = !search || `${row.categoryName || ''} ${row.remark || ''}`.toLowerCase().includes(search)
    return matchesStatus && matchesKeyword
  })
})

const loadCategories = async () => {
  loading.value = true
  try { rows.value = (await listShopCategories()).data || [] } finally { loading.value = false }
}

const openDialog = (row = null) => {
  form.value = row ? { ...defaultForm(), ...row } : defaultForm()
  dialogVisible.value = true
}

const normalizeMediaUrl = (value) => {
  const url = String(value || '').trim()
  if (!url) return ''
  if (/^(?:https?:|data:|blob:)/i.test(url)) return url
  return url.startsWith('/') ? url : `/${url}`
}

const uploadIcon = async ({ file, onSuccess, onError }) => {
  const supportedTypes = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif'])
  if (!supportedTypes.has(String(file?.type || '').toLowerCase())) {
    const error = new Error('分类图标仅支持 JPG、PNG、WEBP 或 GIF 格式')
    onError?.(error)
    return ElMessage.error(error.message)
  }
  if (file.size > 5 * 1024 * 1024) {
    const error = new Error(`图片大小为 ${(file.size / 1024 / 1024).toFixed(2)}MB，不能超过5MB`)
    onError?.(error)
    return ElMessage.error(error.message)
  }
  try {
    const url = normalizeMediaUrl((await uploadShopImage(file)).data)
    if (!url) throw new Error('图片上传成功，但服务器没有返回图片地址')
    form.value.iconUrl = url
    onSuccess?.({ url }, file)
    ElMessage.success('分类图标上传成功')
  } catch (error) {
    onError?.(error)
  }
}

const saveCategory = async () => {
  const categoryName = form.value.categoryName?.trim()
  if (!categoryName) return ElMessage.warning('请输入分类名称')
  saving.value = true
  try {
    const payload = { ...form.value, categoryName }
    if (payload.id) await updateShopCategory(payload.id, payload)
    else await createShopCategory(payload)
    ElMessage.success(payload.id ? '分类修改成功' : '分类添加成功')
    dialogVisible.value = false
    await loadCategories()
  } finally { saving.value = false }
}

const toggleStatus = async (row) => {
  const nextStatus = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(nextStatus === 1 ? `确定启用“${row.categoryName}”吗？` : `停用后前台将隐藏“${row.categoryName}”分类入口，确定继续吗？`, '状态确认', { type: 'warning' })
  } catch {
    return
  }
  await updateShopCategoryStatus(row.id, nextStatus)
  ElMessage.success(nextStatus === 1 ? '分类已启用' : '分类已停用')
  await loadCategories()
}

const removeCategory = async (row) => {
  try {
    await ElMessageBox.confirm(
      `删除“${row.categoryName}”后无法恢复。若仍有商品使用该分类，系统会阻止删除。`,
      '确认删除分类？',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await deleteShopCategory(row.id)
  ElMessage.success('分类已删除')
  await loadCategories()
}

onMounted(loadCategories)
</script>

<style lang="scss" scoped>
.category-page { min-height:100%; }
.page-head,.filter-card { display:flex; align-items:center; justify-content:space-between; gap:18px; padding:20px; margin-bottom:16px; background:#fff; border-radius:8px; }
.page-head h2 { margin:0 0 8px; color:#303133; font-size:22px; }
.page-head p { margin:0; color:#909399; font-size:13px; }
.filter-card { justify-content:flex-start; padding:16px 20px; }
.search-feedback { margin-bottom:16px; }
.category-count { margin-left:auto; color:#909399; font-size:13px; }
.category-cell { display:flex; align-items:center; gap:12px; }
.category-cell > div:last-child { display:flex; flex-direction:column; gap:5px; }
.category-cell small { color:#909399; font-size:12px; }
.category-icon { width:52px; height:52px; border-radius:9px; flex:0 0 auto; }
.category-icon.fallback { display:flex; align-items:center; justify-content:center; color:#a8abb2; background:#f2f3f5; }
.icon-uploader-wrap { display:flex; align-items:center; gap:14px; }
.icon-preview,.icon-uploader { width:92px; height:92px; border-radius:8px; }
.icon-load-error { display:flex; width:100%; height:100%; flex-direction:column; align-items:center; justify-content:center; gap:4px; color:#f56c6c; background:#fef0f0; font-size:12px; text-align:center; }
.icon-load-error small { color:#909399; font-size:11px; }
.icon-uploader { display:flex; flex-direction:column; align-items:center; justify-content:center; gap:6px; color:#909399; border:1px dashed #c0ccda; cursor:pointer; font-size:12px; }
.field-help { max-width:260px; color:#909399; font-size:12px; line-height:1.7; }
.inline-help { margin-left:10px; color:#909399; font-size:12px; }
@media(max-width:760px){.page-head,.filter-card{align-items:flex-start;flex-direction:column}.category-count{margin-left:0}}
</style>
