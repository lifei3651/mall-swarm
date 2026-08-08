<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="search-container">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="会员查询">
          <el-input v-model="searchForm.memberKey" placeholder="登录账号/手机号/名称" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-alert v-if="searchFeedback" :title="searchFeedback" type="warning" :closable="false" show-icon class="search-feedback" />

    <!-- 表格 -->
    <el-table
      :data="tableData"
      v-loading="loading"
      :empty-text="tableEmptyText"
      style="width: 100%"
    >
      <el-table-column prop="memberAccount" label="登录账号" width="145" />
      <el-table-column prop="agentName" label="会员名称" width="120" />
      <el-table-column prop="totalCommission" label="累计佣金" width="120">
        <template #default="{ row }">
          <span style="color: #409eff">¥{{ row.totalCommission }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="settledCommission" label="已结算" width="120">
        <template #default="{ row }">
          <span style="color: #67c23a">¥{{ row.settledCommission }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="unsettledCommission" label="待结算" width="120">
        <template #default="{ row }">
          <span style="color: #e6a23c">¥{{ row.unsettledCommission }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="availableBalance" label="可提现余额" width="120">
        <template #default="{ row }">
          <span style="color: #f56c6c; font-weight: bold">¥{{ row.availableBalance }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="withdrawnAmount" label="已提现" width="120">
        <template #default="{ row }">
          ¥{{ row.withdrawnAmount }}
        </template>
      </el-table-column>
      <el-table-column prop="totalOrders" label="累计有效件数" width="125" />
      <el-table-column prop="totalTeamMembers" label="团队人数" width="100" />
      <el-table-column label="操作" fixed="right" width="150">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
          <el-button type="success" link @click="handleCommission(row)">佣金记录</el-button>
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
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getAccountByAgentId } from '@/api/account'
import { listAgents } from '@/api/agent'
import { memberSearchEmptyText, validateMemberSearch } from '@/utils/searchFeedback'
import { useSearchAutoRestore } from '@/utils/searchAutoRestore'

const router = useRouter()
const loading = ref(false)
const searchFeedback = ref('')
const tableEmptyText = ref('暂无奖金账户，请先在会员管理中确认会员已进入奖金体系')

// 搜索表单
const searchForm = ref({
  memberKey: '',
})

// 分页
const pagination = ref({
  page: 1,
  size: 10,
  total: 0,
})

// 表格数据
const tableData = ref([])
const { markSearchApplied: markKeywordSearchApplied } = useSearchAutoRestore(
  () => searchForm.value.memberKey,
  () => handleSearch(),
)

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
  tableEmptyText.value = validation.keyword
    ? memberSearchEmptyText(validation.keyword, '奖金账户')
    : '暂无奖金账户，请先在会员管理中确认会员已进入奖金体系'
  pagination.value.page = 1
  fetchData()
}

// 重置
const handleReset = () => {
  searchForm.value = {
    memberKey: '',
  }
  handleSearch()
}

// 详情
const handleDetail = (row) => {
  router.push(`/account/detail/${row.agentId}`)
}

// 佣金记录
const handleCommission = (row) => {
  router.push(`/commission/records?memberAccount=${encodeURIComponent(row.memberAccount || '')}`)
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
    const agentsRes = await listAgents({
      keyword: searchForm.value.memberKey,
      pageNum: pagination.value.page,
      pageSize: pagination.value.size,
    })
    const agents = agentsRes.data?.list || []
    const accounts = await Promise.all(
      agents.map(async (agent) => {
        try {
          const res = await getAccountByAgentId(agent.id)
          return { ...res.data, memberAccount: agent.memberAccount || res.data?.memberAccount, agentName: agent.agentName }
        } catch (e) {
          return null
        }
      })
    )
    tableData.value = accounts.filter(Boolean)
    pagination.value.total = agentsRes.data?.total || tableData.value.length
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.search-feedback {
  margin-bottom: 16px;
}
</style>
