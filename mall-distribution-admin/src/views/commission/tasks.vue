<template>
  <div class="page-container">
    <div class="toolbar">
      <el-select v-model="query.status" placeholder="任务状态" clearable class="status-select" @change="handleSearch">
        <el-option label="待处理" :value="0" />
        <el-option label="处理中" :value="1" />
        <el-option label="成功" :value="2" />
        <el-option label="失败" :value="3" />
      </el-select>
      <el-input v-model="query.orderId" placeholder="订单ID" clearable class="order-input" @keyup.enter="handleSearch" />
      <el-button :loading="loading" @click="handleSearch">查询</el-button>
      <el-button type="primary" @click="handleProcessBatch">处理待处理任务</el-button>
    </div>

    <el-alert v-if="searchFeedback" :title="searchFeedback" type="warning" :closable="false" show-icon class="search-feedback" />

    <el-table :data="tableData" v-loading="loading" :empty-text="tableEmptyText" style="width: 100%">
      <el-table-column prop="id" label="任务ID" width="90" />
      <el-table-column prop="orderId" label="订单ID" width="110" />
      <el-table-column prop="orderNo" label="订单编号" min-width="170" />
      <el-table-column prop="orderAmount" label="订单金额" width="110">
        <template #default="{ row }">¥{{ row.orderAmount }}</template>
      </el-table-column>
      <el-table-column prop="orderMemberAccount" label="下单会员账号" width="145" />
      <el-table-column prop="orderUserName" label="下单会员" width="120" />
      <el-table-column prop="status" label="计算状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">{{ getStatusName(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="retryCount" label="重试次数" width="100">
        <template #default="{ row }">{{ row.retryCount || 0 }}/{{ row.maxRetryCount || 0 }}</template>
      </el-table-column>
      <el-table-column prop="failReason" label="失败原因" min-width="220" show-overflow-tooltip />
      <el-table-column prop="nextRetryTime" label="下次重试时间" width="175" />
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" fixed="right" width="110">
        <template #default="{ row }">
          <el-button type="primary" link :disabled="row.status === 1 || row.status === 2" @click="handleProcessOne(row)">
            处理
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pagination"
      background
      layout="total, prev, pager, next, sizes"
      :total="pagination.total"
      v-model:current-page="pagination.page"
      v-model:page-size="pagination.size"
      @current-change="fetchData"
      @size-change="fetchData"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listCalculationTasks, processCalculationTask, processCalculationTasks } from '@/api/commission'
import { useSearchAutoRestore } from '@/utils/searchAutoRestore'

const loading = ref(false)
const query = ref({})
const tableData = ref([])
const pagination = ref({ page: 1, size: 10, total: 0 })
const searchFeedback = ref('')
const tableEmptyText = ref('暂无奖金计算任务')
const { markSearchApplied: markOrderSearchApplied } = useSearchAutoRestore(
  () => query.value.orderId,
  () => {
    pagination.value.page = 1
    fetchData()
  },
)

const fetchData = async () => {
  const orderId = String(query.value.orderId || '').trim()
  if (orderId && !/^\d{1,19}$/.test(orderId)) {
    tableData.value = []
    pagination.value.total = 0
    searchFeedback.value = '订单ID只能输入1至19位数字'
    tableEmptyText.value = '请修改订单ID后重新查询'
    return
  }
  query.value.orderId = orderId || undefined
  markOrderSearchApplied(orderId)
  searchFeedback.value = ''
  tableEmptyText.value = orderId ? `未找到订单ID为“${orderId}”的奖金计算任务` : '暂无奖金计算任务'
  loading.value = true
  try {
    const res = await listCalculationTasks({
      ...query.value,
      pageNum: pagination.value.page,
      pageSize: pagination.value.size,
    })
    tableData.value = res.data?.list || []
    pagination.value.total = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.page = 1
  fetchData()
}

const handleProcessBatch = async () => {
  const res = await processCalculationTasks(20)
  ElMessage.success(`已处理 ${res.data || 0} 条任务`)
  fetchData()
}

const handleProcessOne = async (row) => {
  await processCalculationTask(row.id)
  ElMessage.success('任务已处理')
  fetchData()
}

const getStatusName = (status) => {
  const map = { 0: '待处理', 1: '处理中', 2: '成功', 3: '失败' }
  return map[status] || '未知'
}

const getStatusType = (status) => {
  const map = { 0: 'warning', 1: 'primary', 2: 'success', 3: 'danger' }
  return map[status] || 'info'
}

onMounted(fetchData)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.search-feedback { margin-bottom: 16px; }

.status-select {
  width: 160px;
}

.order-input {
  width: 220px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
