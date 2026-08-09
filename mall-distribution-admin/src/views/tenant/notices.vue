<template>
  <div class="page-container notice-page">
    <section class="page-head">
      <div>
        <h2>商城公告</h2>
        <p>发布商城通知、活动消息和物流提醒，启用后会同步展示在客户首页和公告中心。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增公告</el-button>
    </section>

    <section class="filter-card">
      <el-segmented v-model="statusFilter" :options="statusOptions" @change="loadNotices" />
      <span class="notice-count">共 {{ rows.length }} 条公告</span>
    </section>

    <el-card shadow="never" class="list-card">
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="公告内容" min-width="320">
          <template #default="{ row }">
            <div class="notice-content">
              <strong>{{ row.title }}</strong>
              <span>{{ row.content }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="noticeTypeMeta(row.noticeType).tag">{{ noticeTypeMeta(row.noticeType).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="展示顺序" width="110" sortable />
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.updateTime || row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="展示状态" width="110">
          <template #default="{ row }">
            <el-switch
              :model-value="Number(row.status) === 1"
              :loading="statusUpdatingId === row.id"
              @change="(enabled) => changeStatus(row, enabled)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="removeNotice(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !rows.length" description="暂无公告，点击右上角新增公告" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑公告' : '新增公告'" width="660px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="公告标题" prop="title">
          <el-input v-model="form.title" maxlength="128" show-word-limit placeholder="例如：商城服务时间调整通知" />
        </el-form-item>
        <el-form-item label="公告类型" prop="noticeType">
          <el-radio-group v-model="form.noticeType">
            <el-radio-button :value="1">商城通知</el-radio-button>
            <el-radio-button :value="2">活动消息</el-radio-button>
            <el-radio-button :value="3">物流提醒</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="公告内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            maxlength="1000"
            show-word-limit
            resize="vertical"
            placeholder="请填写客户能够直接理解的完整公告内容"
          />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="展示顺序">
              <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="展示状态">
              <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="展示" inactive-text="隐藏" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createShopNotice,
  deleteShopNotice,
  listShopNotices,
  updateShopNotice,
  updateShopNoticeStatus,
} from '@/api/shop'
import { formatDateTime } from '@/utils/dateTime'

const loading = ref(false)
const saving = ref(false)
const statusUpdatingId = ref(null)
const rows = ref([])
const statusFilter = ref('all')
const dialogVisible = ref(false)
const formRef = ref()
const form = ref({})

const statusOptions = [
  { label: '全部', value: 'all' },
  { label: '展示中', value: '1' },
  { label: '已隐藏', value: '0' },
]
const rules = {
  title: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  noticeType: [{ required: true, message: '请选择公告类型', trigger: 'change' }],
  content: [{ required: true, message: '请输入公告内容', trigger: 'blur' }],
}

const emptyForm = () => ({ tenantId: 1, title: '', content: '', noticeType: 1, sort: 100, status: 1, startTime: null, endTime: null })
const noticeTypeMeta = (value) => ({
  1: { label: '商城通知', tag: '' },
  2: { label: '活动消息', tag: 'success' },
  3: { label: '物流提醒', tag: 'warning' },
}[Number(value)] || { label: '商城通知', tag: '' })

const loadNotices = async () => {
  loading.value = true
  try {
    const params = { tenantId: 1 }
    if (statusFilter.value !== 'all') params.status = Number(statusFilter.value)
    const result = await listShopNotices(params)
    rows.value = (result.data || []).map((item) => ({
      ...item,
      noticeType: Number(item.noticeType || 1),
      sort: Number(item.sort || 0),
      status: Number(item.status || 0),
    }))
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  form.value = emptyForm()
  dialogVisible.value = true
}

const openEdit = (row) => {
  form.value = { ...emptyForm(), ...row }
  dialogVisible.value = true
}

const submit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid === false) return
  saving.value = true
  try {
    const payload = {
      ...form.value,
      title: form.value.title.trim(),
      content: form.value.content.trim(),
    }
    if (payload.id) await updateShopNotice(payload.id, payload)
    else await createShopNotice(payload)
    ElMessage.success(payload.id ? '公告修改成功' : '公告发布成功')
    dialogVisible.value = false
    await loadNotices()
  } finally {
    saving.value = false
  }
}

const changeStatus = async (row, enabled) => {
  statusUpdatingId.value = row.id
  try {
    await updateShopNoticeStatus(row.id, enabled ? 1 : 0)
    ElMessage.success(enabled ? '公告已展示' : '公告已隐藏')
    await loadNotices()
  } finally {
    statusUpdatingId.value = null
  }
}

const removeNotice = async (row) => {
  try {
    await ElMessageBox.confirm(
      `删除“${row.title}”后，客户前台将不再展示且无法恢复。`,
      '确认删除公告？',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await deleteShopNotice(row.id)
  ElMessage.success('公告已删除')
  await loadNotices()
}

onMounted(loadNotices)
</script>

<style scoped>
.notice-page { min-height:100%; }
.page-head,.filter-card { display:flex; align-items:center; justify-content:space-between; gap:18px; padding:20px; margin-bottom:16px; background:#fff; border-radius:10px; }
.page-head h2 { margin:0 0 7px; color:#303133; font-size:22px; }
.page-head p { margin:0; color:#909399; font-size:13px; }
.filter-card { padding:14px 20px; }
.notice-count { margin-left:auto; color:#909399; font-size:13px; }
.list-card { border-radius:10px; }
.notice-content { display:flex; min-width:0; flex-direction:column; gap:7px; }
.notice-content strong { overflow:hidden; color:#303133; font-size:15px; text-overflow:ellipsis; white-space:nowrap; }
.notice-content span { display:-webkit-box; overflow:hidden; color:#909399; font-size:13px; line-height:1.5; -webkit-box-orient:vertical; -webkit-line-clamp:2; }
@media (max-width: 760px) {
  .page-head { align-items:flex-start; }
  .filter-card { align-items:flex-start; flex-direction:column; }
  .notice-count { margin-left:0; }
}
</style>
