<template>
  <div class="page-container merchant-finance-page">
    <div class="page-heading">
      <div>
        <h2>{{ isMerchantUser ? '商户货款工作台' : '商户货款' }}</h2>
        <p>{{ isMerchantUser ? '查看本商户待结算、可提现、保证金和提现进度。' : '管理结算周期、售后冲回、保证金、发票和人工打款。' }}</p>
      </div>
      <el-button v-if="canApply" type="primary" @click="openApply">{{ isMerchantUser ? '申请提现' : '代商户申请提现' }}</el-button>
    </div>

    <el-alert v-if="!isMerchantUser" title="保证金与提现冻结分开记账。系统不自动计算税费；需要少打款时使用负数调整金额并填写原因。" type="warning" :closable="false" show-icon />
    <el-alert v-else title="订单先经过确认收货、售后窗口和商品结算等待期，之后才进入可提现余额；保证金不能直接提现。" type="info" :closable="false" show-icon />

    <el-tabs v-model="tab" @tab-change="loadCurrent">
      <el-tab-pane label="货款账户" name="accounts">
        <el-table :data="accounts" v-loading="loading" stripe>
          <el-table-column prop="merchantName" label="商户" min-width="180" />
          <el-table-column label="待结算"><template #default="{ row }">¥{{ money(row.pendingAmount) }}</template></el-table-column>
          <el-table-column label="可提现"><template #default="{ row }"><strong class="available">¥{{ money(row.availableAmount) }}</strong></template></el-table-column>
          <el-table-column label="提现冻结"><template #default="{ row }">¥{{ money(row.frozenAmount) }}</template></el-table-column>
          <el-table-column label="保证金冻结"><template #default="{ row }"><strong class="deposit">¥{{ money(row.depositFrozenAmount) }}</strong></template></el-table-column>
          <el-table-column label="应缴保证金"><template #default="{ row }">¥{{ money(row.requiredDepositAmount) }}</template></el-table-column>
          <el-table-column label="保证金缺口"><template #default="{ row }"><span :class="{ debt: Number(row.depositShortfallAmount || 0) > 0 }">¥{{ money(row.depositShortfallAmount) }}</span></template></el-table-column>
          <el-table-column label="退款欠款"><template #default="{ row }"><span :class="{ debt: Number(row.debtAmount || 0) > 0 }">¥{{ money(row.debtAmount) }}</span></template></el-table-column>
          <el-table-column label="累计已打款"><template #default="{ row }">¥{{ money(row.totalPaidAmount) }}</template></el-table-column>
          <el-table-column v-if="canManage" label="保证金操作" width="150">
            <template #default="{ row }"><el-button link type="warning" @click="openDeposit(row, 'FREEZE')">余额转入</el-button><el-button link type="primary" @click="openDeposit(row, 'RECEIVE')">登记收款</el-button><el-button link type="success" @click="openDeposit(row, 'RELEASE')">解冻</el-button></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="订单货款明细" name="settlements">
        <el-table :data="settlements" v-loading="loading" stripe>
          <el-table-column prop="merchantName" label="商户" min-width="150" />
          <el-table-column prop="orderNo" label="订单号" min-width="190" />
          <el-table-column prop="quantity" label="数量" width="75" />
          <el-table-column label="结算单价"><template #default="{ row }">¥{{ money(row.costAmount) }}</template></el-table-column>
          <el-table-column label="应结货款"><template #default="{ row }">¥{{ money(row.settlementAmount) }}</template></el-table-column>
          <el-table-column label="已冲回"><template #default="{ row }">¥{{ money(row.reversedAmount) }}</template></el-table-column>
          <el-table-column label="结算等待" width="105"><template #default="{ row }">{{ row.settlementDelayDays || 0 }} 天</template></el-table-column>
          <el-table-column label="预计可结算" min-width="170"><template #default="{ row }">{{ row.eligibleTime ? formatTime(row.eligibleTime) : '待客户确认收货' }}</template></el-table-column>
          <el-table-column label="状态" width="105"><template #default="{ row }"><el-tag :type="settlementStatus(row.status).type">{{ settlementStatus(row.status).label }}</el-tag></template></el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="保证金流水" name="deposits">
        <el-table :data="depositFlows" v-loading="loading" stripe>
          <el-table-column prop="merchantName" label="商户" min-width="160" />
          <el-table-column label="操作" width="100"><template #default="{ row }"><el-tag :type="depositOperation(row.operationType).type">{{ depositOperation(row.operationType).label }}</el-tag></template></el-table-column>
          <el-table-column label="金额"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
          <el-table-column label="操作后保证金"><template #default="{ row }">¥{{ money(row.balanceAfter) }}</template></el-table-column>
          <el-table-column prop="reason" label="原因" min-width="220" />
          <el-table-column prop="operatorName" label="操作人" width="120" />
          <el-table-column label="时间" min-width="170"><template #default="{ row }">{{ formatTime(row.createTime) }}</template></el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="提现、发票与打款" name="withdrawals">
        <el-table :data="withdrawals" v-loading="loading" stripe>
          <el-table-column prop="withdrawalNo" label="申请单号" min-width="190" />
          <el-table-column prop="merchantName" label="商户" min-width="140" />
          <el-table-column prop="bankAccountNameSnapshot" label="收款户名" min-width="150" />
          <el-table-column label="收款账号" min-width="150"><template #default="{ row }">{{ maskBankAccount(row.bankAccountNoSnapshot) }}</template></el-table-column>
          <el-table-column label="申请金额"><template #default="{ row }">¥{{ money(row.requestedAmount) }}</template></el-table-column>
          <el-table-column label="应开票"><template #default="{ row }">¥{{ money(row.invoiceRequiredAmount) }}</template></el-table-column>
          <el-table-column label="已收票"><template #default="{ row }">¥{{ money(row.invoiceReceivedAmount) }}</template></el-table-column>
          <el-table-column label="调整"><template #default="{ row }">¥{{ money(row.adjustmentAmount) }}</template></el-table-column>
          <el-table-column label="实付"><template #default="{ row }">{{ row.actualPaidAmount == null ? '-' : `¥${money(row.actualPaidAmount)}` }}</template></el-table-column>
          <el-table-column label="状态" width="115"><template #default="{ row }"><el-tag>{{ withdrawalStatus(row.status) }}</el-tag></template></el-table-column>
          <el-table-column v-if="canManage" label="操作" width="210"><template #default="{ row }"><el-button v-if="['SUBMITTED','INVOICE_PENDING','READY_TO_PAY'].includes(row.status)" link type="primary" @click="openReview(row)">发票/审核</el-button><el-button v-if="row.status === 'READY_TO_PAY'" link type="success" @click="openPay(row)">确认打款</el-button><el-button v-if="['SUBMITTED','INVOICE_PENDING','READY_TO_PAY'].includes(row.status)" link type="danger" @click="reject(row)">驳回</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="applyVisible" :title="isMerchantUser ? '申请货款提现' : '代商户申请提现'" width="520px">
      <el-form label-width="100px"><el-form-item v-if="!isMerchantUser" label="商户" required><el-select v-model="applyForm.merchantId" filterable style="width:100%"><el-option v-for="item in merchants" :key="item.id" :label="item.merchantName" :value="item.id" /></el-select></el-form-item><el-form-item label="申请金额" required><el-input-number v-model="applyForm.requestedAmount" :min="0.01" :precision="2" style="width:100%" /></el-form-item></el-form>
      <template #footer><el-button @click="applyVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitApply">提交</el-button></template>
    </el-dialog>

    <el-dialog v-model="depositVisible" :title="depositDialogTitle" width="540px">
      <el-alert :title="depositDialogTip" type="warning" :closable="false" show-icon />
      <el-form label-width="105px" class="dialog-form"><el-form-item label="商户"><strong>{{ currentAccount?.merchantName }}</strong></el-form-item><el-form-item label="调整金额" required><el-input-number v-model="depositForm.amount" :min="0.01" :precision="2" style="width:100%" /></el-form-item><el-form-item label="调整原因" required><el-input v-model="depositForm.reason" type="textarea" maxlength="256" show-word-limit placeholder="例如：高风险品类履约保证金" /></el-form-item></el-form>
      <template #footer><el-button @click="depositVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitDeposit">确认{{ depositOperation(depositForm.operationType).label }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="reviewVisible" title="登记发票与打款调整" width="600px"><el-form label-width="120px"><el-form-item label="应开票金额"><el-input-number v-model="reviewForm.invoiceRequiredAmount" :min="0" :precision="2" style="width:100%" /></el-form-item><el-form-item label="已收票金额"><el-input-number v-model="reviewForm.invoiceReceivedAmount" :min="0" :precision="2" style="width:100%" /></el-form-item><el-form-item label="发票状态"><el-select v-model="reviewForm.invoiceStatus" style="width:100%"><el-option label="无需发票" value="NOT_REQUIRED"/><el-option label="待收发票" value="PENDING"/><el-option label="已收发票" value="RECEIVED"/></el-select></el-form-item><el-form-item label="调整金额"><el-input-number v-model="reviewForm.adjustmentAmount" :max="0" :precision="2" style="width:100%"/><div class="help">少打100元填写 -100；不自动认定为税费。</div></el-form-item><el-form-item label="调整原因"><el-input v-model="reviewForm.adjustmentReason" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="reviewVisible=false">取消</el-button><el-button type="primary" @click="submitReview">保存审核</el-button></template></el-dialog>
    <el-dialog v-model="payVisible" title="确认实际打款" width="520px"><el-form label-width="110px"><el-form-item label="实际打款"><el-input-number v-model="payForm.actualPaidAmount" :min="0.01" :precision="2" style="width:100%"/></el-form-item><el-form-item label="银行流水号"><el-input v-model="payForm.paymentReference" /></el-form-item><el-form-item label="凭证地址"><el-input v-model="payForm.paymentVoucherUrl" /></el-form-item></el-form><template #footer><el-button @click="payVisible=false">取消</el-button><el-button type="primary" @click="submitPay">确认已打款</el-button></template></el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAppStore } from '@/store'
import { applyMerchantWithdrawal, freezeMerchantDeposit, listMerchantAccounts, listMerchantDepositFlows, listMerchantSettlements, listMerchants, listMerchantWithdrawals, payMerchantWithdrawal, receiveMerchantDeposit, rejectMerchantWithdrawal, releaseMerchantDeposit, reviewMerchantWithdrawal } from '@/api/merchant'

const store = useAppStore()
const isMerchantUser = computed(() => Boolean(store.userInfo?.merchantId))
const canManage = computed(() => !isMerchantUser.value && store.hasPermission('finance:manage'))
const canApply = computed(() => isMerchantUser.value || canManage.value)
const tab = ref('accounts'); const loading = ref(false); const saving = ref(false)
const merchants = ref([]); const accounts = ref([]); const settlements = ref([]); const depositFlows = ref([]); const withdrawals = ref([])
const applyVisible = ref(false); const depositVisible = ref(false); const reviewVisible = ref(false); const payVisible = ref(false)
const current = ref(null); const currentAccount = ref(null)
const applyForm = ref({ requestNo: '', merchantId: null, requestedAmount: 0 })
const depositForm = ref({ merchantId: null, operationType: 'FREEZE', amount: 0, reason: '' })
const reviewForm = ref({ invoiceRequiredAmount: 0, invoiceReceivedAmount: 0, invoiceStatus: 'NOT_REQUIRED', adjustmentAmount: 0, adjustmentReason: '' })
const payForm = ref({ actualPaidAmount: 0, paymentReference: '', paymentVoucherUrl: '' })

const money = (value) => Number(value || 0).toFixed(2)
const formatTime = (value) => value ? String(value).replace('T', ' ').slice(0, 19) : '-'
const settlementStatus = (status) => ({ PENDING: { label: '待结算', type: 'warning' }, AVAILABLE: { label: '可提现', type: 'success' }, REVERSED: { label: '已冲回', type: 'info' } }[status] || { label: status || '-', type: 'info' })
const withdrawalStatus = (status) => ({ SUBMITTED: '待审核', INVOICE_PENDING: '待收发票', READY_TO_PAY: '待打款', PAID: '已打款', REJECTED: '已驳回' }[status] || status || '-')
const operationNo = () => `MD-${globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`}`
const withdrawalRequestNo = () => `MWR-${globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`}`
const depositOperation = (type) => ({ FREEZE: { label: '余额转入', type: 'warning' }, RECEIVE: { label: '线下收款', type: 'primary' }, RELEASE: { label: '解冻', type: 'success' } }[type] || { label: type || '-', type: 'info' })
const maskBankAccount = (value) => { const text = String(value || ''); return text.length <= 8 ? text : `${text.slice(0, 4)} **** ${text.slice(-4)}` }
const depositDialogTitle = computed(() => ({ FREEZE: '从商户余额转入保证金', RECEIVE: '登记线下收到的保证金', RELEASE: '解冻商户保证金' }[depositForm.value.operationType] || '调整保证金'))
const depositDialogTip = computed(() => depositForm.value.operationType === 'FREEZE'
  ? `本次金额将从可提现余额转入保证金。当前可提现 ¥${money(currentAccount.value?.availableAmount)}`
  : depositForm.value.operationType === 'RECEIVE'
    ? '仅在财务已经实际收到商户线下保证金后登记，本操作不会扣减商户货款余额。'
    : `解冻时优先抵扣退款欠款，剩余金额回到可提现余额。当前保证金 ¥${money(currentAccount.value?.depositFrozenAmount)}`)

const loadCurrent = async () => { loading.value = true; try { if (tab.value === 'accounts') accounts.value = (await listMerchantAccounts()).data || []; if (tab.value === 'settlements') settlements.value = (await listMerchantSettlements()).data || []; if (tab.value === 'deposits') depositFlows.value = (await listMerchantDepositFlows()).data || []; if (tab.value === 'withdrawals') withdrawals.value = (await listMerchantWithdrawals()).data || [] } finally { loading.value = false } }
const openApply = () => { applyForm.value = { requestNo: withdrawalRequestNo(), merchantId: isMerchantUser.value ? store.userInfo.merchantId : null, requestedAmount: 0 }; applyVisible.value = true }
const submitApply = async () => { saving.value = true; try { await applyMerchantWithdrawal(applyForm.value); ElMessage.success('提现申请已提交'); applyVisible.value = false; tab.value = 'withdrawals'; await loadCurrent() } finally { saving.value = false } }
const openDeposit = (row, operationType) => { currentAccount.value = row; depositForm.value = { merchantId: row.merchantId, operationType, amount: 0, reason: '' }; depositVisible.value = true }
const submitDeposit = async () => { if (!depositForm.value.reason?.trim()) return ElMessage.warning('请填写保证金调整原因'); saving.value = true; try { const payload = { merchantId: depositForm.value.merchantId, amount: depositForm.value.amount, reason: depositForm.value.reason.trim(), operationNo: operationNo() }; if (depositForm.value.operationType === 'FREEZE') await freezeMerchantDeposit(payload); else if (depositForm.value.operationType === 'RECEIVE') await receiveMerchantDeposit(payload); else await releaseMerchantDeposit(payload); ElMessage.success('保证金已更新'); depositVisible.value = false; await loadCurrent() } finally { saving.value = false } }
const openReview = (row) => { current.value = row; reviewForm.value = { invoiceRequiredAmount: Number(row.invoiceRequiredAmount || 0), invoiceReceivedAmount: Number(row.invoiceReceivedAmount || 0), invoiceStatus: row.invoiceStatus || 'NOT_REQUIRED', adjustmentAmount: Number(row.adjustmentAmount || 0), adjustmentReason: row.adjustmentReason || '' }; reviewVisible.value = true }
const submitReview = async () => { await reviewMerchantWithdrawal(current.value.id, reviewForm.value); ElMessage.success('发票与审核信息已保存'); reviewVisible.value = false; await loadCurrent() }
const openPay = (row) => { current.value = row; payForm.value = { actualPaidAmount: Number(row.requestedAmount || 0) + Number(row.adjustmentAmount || 0), paymentReference: '', paymentVoucherUrl: '' }; payVisible.value = true }
const submitPay = async () => { await payMerchantWithdrawal(current.value.id, payForm.value); ElMessage.success('打款已确认'); payVisible.value = false; await loadCurrent() }
const reject = async (row) => { const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回商户提现', { inputValidator: (v) => Boolean(v?.trim()) || '必须填写原因' }); await rejectMerchantWithdrawal(row.id, { reason: value }); ElMessage.success('已驳回；冻结金额先抵退款欠款，剩余退回可提现余额'); await loadCurrent() }

onMounted(async () => { if (!isMerchantUser.value) merchants.value = (await listMerchants({ status: 1 })).data || []; await loadCurrent() })
</script>

<style scoped>
.page-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px}.page-heading h2{margin:0}.page-heading p{margin:6px 0 0;color:#909399}.available{color:#1b6f3a}.deposit{color:#b26a00}.debt{color:#d93838;font-weight:700}.dialog-form{margin-top:18px}.help{color:#909399;font-size:12px;margin-top:6px}@media(max-width:760px){.page-heading{align-items:flex-start;gap:12px;flex-direction:column}}
</style>
