<template>
  <div class="page-container">
    <!-- 筛选条件 -->
    <div class="search-container">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="排名类型">
          <el-select v-model="searchForm.rankType" placeholder="请选择" style="width:150px" @change="handleSearch">
            <el-option label="个人业绩" :value="1" />
            <el-option label="团队业绩" :value="2" />
            <el-option label="新增代理（人数）" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="排名周期">
          <el-select v-model="searchForm.rankPeriod" placeholder="请选择" style="width:130px" @change="handleSearch">
            <el-option label="日" :value="1" />
            <el-option label="周" :value="2" />
            <el-option label="月" :value="3" />
            <el-option label="年" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="统计日期">
          <el-date-picker
            v-model="searchForm.statDate"
            type="date"
            placeholder="选择日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="ranking-context">
      <el-tag type="primary">排名类型：{{ rankTypeName }}</el-tag>
      <el-tag type="success">排名周期：{{ rankPeriodName }}</el-tag>
      <el-tag>统计日期：{{ formatDate(searchForm.statDate || new Date()) }}</el-tag>
      <span class="context-tip">业绩按有效订单业绩统计；新增代理按新增代理人数统计。</span>
    </div>

    <!-- 排名表格 -->
    <el-card>
      <template #header>
        <span>{{ rankTypeName }}排行榜（{{ rankPeriodName }}）</span>
      </template>
      <el-table :data="rankingList" v-loading="loading" style="width: 100%">
        <el-table-column label="排名" width="80">
          <template #default="{ row }">
            <div class="rank-cell">
              <span v-if="row.ranking <= 3" class="rank-badge" :class="`rank-${row.ranking}`">
                {{ row.ranking }}
              </span>
              <span v-else>{{ row.ranking }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="agentName" label="代理名称" width="120" />
        <el-table-column label="排名类型" width="135">
          <template #default="{ row }">{{ rankTypeText(row.rankType) }}</template>
        </el-table-column>
        <el-table-column label="排名周期" width="100">
          <template #default="{ row }">{{ rankPeriodText(row.rankPeriod) }}</template>
        </el-table-column>
        <el-table-column prop="statDate" label="统计日期" width="120" />
        <el-table-column prop="agentLevelName" label="会员卡级" width="100">
          <template #default="{ row }">
            <el-tag :type="getLevelType(row.agentLevel)">{{ row.agentLevelName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="performanceValue" label="本期排名值" width="150">
          <template #default="{ row }">
            <span style="color: #f56c6c; font-weight: bold">
              {{ row.rankType === 3 ? `${row.performanceValue || 0} 人` : `¥${money(row.performanceValue)}` }}
            </span>
          </template>
        </el-table-column>
        <el-table-column :label="searchForm.rankType === 3 ? '累计新增代理' : '总业绩'" width="145">
          <template #default="{ row }">
            {{ row.rankType === 3 ? `${row.totalPerformance || 0} 人` : `¥${money(row.totalPerformance)}` }}
          </template>
        </el-table-column>
        <el-table-column :label="searchForm.rankType === 3 ? '当月新增代理' : '当月业绩'" width="145">
          <template #default="{ row }">
            {{ row.rankType === 3 ? `${row.currentMonthPerformance || 0} 人` : `¥${money(row.currentMonthPerformance)}` }}
          </template>
        </el-table-column>
        <el-table-column label="当月新增代理" width="125">
          <template #default="{ row }">{{ row.currentMonthNewAgentCount || 0 }} 人</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleViewAgent(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pagination"
        background
        layout="total, prev, pager, next, sizes"
        :total="total"
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        @current-change="handleSearch"
        @size-change="handleSearch"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getPerformanceRanking } from '@/api/performance'

const router = useRouter()
const loading = ref(false)

// 搜索表单
const searchForm = ref({
  rankType: 2,
  rankPeriod: 3,
  statDate: new Date(),
})

// 排名类型名称
const rankTypeName = computed(() => {
  const map = { 1: '个人业绩', 2: '团队业绩', 3: '新增代理（人数）' }
  return map[searchForm.value.rankType] || ''
})

const rankTypeText = (value) => ({ 1: '个人业绩', 2: '团队业绩', 3: '新增代理（人数）' }[Number(value)] || '未知')
const rankPeriodText = (value) => ({ 1: '日榜', 2: '周榜', 3: '月榜', 4: '年榜' }[Number(value)] || '未知')
const money = (value) => Number(value || 0).toFixed(2)

// 排名周期名称
const rankPeriodName = computed(() => {
  const map = { 1: '日榜', 2: '周榜', 3: '月榜', 4: '年榜' }
  return map[searchForm.value.rankPeriod] || ''
})

// 排名列表
const rankingList = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

// 获取等级类型
const getLevelType = (level) => {
  const map = { 1: 'info', 2: '', 3: 'warning', 4: 'danger' }
  return map[level] || 'info'
}

// 搜索
const handleSearch = async () => {
  loading.value = true
  try {
    const res = await getPerformanceRanking({
      rankType: searchForm.value.rankType,
      rankPeriod: searchForm.value.rankPeriod,
      statDate: formatDate(searchForm.value.statDate || new Date()),
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    rankingList.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

// 查看详情
const handleViewAgent = (row) => {
  router.push(`/agent/detail/${row.agentId}`)
}

onMounted(handleSearch)

const formatDate = (date) => {
  const value = new Date(date)
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`
}
</script>

<style lang="scss" scoped>
.rank-cell {
  display: flex;
  align-items: center;
  justify-content: center;
}

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  font-size: 12px;
  font-weight: bold;
  color: #fff;

  &.rank-1 {
    background-color: #f7ba2a;
  }

  &.rank-2 {
    background-color: #c0c4cc;
  }

  &.rank-3 {
    background-color: #cd7f32;
  }
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.ranking-context {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin: 0 0 16px;
  padding: 12px 14px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fff;
}

.context-tip {
  color: #909399;
  font-size: 13px;
}
</style>
