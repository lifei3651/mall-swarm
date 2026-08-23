<template>
  <div class="page-container live-room-page">
    <div class="page-heading">
      <div>
        <h2>直播间管理</h2>
        <p>维护直播预告、直播中状态、回放与关联商品。推流密钥只保存在直播服务商，不录入商城。</p>
      </div>
      <el-button type="primary" @click="openDialog()">新建直播间</el-button>
    </div>

    <el-alert
      title="直播广场是可选模块。先创建直播间并关联在售商品，再到“商城设置 → 商城视觉与页面”打开首页展示；切换为直播中前必须配置 HTTPS 观看地址。"
      type="info"
      :closable="false"
      show-icon
      class="page-alert"
    />

    <el-card shadow="never">
      <div class="filters">
        <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 160px" @change="loadRows">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button @click="loadRows">刷新</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="room.id">
        <el-table-column label="直播间" min-width="310">
          <template #default="{ row }">
            <div class="room-summary">
              <el-image :src="row.room.coverUrl" fit="cover" class="room-cover" />
              <div>
                <strong>{{ row.room.title }}</strong>
                <span>{{ row.room.subtitle || '未填写副标题' }}</span>
                <small>{{ row.room.anchorName ? `主播：${row.room.anchorName}` : '未填写主播' }}</small>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="计划时间" min-width="190">
          <template #default="{ row }">
            <div>{{ formatTime(row.room.scheduledStartTime) }}</div>
            <small class="muted">{{ row.room.scheduledEndTime ? `至 ${formatTime(row.room.scheduledEndTime)}` : '未设结束时间' }}</small>
          </template>
        </el-table-column>
        <el-table-column label="关联商品" width="110">
          <template #default="{ row }">{{ row.products?.length || 0 }} 个</template>
        </el-table-column>
        <el-table-column label="热度" width="110">
          <template #default="{ row }">{{ row.room.heatCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><el-tag :type="stateType(row.roomState)">{{ stateLabel(row.roomState) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-dropdown trigger="click" @command="(status) => changeStatus(row, status)">
              <el-button link type="primary">切换状态</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="item in statusOptions" :key="item.value" :command="item.value" :disabled="Number(row.room.status) === item.value">
                    {{ item.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !rows.length" description="还没有直播间，创建后可发布直播预告" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑直播间' : '新建直播间'" width="760px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="112px">
        <el-form-item label="直播封面" prop="coverUrl">
          <el-upload action="#" :show-file-list="false" accept="image/*" :http-request="uploadCover">
            <el-image v-if="form.coverUrl" :src="form.coverUrl" fit="cover" class="cover-preview" />
            <div v-else class="cover-upload">点击上传封面</div>
          </el-upload>
          <span class="field-help">建议 16:9 横图，系统会按现有图片安全规则校验并去除元数据。</span>
        </el-form-item>
        <el-form-item label="直播间标题" prop="title"><el-input v-model="form.title" maxlength="80" show-word-limit /></el-form-item>
        <el-form-item label="副标题"><el-input v-model="form.subtitle" maxlength="160" show-word-limit /></el-form-item>
        <el-form-item label="主播名称"><el-input v-model="form.anchorName" maxlength="60" /></el-form-item>
        <el-form-item label="关联商品" prop="productIds">
          <el-select v-model="form.productIds" multiple filterable collapse-tags :max-collapse-tags="3" style="width: 100%" placeholder="最多选择20个在售商品">
            <el-option v-for="item in products" :key="item.id" :label="`${item.productName}（¥${item.salePrice}）`" :value="item.id" />
          </el-select>
          <span class="field-help">直播预告和直播中必须至少关联一个在售商品；商品按选择顺序展示。</span>
        </el-form-item>
        <el-form-item label="计划时间" required>
          <el-date-picker v-model="timeRange" type="datetimerange" start-placeholder="计划开播" end-placeholder="计划结束（可不填）" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="观看/回放地址">
          <el-input v-model="form.watchUrl" maxlength="2048" placeholder="https://..." />
          <span class="field-help">只填写服务商提供的公开 HTTPS 观看地址，不要填写推流地址、Secret 或鉴权密钥。</span>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="展示热度"><el-input-number v-model="form.heatCount" :min="0" :max="999999999" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="观看人数"><el-input-number v-model="form.viewerCount" :min="0" :max="999999999" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="展示顺序"><el-input-number v-model="form.sortOrder" :min="-9999" :max="9999" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="保存后状态">
          <el-radio-group v-model="form.status">
            <el-radio-button v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存直播间</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listLiveRooms, listShopProducts, saveLiveRoom, updateLiveRoomStatus, uploadShopImage } from '@/api/shop'

const statusOptions = [
  { value: 0, label: '草稿' },
  { value: 1, label: '预告' },
  { value: 2, label: '直播中' },
  { value: 3, label: '已结束' },
  { value: 4, label: '停用' },
]
const stateLabel = (state) => ({ DRAFT: '草稿', UPCOMING: '预告', LIVE: '直播中', ENDED: '已结束', DISABLED: '停用' }[state] || state)
const stateType = (state) => ({ LIVE: 'danger', UPCOMING: 'success', ENDED: 'info', DRAFT: 'warning', DISABLED: 'info' }[state] || 'info')
const formatTime = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '-'
const defaults = () => ({ id: null, title: '', subtitle: '', coverUrl: '', anchorName: '', watchUrl: '', status: 0, viewerCount: 0, heatCount: 0, sortOrder: 0, productIds: [] })

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const rows = ref([])
const products = ref([])
const filters = ref({ status: null })
const form = ref(defaults())
const timeRange = ref([])
const rules = {
  title: [{ required: true, message: '请输入直播间标题', trigger: 'blur' }],
  coverUrl: [{ required: true, message: '请上传直播封面', trigger: 'change' }],
}

const loadRows = async () => {
  loading.value = true
  try {
    const res = await listLiveRooms({ status: filters.value.status })
    rows.value = res.data || []
  } finally { loading.value = false }
}

const loadProducts = async () => {
  const res = await listShopProducts({ status: 1, pageNum: 1, pageSize: 100 })
  products.value = res.data?.list || []
}

const openDialog = (row) => {
  form.value = row ? { ...defaults(), ...row.room, productIds: [...(row.productIds || [])] } : defaults()
  timeRange.value = row
    ? [row.room.scheduledStartTime, row.room.scheduledEndTime].filter(Boolean)
    : []
  dialogVisible.value = true
}

const uploadCover = async ({ file }) => {
  const res = await uploadShopImage(file)
  form.value.coverUrl = res.data
  ElMessage.success('封面已上传')
}

const submit = async () => {
  await formRef.value?.validate()
  if (!timeRange.value.length) return ElMessage.warning('请选择计划开播时间')
  if ([1, 2].includes(Number(form.value.status)) && !form.value.productIds.length) return ElMessage.warning('预告或直播中至少关联一个在售商品')
  if (Number(form.value.status) === 2 && !String(form.value.watchUrl || '').trim()) return ElMessage.warning('直播中状态必须填写 HTTPS 观看地址')
  saving.value = true
  try {
    await saveLiveRoom(form.value.id, {
      ...form.value,
      scheduledStartTime: timeRange.value[0],
      scheduledEndTime: timeRange.value[1] || null,
    })
    ElMessage.success('直播间已保存')
    dialogVisible.value = false
    await loadRows()
  } finally { saving.value = false }
}

const changeStatus = async (row, status) => {
  const label = statusOptions.find((item) => item.value === Number(status))?.label || '目标状态'
  try {
    await ElMessageBox.confirm(`确定将“${row.room.title}”切换为“${label}”吗？`, '切换直播状态', { type: 'warning' })
  } catch { return }
  await updateLiveRoomStatus(row.room.id, status)
  ElMessage.success('直播状态已更新')
  await loadRows()
}

onMounted(async () => {
  await Promise.all([loadRows(), loadProducts()])
})
</script>

<style scoped>
.page-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:20px;margin-bottom:16px}.page-heading h2{margin:0;color:#303133;font-size:22px}.page-heading p{max-width:760px;margin:7px 0 0;color:#909399;font-size:13px;line-height:1.6}.page-alert{margin-bottom:16px}.filters{display:flex;gap:10px;margin-bottom:16px}.room-summary{display:flex;align-items:center;gap:12px;min-width:0}.room-cover{width:104px;height:60px;flex:0 0 auto;border-radius:8px;background:#f2f3f5}.room-summary>div{min-width:0}.room-summary strong,.room-summary span,.room-summary small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.room-summary span,.room-summary small,.muted,.field-help{color:#909399;font-size:12px}.room-summary span{margin-top:5px}.room-summary small{margin-top:3px}.cover-preview,.cover-upload{width:240px;height:135px;border-radius:10px}.cover-preview{display:block}.cover-upload{display:grid;place-items:center;color:#909399;background:#f6f7f9;border:1px dashed #c8cdd5}.field-help{display:block;width:100%;margin-top:6px;line-height:1.5}@media(max-width:900px){.page-heading{flex-direction:column}.page-heading .el-button{width:100%}}
</style>
