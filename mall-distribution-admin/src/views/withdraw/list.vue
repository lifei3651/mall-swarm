<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="search-container">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="登录账号">
          <el-input v-model="searchForm.memberKey" placeholder="登录账号/手机号" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="提现状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable @change="handleSearch">
            <el-option label="待审核" :value="0" />
            <el-option label="审核通过" :value="1" />
            <el-option label="打款中" :value="2" />
            <el-option label="打款成功" :value="3" />
            <el-option label="审核拒绝" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-alert v-if="searchFeedback" :title="searchFeedback" type="warning" :closable="false" show-icon class="search-feedback" />

    <!-- 统计卡片 -->
    <div class="stat-cards">
      <el-card class="stat-card">
        <div class="stat-title">总提现</div>
        <div class="stat-value">¥{{ stats.total }}</div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-title">待审核</div>
        <div class="stat-value warning">¥{{ stats.pending }}</div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-title">打款成功</div>
        <div class="stat-value success">¥{{ stats.success }}</div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-title">审核拒绝</div>
        <div class="stat-value danger">¥{{ stats.rejected }}</div>
      </el-card>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" v-loading="loading" :empty-text="tableEmptyText" style="width: 100%">
      <el-table-column prop="withdrawNo" label="提现单号" width="180" />
      <el-table-column prop="memberAccount" label="登录账号" width="145" />
      <el-table-column prop="agentName" label="会员名称" width="120" />
      <el-table-column prop="withdrawAmount" label="提现金额" width="120">
        <template #default="{ row }">
          <span style="color: #f56c6c; font-weight: bold">¥{{ row.withdrawAmount }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="withdrawTypeName" label="提现方式" width="100" />
      <el-table-column prop="bankName" label="银行名称" width="120" />
      <el-table-column prop="bankAccount" label="银行账号" width="180" />
      <el-table-column prop="accountName" label="账户姓名" width="100" />
      <el-table-column prop="statusName" label="提现状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">{{ row.statusName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="申请时间" width="160" :formatter="formatDateTimeCell" />
      <el-table-column prop="auditRemark" label="审核备注" />
      <el-table-column label="操作" fixed="right" width="150">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
          <el-button
            v-if="row.status === 1 && [2, 3].includes(row.withdrawType) && store.hasPermission('finance:manage')"
            type="success"
            link
            :loading="payoutLoadingId === row.id"
            @click="handleStartPayout(row)"
          >
            异常重试打款
          </el-button>
          <el-button
            v-if="row.status === 2 && [2, 3].includes(row.withdrawType) && store.hasPermission('finance:manage')"
            type="warning"
            link
            :loading="payoutLoadingId === row.id"
            @click="handleReconcilePayout(row)"
          >
            核对渠道结果
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-container">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <el-dialog v-model="detailVisible" title="提现详情" width="700px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="提现单号">{{ detail.withdrawNo }}</el-descriptions-item>
        <el-descriptions-item label="申请时间">{{ formatDateTime(detail.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="会员">{{ detail.memberAccount || '-' }} · {{ detail.agentName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.memberPhone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="提现金额">¥{{ detail.withdrawAmount }}</el-descriptions-item>
        <el-descriptions-item label="提现方式">{{ detail.withdrawTypeName }}</el-descriptions-item>
        <el-descriptions-item label="收款渠道">{{ detail.bankName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="收款账号">{{ detail.bankAccount || '-' }}</el-descriptions-item>
        <el-descriptions-item label="账户姓名">{{ detail.accountName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前状态"><el-tag :type="getStatusType(detail.status)">{{ detail.statusName }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ formatDateTime(detail.auditTime) }}</el-descriptions-item>
        <el-descriptions-item label="审核说明">{{ detail.auditRemark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="打款时间">{{ formatDateTime(detail.payTime) }}</el-descriptions-item>
        <el-descriptions-item label="打款流水号">{{ detail.payNo || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getWithdrawById, getWithdrawStats, listWithdraws, reconcileWithdrawalPayout, startWithdrawalPayout } from '@/api/withdraw'
import { useAppStore } from '@/store'
import { memberSearchFailureMessage, memberSearchEmptyText, validateMemberSearch } from '@/utils/searchFeedback'
import { useSearchAutoRestore } from '@/utils/searchAutoRestore'
import { formatDateTime, formatDateTimeCell } from '@/utils/dateTime'

const loading = ref(false)
const store = useAppStore()
const route = useRoute()
const payoutLoadingId = ref(null)
const detailVisible = ref(false)
const detail = ref({})
const searchFeedback = ref('')
const tableEmptyText = ref('暂无提现记录')

// 搜索表单
const searchForm = ref({
  memberKey: route.query.memberAccount || '',
  status: null,
  dateRange: [],
})

// 分页
const pagination = ref({
  page: 1,
  size: 10,
  total: 0,
})

// 统计数据
const stats = ref({
  total: '0.00',
  pending: '0.00',
  success: '0.00',
  rejected: '0.00',
})

// 表格数据
const tableData = ref([])
const { markSearchApplied: markKeywordSearchApplied } = useSearchAutoRestore(
  () => searchForm.value.memberKey,
  () => handleSearch(),
)

// 获取状态类型
const getStatusType = (status) => {
  const map = { 0: 'warning', 1: 'success', 2: '', 3: 'success', 4: 'danger' }
  return map[status] || 'info'
}

// 搜索
const handleSearch = () => {
  const validation = validateMemberSearch(searchForm.value.memberKey)
  if (!validation.valid) {
    tableData.value = []
    pagination.value.total = 0
    searchFeedback.value = validation.message
    tableEmptyText.value = '请修改搜索内容后重新查询'
    return
  }
  searchForm.value.memberKey = validation.keyword
  markKeywordSearchApplied(validation.keyword)
  searchFeedback.value = ''
  tableEmptyText.value = memberSearchEmptyText(validation.keyword, '提现记录')
  pagination.value.page = 1
  fetchData()
}

// 重置
const handleReset = () => {
  searchForm.value = {
    memberKey: '',
    status: null,
    dateRange: [],
  }
  handleSearch()
}

// 详情
const handleDetail = async (row) => {
  detail.value = store.hasPermission('finance:manage')
    ? (await getWithdrawById(row.id)).data || row
    : row
  detailVisible.value = true
}

const handleStartPayout = async (row) => {
  try {
    await ElMessageBox.confirm(
      `这笔提现在自动打款时未完成，将通过${row.withdrawTypeName}官方渠道重新处理 ¥${row.withdrawAmount}。系统只在官方结果核对通过后记为成功。`,
      '异常重试打款',
      { type: 'warning', confirmButtonText: '确认重试', cancelButtonText: '返回核对' },
    )
    payoutLoadingId.value = row.id
    const result = (await startWithdrawalPayout(row.id)).data || {}
    ElMessage.success(result.state === 'SUCCESS' ? '官方渠道已核验打款成功' : '渠道已受理，等待最终结果')
    await fetchData()
  } catch (e) {
    // 取消
  } finally {
    payoutLoadingId.value = null
  }
}

const handleReconcilePayout = async (row) => {
  payoutLoadingId.value = row.id
  try {
    const result = (await reconcileWithdrawalPayout(row.id)).data || {}
    ElMessage.success(result.state === 'SUCCESS' ? '官方渠道已核验打款成功' : '已核对，当前仍未取得最终成功结果')
    await fetchData()
  } finally {
    payoutLoadingId.value = null
  }
}

// 分页大小变化
const handleSizeChange = (size) => {
  pagination.value.size = size
  fetchData()
}

// 页码变化
const handleCurrentChange = (page) => {
  pagination.value.page = page
  fetchData()
}

// 获取数据
const fetchData = async () => {
  loading.value = true
  try {
    const params = buildQueryParams()
    const [listRes, statsRes] = await Promise.all([
      listWithdraws({
        ...params,
        pageNum: pagination.value.page,
        pageSize: pagination.value.size,
      }),
      getWithdrawStats(params),
    ])
    tableData.value = listRes.data?.list || []
    pagination.value.total = listRes.data?.total || 0
    updateStats(statsRes.data || {})
  } catch (error) {
    tableData.value = []
    pagination.value.total = 0
    updateStats({})
    if (searchForm.value.memberKey) {
      searchFeedback.value = memberSearchFailureMessage(error, searchForm.value.memberKey, '提现记录')
    }
  } finally {
    loading.value = false
  }
}

const buildQueryParams = () => {
  const [startDate, endDate] = searchForm.value.dateRange || []
  return {
    memberKey: searchForm.value.memberKey || undefined,
    status: searchForm.value.status ?? undefined,
    startDate: startDate ? formatDate(startDate) : undefined,
    endDate: endDate ? formatDate(endDate) : undefined,
  }
}

const updateStats = (data) => {
  stats.value = {
    total: formatAmount(data.totalAmount),
    pending: formatAmount(data.pendingAmount),
    success: formatAmount(data.successAmount),
    rejected: formatAmount(data.rejectedAmount),
  }
}

const formatDate = (date) => {
  const value = new Date(date)
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`
}

const formatAmount = (value) => Number(value || 0).toFixed(2)

onMounted(handleSearch)
</script>

<style lang="scss" scoped>
.search-feedback {
  margin-bottom: 16px;
}
.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 20px;
}

.stat-card {
  .stat-title {
    font-size: 14px;
    color: #909399;
  }

  .stat-value {
    font-size: 24px;
    font-weight: bold;
    color: #303133;
    margin-top: 10px;

    &.warning {
      color: #e6a23c;
    }

    &.success {
      color: #67c23a;
    }

    &.danger {
      color: #f56c6c;
    }
  }
}
</style>
