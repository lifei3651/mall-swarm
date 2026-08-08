<template>
  <div class="page-container">
    <el-alert
      title="这里记录所有余额资金流水。可按会员、动账时间、资金来源或订单/关联单号查询，支持完整对账。"
      type="info"
      :closable="false"
      show-icon
      class="flow-tip"
    />

    <div class="search-container">
      <el-form :inline="true" :model="query">
        <el-form-item label="会员/流水号">
          <el-input v-model="query.keyword" placeholder="登录账号/手机号/名称/流水单号" clearable @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="订单/关联单号">
          <el-input v-model="query.relatedNo" placeholder="订单号或关联业务号" clearable @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="收支方向">
          <el-select v-model="query.direction" clearable placeholder="全部" style="width:120px" @change="search">
            <el-option label="收入" value="IN" />
            <el-option label="支出" value="OUT" />
          </el-select>
        </el-form-item>
        <el-form-item label="资金来源">
          <el-select v-model="query.sourceType" clearable placeholder="全部" style="width:145px" @change="search">
            <el-option label="人工充值/扣减" value="RECHARGE" />
            <el-option label="奖金入账" value="BONUS" />
            <el-option label="订单成本/剩余款" value="ORDER_ALLOCATION" />
            <el-option label="会员转账" value="TRANSFER" />
            <el-option label="余额支付" value="PAYMENT" />
            <el-option label="退款退回" value="REFUND" />
            <el-option label="提现" value="WITHDRAW" />
            <el-option label="退款追回奖金" value="CLAWBACK" />
          </el-select>
        </el-form-item>
        <el-form-item label="动账时间">
          <el-date-picker
            v-model="query.dateRange"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-alert v-if="searchFeedback" :title="searchFeedback" type="warning" :closable="false" show-icon class="search-feedback" />

    <div class="summary-grid">
      <div class="summary-card primary">
        <div class="summary-label">筛选范围人工充值合计</div>
        <div class="summary-value">¥{{ money(summary.totalRechargeAmount) }}</div>
      </div>
      <div class="summary-card income">
        <div class="summary-label">筛选范围收入合计</div>
        <div class="summary-value">¥{{ money(summary.totalIncomeAmount) }}</div>
      </div>
      <div class="summary-card expense">
        <div class="summary-label">筛选范围支出合计</div>
        <div class="summary-value">¥{{ money(summary.totalExpenseAmount) }}</div>
      </div>
    </div>

    <el-table :data="rows" v-loading="loading" :empty-text="tableEmptyText" style="width:100%">
      <el-table-column label="流水单号" min-width="190" show-overflow-tooltip>
        <template #default="{ row }">{{ row.flowNo || '-' }}</template>
      </el-table-column>
      <el-table-column label="动账时间" width="175"><template #default="{ row }">{{ formatDateTime(row.createTime) }}</template></el-table-column>
      <el-table-column label="账号ID" width="115"><template #default="{ row }">{{ row.memberId || row.userId || row.agentId || '-' }}</template></el-table-column>
      <el-table-column label="会员信息" min-width="190">
        <template #default="{ row }">
          <div class="member-name">{{ row.memberName || row.memberPhone || `用户${row.userId}` }}</div>
          <div class="sub">账号：{{ row.memberUsername || '-' }} · {{ row.memberPhone || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="动账类型/收支类型" min-width="170">
        <template #default="{ row }"><el-tag :type="sourceTag(row)">{{ changeTypeName(row.changeType) }} / {{ isIncome(row.changeType) ? '收入' : '支出' }}</el-tag><div class="sub">{{ sourceName(row) }}</div></template>
      </el-table-column>
      <el-table-column label="收支金额" width="130" align="right">
        <template #default="{ row }"><strong :class="isIncome(row.changeType) ? 'income' : 'expense'">{{ isIncome(row.changeType) ? '+' : '-' }}¥{{ money(row.amount) }}</strong></template>
      </el-table-column>
      <el-table-column label="变动前余额" width="130" align="right">
        <template #default="{ row }">¥{{ money(row.balanceBefore) }}</template>
      </el-table-column>
      <el-table-column label="动账余额" width="130" align="right">
        <template #default="{ row }">¥{{ money(row.balanceAfter) }}</template>
      </el-table-column>
      <el-table-column label="操作管理员" width="145">
        <template #default="{ row }">{{ row.operatorName || '历史记录未留存' }}</template>
      </el-table-column>
      <el-table-column label="关联单号" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ relatedNumber(row) }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="说明" min-width="250" show-overflow-tooltip />
    </el-table>

    <el-pagination
      class="pagination"
      background
      layout="total, prev, pager, next, sizes"
      :total="pagination.total"
      v-model:current-page="pagination.page"
      v-model:page-size="pagination.size"
      :page-sizes="[20, 50, 100]"
      @current-change="fetchRows"
      @size-change="fetchRows"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getBalanceFlowSummary, listBalanceFlowRecords } from '@/api/assets'
import { validateSearchKeyword } from '@/utils/searchFeedback'
import { useSearchAutoRestore } from '@/utils/searchAutoRestore'
import { formatDateTime } from '@/utils/dateTime'

const loading = ref(false)
const rows = ref([])
const summary = ref({ totalRechargeAmount: 0, totalIncomeAmount: 0, totalExpenseAmount: 0 })
const query = ref({ keyword: '', relatedNo: '', direction: null, sourceType: null, dateRange: [] })
const pagination = ref({ page: 1, size: 20, total: 0 })
const searchFeedback = ref('')
const tableEmptyText = ref('暂无余额流水')
const { markSearchApplied: markKeywordSearchApplied } = useSearchAutoRestore(
  () => query.value.keyword,
  () => search(),
)

const money = (value) => Number(value || 0).toFixed(2)
const isIncome = (type) => [1, 4].includes(Number(type))
const changeTypeName = (type) => ({ 1: '发放', 2: '消费', 3: '转出', 4: '转入', 5: '扣减' }[Number(type)] || '其他')
const relatedNumber = (row) => {
  const remark = String(row.remark || '')
  const matched = remark.match(/(?:订单|售后单|售后退款|申请提现|提现审核拒绝退回|退款追回已结算佣金)[：:]([^，,；;\s]+)/)
  return matched?.[1] || row.bizId || '-'
}
const sourceName = (row) => {
  if (String(row.bizType || '').endsWith('MANUAL_MEMBER_ADJUST')) {
    return isIncome(row.changeType) ? '人工充值' : '后台扣减'
  }
  return ({
    COMMISSION_SETTLE: '奖金入账',
    MEMBER_BALANCE_TRANSFER: Number(row.changeType) === 4 ? '会员转入' : '转给会员',
    ORDER_BALANCE_PAYMENT: '余额支付',
    BALANCE_PAYMENT_REFUND: '订单退款退回',
    WITHDRAW_APPLY: '申请提现',
    WITHDRAW_REJECT_REFUND: '提现驳回退回',
    COMMISSION_CLAWBACK: '退款追回奖金',
    ORDER_BALANCE_ALLOCATION: '订单成本/剩余款入账',
    ORDER_BALANCE_ALLOCATION_REFUND: '订单成本/剩余款退款冲回',
  }[row.bizType] || (isIncome(row.changeType) ? '其他收入' : '其他支出'))
}
const sourceTag = (row) => row.bizType === 'COMMISSION_SETTLE' ? 'success' : (isIncome(row.changeType) ? 'primary' : 'warning')

const fetchRows = async () => {
  const validation = validateSearchKeyword(query.value.keyword, { label: '会员或流水号关键词' })
  if (!validation.valid) {
    rows.value = []
    pagination.value.total = 0
    searchFeedback.value = validation.message
    tableEmptyText.value = '请修改搜索内容后重新查询'
    return
  }
  query.value.keyword = validation.keyword
  markKeywordSearchApplied(validation.keyword)
  searchFeedback.value = ''
  tableEmptyText.value = validation.keyword
    ? `未找到与“${validation.keyword}”匹配的余额流水`
    : '暂无余额流水'
  loading.value = true
  try {
    const params = {
      keyword: query.value.keyword?.trim() || undefined,
      relatedNo: query.value.relatedNo?.trim() || undefined,
      direction: query.value.direction || undefined,
      sourceType: query.value.sourceType || undefined,
      startTime: query.value.dateRange?.[0],
      endTime: query.value.dateRange?.[1],
      pageNum: pagination.value.page,
      pageSize: pagination.value.size,
    }
    const [res, summaryRes] = await Promise.all([
      listBalanceFlowRecords(params),
      getBalanceFlowSummary(params),
    ])
    rows.value = res.data?.list || []
    pagination.value.total = res.data?.total || 0
    summary.value = summaryRes.data || { totalRechargeAmount: 0, totalIncomeAmount: 0, totalExpenseAmount: 0 }
  } finally {
    loading.value = false
  }
}

const search = () => { pagination.value.page = 1; fetchRows() }
const reset = () => { query.value = { keyword: '', relatedNo: '', direction: null, sourceType: null, dateRange: [] }; search() }
onMounted(fetchRows)
</script>

<style scoped>
.flow-tip { margin-bottom: 16px; }
.search-feedback { margin-bottom: 16px; }
.summary-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-bottom: 16px; }
.summary-card { border: 1px solid #ebeef5; border-radius: 8px; padding: 14px 16px; background: #fff; }
.summary-card.primary { border-color: #c6e2ff; background: #f4f9ff; }
.summary-card.income { border-color: #c2e7b0; background: #f3fbef; }
.summary-card.expense { border-color: #fbc4c4; background: #fff6f6; }
.summary-label { color: #606266; font-size: 13px; }
.summary-value { margin-top: 6px; font-size: 22px; font-weight: 700; color: #303133; }
.member-name { font-weight: 600; }
.sub { margin-top: 4px; color: #909399; font-size: 12px; }
.income { color: #16a34a; }
.expense { color: #dc2626; }
.pagination { margin-top: 16px; justify-content: flex-end; }
@media (max-width: 900px) { .summary-grid { grid-template-columns: 1fr; } }
</style>
