<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <div class="stat-cards">
      <el-card class="stat-card">
        <div class="stat-title">当前页待结算奖金</div>
        <div class="stat-value warning">¥{{ stats.unsettled }}</div>
        <div class="stat-desc">分页列表当前页合计</div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-title">全部待结算记录</div>
        <div class="stat-value primary">{{ stats.unsettledCount }}</div>
        <div class="stat-desc">到确认收货 T+7 后逐笔自动结算</div>
      </el-card>
    </div>

    <el-alert
      class="toolbar"
      type="success"
      :closable="false"
      title="奖金按订单确认收货 T+7 自动结算"
      description="订单确认收货满7天且没有待处理售后时，系统自动把待结算奖金转入会员余额；不是按月结算。下方月结批次仅保留历史审计，不再允许新建或执行。"
    />

    <el-card class="batch-card">
      <template #header>历史月结批次（只读）</template>
      <el-table :data="settlementBatches" style="width: 100%">
        <el-table-column prop="batchNo" label="批次号" width="210" />
        <el-table-column label="结算周期" min-width="290"><template #default="{ row }">{{ row.periodStart }} 至 {{ row.periodEnd }}</template></el-table-column>
        <el-table-column prop="recordCount" label="锁定笔数" width="100" />
        <el-table-column prop="totalAmount" label="锁定金额" width="120"><template #default="{ row }">¥{{ row.totalAmount }}</template></el-table-column>
        <el-table-column label="结算批次状态" width="130"><template #default="{ row }"><el-tag :type="batchStatusType(row.status)">{{ batchStatusText(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="结果" width="130"><template #default="{ row }">结算 {{ row.settledCount || 0 }} / 跳过 {{ row.skippedCount || 0 }}</template></el-table-column>
        <el-table-column prop="creatorName" label="创建人" width="110" />
        <el-table-column label="操作" width="110" fixed="right"><template #default>-</template></el-table-column>
      </el-table>
    </el-card>

    <!-- 表格 -->
    <el-table
      :data="tableData"
      v-loading="loading"
      style="width: 100%"
    >
      <el-table-column prop="recordNo" label="记录编号" width="180" />
      <el-table-column prop="orderNo" label="订单编号" width="180" />
      <el-table-column prop="agentName" label="获奖会员" width="110" />
      <el-table-column prop="orderAmount" label="订单金额" width="100">
        <template #default="{ row }">
          ¥{{ row.orderAmount }}
        </template>
      </el-table-column>
      <el-table-column prop="commissionLevel" label="佣金层级" width="100">
        <template #default="{ row }">
          <el-tag>{{ getBonusName(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="commissionRate" label="佣金比例" width="100">
        <template #default="{ row }">
          {{ (row.commissionRate * 100).toFixed(2) }}%
        </template>
      </el-table-column>
      <el-table-column prop="commissionAmount" label="佣金金额" width="120">
        <template #default="{ row }">
          <span style="color: #67c23a; font-weight: bold">¥{{ row.commissionAmount }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="160" :formatter="formatDateTimeCell" />
    </el-table>

    <!-- 分页 -->
    <div class="pagination-container">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getCommissionRecords, listSettlementBatches } from '@/api/commission'
import { formatDateTimeCell } from '@/utils/dateTime'
import { customerBonusName } from '@/utils/customerBonus'

const loading = ref(false)
const settlementBatches = ref([])

// 分页
const pagination = ref({
  page: 1,
  size: 10,
  total: 0,
})

// 统计数据
const stats = ref({
  unsettled: '0.00',
  unsettledCount: 0,
})

// 表格数据
const tableData = ref([])

const getBonusName = (row) => customerBonusName(row, { includeLevel: true })

// 分页大小变化
const handleSizeChange = (size) => {
  pagination.value.size = size
  fetchData()
}

// 页码变化
const handleCurrentChange = (page) => {
  pagination.value.page = page
  fetchData()
}

// 获取数据
const fetchData = async () => {
  loading.value = true
  try {
    const res = await getCommissionRecords({
      status: 0,
      pageNum: pagination.value.page,
      pageSize: pagination.value.size,
    })
    tableData.value = res.data?.list || []
    pagination.value.total = res.data?.total || tableData.value.length
    const unsettled = tableData.value.reduce((total, item) => total + Number(item.commissionAmount || 0), 0)
    stats.value.unsettled = unsettled.toFixed(2)
    stats.value.unsettledCount = pagination.value.total
    const batchRes = await listSettlementBatches()
    settlementBatches.value = batchRes.data || []
  } finally {
    loading.value = false
  }
}

const batchStatusText = (status) => ({ 0: '已锁定', 1: '已执行', 2: '已作废' }[status] || '未知')
const batchStatusType = (status) => ({ 0: 'warning', 1: 'success', 2: 'info' }[status] || 'info')

onMounted(() => {
  fetchData()
})
</script>

<style lang="scss" scoped>
.stat-cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(260px, 1fr));
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

    &.warning {
      color: #e6a23c;
    }

    &.success {
      color: #67c23a;
    }

    &.primary {
      color: #409eff;
    }
  }

  .stat-desc {
    font-size: 12px;
    color: #c0c4cc;
  }
}

.toolbar {
  margin-bottom: 20px;
}
</style>
