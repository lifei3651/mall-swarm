<template>
  <div class="dashboard-container" v-loading="loading">
    <section class="dashboard-hero">
      <div>
        <span class="hero-eyebrow">全域经营数据中枢</span>
        <h1>灵启商城智慧经营驾驶舱</h1>
        <p>融合订单、资金、会员与区域画像等多维经营数据，通过实时汇聚与智能分析，为管理者提供全局洞察、趋势研判与高效决策支持。</p>
      </div>
      <div class="hero-actions">
        <span>数据更新于 {{ lastUpdated || '加载中' }}</span>
        <el-button type="primary" :loading="loading" @click="loadDashboard">刷新数据</el-button>
      </div>
    </section>

    <el-alert
      title="核心经营指标由商城业务数据实时汇聚并动态计算，管理视图与实际经营状态始终同步。"
      type="success"
      :closable="false"
      show-icon
      class="real-data-alert"
    />

    <section class="metric-section">
      <div class="section-heading">
        <div><h2>全域销售洞察</h2><p>聚合有效成交与退款数据，多周期呈现商城真实销售动能</p></div>
        <el-button v-if="store.hasPermission('shop:order')" type="primary" link @click="router.push('/shop/orders')">查看订单 →</el-button>
      </div>
      <div class="metric-grid sales-grid">
        <article v-for="item in salesCards" :key="item.title" class="metric-card" :class="item.tone">
          <div class="metric-title">{{ item.title }}</div>
          <div class="metric-value">¥{{ money(item.value) }}</div>
          <div class="metric-caption">{{ item.caption }}</div>
        </article>
      </div>
    </section>

    <section class="metric-section">
      <div class="section-heading">
        <div><h2>智能财务洞察</h2><p>贯通净收款、成本、奖金与公司分账，构建清晰可追溯的经营利润视图</p></div>
        <el-button v-if="store.hasPermission('finance:read')" type="primary" link @click="router.push('/audit/finance')">查看财务总览 →</el-button>
      </div>
      <div class="metric-grid finance-grid">
        <article v-for="item in financeCards" :key="item.title" class="metric-card" :class="item.tone">
          <div class="metric-title-row">
            <span class="metric-title">{{ item.title }}</span>
            <el-tooltip v-if="item.tip" :content="item.tip" placement="top">
              <span class="metric-help">?</span>
            </el-tooltip>
          </div>
          <div class="metric-value" :class="{ negative: item.isNegative }">{{ item.isRate ? percent(item.value) : `¥${money(item.value)}` }}</div>
          <div class="metric-caption">{{ item.caption }}</div>
        </article>
      </div>
      <div class="finance-formula">
        <span>累计总拨出 = 产品成本 + 奖金拨出 + 公司分账</span>
        <span>利润 = 累计净收款 − 累计总拨出</span>
        <span>利润率 = 利润 ÷ 累计净收款</span>
      </div>
    </section>

    <section class="metric-section">
      <div class="section-heading">
        <div><h2>会员资产洞察</h2><p>多维呈现会员规模、转化状态与增长趋势，持续沉淀高价值用户资产</p></div>
        <el-button v-if="store.hasPermission('shop:member')" type="primary" link @click="router.push('/members/list')">查看会员 →</el-button>
      </div>
      <div class="metric-grid member-grid">
        <article v-for="item in memberCards" :key="item.title" class="metric-card" :class="item.tone">
          <div class="metric-title">{{ item.title }}</div>
          <div class="metric-value">{{ count(item.value) }}<small> 人</small></div>
          <div class="metric-caption">{{ item.caption }}</div>
        </article>
      </div>
    </section>

    <el-row :gutter="20" class="chart-row">
      <el-col :xs="24" :xl="15">
        <el-card class="panel-card" shadow="never">
          <template #header>
            <div class="panel-header"><div><b>近30天销售趋势</b><span>基于每日有效成交数据，直观洞察经营走势与增长节奏</span></div></div>
          </template>
          <div ref="salesTrendChart" class="chart trend-chart"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :xl="9">
        <el-card class="panel-card region-card" shadow="never">
          <template #header>
            <div class="panel-header">
              <div><b>会员区域画像</b><span>基于已支付订单的实际收货地址，洞察会员消费区域，辅助市场布局与精细化运营</span></div>
              <small>已识别 {{ count(dashboard.addressedMemberCount) }} 人</small>
            </div>
          </template>
          <div v-if="hasRegionData" ref="regionChart" class="chart region-chart"></div>
          <el-empty v-else description="暂无订单收货区域数据" :image-size="90" />
          <div class="region-summary">
            <span>已识别订单地址会员：{{ count(dashboard.addressedMemberCount) }} 人</span>
            <span>尚无订单地址会员：{{ count(dashboard.unaddressedMemberCount) }} 人</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="panel-card ranking-card" shadow="never">
      <template #header>
        <div class="panel-header">
          <div><b>商品价值排行榜 TOP 10</b><span>以真实有效成交数据识别核心商品表现，快速聚焦高价值经营机会</span></div>
          <el-button v-if="store.hasPermission('shop:product')" type="primary" link @click="router.push('/shop/products')">查看商品 →</el-button>
        </div>
      </template>
      <el-table :data="dashboard.productRanking || []" empty-text="暂无已支付商品销售数据" style="width: 100%">
        <el-table-column label="排名" width="82" align="center">
          <template #default="{ row }"><span class="rank-badge" :class="`rank-${row.ranking}`">{{ row.ranking }}</span></template>
        </el-table-column>
        <el-table-column label="商品" min-width="260">
          <template #default="{ row }">
            <div class="product-cell">
              <el-image v-if="row.productCover" :src="row.productCover" fit="cover" class="product-cover">
                <template #error><div class="product-cover fallback">商品</div></template>
              </el-image>
              <div v-else class="product-cover fallback">商品</div>
              <div><b>{{ row.productName || '未命名商品' }}</b><small>商品ID：{{ row.productId || '-' }}</small></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="orderCount" label="成交订单" min-width="115" align="right">
          <template #default="{ row }">{{ count(row.orderCount) }} 单</template>
        </el-table-column>
        <el-table-column prop="salesQuantity" label="销售数量" min-width="115" align="right">
          <template #default="{ row }">{{ count(row.salesQuantity) }} 件</template>
        </el-table-column>
        <el-table-column label="销售额" min-width="145" align="right">
          <template #default="{ row }"><b class="sales-money">¥{{ money(row.salesAmount) }}</b></template>
        </el-table-column>
      </el-table>
    </el-card>

    <section v-if="visibleQuickActions.length" class="quick-section">
      <div class="section-heading">
        <div><h2>核心业务协同入口</h2><p>一站式连接订单、会员、财务与业绩模块，让关键经营任务高效流转</p></div>
      </div>
      <div class="quick-grid">
        <button v-for="item in visibleQuickActions" :key="item.path" type="button" class="quick-card" @click="router.push(item.path)">
          <span>{{ item.title }}</span><small>{{ item.description }}</small><b>进入办理 →</b>
        </button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import echarts from '@/utils/echarts'
import { getDashboard } from '@/api/dashboard'
import { useAppStore } from '@/store'

const router = useRouter()
const store = useAppStore()
const loading = ref(false)
const dashboard = ref({})
const lastUpdated = ref('')
const salesTrendChart = ref(null)
const regionChart = ref(null)
let salesTrendChartInstance
let regionChartInstance

const money = (value) => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const count = (value) => Number(value || 0).toLocaleString('zh-CN')
const percent = (value) => `${(Number(value || 0) * 100).toFixed(2)}%`
const hasRegionData = computed(() => (dashboard.value.memberRegionDistribution || []).some((item) => Number(item.memberCount) > 0))

const salesCards = computed(() => [
  { title: '累计总销售额', value: dashboard.value.totalSalesAmount, caption: '商城历史累计有效商品成交额', tone: 'tone-blue' },
  { title: '本月销售额', value: dashboard.value.monthSalesAmount, caption: '本自然月截至当前', tone: 'tone-indigo' },
  { title: '近7天销售额', value: dashboard.value.last7DaysSalesAmount, caption: '包含今天在内的最近7个自然日', tone: 'tone-cyan' },
  { title: '今日销售额', value: dashboard.value.todaySalesAmount, caption: '今日00:00起至当前', tone: 'tone-green' },
])

const financeCards = computed(() => [
  { title: '待结算奖金', value: dashboard.value.unsettledCommission, caption: `${count(dashboard.value.unsettledCommissionCount)} 笔等待结算`, tone: 'tone-orange', tip: '已经生成、尚未进入会员余额的奖金。' },
  { title: '待审核提现', value: dashboard.value.pendingWithdrawAmount, caption: `${count(dashboard.value.pendingWithdrawCount)} 笔等待审核`, tone: 'tone-red', tip: '会员已提交、后台尚未审核的提现申请金额。' },
  { title: '累计成功提现', value: dashboard.value.totalWithdrawAmount, caption: '仅统计提现成功的记录', tone: 'tone-purple' },
  { title: '本月成功提现', value: dashboard.value.monthWithdrawAmount, caption: '按实际打款成功时间统计', tone: 'tone-pink' },
  { title: '累计净收款', value: dashboard.value.totalReceiptAmount, caption: '实际支付金额扣除全部退款', tone: 'tone-blue', tip: '包含商品实付与运费实付，并扣除商品退款和运费退款。' },
  { title: '累计总拨出', value: dashboard.value.totalPayoutAmount, caption: '成本、奖金与公司分账合计', tone: 'tone-indigo', tip: `产品成本 ¥${money(dashboard.value.totalProductCostAmount)}；奖金 ¥${money(dashboard.value.totalBonusPayoutAmount)}；公司分账 ¥${money(dashboard.value.totalCompanyShareAmount)}。` },
  { title: '累计利润', value: dashboard.value.totalProfitAmount, caption: '累计净收款减累计总拨出', tone: 'tone-green', isNegative: Number(dashboard.value.totalProfitAmount || 0) < 0 },
  { title: '利润率', value: dashboard.value.profitRate, caption: '累计利润占累计净收款比例', tone: 'tone-teal', isRate: true, isNegative: Number(dashboard.value.profitRate || 0) < 0 },
])

const memberCards = computed(() => [
  { title: '已注册商城账号', value: dashboard.value.registeredMemberCount, caption: '完成商城账号注册的用户', tone: 'tone-blue' },
  { title: '有效正式会员', value: dashboard.value.validMemberCount, caption: '完成有效首单或由后台正式开通', tone: 'tone-green' },
  { title: '未进入会员体系', value: dashboard.value.pendingMemberCount, caption: '已注册但尚未成为正式会员', tone: 'tone-orange' },
  { title: '本月新增账号', value: dashboard.value.monthNewMemberCount, caption: '本自然月新注册商城账号', tone: 'tone-purple' },
])

const quickActions = [
  { title: '处理订单与售后', description: '发货、退款、查看订单状态', path: '/shop/orders', permission: 'shop:order' },
  { title: '查看会员全景', description: '资料、等级、团队、余额与订单', path: '/members/list', permission: 'shop:member' },
  { title: '核对订单账务', description: '订单金额、奖金、成本与退款', path: '/audit/orders', permission: 'finance:read' },
  { title: '执行奖金结算', description: '处理已到结算期的待结算奖金', path: '/commission/settle', permission: 'commission:manage' },
  { title: '审核会员提现', description: '核对并处理待审核提现申请', path: '/withdraw/audit', permission: 'finance:manage' },
  { title: '追溯业绩来源', description: '查询指定会员的业绩贡献明细', path: '/performance/contributions', permission: 'distribution:manage' },
]
const visibleQuickActions = computed(() => quickActions.filter((item) => store.hasPermission(item.permission)))

const renderCharts = () => {
  if (salesTrendChart.value) {
    salesTrendChartInstance?.dispose()
    salesTrendChartInstance = echarts.init(salesTrendChart.value)
    const trend = dashboard.value.performanceTrend || []
    salesTrendChartInstance.setOption({
      color: ['#3b82f6'],
      tooltip: { trigger: 'axis', valueFormatter: (value) => `¥${money(value)}` },
      grid: { left: 70, right: 24, top: 28, bottom: 42 },
      xAxis: { type: 'category', boundaryGap: false, data: trend.map((item) => String(item.statDate || '').slice(5)), axisLine: { lineStyle: { color: '#d9e0e8' } }, axisLabel: { color: '#7a8594' } },
      yAxis: { type: 'value', axisLabel: { color: '#7a8594', formatter: (value) => `¥${value}` }, splitLine: { lineStyle: { color: '#edf1f6' } } },
      series: [{
        name: '商品销售额', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6,
        data: trend.map((item) => Number(item.performanceAmount || 0)),
        areaStyle: { color: 'rgba(59,130,246,.12)' }, lineStyle: { width: 3 },
      }],
    })
  }

  if (regionChart.value && hasRegionData.value) {
    regionChartInstance?.dispose()
    regionChartInstance = echarts.init(regionChart.value)
    const regions = dashboard.value.memberRegionDistribution || []
    regionChartInstance.setOption({
      tooltip: { trigger: 'item', formatter: ({ name, value, percent: ratio }) => `${name}<br/>${count(value)} 人（${ratio}%）` },
      legend: { type: 'scroll', orient: 'vertical', left: 0, top: 'middle', bottom: 8, textStyle: { color: '#667085' } },
      series: [{
        name: '会员地区', type: 'pie', radius: ['40%', '68%'], center: ['68%', '50%'], avoidLabelOverlap: true,
        data: regions.map((item) => ({ value: Number(item.memberCount || 0), name: item.regionName || '未知地区' })),
        label: { formatter: '{b}\n{d}%', color: '#475467' },
      }],
    })
  }
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
  regionChartInstance?.resize()
}

onMounted(() => {
  loadDashboard()
  window.addEventListener('resize', resizeCharts)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  salesTrendChartInstance?.dispose()
  regionChartInstance?.dispose()
})
</script>

<style lang="scss" scoped>
.dashboard-container {
  --panel-border: #e7ecf2;
  --muted: #7a8594;
  color: #1f2937;

  .dashboard-hero { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-bottom: 16px; padding: 25px 28px; color: #fff; background: linear-gradient(120deg, #173e76 0%, #2563a7 58%, #2783b9 100%); border-radius: 14px; box-shadow: 0 12px 30px rgba(35, 86, 145, .18); }
  .hero-eyebrow { display: inline-block; margin-bottom: 7px; color: #bfe2ff; font-size: 12px; letter-spacing: 2px; }
  .dashboard-hero h1 { margin: 0; font-size: 25px; line-height: 1.35; }
  .dashboard-hero p { margin: 9px 0 0; color: rgba(255, 255, 255, .78); font-size: 13px; }
  .hero-actions { display: flex; flex-direction: column; align-items: flex-end; gap: 10px; white-space: nowrap; }
  .hero-actions span { color: rgba(255, 255, 255, .68); font-size: 12px; }
  .hero-actions :deep(.el-button) { color: #17558e; background: #fff; border-color: #fff; }
  .real-data-alert { margin-bottom: 18px; border-radius: 9px; }

  .metric-section, .quick-section { margin-bottom: 20px; padding: 21px; background: #fff; border: 1px solid var(--panel-border); border-radius: 12px; }
  .section-heading, .panel-header { display: flex; align-items: center; justify-content: space-between; gap: 18px; }
  .section-heading { margin-bottom: 16px; }
  .section-heading h2 { margin: 0; color: #263244; font-size: 18px; }
  .section-heading p, .panel-header span { display: block; margin: 5px 0 0; color: var(--muted); font-size: 12px; }
  .panel-header b { display: block; color: #263244; font-size: 16px; }
  .panel-header small { color: var(--muted); font-size: 12px; }

  .metric-grid { display: grid; gap: 14px; }
  .sales-grid, .member-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
  .finance-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
  .metric-card { position: relative; min-width: 0; padding: 17px 18px 16px; overflow: hidden; background: #f8fafc; border: 1px solid #edf0f4; border-radius: 10px; }
  .metric-card::before { position: absolute; top: 0; left: 0; width: 4px; height: 100%; background: var(--accent); content: ''; }
  .metric-title-row { display: flex; align-items: center; gap: 7px; }
  .metric-title { color: #5f6b7a; font-size: 13px; }
  .metric-help { display: inline-flex; align-items: center; justify-content: center; width: 16px; height: 16px; color: #8792a2; font-size: 11px; border: 1px solid #b8c0cc; border-radius: 50%; cursor: help; }
  .metric-value { margin: 11px 0 8px; overflow: hidden; color: #202939; font-size: clamp(22px, 2vw, 28px); font-weight: 800; line-height: 1.15; text-overflow: ellipsis; white-space: nowrap; }
  .metric-value small { color: #687386; font-size: 13px; font-weight: 500; }
  .metric-value.negative { color: #dc3545; }
  .metric-caption { min-height: 18px; color: #8a94a3; font-size: 12px; line-height: 1.5; }
  .tone-blue { --accent: #3b82f6; } .tone-indigo { --accent: #6366f1; } .tone-cyan { --accent: #0891b2; }
  .tone-green { --accent: #16a34a; } .tone-orange { --accent: #ea8a16; } .tone-red { --accent: #e24a4a; }
  .tone-purple { --accent: #8b5cf6; } .tone-pink { --accent: #db5a8d; } .tone-teal { --accent: #0f9f8f; }
  .finance-formula { display: flex; flex-wrap: wrap; gap: 10px 24px; margin-top: 14px; padding: 11px 14px; color: #697586; font-size: 12px; background: #f8fafc; border-radius: 8px; }

  .chart-row { margin-bottom: 20px; }
  .panel-card { border-color: var(--panel-border); border-radius: 12px; }
  .chart { height: 330px; }
  .region-summary { display: flex; justify-content: space-between; gap: 10px; margin-top: -4px; padding-top: 11px; color: #7a8594; font-size: 12px; border-top: 1px solid #edf1f5; }
  .ranking-card { margin-bottom: 20px; }
  .product-cell { display: flex; align-items: center; gap: 12px; min-width: 0; }
  .product-cell > div:last-child { min-width: 0; }
  .product-cell b { display: block; overflow: hidden; color: #303b4d; text-overflow: ellipsis; white-space: nowrap; }
  .product-cell small { display: block; margin-top: 5px; color: #98a1ae; }
  .product-cover { display: flex; flex: 0 0 48px; align-items: center; justify-content: center; width: 48px; height: 48px; border-radius: 8px; }
  .product-cover.fallback { color: #8d98a7; font-size: 11px; background: #eef2f6; }
  .rank-badge { display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 28px; color: #64748b; font-weight: 700; background: #edf1f5; border-radius: 8px; }
  .rank-1 { color: #8c5b00; background: #fff0bd; } .rank-2 { color: #596477; background: #e9edf3; } .rank-3 { color: #99572e; background: #f8ddd0; }
  .sales-money { color: #dc3545; }

  .quick-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
  .quick-card { padding: 15px; text-align: left; background: #f8fafc; border: 1px solid #e5ebf3; border-radius: 9px; cursor: pointer; transition: .18s ease; }
  .quick-card:hover { border-color: #409eff; box-shadow: 0 6px 18px rgba(64, 158, 255, .1); transform: translateY(-1px); }
  .quick-card span, .quick-card small, .quick-card b { display: block; }
  .quick-card span { color: #303b4d; font-size: 14px; font-weight: 700; }
  .quick-card small { margin-top: 6px; color: #7b8492; line-height: 1.5; }
  .quick-card b { margin-top: 10px; color: #409eff; font-size: 12px; }
}

@media (max-width: 1280px) {
  .dashboard-container .finance-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .dashboard-container .chart-row :deep(.el-col + .el-col) { margin-top: 20px; }
}
@media (max-width: 960px) {
  .dashboard-container .sales-grid, .dashboard-container .member-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .dashboard-container .quick-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 640px) {
  .dashboard-container .dashboard-hero { align-items: flex-start; flex-direction: column; }
  .dashboard-container .hero-actions { align-items: flex-start; }
  .dashboard-container .sales-grid, .dashboard-container .finance-grid, .dashboard-container .member-grid, .dashboard-container .quick-grid { grid-template-columns: 1fr; }
  .dashboard-container .region-summary { flex-direction: column; }
}
</style>
