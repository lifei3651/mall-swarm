<template>
  <div class="page-container">
    <el-alert
      :title="`操作日志自动保留 ${retentionDays} 天，超过期限后由系统在低峰时段分批清理。`"
      type="info"
      :closable="false"
      show-icon
      class="retention-alert"
    />
    <div class="search-container">
      <el-form :inline="true" :model="query">
        <el-form-item label="模块">
          <el-select v-model="query.moduleName" clearable placeholder="全部模块" style="width:160px" @change="fetchLogs">
            <el-option label="会员资产" value="ASSET" />
            <el-option label="会员关系" value="AGENT" />
            <el-option label="订单" value="ORDER" />
            <el-option label="商城设置" value="BONUS_CONFIG" />
            <el-option label="ERP" value="ERP" />
            <el-option label="后台操作" value="ADMIN_API" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchLogs">查询</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table :data="logs" v-loading="loading" style="width: 100%">
      <el-table-column label="操作时间" width="170"><template #default="{ row }">{{ formatOperationTime(row.createTime) }}</template></el-table-column>
      <el-table-column prop="operatorName" label="操作人" width="120" />
      <el-table-column label="做了什么" min-width="360">
        <template #default="{ row }"><span class="action-text">{{ actionDescription(row) }}</span></template>
      </el-table-column>
      <el-table-column label="模块" width="110"><template #default="{ row }">{{ moduleName(row.moduleName) }}</template></el-table-column>
      <el-table-column label="结果" width="85"><template #default="{ row }"><el-tag :type="isFailed(row) ? 'danger' : 'success'">{{ isFailed(row) ? '失败' : '成功' }}</el-tag></template></el-table-column>
      <el-table-column label="详情" width="90" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="openDetail(row)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pagination"
      background
      layout="total, prev, pager, next, sizes"
      :total="pagination.total"
      v-model:current-page="pagination.page"
      v-model:page-size="pagination.size"
      @current-change="fetchLogs"
      @size-change="fetchLogs"
    />

    <el-dialog v-model="detailVisible" title="操作详情" width="760px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="操作时间">{{ formatOperationTime(current.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ current.operatorName }}</el-descriptions-item>
        <el-descriptions-item label="业务模块">{{ moduleName(current.moduleName) }}</el-descriptions-item>
        <el-descriptions-item label="操作结果">{{ isFailed(current) ? '失败' : '成功' }}</el-descriptions-item>
        <el-descriptions-item label="做了什么" :span="2">{{ actionDescription(current) }}</el-descriptions-item>
        <el-descriptions-item label="关联对象" :span="2">{{ targetDescription(current) }}</el-descriptions-item>
      </el-descriptions>
      <el-divider v-if="current.beforeData">变更前</el-divider>
      <el-input v-if="current.beforeData" :model-value="current.beforeData" type="textarea" :rows="5" readonly />
      <el-divider v-if="current.afterData">变更后</el-divider>
      <el-input v-if="current.afterData" :model-value="current.afterData" type="textarea" :rows="6" readonly />
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getOperationLogRetention, listOperationLogs } from '@/api/operationLog'
import { formatDateTime as formatOperationTime } from '@/utils/dateTime'

const query = ref({})
const logs = ref([])
const loading = ref(false)
const pagination = ref({ page: 1, size: 10, total: 0 })
const detailVisible = ref(false)
const current = ref({})
const retentionDays = ref(365)

const fetchLogs = async () => {
  loading.value = true
  try {
    const res = await listOperationLogs({
      ...query.value,
      pageNum: pagination.value.page,
      pageSize: pagination.value.size,
    })
    logs.value = res.data?.list || []
    pagination.value.total = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const openDetail = (row) => {
  current.value = row
  detailVisible.value = true
}


const moduleName = (value) => ({
  ASSET: '会员资产', AGENT: '会员关系', ORDER: '订单', BONUS_CONFIG: '奖金设置', ERP: 'ERP', ADMIN_API: '后台操作',
}[value] || value || '其他')

const operationName = (value) => ({
  ISSUE:'增加', DEDUCT:'扣减', CONSUME:'消费', WITHDRAW:'提现', TRANSFER:'转账',
  LINE_CHANGE_APPLY:'提交移线', LINE_CHANGE_APPROVE:'批准移线', LINE_CHANGE_REJECT:'拒绝移线', LINE_CHANGE_EXECUTE:'执行移线',
  ASSET_PAY:'资产支付', CONFIG_SAVE:'保存配置', ORDER_PUSH_SUCCESS:'订单推送成功', ORDER_PUSH_FAIL:'订单推送失败', SHIPMENT_CALLBACK:'发货回传',
  LEVEL_ADJUST:'调整会员级别',
  POST:'提交', PUT:'修改', DELETE:'删除', CREATE:'新增', UPDATE:'修改',
}[value] || value || '操作')

const oldApiDescription = (path = '', method = '') => {
  const viewing = method === 'GET'
  if (/\/shop\/admin\/members\/[^/]+\/level/.test(path)) return '调整会员级别'
  if (/\/shop\/admin\/members\/[^/]+\/status/.test(path)) return '修改登录账号状态'
  if (/\/shop\/admin\/members\/[^/]+\/unlock/.test(path)) return '解除会员登录锁定'
  if (/\/shop\/admin\/members\/[^/]+\/payment-password\/unlock/.test(path)) return '解除会员支付密码锁定'
  if (path === '/shop/admin/members') return viewing ? '查看商城会员列表' : '后台新增商城会员'
  if (/\/shop\/admin\/members\/[^/]+\/profile/.test(path)) return '查看会员详情'
  if (path === '/distribution/assets/issue') return '直接增加会员余额'
  if (path === '/distribution/assets/deduct') return '直接扣减会员余额'
  if (path === '/distribution/assets/accounts') return '查看会员余额'
  if (path === '/distribution/assets/flows') return '查看会员余额流水'
  if (path === '/distribution/agent/switch-line') return '执行会员移线'
  if (/\/line-change-applications\/[^/]+\/audit/.test(path)) return '处理旧版移线申请'
  if (path.includes('/line-change-applications')) return '查看移线记录'
  if (path === '/distribution/commission/records') return '查看奖金记录'
  if (path.startsWith('/distribution/commission')) return viewing ? '查看奖金数据' : '处理奖金数据'
  if (path === '/distribution/audit/person-profile') return '查询会员资金与订单全景'
  if (path.startsWith('/distribution/audit/orders')) return '查看订单账务审计'
  if (path.startsWith('/distribution/audit/finance')) return '查看财务总览'
  if (path.startsWith('/distribution/audit/settings')) return viewing ? '查看业绩显示设置' : '修改业绩显示设置'
  if (path.startsWith('/distribution/operation-logs')) return '查看后台操作日志'
  if (/\/shop\/admin\/orders\/[^/]+\/ship/.test(path)) return '商城订单发货'
  if (/\/shop\/admin\/after-sales\/[^/]+\/audit/.test(path)) return '审核商城售后'
  if (path.startsWith('/shop/admin/orders')) return viewing ? '查看商城订单' : '处理商城订单'
  if (path.startsWith('/shop/admin/products')) return viewing ? '查看商品资料' : '维护商品资料或上架状态'
  if (path.startsWith('/shop/admin/categories')) return viewing ? '查看商品分类' : '维护商品分类'
  if (path.startsWith('/distribution/tenant')) return viewing ? '查看商城品牌或界面设置' : '修改商城品牌或界面设置'
  return path ? `${viewing ? '查看' : '操作'}业务数据（${path}）` : '后台业务操作'
}

const actionDescription = (row = {}) => {
  if (row.remark && !['后台接口调用，状态：200', '后台接口调用，状态：201'].includes(row.remark)) {
    if (row.moduleName !== 'ADMIN_API' || !row.remark.startsWith('后台接口')) return row.remark
  }
  if (row.moduleName === 'ADMIN_API') return `${oldApiDescription(row.targetId, row.operationType)}，${isFailed(row) ? '执行失败' : '执行成功'}`
  return `${operationName(row.operationType)}${targetDescription(row)}`
}

const targetDescription = (row = {}) => {
  if (row.moduleName === 'AGENT' && row.operationType === 'LEVEL_ADJUST') {
    const identity = (row.afterData || row.beforeData || '').split('；会员级别：')[0]
    if (identity) return identity
  }
  return row.targetId ? `${row.targetType || '对象'}：${row.targetId}` : (row.targetType || '-')
}
const isFailed = (row = {}) => /失败|异常|状态：4|状态：5|HTTP [45]/.test(row.remark || '')

onMounted(async () => {
  const [retentionResult] = await Promise.allSettled([getOperationLogRetention(), fetchLogs()])
  if (retentionResult.status === 'fulfilled') retentionDays.value = Number(retentionResult.value.data?.retentionDays || 365)
})
</script>

<style scoped>
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
.retention-alert { margin-bottom: 16px; }
.action-text { color:#303133; line-height:1.55; }
</style>
