<template>
  <div class="page-container">
    <div class="page-heading">
      <div><h2>商户管理</h2><p>平台负责开通账号和认证；经营主体、收款与开票资料由商户自行提交。</p></div>
      <el-button type="primary" @click="open()">开通商户</el-button>
    </div>
    <el-alert title="现有商品默认仍是平台自营。只有商品明确绑定商户后，才会生成商户货款。" type="info" :closable="false" show-icon />
    <div class="toolbar"><el-input v-model="keyword" clearable placeholder="商户名称或编号" @keyup.enter="load" /><el-button type="primary" @click="load">查询</el-button></div>
    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="merchantNo" label="商户编号" min-width="150" />
      <el-table-column prop="merchantName" label="商户名称" min-width="180" />
      <el-table-column prop="contactName" label="联系人" width="120" />
      <el-table-column prop="contactPhone" label="联系电话" min-width="150" />
      <el-table-column label="结算方式" width="150"><template #default>结算价 × 有效数量</template></el-table-column>
      <el-table-column label="默认结算等待" width="145"><template #default="{ row }"><strong>{{ row.defaultSettlementDays || 0 }} 天</strong></template></el-table-column>
      <el-table-column label="合同" width="100"><template #default="{ row }"><el-tag :type="row.contractStatus === 'SIGNED' ? 'success' : row.contractStatus === 'EXPIRED' ? 'danger' : 'warning'">{{ contractLabel(row.contractStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="应缴保证金" width="130"><template #default="{ row }">¥{{ Number(row.requiredDepositAmount || 0).toFixed(2) }}</template></el-table-column>
      <el-table-column label="账号/经营" width="145"><template #default="{ row }"><span>{{ controlLabel('accountStatus', row.accountStatus) }}</span> / <span>{{ controlLabel('businessStatus', row.businessStatus) }}</span></template></el-table-column>
      <el-table-column label="履约" width="105"><template #default="{ row }"><el-tag :type="row.fulfillmentStatus === 'ENABLED' ? 'success' : 'warning'">{{ controlLabel('fulfillmentStatus', row.fulfillmentStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="提现/结算/保证金" width="205"><template #default="{ row }"><span>{{ controlLabel('withdrawalStatus', row.withdrawalStatus) }}</span> / <span>{{ controlLabel('settlementStatus', row.settlementStatus) }}</span> / <span>{{ controlLabel('depositStatus', row.depositStatus) }}</span></template></el-table-column>
      <el-table-column label="审核/退出" width="145"><template #default="{ row }"><span>{{ controlLabel('auditStatus', row.auditStatus) }}</span> / <span>{{ controlLabel('exitStatus', row.exitStatus) }}</span></template></el-table-column>
      <el-table-column label="操作" width="270" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="open(row)">资料</el-button><el-button link type="primary" @click="openControls(row)">业务控制</el-button><el-button link type="warning" @click="checkExit(row)">退出检查</el-button><el-button v-if="row.businessStatus === 'ACTIVE'" link type="danger" @click="freezeAll(row)">全面冻结</el-button></template></el-table-column>
    </el-table>
    <el-dialog v-model="visible" :title="form.id ? '商户经营设置' : '开通商户及商家账号'" width="760px">
      <el-form :model="form" label-width="100px">
        <el-alert v-if="!form.id" title="平台只设置商家登录账号；系统会生成24小时有效的一次性临时密码，商家首次登录后必须自行设置正式密码。经营主体、收款与开票资料仍由商家提交认证。" type="info" :closable="false" show-icon style="margin-bottom:18px" />
        <el-divider content-position="left">基础资料</el-divider>
        <el-form-item label="商户编号"><el-input v-model="form.merchantNo" :disabled="Boolean(form.id)" placeholder="留空自动生成" /></el-form-item>
        <el-form-item label="商户名称" required><el-input v-model="form.merchantName" maxlength="128" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <template v-if="!form.id">
          <el-divider content-position="left">商家登录账号</el-divider>
          <el-form-item label="商家账号" required><el-input v-model="form.username" maxlength="32" placeholder="4至32位，以字母开头，可用字母、数字、下划线" autocomplete="off" /></el-form-item>
          <el-form-item label="当前密码" required><el-input v-model="form.currentAdminPassword" type="password" show-password maxlength="64" placeholder="确认当前平台管理员身份" autocomplete="current-password" /></el-form-item>
        </template>
        <template v-else>
          <el-divider content-position="left">商户提交的认证资料</el-divider>
          <el-form-item label="经营主体"><el-input :model-value="form.legalEntityName || '商户尚未提交'" disabled /></el-form-item>
          <el-form-item label="统一信用代码"><el-input :model-value="form.unifiedSocialCreditCode || '商户尚未提交'" disabled /></el-form-item>
          <el-form-item label="收款户名"><el-input :model-value="form.bankAccountName || '商户尚未提交'" disabled /></el-form-item>
          <el-form-item label="开户银行"><el-input :model-value="form.bankName || '商户尚未提交'" disabled /></el-form-item>
          <el-form-item label="银行账号"><el-input :model-value="form.bankAccountNo || '商户尚未提交'" disabled /></el-form-item>
          <el-form-item label="发票抬头"><el-input :model-value="form.invoiceTitle || '商户尚未提交'" disabled /></el-form-item>
          <el-form-item label="纳税人识别号"><el-input :model-value="form.taxpayerIdentificationNo || '商户尚未提交'" disabled /></el-form-item>
        </template>
        <el-form-item v-if="form.id" label="合同状态"><el-select v-model="form.contractStatus" style="width:220px"><el-option label="待签约" value="PENDING"/><el-option label="已签约生效" value="SIGNED"/><el-option label="已终止" value="EXPIRED"/></el-select></el-form-item>
        <el-divider content-position="left">结算与保证金</el-divider>
        <el-form-item label="结算等待" required>
          <el-input-number v-model="form.defaultSettlementDays" :min="0" :max="365" :precision="0" style="width:180px" />
          <span class="unit">天</span>
          <div class="field-help">从“客户确认收货与售后入口截止时间中的较晚时点”开始计算；0天表示售后窗口结束后即可结算。</div>
        </el-form-item>
        <el-form-item label="应缴保证金"><el-input-number v-model="form.requiredDepositAmount" :min="0" :precision="2" style="width:220px" /><div class="field-help">商户保证金未达到该金额时，不能提交商品审核或申请提现；财务可在商户货款页登记线下收款。</div></el-form-item>
        <el-form-item v-if="form.id" label="备注"><el-input v-model="form.remark" type="textarea" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="credentialVisible" title="商家一次性登录凭据" width="500px" :close-on-click-modal="false">
      <el-alert title="临时密码只显示本次，请立即安全交给商家；24小时后失效，首次登录必须修改正式密码。" type="warning" :closable="false" show-icon />
      <div class="credential-card"><span>登录账号</span><strong>{{ credential.username }}</strong><span>临时密码</span><strong class="temporary-password">{{ credential.temporaryPassword }}</strong><span>有效期至</span><strong>{{ formatCredentialTime(credential.expiresAt) }}</strong></div>
      <template #footer><el-button type="primary" @click="copyCredential">复制凭据</el-button><el-button @click="credentialVisible=false">我已保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="controlVisible" title="商户业务控制" width="720px">
      <el-alert title="暂停新销售不会锁死商户账号；商户仍可按履约状态处理已经成交的订单和售后。" type="warning" :closable="false" show-icon />
      <el-alert v-if="exitReadiness" :title="exitReadiness.ready ? '退出检查通过：可以在关闭全部能力后确认已退出' : '暂不能确认已退出'" :type="exitReadiness.ready ? 'success' : 'error'" :closable="false" show-icon class="exit-alert">
        <template #default><div v-if="!exitReadiness.ready">{{ exitReadiness.blockers.join('；') }}</div></template>
      </el-alert>
      <el-form label-width="125px" class="control-form">
        <el-form-item label="账号状态"><el-radio-group v-model="controlForm.accountStatus"><el-radio-button value="ENABLED">允许登录</el-radio-button><el-radio-button value="DISABLED">禁止登录</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="经营状态"><el-radio-group v-model="controlForm.businessStatus"><el-radio-button value="ACTIVE">正常经营</el-radio-button><el-radio-button value="SUSPENDED">暂停新销售</el-radio-button><el-radio-button value="CLOSED">停止经营</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="履约状态"><el-radio-group v-model="controlForm.fulfillmentStatus"><el-radio-button value="ENABLED">商户可处理</el-radio-button><el-radio-button value="PLATFORM_ONLY">平台接管</el-radio-button><el-radio-button value="DISABLED">暂停处理</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="提现状态"><el-switch v-model="withdrawalEnabled" active-text="允许申请" inactive-text="冻结提现" /></el-form-item>
        <el-form-item label="结算状态"><el-switch v-model="settlementEnabled" active-text="到期释放" inactive-text="继续留在待结算" /></el-form-item>
        <el-form-item label="保证金状态"><el-switch v-model="depositNormal" active-text="正常" inactive-text="风控冻结" /></el-form-item>
        <el-form-item label="入驻认证"><el-select v-model="controlForm.auditStatus"><el-option label="待认证" value="PENDING"/><el-option label="认证通过（允许提交商品审核）" value="APPROVED"/><el-option label="认证驳回" value="REJECTED"/></el-select><div class="field-help">只有商户已提交完整主体、收款与开票资料时才可认证通过；商品仍需逐个审核通过才会上架。</div></el-form-item>
        <el-form-item label="退出状态"><el-select v-model="controlForm.exitStatus" @change="onExitStatusChange"><el-option label="正常" value="NORMAL"/><el-option label="清退中" value="EXITING"/><el-option label="已退出" value="EXITED"/></el-select><el-button link type="warning" @click="refreshExitReadiness">重新检查</el-button></el-form-item>
        <el-form-item label="调整原因" required><el-input v-model="controlForm.reason" type="textarea" maxlength="256" show-word-limit placeholder="记录违规、风控、合同或恢复经营的具体原因" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="controlVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitControls">保存业务控制</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMerchantExitReadiness, listMerchants, saveMerchant, updateMerchantControls } from '@/api/merchant'

const rows = ref([]); const keyword = ref(''); const loading = ref(false); const visible = ref(false); const saving = ref(false)
const credentialVisible = ref(false)
const credential = ref({ username: '', temporaryPassword: '', expiresAt: '' })
const emptyForm = () => ({ id: null, merchantNo: '', merchantName: '', contactName: '', contactPhone: '', legalEntityName: '', unifiedSocialCreditCode: '', bankAccountName: '', bankName: '', bankAccountNo: '', invoiceTitle: '', taxpayerIdentificationNo: '', contractStatus: 'PENDING', requiredDepositAmount: 0, defaultSettlementDays: 0, status: 0, remark: '', username: '', currentAdminPassword: '' })
const form = ref(emptyForm())
const controlVisible = ref(false)
const exitReadiness = ref(null)
const controlForm = ref({ id: null, accountStatus: 'ENABLED', businessStatus: 'ACTIVE', fulfillmentStatus: 'ENABLED', withdrawalStatus: 'ENABLED', settlementStatus: 'ENABLED', depositStatus: 'NORMAL', auditStatus: 'APPROVED', exitStatus: 'NORMAL', reason: '' })
const withdrawalEnabled = computed({ get: () => controlForm.value.withdrawalStatus === 'ENABLED', set: (v) => { controlForm.value.withdrawalStatus = v ? 'ENABLED' : 'FROZEN' } })
const settlementEnabled = computed({ get: () => controlForm.value.settlementStatus === 'ENABLED', set: (v) => { controlForm.value.settlementStatus = v ? 'ENABLED' : 'FROZEN' } })
const depositNormal = computed({ get: () => controlForm.value.depositStatus === 'NORMAL', set: (v) => { controlForm.value.depositStatus = v ? 'NORMAL' : 'FROZEN' } })
const load = async () => { loading.value = true; try { rows.value = (await listMerchants({ keyword: keyword.value })).data || [] } finally { loading.value = false } }
const open = (row) => { form.value = row ? { ...row } : emptyForm(); visible.value = true }
const submit = async () => {
  if (!form.value.merchantName?.trim()) return ElMessage.warning('请输入商户名称')
  if (!form.value.id && !/^[A-Za-z][A-Za-z0-9_]{3,31}$/.test(form.value.username || '')) return ElMessage.warning('请输入规范的商家账号')
  if (!form.value.id && !form.value.currentAdminPassword) return ElMessage.warning('请输入当前管理员密码')
  saving.value = true
  try { const response = await saveMerchant(form.value.id, form.value); if (!form.value.id) { credential.value = { username: response.data?.onboardingUsername || form.value.username, temporaryPassword: response.data?.temporaryPassword || '', expiresAt: response.data?.credentialExpiresAt || '' }; credentialVisible.value = true }; ElMessage.success(form.value.id ? '商户经营设置已保存' : '商户已开通，请保存一次性登录凭据'); visible.value = false; await load() } finally { saving.value = false }
}
const formatCredentialTime = (value) => value ? String(value).replace('T', ' ').slice(0, 19) : '-'
const copyCredential = async () => { await navigator.clipboard.writeText(`商家登录账号：${credential.value.username}\n一次性临时密码：${credential.value.temporaryPassword}\n有效期至：${formatCredentialTime(credential.value.expiresAt)}\n首次登录后必须立即修改正式密码。`); ElMessage.success('商家登录凭据已复制') }
const openControls = async (row) => { controlForm.value = { id: row.id, accountStatus: row.accountStatus || 'ENABLED', businessStatus: row.businessStatus || (row.status === 1 ? 'ACTIVE' : 'SUSPENDED'), fulfillmentStatus: row.fulfillmentStatus || 'ENABLED', withdrawalStatus: row.withdrawalStatus || 'ENABLED', settlementStatus: row.settlementStatus || 'ENABLED', depositStatus: row.depositStatus || 'NORMAL', auditStatus: row.auditStatus || 'PENDING', exitStatus: row.exitStatus || 'NORMAL', reason: '' }; exitReadiness.value = null; controlVisible.value = true; await refreshExitReadiness() }
const refreshExitReadiness = async () => { if (!controlForm.value.id) return; exitReadiness.value = (await getMerchantExitReadiness(controlForm.value.id)).data || null }
const checkExit = async (row) => { await openControls(row) }
const onExitStatusChange = (status) => { if (status === 'EXITING') { controlForm.value.businessStatus = 'SUSPENDED'; controlForm.value.withdrawalStatus = 'FROZEN' } else if (status === 'EXITED') { controlForm.value.accountStatus = 'DISABLED'; controlForm.value.businessStatus = 'CLOSED'; controlForm.value.fulfillmentStatus = 'DISABLED'; controlForm.value.withdrawalStatus = 'FROZEN'; controlForm.value.settlementStatus = 'FROZEN' } }
const submitControls = async () => { if (!controlForm.value.reason?.trim()) return ElMessage.warning('请填写状态调整原因'); if (controlForm.value.exitStatus === 'EXITED') { await refreshExitReadiness(); if (!exitReadiness.value?.ready) return ElMessage.warning('尚有未完成业务或未清资金，不能确认已退出') } saving.value = true; try { const { id, ...payload } = controlForm.value; await updateMerchantControls(id, payload); ElMessage.success('商户业务控制已更新'); controlVisible.value = false; await load() } finally { saving.value = false } }
const freezeAll = async (row) => { await ElMessageBox.confirm('将禁止新销售、商品修改、提现和货款释放，但保留账号登录及历史订单履约。是否继续？', '全面冻结商户', { type: 'warning' }); await openControls(row); controlForm.value.businessStatus = 'SUSPENDED'; controlForm.value.fulfillmentStatus = 'ENABLED'; controlForm.value.withdrawalStatus = 'FROZEN'; controlForm.value.settlementStatus = 'FROZEN'; controlForm.value.reason = '平台全面冻结：保留历史订单与售后履约'; await submitControls() }
const controlLabel = (field, value) => ({ accountStatus: { ENABLED: '可登录', DISABLED: '禁登录' }, businessStatus: { ACTIVE: '正常经营', SUSPENDED: '暂停销售', CLOSED: '停止经营' }, fulfillmentStatus: { ENABLED: '商户处理', PLATFORM_ONLY: '平台接管', DISABLED: '暂停处理' }, withdrawalStatus: { ENABLED: '可提现', FROZEN: '冻结提现' }, settlementStatus: { ENABLED: '正常结算', FROZEN: '冻结结算' }, depositStatus: { NORMAL: '保证金正常', FROZEN: '保证金冻结' }, auditStatus: { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }, exitStatus: { NORMAL: '正常', EXITING: '清退中', EXITED: '已退出' } }[field]?.[value] || value || '-')
const contractLabel = (status) => ({ PENDING: '待签约', SIGNED: '已生效', EXPIRED: '已终止' }[status] || '待签约')
onMounted(load)
</script>

<style scoped>
.page-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px}.page-heading h2{margin:0}.page-heading p{margin:6px 0 0;color:#909399}.toolbar{display:flex;gap:10px;width:420px;margin:16px 0}.unit{margin-left:8px}.field-help{width:100%;color:#909399;font-size:12px;line-height:1.6;margin-top:6px}.control-form{margin-top:18px}.exit-alert{margin-top:12px}.credential-card{display:grid;grid-template-columns:100px minmax(0,1fr);gap:14px;margin-top:18px;padding:18px;border:1px solid #e1e8f0;border-radius:10px;background:#f8fafc}.credential-card span{color:#7b8798}.credential-card strong{word-break:break-all}.temporary-password{color:#b54708;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:18px;letter-spacing:.8px}
</style>
