<template>
  <div class="page-container">
    <div class="search-container">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="会员账号">
          <el-input v-model="searchForm.memberKey" placeholder="登录账号/手机号" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="订单编号">
          <el-input v-model="searchForm.orderNo" placeholder="输入订单编号精确追溯" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading || bonusLoading" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
      <div class="search-tip">默认展示最近记录；输入会员信息后，可同时筛选该会员的订单和获奖记录。</div>
    </div>

    <el-alert v-if="searchFeedback" :title="searchFeedback" type="warning" :closable="false" show-icon class="search-feedback" />

    <el-table :data="orders" v-loading="loading" style="width: 100%" :empty-text="orderEmptyText">
      <el-table-column prop="orderNo" label="订单编号" min-width="210">
        <template #default="{ row }">
          <span class="business-number">{{ row.orderNo || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="ownerMemberAccount" label="下单会员账号" min-width="165">
        <template #default="{ row }">
          <div class="member-identity">
            <span class="business-number">{{ row.ownerMemberAccount || '-' }}</span>
            <span v-if="memberAuxiliary(row.ownerMemberAccount, row.ownerMemberName || row.ownerAgentName)" class="member-auxiliary">
              {{ memberAuxiliary(row.ownerMemberAccount, row.ownerMemberName || row.ownerAgentName) }}
            </span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="orderAmount" label="支付金额" width="120">
        <template #default="{ row }">¥{{ row.orderAmount || 0 }}</template>
      </el-table-column>
      <el-table-column prop="productCost" label="产品成本" width="120">
        <template #default="{ row }">¥{{ row.productCost || 0 }}</template>
      </el-table-column>
      <el-table-column prop="bonusAmount" label="奖金拨出" width="120">
        <template #default="{ row }">¥{{ row.bonusAmount || 0 }}</template>
      </el-table-column>
      <el-table-column prop="companyProfit" label="公司利润" width="120">
        <template #default="{ row }">
          <span :class="{ danger: Number(row.companyProfit || 0) < 0 }">¥{{ row.companyProfit || 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="riskStatus" label="账务风险" width="100">
        <template #default="{ row }">
          <el-tag :type="row.riskStatus === 1 ? 'danger' : 'success'">
            {{ row.riskStatus === 1 ? '亏损' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="120">
        <template #default="{ row }">
          <el-button type="primary" link @click="openFinance(row)">账务详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      class="pagination-container"
      background
      layout="total, prev, pager, next, sizes"
      :total="orderPage.total"
      v-model:current-page="orderPage.page"
      v-model:page-size="orderPage.size"
      @current-change="fetchOrders"
      @size-change="handleOrderSizeChange"
    />

    <el-card class="block">
      <template #header>
        <span>奖金来源</span>
      </template>
      <el-table :data="bonusSources" v-loading="bonusLoading" style="width: 100%" :empty-text="bonusEmptyText">
        <el-table-column prop="orderNo" label="订单编号" min-width="210">
          <template #default="{ row }">
            <span class="business-number">{{ row.orderNo || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="orderMemberAccount" label="下单会员账号" width="145" />
        <el-table-column prop="agentMemberAccount" label="获奖会员账号" min-width="165">
          <template #default="{ row }">
            <div class="member-identity">
              <span class="business-number">{{ row.agentMemberAccount || '-' }}</span>
              <span v-if="memberAuxiliary(row.agentMemberAccount, row.agentName)" class="member-auxiliary">
                {{ memberAuxiliary(row.agentMemberAccount, row.agentName) }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="commissionLevelName" label="奖金关系层级" width="120" />
        <el-table-column prop="commissionAmount" label="奖金金额" width="120">
          <template #default="{ row }">¥{{ row.commissionAmount || 0 }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pagination-container"
        background
        layout="total, prev, pager, next, sizes"
        :total="bonusPage.total"
        v-model:current-page="bonusPage.page"
        v-model:page-size="bonusPage.size"
        @current-change="fetchBonusSources"
        @size-change="handleBonusSizeChange"
      />
    </el-card>

    <el-dialog v-model="financeVisible" title="订单账务详情" width="900px">
      <el-alert title="订单支付金额和产品成本在下单时冻结；支付后不可人工修改，退款统一通过售后流程冲账。" type="warning" :closable="false" show-icon />
      <el-descriptions :column="2" border style="margin-top: 12px">
        <el-descriptions-item label="订单支付金额">¥{{ financeDetail.finance?.payAmount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="冻结产品成本">¥{{ financeDetail.finance?.productCost || 0 }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="financeDetail.bonusFlows || []" style="width: 100%; margin-top: 12px">
        <el-table-column prop="agentMemberAccount" label="获奖会员账号" min-width="165">
          <template #default="{ row }">
            <div class="member-identity">
              <span class="business-number">{{ row.agentMemberAccount || '-' }}</span>
              <span v-if="memberAuxiliary(row.agentMemberAccount, row.agentName)" class="member-auxiliary">
                {{ memberAuxiliary(row.agentMemberAccount, row.agentName) }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="commissionLevelName" label="奖金关系层级" width="120" />
        <el-table-column prop="commissionRate" label="奖金比例" width="100" />
        <el-table-column prop="commissionAmount" label="奖金金额" width="120" />
      </el-table>

      <el-divider>订单商品款自动归集（真实余额）</el-divider>
      <el-alert
        title="运费不参与归集；产品成本和扣除成本、推广奖金后的剩余商品款分别进入系统内部资金账户。确认收货满7天且无待处理售后后到账，退款会自动冲回。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-table :data="financeDetail.balanceAllocations || []" style="width: 100%; margin-top: 12px" empty-text="该订单尚未生成资金归集明细">
        <el-table-column label="归集项目" width="140">
          <template #default="{ row }">{{ allocationTypeText(row.allocationType) }}</template>
        </el-table-column>
        <el-table-column prop="targetAccount" label="进入资金账户" width="180" />
        <el-table-column prop="originalAmount" label="初始金额" width="110" />
        <el-table-column prop="currentAmount" label="退款后净额" width="120" />
        <el-table-column prop="settledAmount" label="已入余额" width="110" />
        <el-table-column prop="reversedAmount" label="已冲回" width="100" />
        <el-table-column label="状态" min-width="140">
          <template #default="{ row }">
            <el-tag :type="allocationStatusType(row.status)">{{ allocationStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-divider>历史人工分账记录（只读）</el-divider>
      <el-table :data="financeDetail.companyShares || []" style="width: 100%" empty-text="无历史人工分账记录">
        <el-table-column prop="accountId" label="历史账号ID" />
        <el-table-column prop="accountName" label="历史账号名称" />
        <el-table-column prop="shareAmount" label="历史分账金额" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { memberSearchFailureMessage, memberSearchEmptyText, validateMemberSearch, validateSearchKeyword } from '@/utils/searchFeedback'
import { useSearchAutoRestore } from '@/utils/searchAutoRestore'
import {
  getOrderFinance,
  listAuditOrders,
  listBonusSources,
} from '@/api/audit'

const loading = ref(false)
const route = useRoute()
const searchForm = ref({ memberKey: route.query.memberAccount || '', orderNo: route.query.orderNo || '' })
const orders = ref([])
const bonusSources = ref([])
const bonusLoading = ref(false)
const orderPage = ref({ page: 1, size: 10, total: 0 })
const bonusPage = ref({ page: 1, size: 10, total: 0 })
const financeVisible = ref(false)
const financeDetail = ref({})
const searchFeedback = ref('')
const orderEmptyText = ref('暂无订单记录')
const bonusEmptyText = ref('暂无奖金来源记录')
const { markSearchApplied: markMemberSearchApplied } = useSearchAutoRestore(
  () => searchForm.value.memberKey,
  () => handleSearch(),
)
const { markSearchApplied: markOrderSearchApplied } = useSearchAutoRestore(
  () => searchForm.value.orderNo,
  () => handleSearch(),
)

const buildParams = (page) => ({
  memberKey: searchForm.value.memberKey?.trim() || undefined,
  orderNo: searchForm.value.orderNo?.trim() || undefined,
  pageNum: page.page,
  pageSize: page.size,
})

const fetchOrders = async () => {
  loading.value = true
  try {
    const res = await listAuditOrders(buildParams(orderPage.value))
    orders.value = res.data?.list || []
    orderPage.value.total = Number(res.data?.total || 0)
  } catch (error) {
    orders.value = []
    orderPage.value.total = 0
    if (searchForm.value.memberKey) {
      searchFeedback.value ||= memberSearchFailureMessage(error, searchForm.value.memberKey, '订单账务')
    }
  } finally {
    loading.value = false
  }
}

const fetchBonusSources = async () => {
  bonusLoading.value = true
  try {
    const res = await listBonusSources(buildParams(bonusPage.value))
    bonusSources.value = res.data?.list || []
    bonusPage.value.total = Number(res.data?.total || 0)
  } catch (error) {
    bonusSources.value = []
    bonusPage.value.total = 0
    if (searchForm.value.memberKey) {
      searchFeedback.value ||= memberSearchFailureMessage(error, searchForm.value.memberKey, '奖金来源')
    }
  } finally {
    bonusLoading.value = false
  }
}

const handleSearch = async () => {
  const validation = validateMemberSearch(searchForm.value.memberKey)
  if (!validation.valid) {
    orders.value = []
    bonusSources.value = []
    orderPage.value.total = 0
    bonusPage.value.total = 0
    searchFeedback.value = validation.message
    orderEmptyText.value = '请修改搜索内容后重新查询'
    bonusEmptyText.value = '请修改搜索内容后重新查询'
    return
  }
  const orderValidation = validateSearchKeyword(searchForm.value.orderNo, { label: '订单编号', maxLength: 64 })
  if (!orderValidation.valid) {
    orders.value = []
    bonusSources.value = []
    orderPage.value.total = 0
    bonusPage.value.total = 0
    searchFeedback.value = orderValidation.message
    orderEmptyText.value = '请修改搜索内容后重新查询'
    bonusEmptyText.value = '请修改搜索内容后重新查询'
    return
  }
  searchForm.value.memberKey = validation.keyword
  searchForm.value.orderNo = orderValidation.keyword
  searchFeedback.value = ''
  const searchKeyword = orderValidation.keyword || validation.keyword
  markMemberSearchApplied(validation.keyword)
  markOrderSearchApplied(orderValidation.keyword)
  orderEmptyText.value = searchKeyword ? `未找到与“${searchKeyword}”匹配的订单记录` : memberSearchEmptyText('', '订单记录')
  bonusEmptyText.value = searchKeyword ? `未找到与“${searchKeyword}”匹配的奖金来源记录` : memberSearchEmptyText('', '奖金来源记录')
  orderPage.value.page = 1
  bonusPage.value.page = 1
  await Promise.all([fetchOrders(), fetchBonusSources()])
}

const resetSearch = () => {
  searchForm.value.memberKey = ''
  searchForm.value.orderNo = ''
  handleSearch()
}

const handleOrderSizeChange = () => { orderPage.value.page = 1; fetchOrders() }
const handleBonusSizeChange = () => { bonusPage.value.page = 1; fetchBonusSources() }

const openFinance = async (row) => {
  const res = await getOrderFinance(row.orderId)
  financeDetail.value = res.data || {}
  financeVisible.value = true
}

const allocationTypeText = (type) => type === 'PRODUCT_COST' ? '产品成本' : '剩余商品款'
const allocationStatusText = (status) => ({ 0: '待满7天结算', 1: '已进入余额', 2: '已全部冲回/无需结算' }[status] || '未知')
const allocationStatusType = (status) => ({ 0: 'warning', 1: 'success', 2: 'info' }[status] || 'info')
const memberAuxiliary = (memberAccount, value) => {
  const normalizedMemberAccount = String(memberAccount || '').trim()
  const normalizedValue = String(value || '').trim()
  return normalizedValue && normalizedValue !== normalizedMemberAccount ? normalizedValue : ''
}

onMounted(handleSearch)
</script>

<style scoped>
.block {
  margin-top: 16px;
}
.search-tip {
  margin-top: -4px;
  color: #909399;
  font-size: 13px;
}
.search-feedback {
  margin-bottom: 16px;
}
.danger {
  color: #f56c6c;
  font-weight: 600;
}
.business-number {
  color: #303133;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
.member-identity {
  display: flex;
  flex-direction: column;
  gap: 3px;
  line-height: 1.35;
}
.member-auxiliary {
  color: #909399;
  font-size: 12px;
}
.dialog-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.asset-pay-form {
  margin-top: 12px;
}
</style>
