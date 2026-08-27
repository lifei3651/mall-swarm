<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="search-container">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="登录账号">
          <el-input v-model="searchForm.memberKey" placeholder="登录账号/手机号" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="订单编号">
          <el-input v-model="searchForm.orderNo" placeholder="请输入订单编号" clearable />
        </el-form-item>
        <el-form-item label="佣金状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable @change="handleSearch">
            <el-option label="待结算" :value="0" />
            <el-option label="已结算" :value="1" />
            <el-option label="已取消" :value="2" />
            <el-option label="已退款" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="奖金类型代码">
          <el-input v-model="searchForm.bonusType" placeholder="按客户程序的类型代码查询" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
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
        <div class="stat-title">总佣金</div>
        <div class="stat-value">¥{{ stats.total }}</div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-title">待结算</div>
        <div class="stat-value warning">¥{{ stats.unsettled }}</div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-title">已结算</div>
        <div class="stat-value success">¥{{ stats.settled }}</div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-title">已取消</div>
        <div class="stat-value info">¥{{ stats.cancelled }}</div>
      </el-card>
    </div>

    <!-- 操作栏 -->
    <div class="toolbar">
      <el-button type="success" @click="handleBatchSettle" :disabled="!selectedRows.length">
        批量结算 ({{ selectedRows.length }})
      </el-button>
    </div>

    <!-- 表格 -->
    <el-table
      :data="tableData"
      v-loading="loading"
      @selection-change="handleSelectionChange"
      :empty-text="tableEmptyText"
      style="width: 100%"
    >
      <el-table-column type="selection" label="选择" width="55" />
      <el-table-column prop="recordNo" label="奖金记录编号" width="180" />
      <el-table-column prop="orderNo" label="订单编号" width="180" />
      <el-table-column prop="orderAmount" label="订单金额" width="100">
        <template #default="{ row }">
          ¥{{ row.orderAmount }}
        </template>
      </el-table-column>
      <el-table-column prop="agentMemberAccount" label="获奖登录账号" width="145" />
      <el-table-column prop="agentName" label="获奖会员" width="120" />
      <el-table-column prop="bonusType" label="奖金类型" width="180">
        <template #default="{ row }">
          <el-tag>{{ getBonusName(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="commissionLevel" label="关系深度" width="90" />
      <el-table-column prop="commissionRate" label="奖金比例" width="100">
        <template #default="{ row }">
          {{ (row.commissionRate * 100).toFixed(2) }}%
        </template>
      </el-table-column>
      <el-table-column prop="commissionAmount" label="奖金金额" width="120">
        <template #default="{ row }">
          <span style="color: #67c23a; font-weight: bold">¥{{ row.commissionAmount }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="statusName" label="奖金状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">{{ row.statusName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="奖金产生时间" width="170" :formatter="formatDateTimeCell" />
      <el-table-column label="操作" fixed="right" width="210">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 0"
            type="success"
            link
            @click="handleSettle(row)"
          >
            结算
          </el-button>
          <el-button
            v-if="row.status === 0"
            type="danger"
            link
            @click="handleCancel(row)"
          >
            取消
          </el-button>
          <el-button type="primary" link @click="handleDetail(row)">
            详情
          </el-button>
          <el-button type="primary" link @click="openSourceOrder(row)">
            来源订单
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

    <el-dialog v-model="detailVisible" title="奖金记录详情" width="760px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="记录编号">{{ detail.recordNo }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(detail.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="订单编号">{{ detail.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="订单金额">¥{{ detail.orderAmount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="获奖登录账号">{{ detail.agentMemberAccount || '-' }}</el-descriptions-item>
        <el-descriptions-item label="获奖会员">{{ detail.agentName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="奖金类型">{{ getBonusName(detail) }}</el-descriptions-item>
        <el-descriptions-item label="关系深度">{{ detail.commissionLevel || '-' }}</el-descriptions-item>
        <el-descriptions-item label="奖金比例">{{ percent(detail.commissionRate) }}</el-descriptions-item>
        <el-descriptions-item label="奖金金额">¥{{ detail.commissionAmount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.statusName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结算时间">{{ formatDateTime(detail.settleTime) }}</el-descriptions-item>
        <el-descriptions-item label="下单登录账号">{{ detail.orderMemberAccount || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下单会员">{{ detail.orderUserName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="规则版本ID">{{ detail.ruleVersionId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="取消原因" :span="2">{{ detail.cancelReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="说明" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { memberSearchFailureMessage, memberSearchEmptyText, validateMemberSearch, validateSearchKeyword } from '@/utils/searchFeedback'
import { useSearchAutoRestore } from '@/utils/searchAutoRestore'
import { formatDateTime, formatDateTimeCell } from '@/utils/dateTime'
import { customerBonusName } from '@/utils/customerBonus'
import {
  cancelCommission,
  getCommissionRecords,
  settleCommission,
  settleCommissionBatch,
} from '@/api/commission'

const loading = ref(false)
const route = useRoute()
const router = useRouter()
const selectedRows = ref([])
const detailVisible = ref(false)
const detail = ref({})
const searchFeedback = ref('')
const tableEmptyText = ref('暂无奖金记录')

// 搜索表单
const searchForm = ref({
  memberKey: route.query.memberAccount || '',
  orderNo: route.query.orderNo || '',
  status: route.query.status === undefined || route.query.status === '' ? null : Number(route.query.status),
  bonusType: route.query.bonusType || '',
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
  unsettled: '0.00',
  settled: '0.00',
  cancelled: '0.00',
})

// 表格数据
const tableData = ref([])
const { markSearchApplied: markMemberSearchApplied } = useSearchAutoRestore(
  () => searchForm.value.memberKey,
  () => handleSearch(),
)
const { markSearchApplied: markOrderSearchApplied } = useSearchAutoRestore(
  () => searchForm.value.orderNo,
  () => handleSearch(),
)

const getBonusName = (row) => customerBonusName(row)

// 获取状态类型
const getStatusType = (status) => {
  const map = { 0: 'warning', 1: 'success', 2: 'info', 3: 'danger' }
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
  const orderValidation = validateSearchKeyword(searchForm.value.orderNo, { label: '订单编号', maxLength: 64 })
  if (!orderValidation.valid) {
    tableData.value = []
    pagination.value.total = 0
    searchFeedback.value = orderValidation.message
    tableEmptyText.value = '请修改搜索内容后重新查询'
    return
  }
  searchForm.value.memberKey = validation.keyword
  searchForm.value.orderNo = orderValidation.keyword
  markMemberSearchApplied(validation.keyword)
  markOrderSearchApplied(orderValidation.keyword)
  searchFeedback.value = ''
  tableEmptyText.value = memberSearchEmptyText(validation.keyword, '奖金记录')
  pagination.value.page = 1
  fetchData()
}

// 重置
const handleReset = () => {
  searchForm.value = {
    memberKey: '',
    orderNo: '',
    status: null,
    bonusType: '',
    dateRange: [],
  }
  handleSearch()
}

// 选择变化
const handleSelectionChange = (rows) => {
  selectedRows.value = rows
}

// 结算
const handleSettle = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要结算佣金 ¥${row.commissionAmount} 吗？`, '提示', { type: 'warning' })
    await settleCommission(row.id)
    ElMessage.success('结算成功')
    fetchData()
  } catch (e) {
    // 取消
  }
}

// 批量结算
const handleBatchSettle = async () => {
  try {
    await ElMessageBox.confirm(`确定要结算选中的 ${selectedRows.value.length} 条记录吗？`, '提示', { type: 'warning' })
    await settleCommissionBatch(selectedRows.value.map((item) => item.id))
    ElMessage.success('批量结算成功')
    fetchData()
  } catch (e) {
    // 取消
  }
}

// 取消
const handleCancel = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入取消原因', '取消佣金', {
      inputType: 'textarea',
      inputValidator: (v) => {
        const reason = String(v || '').trim()
        if (!reason) return '请输入取消原因'
        if (reason.length > 200) return '取消原因不能超过200个字符'
        return true
      },
    })
    await cancelCommission(row.id, value.trim())
    ElMessage.success('取消成功')
    fetchData()
  } catch (e) {
    // 取消
  }
}

// 详情
const handleDetail = (row) => {
  detail.value = row
  detailVisible.value = true
}

const openSourceOrder = (row) => {
  router.push({ path: '/audit/orders', query: { orderNo: row.orderNo } })
}

const percent = (value) => `${(Number(value || 0) * 100).toFixed(2)}%`

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
    const res = await getCommissionRecords({
      memberKey: searchForm.value.memberKey || null,
      orderNo: searchForm.value.orderNo || null,
      status: searchForm.value.status,
      bonusType: searchForm.value.bonusType,
      startTime: searchForm.value.dateRange?.[0] || null,
      endTime: searchForm.value.dateRange?.[1] || null,
      pageNum: pagination.value.page,
      pageSize: pagination.value.size,
    })
    const list = res.data?.list || []
    tableData.value = list
    pagination.value.total = res.data?.total || list.length
    updateStats(list)
  } catch (error) {
    tableData.value = []
    pagination.value.total = 0
    updateStats([])
    if (searchForm.value.memberKey) {
      searchFeedback.value = memberSearchFailureMessage(error, searchForm.value.memberKey, '奖金记录')
    }
  } finally {
    loading.value = false
  }
}

const updateStats = (list) => {
  const sum = (items) => items.reduce((total, item) => total + Number(item.commissionAmount || 0), 0)
  stats.value = {
    total: sum(list).toFixed(2),
    unsettled: sum(list.filter((item) => item.status === 0)).toFixed(2),
    settled: sum(list.filter((item) => item.status === 1)).toFixed(2),
    cancelled: sum(list.filter((item) => item.status === 2)).toFixed(2),
  }
}

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

    &.info {
      color: #909399;
    }
  }
}

.toolbar {
  margin-bottom: 20px;
}
</style>
