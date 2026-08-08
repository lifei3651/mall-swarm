<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <span>待审核提现</span>
      </template>

      <el-table :data="pendingList" v-loading="loading" style="width: 100%">
        <el-table-column prop="withdrawNo" label="提现单号" width="180" />
        <el-table-column prop="memberAccount" label="登录账号" width="145" />
        <el-table-column prop="agentName" label="会员名称" width="120" />
        <el-table-column prop="withdrawAmount" label="提现金额" width="120">
          <template #default="{ row }">
            <span style="color: #f56c6c; font-weight: bold">¥{{ row.withdrawAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="withdrawTypeName" label="提现方式" width="100" />
        <el-table-column prop="bankName" label="银行名称" width="120" />
        <el-table-column prop="bankAccount" label="银行账号" width="180" />
        <el-table-column prop="accountName" label="账户姓名" width="100" />
        <el-table-column prop="createTime" label="申请时间" width="160" :formatter="formatDateTimeCell" />
        <el-table-column label="操作" fixed="right" width="200">
          <template #default="{ row }">
            <el-button type="success" link @click="handleAudit(row, 1)">通过</el-button>
            <el-button type="danger" link @click="handleAudit(row, 4)">拒绝</el-button>
            <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 审核对话框 -->
    <el-dialog v-model="auditDialogVisible" :title="auditForm.status === 1 ? '审核通过' : '审核拒绝'" width="400px">
      <el-form :model="auditForm" label-width="100px">
        <el-form-item label="提现单号">
          <el-input :value="auditForm.withdrawNo" disabled />
        </el-form-item>
        <el-form-item label="提现金额">
          <el-input :value="`¥${auditForm.withdrawAmount}`" disabled />
        </el-form-item>
        <el-form-item label="会员">
          <el-input :value="`${auditForm.memberAccount || '-'} · ${auditForm.agentName || '-'}`" disabled />
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

    <el-dialog v-model="detailVisible" title="提现申请详情" width="650px">
      <el-descriptions :column="2" border>
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
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auditWithdraw, getPendingAuditWithdraws } from '@/api/withdraw'
import { formatDateTime, formatDateTimeCell } from '@/utils/dateTime'

const loading = ref(false)
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
const handleAudit = (row, status) => {
  auditForm.value = {
    id: row.id,
    withdrawNo: row.withdrawNo,
    withdrawAmount: row.withdrawAmount,
    agentName: row.agentName,
    memberAccount: row.memberAccount,
    status,
    auditRemark: '',
  }
  auditDialogVisible.value = true
}

// 详情
const handleDetail = (row) => {
  detail.value = row
  detailVisible.value = true
}

// 提交审核
const submitAudit = async () => {
  const action = auditForm.value.status === 1 ? '通过' : '拒绝'
  try {
    await ElMessageBox.confirm(`确定要${action}该提现申请吗？`, '提示', { type: 'warning' })
    submitLoading.value = true
    await auditWithdraw({
      id: auditForm.value.id,
      status: auditForm.value.status,
      auditRemark: auditForm.value.auditRemark,
    })
    ElMessage.success(`${action}成功`)
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
  loading.value = true
  try {
    const res = await getPendingAuditWithdraws()
    pendingList.value = res.data?.list || []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchData()
})
</script>
