<template>
  <div class="page-container finance-page">
    <div class="toolbar">
      <el-radio-group v-model="range" @change="handleRangeChange">
        <el-radio-button value="today">当日</el-radio-button>
        <el-radio-button value="7days">7天</el-radio-button>
        <el-radio-button value="month">当月</el-radio-button>
        <el-radio-button value="total">总计</el-radio-button>
        <el-radio-button value="custom">自定义</el-radio-button>
      </el-radio-group>
      <el-date-picker
        v-if="range === 'custom'"
        v-model="customDates"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        @change="fetchSummary"
      />
      <el-button type="primary" @click="fetchSummary">刷新</el-button>
      <el-button @click="handleExport">导出Excel</el-button>
    </div>

    <div class="summary-grid" v-loading="loading">
      <div class="summary-item">
        <div class="label">订单数</div>
        <div class="value">{{ summary.orderCount || 0 }}</div>
      </div>
      <div class="summary-item">
        <div class="label">成交额</div>
        <div class="value primary">¥{{ money(summary.payAmount) }}</div>
      </div>
      <div class="summary-item">
        <div class="label">退款金额</div>
        <div class="value danger">¥{{ money(summary.refundAmount) }}</div>
      </div>
      <div class="summary-item">
        <div class="label">净收入</div>
        <div class="value primary">¥{{ money(summary.netPayAmount) }}</div>
      </div>
      <div class="summary-item">
        <div class="label">奖金拨出</div>
        <div class="value warning">¥{{ money(summary.bonusAmount) }}</div>
      </div>
      <div class="summary-item">
        <div class="label">产品成本</div>
        <div class="value">¥{{ money(summary.productCost) }}</div>
      </div>
      <div class="summary-item">
        <div class="label">公司分账</div>
        <div class="value">¥{{ money(summary.companyShareAmount) }}</div>
      </div>
      <div class="summary-item">
        <div class="label">公司利润</div>
        <div class="value" :class="Number(summary.companyProfit || 0) < 0 ? 'danger' : 'success'">
          ¥{{ money(summary.companyProfit) }}
        </div>
      </div>
      <div class="summary-item">
        <div class="label">利润率</div>
        <div class="value">{{ percent(summary.profitRate) }}</div>
      </div>
      <div class="summary-item">
        <div class="label">奖金拨出率</div>
        <div class="value">{{ percent(summary.payoutRate) }}</div>
      </div>
      <div class="summary-item risk">
        <div class="label">风险订单</div>
        <div class="value danger">{{ summary.riskOrderCount || 0 }}</div>
      </div>
    </div>

    <el-alert
      class="hint"
      type="info"
      :closable="false"
      title="统计口径：以订单财务审计表为准，成交额 - 退款 - 产品成本 - 奖金拨出 - 公司分账 = 公司利润。"
    />

    <div v-if="riskAlerts.length" class="alert-list">
      <el-alert
        v-for="item in riskAlerts"
        :key="item.ruleCode"
        class="risk-alert"
        type="warning"
        :closable="false"
        :title="`${item.ruleName}：${item.message}`"
      />
    </div>

    <div class="chart-section">
      <div class="section-title">每日趋势</div>
      <div ref="chartRef" class="finance-chart"></div>
    </div>

    <el-tabs class="finance-tabs" model-value="shares">
      <el-tab-pane label="公司分账汇总" name="shares">
        <el-table :data="shareRows" style="width: 100%">
          <el-table-column prop="accountId" label="分账账号ID" width="120" />
          <el-table-column prop="accountName" label="分账账号名称" min-width="180" />
          <el-table-column prop="orderCount" label="订单数" width="120" />
          <el-table-column prop="shareAmount" label="分账金额" width="160">
            <template #default="{ row }">¥{{ money(row.shareAmount) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="退款冲账" name="refund">
        <el-alert
          type="info"
          :closable="false"
          title="退款统一从“商城管理 → 订单管理 → 售后审核”办理"
          description="必须按订单商品和实际退回件数退款。系统自动拆分商品款与运费、精确冲减件数/团队业绩/奖金；财务页不再允许手填金额，防止账实不符。"
        />
      </el-tab-pane>
      <el-tab-pane label="风险规则" name="rules">
        <el-table :data="riskRules" style="width: 100%">
          <el-table-column prop="ruleName" label="风险规则" min-width="180" />
          <el-table-column prop="thresholdValue" label="预警阈值" width="180">
            <template #default="{ row }">
              <el-input-number v-model="row.thresholdValue" :precision="4" :step="0.01" />
            </template>
          </el-table-column>
          <el-table-column prop="enabled" label="规则状态" width="100">
            <template #default="{ row }">
              <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" />
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="规则说明" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button type="primary" link :loading="savingRiskRuleId === row.id" :disabled="savingRiskRuleId !== null" @click="submitRiskRule(row)">保存</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  exportFinanceDailySummary,
  getCompanyShareSummary,
  getFinanceDailySummary,
  getFinanceSummary,
  getRiskAlerts,
  listRiskRules,
  saveRiskRule,
} from '@/api/audit'

const loading = ref(false)
const route = useRoute()
const allowedRanges = new Set(['today', '7days', 'month', 'total', 'custom'])
const range = ref(allowedRanges.has(route.query.range) ? route.query.range : 'today')
const customDates = ref([])
const summary = ref({})
const dailyRows = ref([])
const shareRows = ref([])
const riskAlerts = ref([])
const riskRules = ref([])
const savingRiskRuleId = ref(null)
const chartRef = ref(null)
let chartInstance = null
let echarts
const ensureEcharts = async () => {
  echarts ||= (await import('@/utils/echarts')).default
  return echarts
}

const fetchSummary = async () => {
  if (range.value === 'custom' && (!customDates.value || customDates.value.length !== 2)) {
    ElMessage.warning('请选择自定义日期范围')
    return
  }
  loading.value = true
  try {
    const params = { range: range.value }
    if (range.value === 'custom') {
      params.startDate = customDates.value[0]
      params.endDate = customDates.value[1]
    }
    const [summaryRes, dailyRes, shareRes, alertRes, ruleRes] = await Promise.all([
      getFinanceSummary(params),
      getFinanceDailySummary(params),
      getCompanyShareSummary(params),
      getRiskAlerts(params),
      listRiskRules(),
    ])
    summary.value = summaryRes.data || {}
    dailyRows.value = dailyRes.data || []
    shareRows.value = shareRes.data || []
    riskAlerts.value = alertRes.data || []
    riskRules.value = ruleRes.data || []
    await nextTick()
    renderChart()
  } finally {
    loading.value = false
  }
}

const buildParams = () => {
  const params = { range: range.value }
  if (range.value === 'custom') {
    params.startDate = customDates.value[0]
    params.endDate = customDates.value[1]
  }
  return params
}

const handleExport = async () => {
  if (range.value === 'custom' && (!customDates.value || customDates.value.length !== 2)) {
    ElMessage.warning('请选择自定义日期范围')
    return
  }
  const res = await exportFinanceDailySummary(buildParams())
  const blob = new Blob([res.data], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `财务日报-${range.value}.xlsx`
  link.click()
  window.URL.revokeObjectURL(url)
}

const handleRangeChange = () => {
  if (range.value !== 'custom') {
    fetchSummary()
  }
}

const submitRiskRule = async (row) => {
  if (savingRiskRuleId.value !== null) return
  await ElMessageBox.confirm(`确认保存“${row.ruleName || row.ruleCode}”风险规则？变更会影响后续财务风险预警。`, '确认修改风险规则', { type: 'warning' })
  savingRiskRuleId.value = row.id || row.ruleCode
  try {
    await saveRiskRule(row)
    ElMessage.success('风险规则已保存')
    await fetchSummary()
  } finally {
    savingRiskRuleId.value = null
  }
}

const money = (value) => Number(value || 0).toFixed(2)
const percent = (value) => `${(Number(value || 0) * 100).toFixed(2)}%`

const renderChart = async () => {
  await ensureEcharts()
  if (!chartRef.value) {
    return
  }
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }
  const dates = dailyRows.value.map((item) => item.statDate)
  chartInstance.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, data: ['成交额', '奖金拨出', '产品成本', '公司利润'] },
    grid: { left: 56, right: 24, top: 48, bottom: 36 },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value' },
    series: [
      { name: '成交额', type: 'line', smooth: true, data: dailyRows.value.map((item) => Number(item.payAmount || 0)) },
      { name: '奖金拨出', type: 'line', smooth: true, data: dailyRows.value.map((item) => Number(item.bonusAmount || 0)) },
      { name: '产品成本', type: 'line', smooth: true, data: dailyRows.value.map((item) => Number(item.productCost || 0)) },
      { name: '公司利润', type: 'line', smooth: true, data: dailyRows.value.map((item) => Number(item.companyProfit || 0)) },
    ],
  })
}

const handleResize = () => {
  chartInstance?.resize()
}

onMounted(() => {
  fetchSummary()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
})
</script>

<style scoped>
.finance-page .toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(220px, 1fr));
  gap: 16px;
}

.summary-item {
  min-height: 100px;
  padding: 18px 20px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fff;
}

.summary-item .label {
  color: #909399;
  font-size: 14px;
  margin-bottom: 12px;
}

.summary-item .value {
  color: #303133;
  font-size: 26px;
  font-weight: 700;
}

.summary-item .primary {
  color: #409eff;
}

.summary-item .warning {
  color: #e6a23c;
}

.summary-item .success {
  color: #67c23a;
}

.summary-item .danger {
  color: #f56c6c;
}

.risk {
  border-color: #f8d7da;
}

.hint {
  margin-top: 18px;
}

.alert-list {
  margin-top: 12px;
}

.risk-alert + .risk-alert {
  margin-top: 8px;
}

.chart-section {
  margin-top: 18px;
  padding: 18px 20px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fff;
}

.section-title {
  margin-bottom: 12px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.finance-chart {
  width: 100%;
  height: 360px;
}

.finance-tabs {
  margin-top: 18px;
  padding: 16px 20px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fff;
}

.refund-form {
  max-width: 720px;
}
</style>
