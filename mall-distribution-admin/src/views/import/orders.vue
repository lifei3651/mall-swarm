<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <span>批量导入订单</span>
      </template>

      <!-- 导入说明 -->
      <el-alert
        title="导入说明"
        type="info"
        :closable="false"
        style="margin-bottom: 20px"
      >
        <template #default>
          <p>1. 请按照模板格式准备Excel文件</p>
          <p>2. 必填字段：订单编号、订单金额、下单时间、订单归属登录账号</p>
          <p>3. 选填字段：商品名称、商品数量、备注</p>
          <p>4. 导入后系统会自动计算佣金并记录业绩</p>
        </template>
      </el-alert>

      <!-- 下载模板 -->
      <div class="template-section">
        <el-button type="primary" @click="downloadTemplate">
          <el-icon><Download /></el-icon>
          下载导入模板
        </el-button>
      </div>

      <!-- 上传文件 -->
      <el-upload
        ref="uploadRef"
        class="upload-section"
        drag
        :auto-upload="false"
        :limit="1"
        :on-exceed="handleExceed"
        :on-change="handleChange"
        accept=".xlsx,.xls,.csv"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          将文件拖到此处，或<em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            只能上传 .xlsx / .xls / .csv 文件，且不超过 10MB
          </div>
        </template>
      </el-upload>

      <!-- 操作按钮 -->
      <div class="action-section">
        <el-button type="success" @click="handleImport" :loading="loading" :disabled="!file">
          <el-icon><Upload /></el-icon>
          开始导入
        </el-button>
      </div>

      <div v-if="loading || importResult" class="progress-panel">
        <div class="progress-header">
          <span>{{ loading ? '正在逐条导入' : '导入进度' }}</span>
          <span>{{ processedCount }} / {{ importResult?.totalCount || 0 }} 条</span>
        </div>
        <el-progress :percentage="progressPercentage" :status="progressStatus" :stroke-width="18" text-inside />
        <div v-if="loading" class="progress-tip">页面会自动刷新成功、失败和总进度，请勿重复提交。</div>
      </div>

      <!-- 导入结果 -->
      <div v-if="importResult" class="result-section">
        <el-divider />
        <h3>导入结果</h3>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="批次编号">{{ importResult.batchNo }}</el-descriptions-item>
          <el-descriptions-item label="总记录数">{{ importResult.totalCount }}</el-descriptions-item>
          <el-descriptions-item label="成功数">
            <span style="color: #67c23a">{{ importResult.successCount }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="失败数">
            <span style="color: #f56c6c">{{ importResult.failCount }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 错误信息 -->
        <div v-if="importResult.errorMessages?.length" class="error-section">
          <h4>错误信息</h4>
          <el-scrollbar max-height="200px">
            <p v-for="(msg, index) in importResult.errorMessages" :key="index" class="error-msg">
              {{ msg }}
            </p>
          </el-scrollbar>
        </div>

        <el-button type="primary" style="margin-top: 15px" @click="viewResult">
          查看详情
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Download, UploadFilled, Upload } from '@element-plus/icons-vue'
import { getImportResult, importOrdersByFile } from '@/api/import'

const router = useRouter()
const uploadRef = ref(null)
const loading = ref(false)
const file = ref(null)
const importResult = ref(null)
let progressTimer = null

const processedCount = computed(() => importResult.value?.processedCount
  ?? ((importResult.value?.successCount || 0) + (importResult.value?.failCount || 0)))
const progressPercentage = computed(() => importResult.value?.progressPercent
  ?? (importResult.value?.totalCount ? Math.round(processedCount.value * 100 / importResult.value.totalCount) : 0))
const progressStatus = computed(() => !loading.value && (importResult.value?.failCount || 0) > 0 ? 'warning' : undefined)

const stopPolling = () => {
  if (progressTimer) window.clearInterval(progressTimer)
  progressTimer = null
}

const pollProgress = async (batchNo) => {
  try {
    const res = await getImportResult(batchNo, true)
    if (res.data) importResult.value = res.data
  } catch {
    // 文件仍在解析、批次尚未创建时保持安静，下一轮继续查询。
  }
}

const startPolling = (batchNo) => {
  stopPolling()
  pollProgress(batchNo)
  progressTimer = window.setInterval(() => pollProgress(batchNo), 800)
}

// 下载模板
const downloadTemplate = () => {
  downloadCsv('订单导入模板.csv', '订单编号,订单金额,下单时间,订单归属登录账号,商品名称,商品数量,备注\n')
}

// 文件变化
const handleChange = (uploadFile) => {
  file.value = uploadFile.raw
}

// 超出限制
const handleExceed = () => {
  ElMessage.warning('只能上传一个文件')
}

// 开始导入
const handleImport = async () => {
  if (!file.value) {
    ElMessage.warning('请先选择文件')
    return
  }

  loading.value = true
  importResult.value = null
  const batchNo = `BATCHWEB${Date.now()}${Math.random().toString(36).slice(2, 8)}`.toUpperCase()
  startPolling(batchNo)
  try {
    const res = await importOrdersByFile(file.value, 1, 'admin', batchNo)
    importResult.value = res.data
    ElMessage.success('导入完成')
  } finally {
    stopPolling()
    await pollProgress(batchNo)
    loading.value = false
  }
}

onBeforeUnmount(stopPolling)

// 查看详情
const viewResult = () => {
  if (importResult.value) {
    router.push(`/import/result/${importResult.value.batchNo}`)
  }
}

const downloadCsv = (filename, content) => {
  const blob = new Blob([`\uFEFF${content}`], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  link.click()
  URL.revokeObjectURL(link.href)
}
</script>

<style lang="scss" scoped>
.template-section {
  margin-bottom: 20px;
}

.upload-section {
  margin-bottom: 20px;
}

.action-section {
  margin-bottom: 20px;
}

.progress-panel {
  margin: 18px 0;
  padding: 16px 18px;
  border: 1px solid #d9ecff;
  border-radius: 10px;
  background: #f5faff;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  color: #303133;
  font-weight: 600;
}

.progress-tip {
  margin-top: 9px;
  color: #909399;
  font-size: 13px;
}

.result-section {
  .error-section {
    margin-top: 15px;

    h4 {
      color: #f56c6c;
      margin-bottom: 10px;
    }

    .error-msg {
      color: #f56c6c;
      font-size: 14px;
      margin: 5px 0;
    }
  }
}
</style>
