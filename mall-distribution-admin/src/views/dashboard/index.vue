<template>
  <div class="dashboard-container" v-loading="loading">
    <header class="command-header">
      <div class="command-title">
        <span class="command-mark"><el-icon><TrendCharts /></el-icon></span>
        <div>
          <h1>智慧经营驾驶舱</h1>
          <p>全域经营数据中枢</p>
        </div>
      </div>
      <div class="command-meta">
        <span class="system-health"><el-icon><CircleCheckFilled /></el-icon>系统运行正常</span>
        <span class="update-time">数据更新于 {{ lastUpdated || '加载中' }}</span>
        <button type="button" class="refresh-button" :disabled="loading" @click="loadDashboard">
          <el-icon :class="{ rotating: loading }"><Refresh /></el-icon>
          <span>刷新数据</span>
        </button>
      </div>
    </header>

    <section class="metric-strip" aria-label="核心经营指标">
      <article v-for="(item, index) in coreMetrics" :key="item.title" class="core-metric" :class="`tone-${item.tone}`">
        <div class="metric-copy">
          <span class="metric-icon"><el-icon><component :is="item.icon" /></el-icon></span>
          <div>
            <span class="metric-label">{{ item.title }}</span>
            <strong>{{ item.value }}</strong>
            <small :class="item.state">{{ item.caption }}</small>
          </div>
        </div>
        <div :ref="(element) => setMetricChartRef(element, index)" class="metric-spark" aria-hidden="true"></div>
      </article>
    </section>

    <div class="dashboard-main-grid">
      <section class="command-panel trend-panel">
        <div class="panel-heading">
          <div>
            <div class="heading-title"><el-icon><Histogram /></el-icon><h2>经营脉搏</h2></div>
            <p>近30天有效销售趋势与7日均线（单位：元）</p>
          </div>
          <span class="range-chip"><el-icon><Clock /></el-icon>近30天</span>
        </div>
        <div ref="salesTrendChart" class="trend-chart" aria-label="近30天有效销售趋势图"></div>
      </section>

      <aside class="decision-rail">
        <section class="command-panel task-panel">
          <div class="panel-heading compact">
            <div class="heading-title"><el-icon><Tickets /></el-icon><h2>待处理事项</h2></div>
            <span>{{ totalTaskCount }} 项</span>
          </div>
          <div class="task-list">
            <button v-for="item in visibleTasks" :key="item.title" type="button" class="task-row" @click="router.push(item.path)">
              <span class="task-icon" :class="item.tone"><el-icon><component :is="item.icon" /></el-icon></span>
              <span class="task-copy"><b>{{ item.title }}</b><small>{{ item.description }}</small></span>
              <span class="task-count">{{ count(item.count) }}<small>{{ item.unit }}</small></span>
              <el-icon class="task-arrow"><ArrowRight /></el-icon>
            </button>
          </div>
        </section>

        <section class="command-panel risk-panel">
          <div class="panel-heading compact">
            <div class="heading-title"><el-icon><WarningFilled /></el-icon><h2>风险预警</h2></div>
            <span>实时</span>
          </div>
          <div class="risk-list">
            <div v-for="item in riskAlerts" :key="item.title" class="risk-row">
              <span class="risk-icon" :class="item.state"><el-icon><component :is="item.icon" /></el-icon></span>
              <span><b>{{ item.title }}</b><small>{{ item.description }}</small></span>
              <em :class="item.state">{{ item.status }}</em>
            </div>
          </div>
          <button v-if="store.hasPermission('finance:read')" type="button" class="panel-link" @click="router.push('/audit/finance')">
            <span>查看财务风控</span><el-icon><ArrowRight /></el-icon>
          </button>
        </section>
      </aside>
    </div>

    <div class="insight-grid">
      <section class="command-panel finance-panel">
        <div class="panel-heading compact">
          <div>
            <div class="heading-title"><el-icon><Wallet /></el-icon><h2>财务构成</h2></div>
            <p>净收款、总拨出与经营结果完整核对</p>
          </div>
          <button v-if="store.hasPermission('finance:read')" type="button" class="text-link" @click="router.push('/audit/finance')">
            详情<el-icon><ArrowRight /></el-icon>
          </button>
        </div>
        <div class="finance-totals">
          <div class="finance-total-card receipt">
            <span>累计净收款</span>
            <strong>¥{{ money(dashboard.totalReceiptAmount) }}</strong>
            <small>实际支付金额扣除全部退款</small>
          </div>
          <div class="finance-total-card payout">
            <span>累计总拨出</span>
            <strong>¥{{ money(dashboard.totalPayoutAmount) }}</strong>
            <small>产品成本＋奖金＋公司分账</small>
          </div>
        </div>
        <div class="finance-body">
          <div v-if="hasFinanceComposition" ref="financeChart" class="finance-chart" aria-label="累计资金构成图"></div>
          <div v-else class="empty-ring"><span>¥{{ money(dashboard.totalReceiptAmount) }}</span><small>累计净收款</small></div>
          <div class="finance-legend">
            <div v-for="item in financeComposition" :key="item.name">
              <span class="legend-dot" :style="{ backgroundColor: item.color }"></span>
              <span>{{ item.name }}</span>
              <b>¥{{ money(item.value) }}</b>
            </div>
          </div>
        </div>
        <div class="finance-summary">
          <span>利润 = 净收款 − 总拨出</span>
          <span>累计利润 <b :class="{ negative: Number(dashboard.totalProfitAmount || 0) < 0 }">¥{{ money(dashboard.totalProfitAmount) }}</b></span>
          <span>利润率 <b :class="{ negative: Number(dashboard.profitRate || 0) < 0 }">{{ percent(dashboard.profitRate) }}</b></span>
        </div>
      </section>

      <section class="command-panel member-panel">
        <div class="panel-heading compact">
          <div>
            <div class="heading-title"><el-icon><User /></el-icon><h2>会员与区域洞察</h2></div>
            <p>会员资产与真实订单地址画像</p>
          </div>
          <button v-if="store.hasPermission('shop:member')" type="button" class="text-link" @click="router.push('/members/list')">
            详情<el-icon><ArrowRight /></el-icon>
          </button>
        </div>
        <div class="member-kpis">
          <div><span>注册会员</span><b>{{ count(dashboard.registeredMemberCount) }}</b><small>人</small></div>
          <div><span>有效会员</span><b>{{ count(dashboard.validMemberCount) }}</b><small>人</small></div>
          <div><span>本月新增</span><b>{{ count(dashboard.monthNewMemberCount) }}</b><small>人</small></div>
        </div>
        <div class="region-list">
          <div v-for="item in topRegions" :key="item.regionName" class="region-row">
            <span>{{ item.regionName || '未知地区' }}</span>
            <el-progress :percentage="regionPercentage(item)" :show-text="false" :stroke-width="5" />
            <b>{{ count(item.memberCount) }} 人</b>
          </div>
          <div v-if="!topRegions.length" class="region-empty"><el-icon><MapLocation /></el-icon><span>暂无订单收货区域数据</span></div>
        </div>
      </section>

      <section class="command-panel ranking-panel">
        <div class="panel-heading compact">
          <div>
            <div class="heading-title"><el-icon><Goods /></el-icon><h2>商品价值榜</h2></div>
            <p>按真实有效成交额排序</p>
          </div>
          <button v-if="store.hasPermission('shop:product')" type="button" class="text-link" @click="router.push('/shop/products')">
            全部<el-icon><ArrowRight /></el-icon>
          </button>
        </div>
        <div v-if="topProducts.length" class="product-ranking">
          <div v-for="item in topProducts" :key="item.productId || item.ranking" class="product-row">
            <span class="rank-index" :class="`rank-${item.ranking}`">{{ item.ranking }}</span>
            <span class="product-name">{{ item.productName || '未命名商品' }}</span>
            <span class="product-orders">{{ count(item.orderCount) }} 单</span>
            <b>¥{{ money(item.salesAmount) }}</b>
          </div>
        </div>
        <div v-else class="ranking-empty"><el-icon><Goods /></el-icon><span>暂无已支付商品销售数据</span></div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  CircleCheckFilled,
  Clock,
  Coin,
  Goods,
  Histogram,
  MapLocation,
  Money,
  Refresh,
  Tickets,
  TrendCharts,
  User,
  Wallet,
  WarningFilled,
} from '@element-plus/icons-vue'
import echarts from '@/utils/echarts'
import { getDashboard } from '@/api/dashboard'
import { useAppStore } from '@/store'

const router = useRouter()
const store = useAppStore()
const loading = ref(false)
const dashboard = ref({})
const lastUpdated = ref('')
const salesTrendChart = ref(null)
const financeChart = ref(null)
const metricChartRefs = []
let salesTrendChartInstance
let financeChartInstance
let metricChartInstances = []

const money = (value) => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const count = (value) => Number(value || 0).toLocaleString('zh-CN')
const percent = (value) => `${(Number(value || 0) * 100).toFixed(2)}%`
const setMetricChartRef = (element, index) => { metricChartRefs[index] = element }

const todayComparison = computed(() => {
  const today = Number(dashboard.value.todayPerformance || 0)
  const yesterday = Number(dashboard.value.yesterdayPerformance || 0)
  if (yesterday <= 0) return { text: '今日实时', state: 'neutral' }
  const change = ((today - yesterday) / yesterday) * 100
  return { text: `较昨日 ${change >= 0 ? '+' : ''}${change.toFixed(2)}%`, state: change >= 0 ? 'positive' : 'negative' }
})

const coreMetrics = computed(() => [
  { title: '累计销售额', value: `¥${money(dashboard.value.totalSalesAmount)}`, caption: '历史有效成交', state: 'neutral', tone: 'blue', icon: Coin },
  { title: '本月销售额', value: `¥${money(dashboard.value.monthSalesAmount)}`, caption: '本自然月累计', state: 'positive', tone: 'violet', icon: Wallet },
  { title: '今日销售额', value: `¥${money(dashboard.value.todaySalesAmount)}`, caption: todayComparison.value.text, state: todayComparison.value.state, tone: 'cyan', icon: Histogram },
  { title: '累计利润', value: `¥${money(dashboard.value.totalProfitAmount)}`, caption: `利润率 ${percent(dashboard.value.profitRate)}`, state: Number(dashboard.value.totalProfitAmount || 0) < 0 ? 'negative' : 'neutral', tone: 'amber', icon: Money },
])

const taskItems = computed(() => [
  { title: '待审核提现', description: `待审核金额 ¥${money(dashboard.value.pendingWithdrawAmount)}`, count: dashboard.value.pendingWithdrawCount, unit: '笔', path: '/withdraw/audit', permission: 'finance:manage', tone: 'violet', icon: Money },
  { title: '待结算奖金', description: `待结算金额 ¥${money(dashboard.value.unsettledCommission)}`, count: dashboard.value.unsettledCommissionCount, unit: '笔', path: '/commission/settle', permission: 'commission:manage', tone: 'amber', icon: Coin },
  { title: '待转化会员', description: '已注册但尚未成为正式会员', count: dashboard.value.pendingMemberCount, unit: '人', path: '/members/list', permission: 'shop:member', tone: 'cyan', icon: User },
])
const visibleTasks = computed(() => taskItems.value.filter((item) => store.hasPermission(item.permission)))
const totalTaskCount = computed(() => visibleTasks.value.reduce((total, item) => total + Number(item.count || 0), 0))

const riskAlerts = computed(() => {
  const profitRisk = Number(dashboard.value.totalProfitAmount || 0) < 0
  const missingRegions = Number(dashboard.value.unaddressedMemberCount || 0)
  const hasProducts = (dashboard.value.productRanking || []).length > 0
  return [
    { title: '经营利润', description: profitRisk ? '累计利润为负，请核对成本与拨出' : '资金收入与拨出状态正常', status: profitRisk ? '需关注' : '正常', state: profitRisk ? 'warning' : 'healthy', icon: Wallet },
    { title: '会员画像', description: missingRegions ? `${count(missingRegions)} 位会员尚无订单地址` : '会员订单地址识别完整', status: missingRegions ? '待完善' : '正常', state: missingRegions ? 'notice' : 'healthy', icon: MapLocation },
    { title: '商品数据', description: hasProducts ? '商品成交价值数据已同步' : '暂无已支付商品成交数据', status: hasProducts ? '正常' : '待沉淀', state: hasProducts ? 'healthy' : 'notice', icon: Goods },
  ]
})

const financeComposition = computed(() => [
  { name: '产品成本', value: Number(dashboard.value.totalProductCostAmount || 0), color: '#3f7cff' },
  { name: '奖金拨出', value: Number(dashboard.value.totalBonusPayoutAmount || 0), color: '#805cff' },
  { name: '公司分账', value: Number(dashboard.value.totalCompanyShareAmount || 0), color: '#0bb8d4' },
  { name: '累计利润', value: Math.max(Number(dashboard.value.totalProfitAmount || 0), 0), color: '#f59e0b' },
])
const hasFinanceComposition = computed(() => financeComposition.value.some((item) => item.value > 0))
const topRegions = computed(() => (dashboard.value.memberRegionDistribution || []).slice(0, 4))
const topProducts = computed(() => (dashboard.value.productRanking || []).slice(0, 5))
const regionPercentage = (item) => Math.min(100, Math.max(0, Number(item.percentage || 0)))

const rollingAverage = (values, windowSize = 7) => values.map((_, index) => {
  const start = Math.max(0, index - windowSize + 1)
  const sample = values.slice(start, index + 1)
  return Number((sample.reduce((sum, value) => sum + value, 0) / sample.length).toFixed(2))
})

const renderMetricCharts = () => {
  metricChartInstances.forEach((chart) => chart?.dispose())
  metricChartInstances = []
  const dailyValues = (dashboard.value.performanceTrend || []).map((item) => Number(item.performanceAmount || 0))
  const monthlyValues = (dashboard.value.monthlyPerformanceTrend || []).map((item) => Number(item.performanceAmount || 0))
  const palettes = [
    { line: '#4f8cff', fill: 'rgba(79,140,255,.16)', data: dailyValues },
    { line: '#8a63ff', fill: 'rgba(138,99,255,.16)', data: monthlyValues },
    { line: '#16c6e8', fill: 'rgba(22,198,232,.15)', data: dailyValues.slice(-7) },
    { line: '#f59e0b', fill: 'rgba(245,158,11,.14)', data: monthlyValues },
  ]
  metricChartRefs.forEach((element, index) => {
    if (!element) return
    const chart = echarts.init(element)
    const palette = palettes[index]
    chart.setOption({
      animationDuration: 500,
      grid: { left: 0, right: 0, top: 5, bottom: 0 },
      xAxis: { type: 'category', show: false, boundaryGap: false, data: palette.data.map((_, dataIndex) => dataIndex) },
      yAxis: { type: 'value', show: false, min: 'dataMin', max: 'dataMax' },
      series: [{
        type: 'line', data: palette.data.length ? palette.data : [0, 0], smooth: true, showSymbol: false,
        lineStyle: { width: 1.6, color: palette.line }, areaStyle: { color: palette.fill },
      }],
    })
    metricChartInstances.push(chart)
  })
}

const renderCharts = () => {
  if (salesTrendChart.value) {
    salesTrendChartInstance?.dispose()
    salesTrendChartInstance = echarts.init(salesTrendChart.value)
    const trend = dashboard.value.performanceTrend || []
    const values = trend.map((item) => Number(item.performanceAmount || 0))
    salesTrendChartInstance.setOption({
      animationDuration: 650,
      color: ['#4b86ff', '#92a4c5'],
      tooltip: {
        trigger: 'axis', backgroundColor: 'rgba(5,17,36,.96)', borderColor: '#233c63', textStyle: { color: '#eaf2ff' },
        valueFormatter: (value) => `¥${money(value)}`,
      },
      legend: { top: 4, right: 16, itemWidth: 18, itemHeight: 2, textStyle: { color: '#91a1ba', fontSize: 11 } },
      grid: { left: 60, right: 24, top: 50, bottom: 40 },
      xAxis: {
        type: 'category', boundaryGap: false, data: trend.map((item) => String(item.statDate || '').slice(5)),
        axisLine: { lineStyle: { color: '#243653' } }, axisTick: { show: false }, axisLabel: { color: '#71829c', fontSize: 11 },
      },
      yAxis: {
        type: 'value', minInterval: 1, axisLabel: { color: '#71829c', fontSize: 11 },
        splitLine: { lineStyle: { color: 'rgba(63,89,128,.24)', type: 'dashed' } },
      },
      series: [
        {
          name: '有效销售额', type: 'line', smooth: true, showSymbol: false, data: values,
          lineStyle: { width: 2.6, color: '#4b86ff' }, areaStyle: { color: 'rgba(45,111,247,.18)' },
          markPoint: values.length ? { symbol: 'circle', symbolSize: 12, data: [{ coord: [values.length - 1, values.at(-1)], name: '今天' }], itemStyle: { color: '#4b86ff', borderColor: '#dce9ff', borderWidth: 2 }, label: { show: false } } : undefined,
        },
        {
          name: '7日均线', type: 'line', smooth: true, showSymbol: false, data: rollingAverage(values),
          lineStyle: { width: 1.4, type: 'dashed', color: '#9aa8be' },
        },
      ],
    })
  }

  if (financeChart.value && hasFinanceComposition.value) {
    financeChartInstance?.dispose()
    financeChartInstance = echarts.init(financeChart.value)
    financeChartInstance.setOption({
      animationDuration: 650,
      tooltip: { trigger: 'item', backgroundColor: 'rgba(5,17,36,.96)', borderColor: '#233c63', textStyle: { color: '#eaf2ff' }, valueFormatter: (value) => `¥${money(value)}` },
      series: [{
        type: 'pie', radius: ['64%', '82%'], center: ['50%', '50%'], avoidLabelOverlap: true,
        label: { show: false }, emphasis: { scaleSize: 4 },
        data: financeComposition.value.map((item) => ({ name: item.name, value: item.value, itemStyle: { color: item.color } })),
      }],
      graphic: [{
        type: 'text', left: 'center', top: '43%', style: { text: `¥${money(dashboard.value.totalReceiptAmount)}`, fill: '#f4f8ff', fontSize: 16, fontWeight: 700, textAlign: 'center' },
      }, {
        type: 'text', left: 'center', top: '57%', style: { text: '累计净收款', fill: '#71829c', fontSize: 10, textAlign: 'center' },
      }],
    })
  }
  renderMetricCharts()
}

const loadDashboard = async () => {
  loading.value = true
  try {
    const res = await getDashboard()
    dashboard.value = res.data || {}
    lastUpdated.value = new Date().toLocaleString('zh-CN', { hour12: false })
    await nextTick()
    renderCharts()
  } finally {
    loading.value = false
  }
}

const resizeCharts = () => {
  salesTrendChartInstance?.resize()
  financeChartInstance?.resize()
  metricChartInstances.forEach((chart) => chart?.resize())
}

onMounted(() => {
  loadDashboard()
  window.addEventListener('resize', resizeCharts)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  salesTrendChartInstance?.dispose()
  financeChartInstance?.dispose()
  metricChartInstances.forEach((chart) => chart?.dispose())
})
</script>

<style lang="scss" scoped>
.dashboard-container {
  --panel: rgba(6, 20, 43, .86);
  --panel-strong: rgba(7, 24, 51, .95);
  --line: rgba(77, 112, 164, .28);
  --muted: #7f90ab;
  --text: #f4f7fc;
  min-width: 0;
  color: var(--text);
  font-variant-numeric: tabular-nums;
}

.command-header {
  min-height: 62px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 16px;
  padding: 0 2px;
}

.command-title,
.command-meta,
.metric-copy,
.heading-title,
.range-chip,
.system-health,
.refresh-button,
.text-link,
.panel-link {
  display: flex;
  align-items: center;
}

.command-title { gap: 12px; }
.command-mark {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  color: #69a1ff;
  font-size: 21px;
  border: 1px solid rgba(74, 133, 241, .34);
  border-radius: 12px;
  background: rgba(18, 56, 112, .42);
}
.command-title h1 { margin: 0; font-size: 25px; line-height: 1.2; letter-spacing: .5px; }
.command-title p { margin: 5px 0 0; color: #71829c; font-size: 11px; letter-spacing: 2px; }
.command-meta { justify-content: flex-end; gap: 18px; color: var(--muted); font-size: 12px; }
.system-health { gap: 6px; padding: 7px 10px; color: #46dca0; border: 1px solid rgba(48, 205, 145, .18); border-radius: 8px; background: rgba(19, 99, 72, .2); }
.system-health .el-icon { font-size: 14px; }
.refresh-button {
  gap: 7px;
  min-height: 38px;
  padding: 0 14px;
  color: #dce9ff;
  font: inherit;
  border: 1px solid rgba(73, 134, 247, .75);
  border-radius: 8px;
  background: rgba(31, 82, 172, .32);
  cursor: pointer;
  transition: background .18s ease, border-color .18s ease;
}
.refresh-button:hover:not(:disabled) { border-color: #70a4ff; background: rgba(43, 101, 204, .46); }
.refresh-button:focus-visible,
.task-row:focus-visible,
.text-link:focus-visible,
.panel-link:focus-visible { outline: 2px solid #69a1ff; outline-offset: 2px; }
.refresh-button:disabled { cursor: wait; opacity: .65; }
.rotating { animation: rotating .9s linear infinite; }
@keyframes rotating { to { transform: rotate(360deg); } }

.metric-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 14px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: rgba(5, 18, 39, .88);
  box-shadow: 0 16px 38px rgba(0, 7, 19, .22);
}
.core-metric { position: relative; min-width: 0; padding: 22px 18px 18px; overflow: hidden; }
.core-metric + .core-metric { border-left: 1px solid var(--line); }
.metric-copy { position: relative; z-index: 1; gap: 12px; }
.metric-icon {
  width: 38px;
  height: 38px;
  display: grid;
  flex: 0 0 38px;
  place-items: center;
  color: var(--accent);
  font-size: 19px;
  border: 1px solid color-mix(in srgb, var(--accent) 34%, transparent);
  border-radius: 50%;
  background: color-mix(in srgb, var(--accent) 13%, transparent);
}
.metric-label { display: block; color: #9aaaC0; font-size: 12px; }
.core-metric strong { display: block; margin-top: 5px; font-size: clamp(22px, 2vw, 29px); line-height: 1.15; white-space: nowrap; }
.core-metric small { display: block; margin-top: 6px; color: #73849f; font-size: 11px; }
.core-metric small.positive { color: #42d99b; }
.core-metric small.negative { color: #ff6b7d; }
.metric-spark { height: 72px; margin: 7px -4px -5px; }
.tone-blue { --accent: #4f8cff; }
.tone-violet { --accent: #8a63ff; }
.tone-cyan { --accent: #16c6e8; }
.tone-amber { --accent: #f59e0b; }

.dashboard-main-grid { position: relative; margin-bottom: 14px; padding-right: 300px; }
.command-panel { min-width: 0; border: 1px solid var(--line); border-radius: 12px; background: var(--panel); box-shadow: 0 16px 36px rgba(0, 7, 18, .18); }
.trend-panel { padding: 18px 18px 10px; }
.panel-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.panel-heading.compact { align-items: center; }
.heading-title { gap: 8px; }
.heading-title > .el-icon { color: #5f9aff; font-size: 18px; }
.panel-heading h2 { margin: 0; color: #eaf1fc; font-size: 16px; line-height: 1.35; }
.panel-heading p { margin: 6px 0 0; color: var(--muted); font-size: 12px; }
.panel-heading > span { color: #6f829f; font-size: 11px; }
.range-chip { gap: 7px; padding: 7px 10px; color: #9dadc5; font-size: 11px; border: 1px solid rgba(81, 111, 154, .34); border-radius: 7px; background: rgba(11, 31, 62, .68); }
.trend-chart { height: 338px; }

.decision-rail { position: absolute; top: 0; right: 0; width: 286px; display: grid; grid-template-rows: auto 1fr; gap: 14px; }
.task-panel,
.risk-panel,
.finance-panel,
.member-panel,
.ranking-panel { padding: 16px; }
.task-list,
.risk-list { margin-top: 12px; }
.task-list { display: grid; gap: 8px; }
.task-row {
  width: 100%;
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto 14px;
  align-items: center;
  gap: 9px;
  padding: 10px;
  color: inherit;
  text-align: left;
  border: 1px solid rgba(76, 105, 148, .25);
  border-radius: 9px;
  background: rgba(12, 31, 63, .62);
  cursor: pointer;
  transition: background .18s ease, border-color .18s ease, transform .18s ease;
}
.task-row:hover { border-color: rgba(79, 140, 255, .48); background: rgba(18, 45, 88, .76); transform: translateX(2px); }
.task-icon,
.risk-icon {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  background: rgba(75, 134, 255, .16);
  color: #5791ff;
  font-size: 15px;
}
.task-icon.violet { color: #9c7cff; background: rgba(128, 92, 255, .16); }
.task-icon.amber { color: #f8a823; background: rgba(245, 158, 11, .15); }
.task-icon.cyan { color: #29c3dc; background: rgba(22, 198, 232, .14); }
.task-copy b,
.task-copy small,
.risk-row b,
.risk-row small { display: block; }
.task-copy b,
.risk-row b { color: #ced9e9; font-size: 12px; font-weight: 600; }
.task-copy small,
.risk-row small { margin-top: 3px; color: #71829c; font-size: 11px; line-height: 1.35; }
.task-count { color: #f2f6fc; font-size: 18px; font-weight: 700; }
.task-count small { margin-left: 2px; color: #71829c; font-size: 10px; font-weight: 400; }
.task-arrow { color: #566b89; font-size: 12px; }
.risk-list { display: grid; gap: 2px; }
.risk-row { display: grid; grid-template-columns: 32px minmax(0, 1fr) auto; align-items: center; gap: 9px; padding: 9px 0; }
.risk-row + .risk-row { border-top: 1px solid rgba(63, 91, 132, .18); }
.risk-icon.healthy { color: #42d99b; background: rgba(48, 205, 145, .13); }
.risk-icon.notice { color: #29b4db; background: rgba(22, 174, 218, .12); }
.risk-icon.warning { color: #ff6b7d; background: rgba(255, 84, 105, .13); }
.risk-row em { font-size: 11px; font-style: normal; }
.risk-row em.healthy { color: #42d99b; }
.risk-row em.notice { color: #29b4db; }
.risk-row em.warning { color: #ff6b7d; }
.panel-link { width: 100%; justify-content: center; gap: 6px; margin-top: 8px; padding: 8px 0 0; color: #4f8cff; font-size: 11px; border: 0; border-top: 1px solid rgba(63, 91, 132, .18); background: transparent; cursor: pointer; }

.insight-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-right: 300px; }
.ranking-panel { grid-column: 1 / -1; }
.text-link { gap: 4px; padding: 4px; color: #4f8cff; font-size: 11px; border: 0; background: transparent; cursor: pointer; }
.finance-totals { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 9px; margin-top: 14px; }
.finance-total-card { position: relative; min-width: 0; padding: 12px 13px; overflow: hidden; border: 1px solid rgba(76, 105, 148, .28); border-radius: 9px; background: rgba(10, 29, 60, .7); }
.finance-total-card::before { position: absolute; top: 0; left: 0; width: 3px; height: 100%; content: ''; }
.finance-total-card.receipt::before { background: #4f8cff; }
.finance-total-card.payout::before { background: #8a63ff; }
.finance-total-card span,
.finance-total-card small { display: block; }
.finance-total-card span { color: #8ea0ba; font-size: 11px; }
.finance-total-card strong { display: block; margin-top: 6px; color: #f0f5fc; font-size: 21px; font-weight: 700; letter-spacing: -.4px; }
.finance-total-card small { min-height: 28px; margin-top: 5px; color: #60738f; font-size: 10px; line-height: 1.4; }
.finance-body { display: grid; grid-template-columns: 136px minmax(0, 1fr); align-items: center; gap: 12px; margin-top: 12px; }
.finance-chart { height: 136px; }
.empty-ring { width: 122px; height: 122px; display: grid; align-content: center; justify-items: center; margin: 7px; border: 10px solid rgba(73, 110, 166, .22); border-radius: 50%; }
.empty-ring span { color: #e6eef9; font-size: 15px; font-weight: 700; }
.empty-ring small { margin-top: 4px; color: #71829c; font-size: 9px; }
.finance-legend { display: grid; gap: 10px; }
.finance-legend > div { display: grid; grid-template-columns: 8px minmax(0, 1fr) auto; align-items: center; gap: 8px; color: #899ab3; font-size: 11px; }
.legend-dot { width: 7px; height: 7px; border-radius: 50%; }
.finance-legend b { color: #c9d5e6; font-weight: 600; }
.finance-summary { display: grid; grid-template-columns: minmax(0, 1.5fr) auto auto; align-items: center; gap: 14px; margin-top: 10px; padding-top: 10px; color: #71829c; font-size: 11px; border-top: 1px solid rgba(63, 91, 132, .22); }
.finance-summary b { margin-left: 4px; color: #e8f0fb; }
.finance-summary b.negative { color: #ff6b7d; }

.member-kpis { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 16px; }
.member-kpis > div { padding-right: 8px; }
.member-kpis > div + div { padding-left: 12px; border-left: 1px solid rgba(65, 94, 137, .26); }
.member-kpis span { display: block; color: #71829c; font-size: 10px; }
.member-kpis b { display: inline-block; margin-top: 6px; color: #eaf1fc; font-size: 23px; }
.member-kpis small { margin-left: 3px; color: #71829c; font-size: 9px; }
.region-list { display: grid; gap: 9px; margin-top: 14px; padding-top: 12px; border-top: 1px solid rgba(63, 91, 132, .22); }
.region-row { display: grid; grid-template-columns: 64px minmax(60px, 1fr) 52px; align-items: center; gap: 9px; color: #8293ad; font-size: 10px; }
.region-row b { color: #bdc9dc; font-weight: 500; text-align: right; }
.region-row :deep(.el-progress-bar__outer) { background: rgba(53, 79, 117, .34); }
.region-row :deep(.el-progress-bar__inner) { background: #4f8cff; }
.region-empty,
.ranking-empty { min-height: 128px; display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 8px; color: #60738f; font-size: 11px; }
.region-empty .el-icon,
.ranking-empty .el-icon { font-size: 24px; }

.product-ranking { display: grid; margin-top: 12px; }
.product-row { display: grid; grid-template-columns: 24px minmax(0, 1fr) 50px 72px; align-items: center; gap: 8px; min-height: 32px; color: #7f90aa; font-size: 10px; }
.product-row + .product-row { border-top: 1px solid rgba(63, 91, 132, .16); }
.rank-index { width: 20px; height: 20px; display: grid; place-items: center; color: #8da0bc; border-radius: 6px; background: rgba(73, 98, 135, .25); }
.rank-index.rank-1 { color: #ffc65b; background: rgba(245, 158, 11, .16); }
.rank-index.rank-2 { color: #b7c8e2; background: rgba(124, 149, 185, .17); }
.rank-index.rank-3 { color: #e59b77; background: rgba(206, 112, 72, .15); }
.product-name { overflow: hidden; color: #bdc9dc; text-overflow: ellipsis; white-space: nowrap; }
.product-orders { text-align: right; }
.product-row > b { color: #e4ecf8; font-weight: 600; text-align: right; }

.dashboard-container :deep(.el-loading-mask) { background: rgba(3, 12, 27, .76); }

@media (max-width: 1280px) {
  .command-meta .update-time { display: none; }
}

@media (max-width: 1024px) {
  .metric-strip { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .core-metric:nth-child(3) { border-left: 0; border-top: 1px solid var(--line); }
  .core-metric:nth-child(4) { border-top: 1px solid var(--line); }
  .dashboard-main-grid { padding-right: 0; }
  .decision-rail { position: static; width: auto; grid-template-columns: repeat(2, minmax(0, 1fr)); grid-template-rows: auto; margin-top: 14px; }
  .insight-grid { margin-right: 0; }
}

@media (max-width: 900px) {
  .insight-grid { grid-template-columns: 1fr; }
  .ranking-panel { grid-column: auto; }
}

@media (max-width: 720px) {
  .command-header { align-items: flex-start; flex-direction: column; }
  .command-meta { width: 100%; justify-content: flex-start; flex-wrap: wrap; }
  .command-meta .update-time { display: inline; }
  .metric-strip,
  .decision-rail,
  .insight-grid { grid-template-columns: 1fr; }
  .core-metric + .core-metric { border-top: 1px solid var(--line); border-left: 0; }
  .ranking-panel { grid-column: auto; }
  .trend-chart { height: 290px; }
  .finance-summary { grid-template-columns: 1fr; gap: 7px; }
}

@media (max-width: 520px) {
  .finance-totals,
  .finance-body { grid-template-columns: 1fr; }
  .finance-chart,
  .empty-ring { justify-self: center; width: 150px; }
}

@media (prefers-reduced-motion: reduce) {
  .rotating { animation: none; }
  .task-row,
  .refresh-button { transition: none; }
}
</style>
