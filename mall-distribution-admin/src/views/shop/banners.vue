<template>
  <div class="page-container banner-page">
    <div class="toolbar">
      <div>
        <h2>首页 Banner</h2>
        <p>管理首页顶部轮播图；只有启用且在有效时间内的内容会展示给客户。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增 Banner</el-button>
    </div>

    <el-alert
      title="是否展示轮播由“商城视觉与页面”的首页模块开关统一控制；这里负责图片、链接和展示顺序。"
      type="info"
      :closable="false"
      show-icon
      class="banner-alert"
    />

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="预览" width="170">
          <template #default="{ row }">
            <el-image :src="row.imageUrl" fit="cover" class="banner-thumb" />
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="跳转" min-width="150">
          <template #default="{ row }">{{ linkTypeName(row.linkType) }}{{ row.linkValue ? `：${row.linkValue}` : '' }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="顺序" width="80" sortable />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="(value) => toggleStatus(row, value)" />
          </template>
        </el-table-column>
        <el-table-column label="有效期" width="210">
          <template #default="{ row }">{{ formatDate(row.startTime) }} 至 {{ formatDate(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="toggleStatus(row, row.status !== 1)">{{ row.status === 1 ? '隐藏' : '启用' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !rows.length" description="还没有 Banner，新增一张活动图吧" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑 Banner' : '新增 Banner'" width="720px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="Banner图片" prop="imageUrl">
          <div class="image-editor">
            <el-upload action="#" :show-file-list="false" accept="image/*" :http-request="uploadImage">
              <el-image v-if="form.imageUrl" :src="form.imageUrl" fit="cover" class="banner-preview" />
              <div v-else class="upload-placeholder">点击上传图片</div>
            </el-upload>
            <span class="form-help">建议使用 16:6 横幅图，单张不超过 5MB。</span>
          </div>
        </el-form-item>
        <el-form-item label="标题" prop="title"><el-input v-model="form.title" maxlength="64" show-word-limit /></el-form-item>
        <el-form-item label="点击跳转">
          <el-select v-model="form.linkType" style="width:150px">
            <el-option label="不跳转" value="none" />
            <el-option label="商品详情" value="product" />
            <el-option label="商品分类" value="category" />
            <el-option label="外部链接" value="url" />
          </el-select>
          <el-input v-if="form.linkType !== 'none'" v-model="form.linkValue" :placeholder="linkPlaceholder" class="link-input" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="展示顺序"><el-input-number v-model="form.sort" :min="0" :max="9999" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="启用状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="展示" inactive-text="隐藏" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="展示时间">
          <el-date-picker v-model="form.timeRange" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" clearable />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createShopBanner, listShopBanners, updateShopBanner, updateShopBannerStatus, uploadShopImage } from '@/api/shop'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const dialogVisible = ref(false)
const formRef = ref()
const form = ref({})
const rules = { imageUrl: [{ required: true, message: '请上传 Banner 图片', trigger: 'change' }], title: [{ required: true, message: '请输入标题', trigger: 'blur' }] }
const linkPlaceholder = computed(() => ({ product: '填写商品 ID', category: '填写分类名称', url: '填写 https:// 开头的链接' }[form.value.linkType] || ''))

const emptyForm = () => ({ tenantId: 1, title: '', imageUrl: '', linkType: 'none', linkValue: '', sort: 100, status: 1, timeRange: [], remark: '' })
const formatDate = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '不限时间'
const linkTypeName = (value) => ({ none: '不跳转', product: '商品详情', category: '商品分类', url: '外部链接' }[value] || '不跳转')
const normalizeRow = (row) => ({ ...row, timeRange: [row.startTime, row.endTime].filter(Boolean) })

const fetchRows = async () => {
  loading.value = true
  try {
    const res = await listShopBanners({ tenantId: 1 })
    rows.value = (res.data || []).map(normalizeRow)
  } finally { loading.value = false }
}

const openCreate = () => { form.value = emptyForm(); dialogVisible.value = true }
const openEdit = (row) => { form.value = { ...emptyForm(), ...row, timeRange: row.timeRange || [row.startTime, row.endTime].filter(Boolean) }; dialogVisible.value = true }
const uploadImage = async ({ file }) => {
  const res = await uploadShopImage(file)
  form.value.imageUrl = res.data
  ElMessage.success('图片上传成功')
}
const toggleStatus = async (row, enabled) => {
  await updateShopBannerStatus(row.id, enabled ? 1 : 0)
  row.status = enabled ? 1 : 0
  ElMessage.success(enabled ? 'Banner 已展示' : 'Banner 已隐藏')
}
const submit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid === false) return
  saving.value = true
  try {
    const payload = { ...form.value, startTime: form.value.timeRange?.[0] || null, endTime: form.value.timeRange?.[1] || null }
    delete payload.timeRange
    if (payload.linkType === 'none') payload.linkValue = ''
    if (payload.id) await updateShopBanner(payload.id, payload)
    else await createShopBanner(payload)
    ElMessage.success('Banner 已保存')
    dialogVisible.value = false
    await fetchRows()
  } finally { saving.value = false }
}

onMounted(fetchRows)
</script>

<style scoped>
.toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:16px}.toolbar h2{margin:0;color:#303133;font-size:20px}.toolbar p{margin:6px 0 0;color:#909399;font-size:13px}.banner-alert{margin-bottom:16px}.banner-thumb{width:145px;height:52px;border-radius:7px;background:#f5f7fa}.image-editor{display:flex;align-items:center;gap:14px}.banner-preview,.upload-placeholder{display:grid;width:260px;height:98px;place-items:center;overflow:hidden;border:1px dashed #c0c4cc;border-radius:8px;background:#fafafa}.form-help{color:#909399;font-size:12px}.link-input{width:calc(100% - 166px);margin-left:10px}
</style>
