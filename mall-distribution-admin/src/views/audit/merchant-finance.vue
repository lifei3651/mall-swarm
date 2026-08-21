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

    <div v-if="isMerchantUser" class="merchant-summary">
      <div class="summary-card"><span>待发货</span><strong>{{ orderSummary.pendingShipment }}</strong><small>笔订单</small></div>
      <div class="summary-card"><span>售后处理中</span><strong>{{ orderSummary.afterSale }}</strong><small>笔售后</small></div>
      <div class="summary-card"><span>待结算</span><strong>¥{{ money(ownAccount.pendingAmount) }}</strong><small>售后期及结算期内</small></div>
      <div class="summary-card"><span>可提现</span><strong class="available">¥{{ money(ownAccount.availableAmount) }}</strong><small>可发起提现申请</small></div>
      <div class="summary-card"><span>提现中</span><strong>¥{{ money(ownAccount.frozenAmount) }}</strong><small>财务处理中</small></div>
      <div class="summary-card"><span>保证金</span><strong :class="{ debt: Number(ownAccount.depositShortfallAmount || 0) > 0 }">¥{{ money(ownAccount.depositFrozenAmount) }}</strong><small>{{ Number(ownAccount.depositShortfallAmount || 0) > 0 ? `尚缺 ¥${money(ownAccount.depositShortfallAmount)}` : '状态正常' }}</small></div>
    </div>

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

      <el-tab-pane label="资金总账" name="ledger">
        <el-alert title="每一次待结算、可提现、提现冻结、保证金、欠款和已打款变化都会形成不可变流水，可从第一笔复算到当前余额。" type="success" :closable="false" show-icon />
        <el-table :data="ledger" v-loading="loading" stripe class="ledger-table">
          <el-table-column label="流水" width="160"><template #default="{ row }"><strong>{{ row.ledgerNo }}</strong><div class="ledger-sub">{{ formatTime(row.createTime) }}</div></template></el-table-column>
          <el-table-column v-if="!isMerchantUser" prop="merchantName" label="商户" width="120" show-overflow-tooltip />
          <el-table-column label="业务说明" min-width="180"><template #default="{ row }"><strong>{{ ledgerType(row.bizType) }}</strong><div class="ledger-sub">{{ row.summary || '-' }}</div></template></el-table-column>
          <el-table-column label="本次资金变化" width="160"><template #default="{ row }"><div class="ledger-change-list"><span v-for="item in ledgerChanges(row)" :key="item.label" class="ledger-change"><small>{{ item.label }}</small><strong :class="deltaClass(item.value)">{{ signedMoney(item.value) }}</strong></span></div></template></el-table-column>
          <el-table-column label="变化后余额" min-width="310"><template #default="{ row }"><div class="ledger-balance"><span>待结算 ¥{{ money(row.pendingAfter) }}</span><span>可提现 ¥{{ money(row.availableAfter) }}</span><span>提现冻结 ¥{{ money(row.frozenAfter) }}</span><span>保证金 ¥{{ money(row.depositAfter) }}</span><span>欠款 ¥{{ money(row.debtAfter) }}</span><span>累计打款 ¥{{ money(row.paidAfter) }}</span></div></template></el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="账本对账" name="reconciliation">
        <el-alert title="系统逐商户比较资金账户与最后一笔总账余额；存在差额时应先停止人工打款并核查流水，不要直接改余额。" type="warning" :closable="false" show-icon />
        <el-table :data="reconciliation" v-loading="loading" stripe class="ledger-table">
          <el-table-column prop="merchantName" label="商户" min-width="150" />
          <el-table-column label="对账结果" width="120"><template #default="{ row }"><el-tag :type="row.consistent ? 'success' : 'danger'">{{ row.consistent ? '账实一致' : '存在差额' }}</el-tag></template></el-table-column>
          <el-table-column label="最后流水" min-width="190"><template #default="{ row }">{{ row.latestLedgerNo || '未建立期初账' }}<div class="ledger-sub">{{ formatTime(row.latestLedgerTime) }}</div></template></el-table-column>
          <el-table-column label="当前账户余额" min-width="270"><template #default="{ row }"><div class="ledger-balance"><span>待结算 ¥{{ money(row.pendingAmount) }}</span><span>可提现 ¥{{ money(row.availableAmount) }}</span><span>提现冻结 ¥{{ money(row.frozenAmount) }}</span><span>保证金 ¥{{ money(row.depositAmount) }}</span><span>欠款 ¥{{ money(row.debtAmount) }}</span><span>累计打款 ¥{{ money(row.paidAmount) }}</span></div></template></el-table-column>
          <el-table-column label="账户减总账差额" min-width="270"><template #default="{ row }"><div class="ledger-balance"><span :class="deltaClass(row.pendingDifference)">待结算 {{ signedMoney(row.pendingDifference) }}</span><span :class="deltaClass(row.availableDifference)">可提现 {{ signedMoney(row.availableDifference) }}</span><span :class="deltaClass(row.frozenDifference)">提现冻结 {{ signedMoney(row.frozenDifference) }}</span><span :class="deltaClass(row.depositDifference)">保证金 {{ signedMoney(row.depositDifference) }}</span><span :class="deltaClass(row.debtDifference)">欠款 {{ signedMoney(row.debtDifference) }}</span><span :class="deltaClass(row.paidDifference)">累计打款 {{ signedMoney(row.paidDifference) }}</span></div></template></el-table-column>
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
          <el-table-column label="操作" width="390"><template #default="{ row }"><el-button link @click="showEvents(row)">审批轨迹</el-button><el-button v-if="merchantCanManageFunds && ['SUBMITTED','INVOICE_PENDING'].includes(row.status)" link type="warning" @click="cancelWithdrawal(row)">撤回</el-button><template v-if="canManage"><el-button v-if="['SUBMITTED','INVOICE_PENDING','READY_TO_PAY'].includes(row.status)" link type="primary" @click="openReview(row)">发票/审核</el-button><el-button v-if="['READY_TO_PAY','PAYMENT_FAILED'].includes(row.status)" link type="success" @click="startPayment(row)">开始付款</el-button><el-button v-if="row.status === 'PAYMENT_PROCESSING'" link type="success" @click="openPay(row)">确认到账</el-button><el-button v-if="row.status === 'PAYMENT_PROCESSING'" link type="danger" @click="paymentFailed(row)">付款失败</el-button><el-button v-if="['SUBMITTED','INVOICE_PENDING','READY_TO_PAY','PAYMENT_PROCESSING','PAYMENT_FAILED'].includes(row.status)" link type="warning" @click="riskFreeze(row)">风控冻结</el-button><el-button v-if="row.status === 'RISK_FROZEN'" link type="warning" @click="riskResume(row)">解除冻结</el-button><el-button v-if="['SUBMITTED','INVOICE_PENDING','READY_TO_PAY','PAYMENT_FAILED'].includes(row.status)" link type="danger" @click="reject(row)">驳回</el-button><el-button v-if="row.status === 'PAID'" link type="primary" @click="completeWithdrawalRecord(row)">归档完成</el-button></template></template></el-table-column>
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
    <el-dialog v-model="payVisible" title="确认实际打款" width="520px"><el-form label-width="110px"><el-form-item label="实际打款"><el-input-number v-model="payForm.actualPaidAmount" :min="0.01" :precision="2" style="width:100%"/></el-form-item><el-form-item label="银行流水号"><el-input v-model="payForm.paymentReference" /></el-form-item><el-form-item label="凭证地址"><el-input v-model="payForm.paymentVoucherUrl" /></el-form-item><el-form-item label="管理员密码" required><el-input v-model="payForm.adminPassword" type="password" show-password maxlength="64" autocomplete="current-password" placeholder="二次验证当前管理员登录密码" /></el-form-item></el-form><template #footer><el-button @click="payVisible=false">取消</el-button><el-button type="primary" @click="submitPay">确认已打款</el-button></template></el-dialog>
    <el-dialog v-model="eventsVisible" title="提现审批轨迹" width="620px"><el-timeline><el-timeline-item v-for="item in withdrawalEvents" :key="item.id" :timestamp="formatTime(item.createTime)" placement="top"><strong>{{ withdrawalStatus(item.toStatus) }}</strong><div class="event-remark">{{ item.remark || '-' }}</div><small>{{ item.operatorName || '商户提交' }}</small></el-timeline-item></el-timeline></el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/store'
import { getAdminOrderWorkSummary } from '@/api/shop'
import { applyMerchantWithdrawal, cancelMerchantWithdrawal, completeMerchantWithdrawal, failMerchantWithdrawalPayment, freezeMerchantDeposit, freezeMerchantWithdrawal, listMerchantAccounts, listMerchantDepositFlows, listMerchantLedger, listMerchantReconciliation, listMerchantSettlements, listMerchants, listMerchantWithdrawalEvents, listMerchantWithdrawals, payMerchantWithdrawal, receiveMerchantDeposit, rejectMerchantWithdrawal, releaseMerchantDeposit, resumeMerchantWithdrawal, reviewMerchantWithdrawal, startMerchantWithdrawalPayment } from '@/api/merchant'

const store = useAppStore()
const route = useRoute()
const isMerchantUser = computed(() => Boolean(store.userInfo?.merchantId))
const merchantCanManageFunds = computed(() => isMerchantUser.value && store.hasPermission('finance:manage'))
const canManage = computed(() => !isMerchantUser.value && store.hasPermission('finance:manage'))
const canApply = computed(() => merchantCanManageFunds.value || canManage.value)
const financeTabs = ['accounts', 'ledger', 'reconciliation', 'settlements', 'deposits', 'withdrawals']
const tab = ref(financeTabs.includes(String(route.query.tab || '')) ? String(route.query.tab) : 'accounts'); const loading = ref(false); const saving = ref(false)
const merchants = ref([]); const accounts = ref([]); const settlements = ref([]); const depositFlows = ref([]); const ledger = ref([]); const reconciliation = ref([]); const withdrawals = ref([])
const applyVisible = ref(false); const depositVisible = ref(false); const reviewVisible = ref(false); const payVisible = ref(false)
const current = ref(null); const currentAccount = ref(null)
const eventsVisible = ref(false); const withdrawalEvents = ref([])
const orderSummary = ref({ pendingShipment: 0, afterSale: 0 })
const ownAccount = computed(() => accounts.value[0] || {})
const applyForm = ref({ requestNo: '', merchantId: null, requestedAmount: 0 })
const depositForm = ref({ merchantId: null, operationType: 'FREEZE', amount: 0, reason: '' })
const reviewForm = ref({ invoiceRequiredAmount: 0, invoiceReceivedAmount: 0, invoiceStatus: 'NOT_REQUIRED', adjustmentAmount: 0, adjustmentReason: '' })
const payForm = ref({ actualPaidAmount: 0, paymentReference: '', paymentVoucherUrl: '', adminPassword: '' })

const money = (value) => Number(value || 0).toFixed(2)
const formatTime = (value) => value ? String(value).replace('T', ' ').slice(0, 19) : '-'
const settlementStatus = (status) => ({ PENDING: { label: '待结算', type: 'warning' }, AVAILABLE: { label: '可提现', type: 'success' }, REVERSED: { label: '已冲回', type: 'info' } }[status] || { label: status || '-', type: 'info' })
const withdrawalStatus = (status) => ({ SUBMITTED: '待审核', INVOICE_PENDING: '待收发票', READY_TO_PAY: '待付款', PAYMENT_PROCESSING: '付款处理中', PAYMENT_FAILED: '付款失败', RISK_FROZEN: '风控冻结', PAID: '已打款', COMPLETED: '已完成', REJECTED: '已驳回', CANCELED: '已撤回' }[status] || status || '-')
const operationNo = () => `MD-${globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`}`
const withdrawalRequestNo = () => `MWR-${globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`}`
const depositOperation = (type) => ({ FREEZE: { label: '余额转入', type: 'warning' }, RECEIVE: { label: '线下收款', type: 'primary' }, RELEASE: { label: '解冻', type: 'success' } }[type] || { label: type || '-', type: 'info' })
const ledgerType = (type) => ({ OPENING_BALANCE: '期初余额', ORDER_PENDING: '订单进入待结算', SETTLEMENT_RELEASE: '到期释放货款', AFTER_SALE_REVERSAL: '售后冲回', WITHDRAWAL_APPLY: '申请提现', WITHDRAWAL_REJECT: '提现驳回', WITHDRAWAL_CANCEL: '提现撤回', WITHDRAWAL_PAID: '提现付款', DEPOSIT_FREEZE: '余额转保证金', DEPOSIT_RECEIVE: '线下保证金', DEPOSIT_RELEASE: '释放保证金' }[type] || type || '-')
const signedMoney = (value) => { const number = Number(value || 0); return `${number > 0 ? '+' : ''}¥${number.toFixed(2)}` }
const deltaClass = (value) => Number(value || 0) > 0 ? 'delta-plus' : Number(value || 0) < 0 ? 'delta-minus' : 'delta-zero'
const ledgerChanges = (row) => {
  const items = [
    { label: '待结算', value: row.pendingDelta },
    { label: '可提现', value: row.availableDelta },
    { label: '提现冻结', value: row.frozenDelta },
    { label: '保证金', value: row.depositDelta },
    { label: '欠款', value: row.debtDelta },
    { label: '累计打款', value: row.paidDelta },
  ].filter((item) => Number(item.value || 0) !== 0)
  return items.length ? items : [{ label: '余额', value: 0 }]
}
const maskBankAccount = (value) => { const text = String(value || ''); return text.length <= 8 ? text : `${text.slice(0, 4)} **** ${text.slice(-4)}` }
const depositDialogTitle = computed(() => ({ FREEZE: '从商户余额转入保证金', RECEIVE: '登记线下收到的保证金', RELEASE: '解冻商户保证金' }[depositForm.value.operationType] || '调整保证金'))
const depositDialogTip = computed(() => depositForm.value.operationType === 'FREEZE'
  ? `本次金额将从可提现余额转入保证金。当前可提现 ¥${money(currentAccount.value?.availableAmount)}`
  : depositForm.value.operationType === 'RECEIVE'
    ? '仅在财务已经实际收到商户线下保证金后登记，本操作不会扣减商户货款余额。'
    : `解冻时优先抵扣退款欠款，剩余金额回到可提现余额。当前保证金 ¥${money(currentAccount.value?.depositFrozenAmount)}`)

const loadCurrent = async () => { loading.value = true; try { if (tab.value === 'accounts') accounts.value = (await listMerchantAccounts()).data || []; if (tab.value === 'settlements') settlements.value = (await listMerchantSettlements()).data || []; if (tab.value === 'deposits') depositFlows.value = (await listMerchantDepositFlows()).data || []; if (tab.value === 'ledger') ledger.value = (await listMerchantLedger()).data || []; if (tab.value === 'reconciliation') reconciliation.value = (await listMerchantReconciliation()).data || []; if (tab.value === 'withdrawals') withdrawals.value = (await listMerchantWithdrawals()).data || [] } finally { loading.value = false } }
const openApply = () => { applyForm.value = { requestNo: withdrawalRequestNo(), merchantId: isMerchantUser.value ? store.userInfo.merchantId : null, requestedAmount: 0 }; applyVisible.value = true }
const submitApply = async () => { saving.value = true; try { await applyMerchantWithdrawal(applyForm.value); ElMessage.success('提现申请已提交'); applyVisible.value = false; tab.value = 'withdrawals'; await loadCurrent() } finally { saving.value = false } }
const openDeposit = (row, operationType) => { currentAccount.value = row; depositForm.value = { merchantId: row.merchantId, operationType, amount: 0, reason: '' }; depositVisible.value = true }
const submitDeposit = async () => { if (!depositForm.value.reason?.trim()) return ElMessage.warning('请填写保证金调整原因'); saving.value = true; try { const payload = { merchantId: depositForm.value.merchantId, amount: depositForm.value.amount, reason: depositForm.value.reason.trim(), operationNo: operationNo() }; if (depositForm.value.operationType === 'FREEZE') await freezeMerchantDeposit(payload); else if (depositForm.value.operationType === 'RECEIVE') await receiveMerchantDeposit(payload); else await releaseMerchantDeposit(payload); ElMessage.success('保证金已更新'); depositVisible.value = false; await loadCurrent() } finally { saving.value = false } }
const openReview = (row) => { current.value = row; reviewForm.value = { invoiceRequiredAmount: Number(row.invoiceRequiredAmount || 0), invoiceReceivedAmount: Number(row.invoiceReceivedAmount || 0), invoiceStatus: row.invoiceStatus || 'NOT_REQUIRED', adjustmentAmount: Number(row.adjustmentAmount || 0), adjustmentReason: row.adjustmentReason || '' }; reviewVisible.value = true }
const submitReview = async () => { await reviewMerchantWithdrawal(current.value.id, reviewForm.value); ElMessage.success('发票与审核信息已保存'); reviewVisible.value = false; await loadCurrent() }
const openPay = (row) => { current.value = row; payForm.value = { actualPaidAmount: Number(row.requestedAmount || 0) + Number(row.adjustmentAmount || 0), paymentReference: '', paymentVoucherUrl: '', adminPassword: '' }; payVisible.value = true }
const submitPay = async () => {
  if (!payForm.value.adminPassword) return ElMessage.warning('请输入当前管理员登录密码进行二次验证')
  await ElMessageBox.confirm('确认银行已经实际打款，并将本笔商户提现登记为已付款？', '确认实际打款', { type: 'warning' })
  await payMerchantWithdrawal(current.value.id, payForm.value)
  payForm.value.adminPassword = ''
  ElMessage.success('打款已确认')
  payVisible.value = false
  await loadCurrent()
}
const reject = async (row) => { const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回商户提现', { inputValidator: (v) => Boolean(v?.trim()) || '必须填写原因' }); await rejectMerchantWithdrawal(row.id, { reason: value }); ElMessage.success('已驳回；冻结金额先抵退款欠款，剩余退回可提现余额'); await loadCurrent() }
const startPayment = async (row) => { await ElMessageBox.confirm('确认已经开始向快照中的收款账户执行付款？进入付款处理中后仍需登记成功或失败结果。', '开始付款', { type: 'warning' }); await startMerchantWithdrawalPayment(row.id); ElMessage.success('已进入付款处理中'); await loadCurrent() }
const paymentFailed = async (row) => { const { value } = await ElMessageBox.prompt('请填写银行退回、账户异常等具体原因', '登记付款失败', { inputValidator: (v) => Boolean(v?.trim()) || '必须填写原因' }); await failMerchantWithdrawalPayment(row.id, { reason: value }); ElMessage.warning('已登记付款失败，申请金额仍保持冻结，可重试或驳回'); await loadCurrent() }
const cancelWithdrawal = async (row) => { const { value } = await ElMessageBox.prompt('请填写撤回原因', '撤回提现申请', { inputValidator: (v) => Boolean(v?.trim()) || '必须填写原因' }); await cancelMerchantWithdrawal(row.id, { reason: value }); ElMessage.success('申请已撤回，冻结金额已按退款欠款规则退回'); await loadCurrent() }
const riskFreeze = async (row) => { const { value } = await ElMessageBox.prompt('请填写风控冻结原因', '冻结提现流程', { inputValidator: (v) => Boolean(v?.trim()) || '必须填写原因' }); await freezeMerchantWithdrawal(row.id, { reason: value }); ElMessage.warning('提现流程已冻结，资金保持冻结不变'); await loadCurrent() }
const riskResume = async (row) => { const { value } = await ElMessageBox.prompt('请填写解除冻结原因', '恢复提现流程', { inputValidator: (v) => Boolean(v?.trim()) || '必须填写原因' }); await resumeMerchantWithdrawal(row.id, { reason: value }); ElMessage.success('已恢复到冻结前状态'); await loadCurrent() }
const completeWithdrawalRecord = async (row) => { await ElMessageBox.confirm('确认银行凭证、发票和审批资料均已归档？', '归档完成'); await completeMerchantWithdrawal(row.id); ElMessage.success('提现单已完成归档'); await loadCurrent() }
const showEvents = async (row) => { withdrawalEvents.value = (await listMerchantWithdrawalEvents(row.id)).data || []; eventsVisible.value = true }

onMounted(async () => {
  if (!isMerchantUser.value) merchants.value = (await listMerchants()).data || []
  await loadCurrent()
  if (isMerchantUser.value && store.hasPermission('shop:order')) {
    try {
      const res = await getAdminOrderWorkSummary()
      orderSummary.value = { pendingShipment: Number(res.data?.pendingShipment || 0), afterSale: Number(res.data?.afterSale || 0) }
    } catch {
      orderSummary.value = { pendingShipment: 0, afterSale: 0 }
    }
  }
})
</script>

<style scoped>
.page-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px}.page-heading h2{margin:0}.page-heading p{margin:6px 0 0;color:#909399}.merchant-summary{display:grid;grid-template-columns:repeat(6,minmax(140px,1fr));gap:12px;margin:16px 0}.summary-card{display:flex;flex-direction:column;gap:7px;padding:16px;border:1px solid #e6ebf2;border-radius:10px;background:#fff}.summary-card span{color:#606266;font-size:13px}.summary-card strong{color:#25324b;font-size:22px}.summary-card small{color:#909399}.available{color:#1b6f3a!important}.deposit{color:#b26a00}.debt,.delta-minus{color:#d93838!important;font-weight:700}.delta-plus{color:#16834b;font-weight:700}.delta-zero{color:#909399}.dialog-form{margin-top:18px}.help,.event-remark,.ledger-sub{color:#606266;font-size:12px;margin-top:6px}.ledger-table{margin-top:14px}.ledger-change-list,.ledger-balance{display:flex;flex-wrap:wrap;gap:7px 10px}.ledger-change{display:inline-flex;align-items:center;gap:5px;padding:4px 8px;border-radius:6px;background:#f5f7fa}.ledger-change small{color:#606266}.ledger-balance span{min-width:132px;color:#4b5563;font-size:12px}@media(max-width:1100px){.merchant-summary{grid-template-columns:repeat(3,minmax(150px,1fr))}}@media(max-width:760px){.page-heading{align-items:flex-start;gap:12px;flex-direction:column}.merchant-summary{grid-template-columns:repeat(2,minmax(130px,1fr))}}
</style>
