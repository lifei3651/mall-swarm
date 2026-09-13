<template>
  <div class="page-container withdrawal-audit-page">
    <header class="withdrawal-heading"><div><h2>提现审核</h2><p>核对申请与收款资料。审核通过不等于已经打款。</p></div><el-button :loading="loading" @click="fetchData">刷新列表</el-button></header>
    <el-alert v-if="loadError" title="提现列表读取失败，请重试" type="error" :closable="false" show-icon />
      <el-table :data="pendingList" v-loading="loading" style="width: 100%" empty-text="暂无待审核的提现申请">
        <el-table-column prop="withdrawNo" label="提现单号 / 申请时间" min-width="180" show-overflow-tooltip>
          <template #default="{ row }"><span>{{ row.withdrawNo }}</span><div class="secondary-text">{{ formatDateTime(row.createTime) }}</div></template>
        </el-table-column>
        <el-table-column label="申请会员" min-width="140">
          <template #default="{ row }"><span>{{ row.agentName || '—' }}</span><div class="secondary-text">{{ row.memberAccount || '—' }}</div></template>
        </el-table-column>
        <el-table-column prop="withdrawAmount" label="提现金额（元）" width="130" align="right">
          <template #default="{ row }">
            <span class="money-value">¥{{ row.withdrawAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="withdrawTypeName" label="提现方式" width="90" />
        <el-table-column label="操作" fixed="right" width="160">
          <template #default="{ row }">
            <el-button type="success" link @click="handleAudit(row, 1)">通过</el-button>
            <el-button type="danger" link @click="handleAudit(row, 4)">拒绝</el-button>
            <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

    <!-- 审核对话框 -->
    <el-dialog v-model="auditDialogVisible" :title="auditForm.status === 1 ? '审核通过' : '审核拒绝'" width="400px">
      <el-form :model="auditForm" label-width="100px">
        <el-form-item label="提现单号">
          <el-input :model-value="auditForm.withdrawNo" disabled />
        </el-form-item>
        <el-form-item label="提现金额">
          <el-input :model-value="`¥${auditForm.withdrawAmount}`" disabled />
        </el-form-item>
        <el-form-item label="会员">
          <el-input :model-value="`${auditForm.memberAccount || '-'} · ${auditForm.agentName || '-'}`" disabled />
        </el-form-item>
        <el-form-item label="审核备注">
          <el-input
            v-model="auditForm.auditRemark"
            type="textarea"
            placeholder="请输入审核备注"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAudit" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="提现申请详情" size="560px" class="withdrawal-detail-drawer">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="提现单号">{{ detail.withdrawNo }}</el-descriptions-item>
        <el-descriptions-item label="申请时间">{{ formatDateTime(detail.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="会员">{{ detail.memberAccount || '-' }} · {{ detail.agentName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.memberPhone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="提现金额">¥{{ detail.withdrawAmount }}</el-descriptions-item>
        <el-descriptions-item label="提现方式">{{ detail.withdrawTypeName }}</el-descriptions-item>
        <el-descriptions-item label="收款渠道">{{ detail.bankName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="收款账号">{{ detail.bankAccount || '-' }}</el-descriptions-item>
        <el-descriptions-item label="账户姓名">{{ detail.accountName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前状态">{{ detail.statusName || '待审核' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer><el-button @click="detailVisible = false">关闭详情</el-button></template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auditWithdraw, getPendingAuditWithdraws, getWithdrawById } from '@/api/withdraw'
import { formatDateTime } from '@/utils/dateTime'

const loading = ref(false)
const loadError = ref(false)
const submitLoading = ref(false)
const auditDialogVisible = ref(false)
const detailVisible = ref(false)
const detail = ref({})

// 审核表单
const auditForm = ref({
  id: null,
  withdrawNo: '',
  withdrawAmount: '',
  agentName: '',
  memberAccount: '',
  status: null,
  auditRemark: '',
})

// 待审核列表
const pendingList = ref([])

// 审核
const handleAudit = async (row, status) => {
  const fullRow = (await getWithdrawById(row.id)).data || row
  auditForm.value = {
    id: fullRow.id,
    withdrawNo: fullRow.withdrawNo,
    withdrawAmount: fullRow.withdrawAmount,
    agentName: fullRow.agentName,
    memberAccount: fullRow.memberAccount,
    status,
    auditRemark: '',
  }
  auditDialogVisible.value = true
}

// 详情
const handleDetail = async (row) => {
  detail.value = (await getWithdrawById(row.id)).data || row
  detailVisible.value = true
}

// 提交审核
const submitAudit = async () => {
  const action = auditForm.value.status === 1 ? '通过' : '拒绝'
  try {
    await ElMessageBox.confirm(`确定要${action}该提现申请吗？`, '提示', { type: 'warning' })
    submitLoading.value = true
    const response = await auditWithdraw({
      id: auditForm.value.id,
      status: auditForm.value.status,
      auditRemark: auditForm.value.auditRemark,
    })
    ElMessage.success(response.message || `${action}成功`)
    auditDialogVisible.value = false
    fetchData()
  } catch (e) {
    // 取消
  } finally {
    submitLoading.value = false
  }
}

// 获取数据
const fetchData = async () => {
  if (loading.value) return
  loading.value = true
  loadError.value = false
  try {
    const res = await getPendingAuditWithdraws()
    pendingList.value = res.data?.list || []
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.withdrawal-heading { display:flex; align-items:center; justify-content:space-between; gap:24px; margin-bottom:24px; }
.withdrawal-heading h2 { margin:0 0 10px; }.withdrawal-heading p { color:var(--admin-muted); font-size:13px; line-height:1.8; }
.secondary-text { color:var(--admin-muted); font-size:12px; }.money-value { font-weight:600; font-variant-numeric:tabular-nums; }
.el-alert { margin-bottom:16px; }:global(.withdrawal-detail-drawer) { max-width:100vw; }:global(.withdrawal-detail-drawer .el-descriptions__content) { overflow-wrap:anywhere; }
@media(max-width:640px) { .withdrawal-heading { align-items:flex-start; }.withdrawal-heading .el-button { flex-shrink:0; } }
</style>
