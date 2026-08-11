<template>
  <div class="page-container">
    <el-page-header @back="handleBack">
      <template #content>
        <span>导入结果详情</span>
      </template>
    </el-page-header>

    <el-card style="margin-top: 20px">
      <!-- 批次信息 -->
      <el-descriptions title="批次信息" :column="2" border>
        <el-descriptions-item label="批次编号">{{ result.batchNo }}</el-descriptions-item>
        <el-descriptions-item label="批次名称">{{ result.batchName }}</el-descriptions-item>
        <el-descriptions-item label="导入类型">{{ result.importTypeName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(result.status)">{{ result.statusName }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="总记录数">{{ result.totalCount }}</el-descriptions-item>
        <el-descriptions-item label="成功数">
          <span style="color: #67c23a">{{ result.successCount }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="失败数">
          <span style="color: #f56c6c">{{ result.failCount }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="操作人">{{ result.operatorName }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(result.createTime) }}</el-descriptions-item>
      </el-descriptions>

      <!-- 进度条 -->
      <div class="progress-section">
        <div class="progress-label">导入进度</div>
        <el-progress
          :percentage="progressPercentage"
          :status="progressStatus"
          :stroke-width="20"
          text-inside
        />
      </div>

      <!-- 错误详情 -->
      <div v-if="result.errorMessages?.length" class="error-section">
        <h3>错误详情</h3>
        <el-table :data="errorDetails" style="width: 100%">
          <el-table-column prop="rowNum" label="行号" width="100" />
          <el-table-column prop="errorMsg" label="错误信息" />
          <el-table-column prop="rawData" label="原始数据" />
        </el-table>
      </div>

      <!-- 操作按钮 -->
      <div class="action-section">
        <el-button type="primary" @click="handleBack">返回</el-button>
        <el-button v-if="result.errorFileUrl" type="success" @click="downloadErrorFile">
          下载错误文件
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onBeforeUnmount, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getImportResult } from '@/api/import'
import { formatDateTime } from '@/utils/dateTime'

const router = useRouter()
const route = useRoute()
const batchNo = route.params.batchNo

// 导入结果
const result = ref({})

// 错误详情
const errorDetails = ref([])
let pollTimer = null

// 进度百分比
const progressPercentage = computed(() => {
  if (result.value.progressPercent !== undefined) return result.value.progressPercent
  if (!result.value.totalCount) return 0
  return Math.round((((result.value.successCount || 0) + (result.value.failCount || 0)) / result.value.totalCount) * 100)
})

// 进度状态
const progressStatus = computed(() => {
  if (result.value.status === 1) return undefined
  if (result.value.failCount > 0) return 'warning'
  return result.value.status === 2 ? 'success' : undefined
})

// 获取状态类型
const getStatusType = (status) => {
  const map = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }
  return map[status] || 'info'
}

// 返回
const handleBack = () => {
  router.back()
}

// 下载错误文件
const downloadErrorFile = () => {
  if (result.value.errorFileUrl) {
    window.open(result.value.errorFileUrl)
  }
}

const loadResult = async () => {
  const res = await getImportResult(batchNo)
  result.value = res.data || {}
  errorDetails.value = (result.value.errorMessages || []).map((msg) => {
    const matched = msg.match(/^第(\d+)行[:：]\s*(.*)$/)
    return {
      rowNum: matched ? Number(matched[1]) : '-',
      errorMsg: matched ? matched[2] : msg,
      rawData: '',
    }
  })
  if (![0, 1].includes(result.value.status) && pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

onMounted(async () => {
  await loadResult()
  if ([0, 1].includes(result.value.status)) {
    pollTimer = window.setInterval(loadResult, 1000)
  }
})

onBeforeUnmount(() => {
  if (pollTimer) window.clearInterval(pollTimer)
})
</script>

<style lang="scss" scoped>
.progress-section {
  margin: 30px 0;

  .progress-label {
    font-size: 16px;
    font-weight: bold;
    margin-bottom: 15px;
  }
}

.error-section {
  margin-top: 30px;

  h3 {
    margin-bottom: 15px;
  }
}

.action-section {
  margin-top: 30px;
  display: flex;
  gap: 10px;
}
</style>
