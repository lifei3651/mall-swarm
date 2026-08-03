<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="search-container">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="会员账号">
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

    <!-- 未搜索提示 -->
    <el-empty
      v-if="!hasSearched && !loading && !searchFeedback"
      description="请输入登录账号或手机号查询业绩概览"
      style="margin: 40px 0"
    />

    <!-- 已查询但暂无业绩 -->
    <el-empty v-else-if="!searchFeedback && !hasPerformance && !loading" :image-size="140" style="margin: 40px 0">
      <template #description>
        <div class="empty-result">
          <div class="empty-result__title">{{ emptyResultTitle }}</div>
          <div class="empty-result__description">
            当前没有已计入的本人订单或团队订单业绩，可调整时间范围后重新查询。
          </div>
        </div>
      </template>
    </el-empty>

    <!-- 业绩概览卡片 -->
    <div v-if="hasPerformance" class="stat-cards">
      <el-card class="stat-card">
        <div class="stat-title">个人业绩</div>
        <div class="stat-value">¥{{ overview.personalPerformance || '0.00' }}</div>
        <div class="stat-desc">{{ overview.personalOrderCount || 0 }}笔订单</div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-title">团队业绩</div>
        <div class="stat-value primary">¥{{ overview.teamPerformance || '0.00' }}</div>
        <div class="stat-desc">{{ overview.teamOrderCount || 0 }}笔订单</div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-title">团队人数</div>
        <div class="stat-value success">{{ overview.teamMemberCount || 0 }}</div>
        <div class="stat-desc">活跃 {{ overview.activeMemberCount || 0 }} 人</div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-title">增长率</div>
        <div class="stat-value" :class="growthClass">
          {{ overview.performanceGrowthRate || '0' }}%
        </div>
        <div class="stat-desc">较上期</div>
      </el-card>
    </div>

    <!-- 分层业绩 -->
    <el-card v-if="hasPerformance" style="margin-bottom: 20px">
      <template #header>
        <span>分层业绩统计</span>
      </template>
      <el-row :gutter="20">
        <el-col :span="8">
          <div class="level-card">
            <div class="level-title">一级业绩（直属）</div>
            <div class="level-value">¥{{ overview.level1Performance || '0.00' }}</div>
            <div class="level-count">{{ overview.level1MemberCount || 0 }} 人</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="level-card">
            <div class="level-title">二级业绩</div>
            <div class="level-value">¥{{ overview.level2Performance || '0.00' }}</div>
            <div class="level-count">{{ overview.level2MemberCount || 0 }} 人</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="level-card">
            <div class="level-title">三级业绩</div>
            <div class="level-value">¥{{ overview.level3Performance || '0.00' }}</div>
            <div class="level-count">{{ overview.level3MemberCount || 0 }} 人</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 业绩趋势图 -->
    <el-card v-if="hasPerformance">
      <template #header>
        <span>业绩趋势</span>
      </template>
      <div ref="chartRef" style="height: 400px"></div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import echarts from '@/utils/echarts'
import { createRecentDateRange, performanceDateShortcuts } from '@/utils/performanceDateRange'
import { memberSearchFailureMessage, validateMemberSearch } from '@/utils/searchFeedback'
import { getPerformanceOverview } from '@/api/performance'
import { useSearchAutoRestore } from '@/utils/searchAutoRestore'

const route = useRoute()
const chartRef = ref(null)
const loading = ref(false)
const hasSearched = ref(false)
const lastSearchKeyword = ref('')
const lastSearchRange = ref([])
const searchFeedback = ref('')
let chart

// 搜索表单
const searchForm = ref({
  memberKey: route.query.memberAccount || '',
  dateRange: createRecentDateRange(30),
})

// 业绩概览
const overview = ref({})
const restoreInitialState = () => {
  overview.value = {}
  hasSearched.value = false
  lastSearchKeyword.value = ''
  lastSearchRange.value = []
  searchFeedback.value = ''
  if (chart) {
    chart.dispose()
    chart = null
  }
}
const { markSearchApplied: markKeywordSearchApplied } = useSearchAutoRestore(
  () => searchForm.value.memberKey,
  restoreInitialState,
)

const hasPerformance = computed(() => (
  Number(overview.value.personalPerformance || 0) > 0
  || Number(overview.value.teamPerformance || 0) > 0
))

const emptyResultTitle = computed(() => {
  const keyword = lastSearchKeyword.value
  const accountName = String(overview.value.agentName || '').trim()
  const memberLabel = accountName && accountName !== keyword
    ? `会员 ${keyword}（${accountName}）`
    : `会员 ${keyword || accountName}`
  const rangeLabel = lastSearchRange.value.length === 2
    ? `${lastSearchRange.value[0]} 至 ${lastSearchRange.value[1]}`
    : '当前查询时间范围内'
  return `${memberLabel}在 ${rangeLabel} 暂无业绩`
})

// 增长率样式
const growthClass = computed(() => {
  const rate = parseFloat(overview.value.performanceGrowthRate)
  if (rate > 0) return 'growth-up'
  if (rate < 0) return 'growth-down'
  return ''
})

// 搜索
const handleSearch = async () => {
  const validation = validateMemberSearch(searchForm.value.memberKey, { required: true })
  if (!validation.valid) {
    searchFeedback.value = validation.message
    overview.value = {}
    hasSearched.value = false
    return
  }
  const keyword = validation.keyword
  searchForm.value.memberKey = keyword
  markKeywordSearchApplied(keyword)
  const [startDate, endDate] = getDateRange()
  loading.value = true
  hasSearched.value = false
  searchFeedback.value = ''
  try {
    const res = await getPerformanceOverview(keyword, startDate, endDate)
    overview.value = res.data || {}
    lastSearchKeyword.value = keyword
    lastSearchRange.value = [startDate, endDate]
    hasSearched.value = true
    if (hasPerformance.value) {
      await nextTick()
      renderChart()
    } else if (chart) {
      chart.dispose()
      chart = null
    }
  } catch (error) {
    overview.value = {}
    lastSearchKeyword.value = keyword
    lastSearchRange.value = [startDate, endDate]
    hasSearched.value = true
    searchFeedback.value = memberSearchFailureMessage(error, keyword, '业绩概览')
    if (chart) {
      chart.dispose()
      chart = null
    }
  } finally {
    loading.value = false
  }
}

// 初始化图表
const renderChart = () => {
  if (chartRef.value) {
    chart ||= echarts.init(chartRef.value)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['个人业绩', '团队业绩'] },
      xAxis: {
        type: 'category',
        data: ['当前区间'],
      },
      yAxis: { type: 'value' },
      series: [
        {
          name: '个人业绩',
          type: 'line',
          smooth: true,
          data: [Number(overview.value.personalPerformance || 0)],
        },
        {
          name: '团队业绩',
          type: 'line',
          smooth: true,
          data: [Number(overview.value.teamPerformance || 0)],
        },
      ],
    })
  }
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

onMounted(() => {
  if (searchForm.value.memberKey) handleSearch()
})
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
    font-size: 28px;
    font-weight: bold;
    color: #303133;
    margin: 10px 0;

    &.primary {
      color: #409eff;
    }

    &.success {
      color: #67c23a;
    }

    &.growth-up {
      color: #67c23a;
    }

    &.growth-down {
      color: #f56c6c;
    }
  }

  .stat-desc {
    font-size: 12px;
    color: #c0c4cc;
  }
}

.level-card {
  text-align: center;
  padding: 30px;
  background-color: #f5f7fa;
  border-radius: 4px;

  .level-title {
    font-size: 14px;
    color: #909399;
    margin-bottom: 15px;
  }

  .level-value {
    font-size: 28px;
    font-weight: bold;
    color: #303133;
    margin-bottom: 10px;
  }

  .level-count {
    font-size: 14px;
    color: #c0c4cc;
  }
}

.empty-result {
  max-width: 620px;
  text-align: center;

  &__title {
    color: #606266;
    font-size: 16px;
    font-weight: 500;
    line-height: 24px;
  }

  &__description {
    margin-top: 10px;
    color: #909399;
    font-size: 14px;
    line-height: 22px;
  }
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
