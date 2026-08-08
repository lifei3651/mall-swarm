<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="search-container">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="登录账号">
          <el-input v-model="searchForm.memberKey" placeholder="登录账号/手机号" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="时间范围">
          <div class="date-range-field">
            <el-date-picker
              v-model="searchForm.dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              :shortcuts="performanceDateShortcuts"
              :clearable="false"
            />
            <span class="date-range-hint">默认近30天（含今天）</span>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button type="success" @click="handleSourceDetails">查看该会员业绩来源</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-alert
      v-if="searchFeedback"
      :title="searchFeedback"
      type="warning"
      :closable="false"
      show-icon
      class="search-feedback"
    />

    <!-- 贡献排名 -->
    <el-card>
      <template #header>
        <span>下属业绩贡献排名</span>
      </template>
      <el-table :data="contributions" v-loading="loading" :empty-text="contributionEmptyText" style="width: 100%">
        <el-table-column type="index" label="排名" width="80" />
        <el-table-column prop="subordinateMemberAccount" label="登录账号" width="145" />
        <el-table-column prop="subordinateName" label="下属会员" width="120" />
        <el-table-column prop="relationLevelName" label="关系层级" width="100">
          <template #default="{ row }">
            <el-tag :type="getLevelType(row.relationLevel)">{{ row.relationLevelName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="contributionAmount" label="贡献业绩" width="150">
          <template #default="{ row }">
            <span style="color: #f56c6c; font-weight: bold">¥{{ row.contributionAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="orderCount" label="有效商品件数" width="125" />
        <el-table-column prop="selfPerformance" label="个人业绩" width="150">
          <template #default="{ row }">
            ¥{{ row.selfPerformance }}
          </template>
        </el-table-column>
        <el-table-column prop="teamPerformance" label="团队业绩" width="150">
          <template #default="{ row }">
            ¥{{ row.teamPerformance }}
          </template>
        </el-table-column>
        <el-table-column prop="subordinateCount" label="下级人数" width="100" />
        <el-table-column label="操作" fixed="right" width="150">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleDetails(row)">
              订单明细
            </el-button>
            <el-button type="success" link @click="handleViewAgent(row)">
              查看代理
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 订单明细对话框 -->
    <el-dialog v-model="detailVisible" title="贡献订单明细" width="900px">
      <div class="detail-header">
        <span>会员：{{ currentContribution?.subordinateMemberAccount || '-' }} · {{ currentContribution?.subordinateName }}</span>
        <span style="margin-left: 20px">贡献业绩：¥{{ currentContribution?.contributionAmount }}</span>
      </div>
      <el-table :data="orderDetails" empty-text="该会员在所选时间内暂无业绩来源明细" style="width: 100%; margin-top: 15px">
        <el-table-column prop="orderNo" label="订单编号" width="180" />
        <el-table-column prop="orderAmount" label="订单金额" width="120">
          <template #default="{ row }">
            ¥{{ row.orderAmount }}
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="商品名称" />
        <el-table-column prop="quantity" label="数量" width="80" />
        <el-table-column prop="performanceAmount" label="业绩金额" width="120">
          <template #default="{ row }">
            <span style="color: #67c23a">¥{{ row.performanceAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="orderTime" label="下单时间" width="160" :formatter="formatDateTimeCell" />
        <el-table-column prop="performanceType" label="来源类型" width="100">
          <template #default="{ row }">{{ row.performanceType === 1 ? '个人订单' : '团队订单' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="业绩状态" width="100">
          <template #default="{ row }">{{ row.status === 1 ? '有效' : '冲正' }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { getSubordinateContributions, getSubordinateOrderDetails, getPerformanceSourceDetails } from '@/api/performance'
import { createRecentDateRange, performanceDateShortcuts } from '@/utils/performanceDateRange'
import { memberSearchFailureMessage, validateMemberSearch } from '@/utils/searchFeedback'
import { useSearchAutoRestore } from '@/utils/searchAutoRestore'
import { formatDateTimeCell } from '@/utils/dateTime'

const router = useRouter()
const loading = ref(false)
const detailVisible = ref(false)
const currentContribution = ref(null)
const searchFeedback = ref('')
const lastSearchKeyword = ref('')

// 搜索表单
const searchForm = ref({
  memberKey: '',
  dateRange: createRecentDateRange(30),
})

// 贡献列表
const contributions = ref([])

// 订单明细
const orderDetails = ref([])
const contributionEmptyText = ref('请先输入会员信息查询下属业绩贡献')
const restoreInitialState = () => {
  contributions.value = []
  orderDetails.value = []
  currentContribution.value = null
  detailVisible.value = false
  lastSearchKeyword.value = ''
  searchFeedback.value = ''
  contributionEmptyText.value = '请先输入会员信息查询下属业绩贡献'
}
const { markSearchApplied: markKeywordSearchApplied } = useSearchAutoRestore(
  () => searchForm.value.memberKey,
  restoreInitialState,
)

// 获取层级类型
const getLevelType = (level) => {
  const map = { 1: 'success', 2: 'warning', 3: 'danger' }
  return map[level] || 'info'
}

// 搜索
const handleSearch = async () => {
  const validation = validateMemberSearch(searchForm.value.memberKey, { required: true })
  if (!validation.valid) {
    contributions.value = []
    searchFeedback.value = validation.message
    contributionEmptyText.value = '请修改搜索内容后重新查询'
    return
  }
  const keyword = validation.keyword
  searchForm.value.memberKey = keyword
  markKeywordSearchApplied(keyword)
  lastSearchKeyword.value = keyword
  searchFeedback.value = ''
  loading.value = true
  try {
    const [startDate, endDate] = getDateRange()
    const res = await getSubordinateContributions(keyword, startDate, endDate)
    contributions.value = res.data?.list || []
    contributionEmptyText.value = `会员“${keyword}”在所选时间内暂无下属业绩贡献`
  } catch (error) {
    contributions.value = []
    searchFeedback.value = memberSearchFailureMessage(error, keyword, '业绩贡献')
    contributionEmptyText.value = '未能完成查询，请核对搜索内容后重试'
  } finally {
    loading.value = false
  }
}

// 订单明细
const handleDetails = async (row) => {
  currentContribution.value = row
  const [startDate, endDate] = getDateRange()
  try {
    const res = await getSubordinateOrderDetails(lastSearchKeyword.value, row.subordinateAgentId, startDate, endDate)
    orderDetails.value = res.data?.list || []
    detailVisible.value = true
  } catch (error) {
    searchFeedback.value = memberSearchFailureMessage(error, lastSearchKeyword.value, '贡献订单明细')
  }
}

const handleSourceDetails = async () => {
  const validation = validateMemberSearch(searchForm.value.memberKey, { required: true })
  if (!validation.valid) {
    searchFeedback.value = validation.message
    return
  }
  const keyword = validation.keyword
  searchForm.value.memberKey = keyword
  markKeywordSearchApplied(keyword)
  searchFeedback.value = ''
  const [startDate, endDate] = getDateRange()
  try {
    const res = await getPerformanceSourceDetails(keyword, startDate, endDate)
    currentContribution.value = { subordinateMemberAccount: keyword, subordinateName: '全部业绩来源', contributionAmount: '全部来源' }
    orderDetails.value = res.data?.list || []
    detailVisible.value = true
  } catch (error) {
    orderDetails.value = []
    searchFeedback.value = memberSearchFailureMessage(error, keyword, '业绩来源')
  }
}

// 查看代理
const handleViewAgent = (row) => {
  router.push(`/agent/detail/${row.subordinateAgentId}`)
}

const getDateRange = () => {
  const dateRange = searchForm.value.dateRange?.length === 2
    ? searchForm.value.dateRange
    : createRecentDateRange(30)
  return dateRange.map(formatDate)
}

const formatDate = (date) => {
  const d = new Date(date)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
</script>

<style lang="scss" scoped>
.detail-header {
  font-size: 16px;
  font-weight: bold;
}

.search-feedback {
  margin-bottom: 16px;
}

.date-range-field {
  display: flex;
  align-items: center;
  gap: 10px;
}

.date-range-hint {
  flex: none;
  color: #909399;
  font-size: 12px;
}
</style>
