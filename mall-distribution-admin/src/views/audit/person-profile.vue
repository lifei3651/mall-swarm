<template>
  <div class="page-container">
    <div class="search-container">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="会员">
          <el-input v-model="searchForm.keyword" placeholder="登录账号/手机号" clearable @keyup.enter="handleSearch" />
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

    <el-empty v-if="!profile.agent && !loading" :description="profileEmptyText" />

    <template v-else>
      <div class="metric-grid">
        <div class="metric">
          <div class="label">累计佣金</div>
          <div class="value">¥{{ money(account.totalCommission) }}</div>
        </div>
        <div class="metric">
          <div class="label">待结算</div>
          <div class="value warning">¥{{ money(account.unsettledCommission) }}</div>
        </div>
        <div class="metric">
          <div class="label">余额</div>
          <div class="value success">¥{{ money(balance) }}</div>
        </div>
        <div class="metric">
          <div class="label">未清欠款</div>
          <div class="value" :class="{ danger: Number(profile.pendingDebtAmount || 0) > 0 }">
            ¥{{ money(profile.pendingDebtAmount) }}
          </div>
        </div>
      </div>

      <el-card class="block">
        <template #header>基础资料</template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="登录账号">{{ memberAccount }}</el-descriptions-item>
          <el-descriptions-item label="会员名称">{{ member.nickname || member.username || member.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ member.phone || agent.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前级别">{{ levelName(agent.agentLevel) }}</el-descriptions-item>
          <el-descriptions-item label="真实姓名">{{ agent.realName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="邀请码">{{ agent.inviteCode || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card class="block">
        <template #header>订单</template>
        <el-table :data="profile.orders || []" v-loading="loading" style="width: 100%">
          <el-table-column prop="orderId" label="订单ID" width="120" />
          <el-table-column prop="orderNo" label="订单编号" min-width="170" />
          <el-table-column prop="orderTime" label="下单时间" min-width="160" :formatter="formatDateTimeCell" />
          <el-table-column prop="orderAmount" label="支付金额" width="120">
            <template #default="{ row }">¥{{ money(row.orderAmount) }}</template>
          </el-table-column>
          <el-table-column prop="bonusAmount" label="奖金拨出" width="120">
            <template #default="{ row }">¥{{ money(row.bonusAmount) }}</template>
          </el-table-column>
          <el-table-column prop="companyProfit" label="公司利润" width="120">
            <template #default="{ row }">
              <span :class="{ danger: Number(row.companyProfit || 0) < 0 }">¥{{ money(row.companyProfit) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card class="block">
        <template #header>奖金记录</template>
        <el-table :data="profile.commissions || []" style="width: 100%">
          <el-table-column prop="orderId" label="来源订单ID" width="130" />
          <el-table-column prop="orderNo" label="订单编号" min-width="170" />
          <el-table-column prop="orderMemberAccount" label="下单登录账号" width="145" />
          <el-table-column label="奖金类型" width="180">
            <template #default="{ row }"><el-tag>{{ bonusTypeText(row) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="commissionLevel" label="关系深度" width="100" />
          <el-table-column label="奖金比例" width="100">
            <template #default="{ row }">{{ percent(row.commissionRate) }}</template>
          </el-table-column>
          <el-table-column prop="commissionAmount" label="奖金金额" width="120">
            <template #default="{ row }">¥{{ money(row.commissionAmount) }}</template>
          </el-table-column>
          <el-table-column prop="statusName" label="奖金状态" width="100" />
          <el-table-column prop="createTime" label="产生时间" width="170" :formatter="formatDateTimeCell" />
          <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        </el-table>
      </el-card>

      <el-card class="block">
        <template #header>退款追回与欠款抵扣</template>
        <el-table :data="profile.clawbacks || []" style="width: 100%">
          <el-table-column prop="orderId" label="订单ID" width="120" />
          <el-table-column prop="orderNo" label="订单编号" min-width="170" />
          <el-table-column prop="clawbackType" label="追回类型" width="130">
            <template #default="{ row }">{{ clawbackTypeText(row.clawbackType) }}</template>
          </el-table-column>
          <el-table-column prop="clawbackAmount" label="追回/抵扣" width="120">
            <template #default="{ row }">¥{{ money(row.clawbackAmount) }}</template>
          </el-table-column>
          <el-table-column prop="deductedAmount" label="已扣回" width="120">
            <template #default="{ row }">¥{{ money(row.deductedAmount) }}</template>
          </el-table-column>
          <el-table-column prop="debtAmount" label="剩余欠款" width="120">
            <template #default="{ row }">
              <span :class="{ danger: Number(row.debtAmount || 0) > 0 }">¥{{ money(row.debtAmount) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="追回状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'warning'">{{ row.status === 1 ? '已完成' : '部分完成' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="追回原因" min-width="220" show-overflow-tooltip />
        </el-table>
      </el-card>

      <el-card class="block">
        <template #header>余额</template>
        <el-table :data="visibleAssetAccounts" style="width: 100%">
          <el-table-column prop="balance" label="可用余额" width="130">
            <template #default="{ row }">{{ money(row.balance) }}</template>
          </el-table-column>
          <el-table-column prop="frozenBalance" label="冻结" width="120">
            <template #default="{ row }">{{ money(row.frozenBalance) }}</template>
          </el-table-column>
          <el-table-column prop="totalIn" label="累计收入" width="130">
            <template #default="{ row }">{{ money(row.totalIn) }}</template>
          </el-table-column>
          <el-table-column prop="totalOut" label="累计支出" width="130">
            <template #default="{ row }">{{ money(row.totalOut) }}</template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card class="block">
        <template #header>余额流水</template>
        <el-table :data="visibleAssetFlows" style="width: 100%">
          <el-table-column prop="flowNo" label="流水号" width="190" />
          <el-table-column prop="changeType" label="余额变动类型" width="120">
            <template #default="{ row }">{{ assetFlowTypeText(row.changeType) }}</template>
          </el-table-column>
          <el-table-column label="资金来源" width="160">
            <template #default="{ row }">{{ assetFlowSourceText(row) }}</template>
          </el-table-column>
          <el-table-column prop="amount" label="变动金额" width="110">
            <template #default="{ row }"><span :class="isIncome(row.changeType) ? 'success' : 'danger'">{{ isIncome(row.changeType) ? '+' : '-' }}¥{{ money(row.amount) }}</span></template>
          </el-table-column>
          <el-table-column prop="balanceBefore" label="变动前余额" width="130">
            <template #default="{ row }">{{ money(row.balanceBefore) }}</template>
          </el-table-column>
          <el-table-column prop="balanceAfter" label="变动后余额" width="130">
            <template #default="{ row }">{{ money(row.balanceAfter) }}</template>
          </el-table-column>
          <el-table-column label="操作管理员" width="145"><template #default="{ row }">{{ row.operatorName || '历史记录未留存' }}</template></el-table-column>
          <el-table-column prop="relatedAgentId" label="关联会员ID" width="120" />
          <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
          <el-table-column label="变动时间" width="170"><template #default="{ row }">{{ formatDateTime(row.createTime) }}</template></el-table-column>
        </el-table>
      </el-card>

      <el-card class="block">
        <template #header>提现记录</template>
        <el-table :data="profile.withdraws || []" style="width: 100%">
          <el-table-column prop="withdrawNo" label="提现单号" min-width="170" />
          <el-table-column prop="withdrawAmount" label="提现金额" width="120">
            <template #default="{ row }">¥{{ money(row.withdrawAmount) }}</template>
          </el-table-column>
          <el-table-column prop="accountName" label="账户姓名" width="120" />
          <el-table-column prop="status" label="提现状态" width="100" />
          <el-table-column prop="createTime" label="申请时间" min-width="160" :formatter="formatDateTimeCell" />
          <el-table-column prop="auditRemark" label="审核备注" min-width="180" show-overflow-tooltip />
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getPersonProfile } from '@/api/audit'
import { memberSearchFailureMessage, validateMemberSearch } from '@/utils/searchFeedback'
import { useSearchAutoRestore } from '@/utils/searchAutoRestore'
import { formatDateTime, formatDateTimeCell } from '@/utils/dateTime'
import { customerBonusName } from '@/utils/customerBonus'

const loading = ref(false)
const route = useRoute()
const searchForm = ref({ keyword: '' })
const profile = ref({})
const searchFeedback = ref('')
const profileEmptyText = ref('输入登录账号或手机号，可集中查看订单、奖金、退款、余额和提现记录')
const restoreInitialState = () => {
  profile.value = {}
  searchFeedback.value = ''
  profileEmptyText.value = '输入登录账号或手机号，可集中查看订单、奖金、退款、余额和提现记录'
}
const { markSearchApplied: markKeywordSearchApplied } = useSearchAutoRestore(
  () => searchForm.value.keyword,
  restoreInitialState,
)

const agent = computed(() => profile.value.agent || {})
const member = computed(() => profile.value.member || {})
const account = computed(() => profile.value.account || {})
const visibleAssetAccounts = computed(() => (profile.value.assetAccounts || []).filter((item) => item.assetCode === 'CASH_BONUS'))
const visibleAssetFlows = computed(() => (profile.value.assetFlows || []).filter((item) => item.assetCode === 'CASH_BONUS'))
const balance = computed(() => visibleAssetAccounts.value.find((item) => item.assetCode === 'CASH_BONUS')?.balance || 0)
const memberAccount = computed(() => member.value.username || member.value.phone || '-')

const handleSearch = async () => {
  const validation = validateMemberSearch(searchForm.value.keyword, { required: true })
  if (!validation.valid) {
    profile.value = {}
    searchFeedback.value = validation.message
    profileEmptyText.value = '请修改搜索内容后重新查询'
    return
  }
  const keyword = validation.keyword
  searchForm.value.keyword = keyword
  markKeywordSearchApplied(keyword)
  searchFeedback.value = ''
  loading.value = true
  try {
    const res = await getPersonProfile({ keyword })
    profile.value = res.data || {}
    profileEmptyText.value = `会员“${keyword}”暂无可展示的全景数据`
  } catch (error) {
    profile.value = {}
    searchFeedback.value = memberSearchFailureMessage(error, keyword, '会员全景')
    profileEmptyText.value = '未能完成会员全景查询，请核对搜索内容后重试'
  } finally {
    loading.value = false
  }
}

const money = (value) => Number(value || 0).toFixed(2)
const percent = (value) => `${(Number(value || 0) * 100).toFixed(2)}%`
const levelName = (level) => ({ 1:'会员', 2:'VIP会员', 3:'店铺', 4:'代理', 5:'一星董事', 6:'二星董事', 7:'三星董事', 8:'合伙人' }[Number(level)] || '-')

const clawbackTypeText = (type) => ({
  1: '待结算减少',
  2: '可提现扣回',
  3: '欠款待抵扣',
  4: '未来佣金抵扣',
}[type] || '未知')

const assetFlowTypeText = (type) => ({
  1: '发放',
  2: '消费',
  3: '转出',
  4: '转入',
  5: '扣减',
}[type] || '未知')

const bonusTypeText = (row) => customerBonusName(row)
const isIncome = (type) => [1, 4].includes(Number(type))
const assetFlowSourceText = (row) => {
  if (String(row.bizType || '').endsWith('MANUAL_MEMBER_ADJUST')) {
    return isIncome(row.changeType) ? '后台人工充值' : '后台人工扣减'
  }
  return ({
    COMMISSION_SETTLE: '奖金结算入账',
    MEMBER_BALANCE_TRANSFER: Number(row.changeType) === 4 ? '会员转入' : '转给会员',
    ORDER_BALANCE_PAYMENT: '商城余额支付',
    BALANCE_PAYMENT_REFUND: '订单退款退回',
    WITHDRAW_APPLY: '申请提现',
    WITHDRAW_REJECT_REFUND: '提现驳回退回',
    COMMISSION_CLAWBACK: '退款追回已结算奖金',
    ORDER_BALANCE_ALLOCATION: '订单成本/剩余款入账',
    ORDER_BALANCE_ALLOCATION_REFUND: '订单成本/剩余款退款冲回',
  }[row.bizType] || '其他余额变动')
}

onMounted(() => {
  const keyword = String(route.query.keyword || '').trim()
  if (keyword) {
    searchForm.value.keyword = keyword
    handleSearch()
  }
})
</script>

<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.metric {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 14px 16px;
  background: #fff;
}
.label {
  color: #909399;
  font-size: 13px;
}
.value {
  margin-top: 8px;
  font-size: 22px;
  font-weight: 600;
}
.success {
  color: #67c23a;
}
.warning {
  color: #e6a23c;
}
.danger {
  color: #f56c6c;
  font-weight: 600;
}
.block {
  margin-top: 16px;
}
.search-feedback {
  margin-bottom: 16px;
}
@media (max-width: 900px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
