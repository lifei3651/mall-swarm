<template>
  <div class="page-container">
    <el-page-header @back="handleBack">
      <template #content>
        <span>账户详情</span>
      </template>
    </el-page-header>

    <el-row :gutter="20" style="margin-top: 20px">
      <!-- 账户信息 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>账户信息</span>
          </template>
          <div class="account-stats">
            <div class="stat-item">
              <div class="label">累计佣金</div>
              <div class="value">¥{{ account.totalCommission }}</div>
            </div>
            <div class="stat-item">
              <div class="label">已结算佣金</div>
              <div class="value success">¥{{ account.settledCommission }}</div>
            </div>
            <div class="stat-item">
              <div class="label">待结算佣金</div>
              <div class="value warning">¥{{ account.unsettledCommission }}</div>
            </div>
            <div class="stat-item">
              <div class="label">可提现余额</div>
              <div class="value primary">¥{{ account.availableBalance }}</div>
            </div>
            <div class="stat-item">
              <div class="label">已提现金额</div>
              <div class="value">¥{{ account.withdrawnAmount }}</div>
            </div>
            <div class="stat-item">
              <div class="label">冻结佣金</div>
              <div class="value danger">¥{{ account.frozenCommission }}</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 推广信息 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>推广信息</span>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="登录账号">{{ account.memberAccount || '-' }}</el-descriptions-item>
            <el-descriptions-item label="会员名称">{{ account.agentName }}</el-descriptions-item>
            <el-descriptions-item label="邀请码">{{ account.inviteCode }}</el-descriptions-item>
            <el-descriptions-item label="本人及团队累计有效件数">{{ account.totalOrders }}</el-descriptions-item>
            <el-descriptions-item label="团队成员数">{{ account.totalTeamMembers }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近佣金记录 -->
    <el-card style="margin-top: 20px">
      <template #header>
        <div class="card-header">
          <span>最近佣金记录</span>
          <el-button type="primary" link @click="viewAllCommission">查看全部</el-button>
        </div>
      </template>
      <el-table :data="recentCommissions" style="width: 100%">
        <el-table-column prop="recordNo" label="奖金记录编号" width="180" />
        <el-table-column prop="orderNo" label="订单编号" width="180" />
        <el-table-column prop="orderAmount" label="订单金额" width="100">
          <template #default="{ row }">
            ¥{{ row.orderAmount }}
          </template>
        </el-table-column>
        <el-table-column prop="commissionLevel" label="奖金类型" width="130">
          <template #default="{ row }">
            <el-tag>{{ getBonusName(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="commissionAmount" label="奖金金额" width="120">
          <template #default="{ row }">
            <span style="color: #67c23a">¥{{ row.commissionAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="statusName" label="奖金状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="奖金产生时间" />
      </el-table>
    </el-card>

    <!-- 最近提现记录 -->
    <el-card style="margin-top: 20px">
      <template #header>
        <div class="card-header">
          <span>最近提现记录</span>
          <el-button type="primary" link @click="viewAllWithdraw">查看全部</el-button>
        </div>
      </template>
      <el-table :data="recentWithdraws" style="width: 100%">
        <el-table-column prop="withdrawNo" label="提现单号" width="180" />
        <el-table-column prop="withdrawAmount" label="提现金额" width="120">
          <template #default="{ row }">
            <span style="color: #f56c6c">¥{{ row.withdrawAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="withdrawTypeName" label="提现方式" width="100" />
        <el-table-column prop="statusName" label="提现状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getWithdrawStatusType(row.status)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="申请时间" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getAccountByAgentId } from '@/api/account'
import { getAgentById } from '@/api/agent'
import { getCommissionRecords } from '@/api/commission'
import { getWithdrawsByAgentId } from '@/api/withdraw'

const router = useRouter()
const route = useRoute()
const agentId = route.params.agentId

// 账户信息
const account = ref({})

// 最近佣金记录
const recentCommissions = ref([])

// 最近提现记录
const recentWithdraws = ref([])

const getBonusName = (row) => row.bonusType === 'DIRECT_REWARD'
  ? '直推奖'
  : row.bonusType === 'DIRECTOR_SHARE' ? `董事团队分红（第${row.commissionLevel}层）` : '历史奖金'

// 获取状态类型
const getStatusType = (status) => {
  const map = { 0: 'warning', 1: 'success', 2: 'info', 3: 'danger' }
  return map[status] || 'info'
}

// 获取提现状态类型
const getWithdrawStatusType = (status) => {
  const map = { 0: 'warning', 1: 'success', 2: '', 3: 'success', 4: 'danger' }
  return map[status] || 'info'
}

// 返回
const handleBack = () => {
  router.back()
}

// 查看全部佣金记录
const viewAllCommission = () => {
  router.push(`/commission/records?memberAccount=${encodeURIComponent(account.value.memberAccount || '')}`)
}

// 查看全部提现记录
const viewAllWithdraw = () => {
  router.push(`/withdraw/list?memberAccount=${encodeURIComponent(account.value.memberAccount || '')}`)
}

onMounted(async () => {
  const [accountRes, agentRes, commissionRes, withdrawRes] = await Promise.all([
    getAccountByAgentId(agentId),
    getAgentById(agentId),
    getCommissionRecords({ agentId: Number(agentId), pageNum: 1, pageSize: 5 }),
    getWithdrawsByAgentId(agentId),
  ])
  account.value = {
    ...accountRes.data,
    memberAccount: agentRes.data?.memberAccount || accountRes.data?.memberAccount,
    agentName: agentRes.data?.agentName,
    inviteCode: agentRes.data?.inviteCode,
  }
  recentCommissions.value = commissionRes.data?.list || []
  recentWithdraws.value = (withdrawRes.data?.list || []).slice(0, 5)
})
</script>

<style lang="scss" scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.account-stats {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;

  .stat-item {
    .label {
      font-size: 14px;
      color: #909399;
      margin-bottom: 8px;
    }

    .value {
      font-size: 24px;
      font-weight: bold;
      color: #303133;

      &.success {
        color: #67c23a;
      }

      &.warning {
        color: #e6a23c;
      }

      &.primary {
        color: #409eff;
      }

      &.danger {
        color: #f56c6c;
      }
    }
  }
}
</style>
