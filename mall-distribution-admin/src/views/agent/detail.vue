<template>
  <div class="page-container">
    <el-page-header @back="handleBack">
      <template #content>
        <span>会员关系详情</span>
      </template>
    </el-page-header>

    <el-row :gutter="20" style="margin-top: 20px">
      <!-- 基本信息 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>基本信息</span>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="登录账号">{{ agentInfo.memberAccount || '-' }}</el-descriptions-item>
            <el-descriptions-item label="会员名称">{{ agentInfo.agentName }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ agentInfo.phone }}</el-descriptions-item>
            <el-descriptions-item label="真实姓名">{{ agentInfo.realName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="会员卡级">
              <el-tag :type="getLevelType(agentInfo.agentLevel)">{{ agentInfo.agentLevelName }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusType(agentInfo.status)">{{ agentInfo.statusName }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="邀请码">
              <el-tag>{{ agentInfo.inviteCode }}</el-tag>
              <el-button type="primary" link style="margin-left: 10px" @click="copyInviteCode">复制</el-button>
            </el-descriptions-item>
            <el-descriptions-item label="推广线上级">{{ agentInfo.parentName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="层级深度">{{ agentInfo.levelDepth }}</el-descriptions-item>
            <el-descriptions-item label="来源">{{ agentInfo.sourceTypeName }}</el-descriptions-item>
            <el-descriptions-item label="注册时间">{{ formatDateTime(agentInfo.createTime) }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <!-- 账户信息 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>账户信息</span>
          </template>
          <div class="account-stats">
            <div class="stat-item">
              <div class="label">累计佣金</div>
              <div class="value">¥{{ accountInfo.totalCommission || '0.00' }}</div>
            </div>
            <div class="stat-item">
              <div class="label">已结算佣金</div>
              <div class="value success">¥{{ accountInfo.settledCommission || '0.00' }}</div>
            </div>
            <div class="stat-item">
              <div class="label">待结算佣金</div>
              <div class="value warning">¥{{ accountInfo.unsettledCommission || '0.00' }}</div>
            </div>
            <div class="stat-item">
              <div class="label">可提现余额</div>
              <div class="value primary">¥{{ accountInfo.availableBalance || '0.00' }}</div>
            </div>
            <div class="stat-item">
              <div class="label">已提现金额</div>
              <div class="value">¥{{ accountInfo.withdrawnAmount || '0.00' }}</div>
            </div>
            <div class="stat-item">
              <div class="label">本人及团队累计有效件数</div>
              <div class="value">{{ accountInfo.totalOrders || 0 }}</div>
            </div>
            <div class="stat-item">
              <div class="label">团队成员数</div>
              <div class="value">{{ accountInfo.totalTeamMembers || 0 }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 业绩概览 -->
    <el-card style="margin-top: 20px">
      <template #header>
        <div class="card-header">
          <span>业绩概览（本月）</span>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            size="small"
            @change="fetchPerformance"
          />
        </div>
      </template>
      <el-row :gutter="20">
        <el-col :span="6">
          <div class="performance-item">
            <div class="label">个人业绩</div>
            <div class="value">¥{{ performance.personalPerformance || '0.00' }}</div>
            <div class="count">{{ performance.personalOrderCount || 0 }}笔</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="performance-item">
            <div class="label">团队业绩</div>
            <div class="value">¥{{ performance.teamPerformance || '0.00' }}</div>
            <div class="count">{{ performance.teamOrderCount || 0 }}笔</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="performance-item">
            <div class="label">一级业绩</div>
            <div class="value">¥{{ performance.level1Performance || '0.00' }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="performance-item">
            <div class="label">二级业绩</div>
            <div class="value">¥{{ performance.level2Performance || '0.00' }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 推广二维码 -->
    <el-card style="margin-top: 20px">
      <template #header>
        <span>推广二维码</span>
      </template>
      <div class="qrcode-container">
        <img :src="qrcodeUrl" alt="推广二维码" class="qrcode-img" v-if="qrcodeUrl" />
        <div v-else class="qrcode-placeholder">暂无二维码</div>
        <el-button type="primary" @click="generateQrcode">生成二维码</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAccountByAgentId } from '@/api/account'
import { generateQrCode, getAgentById } from '@/api/agent'
import { getPerformanceOverview } from '@/api/performance'
import { formatDateTime } from '@/utils/dateTime'

const router = useRouter()
const route = useRoute()
const agentId = route.params.id

// 代理信息
const agentInfo = ref({})

// 账户信息
const accountInfo = ref({})

// 业绩信息
const performance = ref({})

// 日期范围
const dateRange = ref([])

// 二维码URL
const qrcodeUrl = ref('')

// 获取等级类型
const getLevelType = (level) => {
  const map = { 1: 'info', 2: '', 3: 'warning', 4: 'danger' }
  return map[level] || 'info'
}

// 获取状态类型
const getStatusType = (status) => {
  const map = { 0: 'danger', 1: 'success', 2: 'warning' }
  return map[status] || 'info'
}

// 返回
const handleBack = () => {
  router.back()
}

// 复制邀请码
const copyInviteCode = () => {
  navigator.clipboard.writeText(agentInfo.value.inviteCode)
  ElMessage.success('复制成功')
}

// 获取业绩数据
const fetchPerformance = async () => {
  const [startDate, endDate] = getDateRange()
  const res = await getPerformanceOverview(agentId, startDate, endDate)
  performance.value = res.data || {}
}

// 生成二维码
const generateQrcode = async () => {
  const res = await generateQrCode(agentId)
  qrcodeUrl.value = res.data
  ElMessage.success('二维码生成成功')
}

onMounted(async () => {
  const [agentRes, accountRes] = await Promise.all([
    getAgentById(agentId),
    getAccountByAgentId(agentId),
  ])
  agentInfo.value = agentRes.data || {}
  accountInfo.value = accountRes.data || {}
  fetchPerformance()
})

const getDateRange = () => {
  if (dateRange.value?.length === 2) {
    return dateRange.value.map(formatDate)
  }
  const end = new Date()
  const start = new Date(end.getFullYear(), end.getMonth(), 1)
  return [formatDate(start), formatDate(end)]
}

const formatDate = (date) => {
  const d = new Date(date)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
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
    }
  }
}

.performance-item {
  text-align: center;
  padding: 20px;
  background-color: #f5f7fa;
  border-radius: 4px;

  .label {
    font-size: 14px;
    color: #909399;
    margin-bottom: 10px;
  }

  .value {
    font-size: 24px;
    font-weight: bold;
    color: #303133;
  }

  .count {
    font-size: 12px;
    color: #c0c4cc;
    margin-top: 5px;
  }
}

.qrcode-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;

  .qrcode-img {
    width: 200px;
    height: 200px;
    border: 1px solid #dcdfe6;
  }

  .qrcode-placeholder {
    width: 200px;
    height: 200px;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: #f5f7fa;
    color: #c0c4cc;
  }
}
</style>
