<template>
  <div class="page-container customer-bonus-page">
    <div class="page-heading">
      <div><h2>客户奖金接入</h2><p>邀请记录订单归属，推广资格决定谁能收佣金，本页配置直接推荐成交佣金或查看客户独立程序。</p></div>
      <el-tag :type="statusMeta.type" size="large">{{ statusMeta.label }}</el-tag>
    </div>
    <el-alert v-if="!canView" title="平台管理员拥有奖金配置权限后可查看本页。" type="info" :closable="false" show-icon />
    <template v-else>
      <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon class="page-alert"><el-button :loading="loading" @click="load">重新读取</el-button></el-alert>
      <el-card shadow="never" v-loading="loading" class="status-card">
        <template #header><div class="card-title"><strong>当前客户项目</strong><span>规则变更只作用于之后支付的新订单，历史快照和奖金保留</span></div></template>
        <div class="status-grid">
          <div><span>奖金程序状态</span><strong>{{ statusMeta.label }}</strong></div>
          <div><span>当前版本</span><strong>{{ config.versionName || '未登记' }}</strong></div>
          <div><span>程序代码</span><strong>{{ config.currentPolicyCode || '-' }}</strong></div>
          <div><span>生效时间</span><strong>{{ config.effectiveTime || '-' }}</strong></div>
        </div>
        <el-alert v-if="isLegacySample" type="warning" :closable="false" show-icon title="当前登记为历史演示制度，本页只读展示；可明确切换为基座直接推荐佣金，或继续由客户独立项目维护。" />
        <el-alert v-else-if="isExternalPolicy" type="info" :closable="false" show-icon title="当前使用客户独立奖金程序，本页不编辑其制度。切换为基座直接推荐佣金会替换之后支付订单的计算规则。" />
      </el-card>
      <el-card shadow="never" class="direct-card">
        <template #header><div class="card-title"><strong>基座直接推荐成交佣金</strong><span>新客户默认关闭，配置有效比例后开启</span></div></template>
        <el-alert title="A 有推广资格，B 绑定 A 后购买参与佣金的商品，可给 A 计佣；B 保持普通客户，无需购买后自动升级。B 分享给 C 时，B 没有推广资格就不计佣，也不会越过 B 给 A 计佣。" type="info" :closable="false" show-icon class="page-alert" />
        <div v-if="isExternalPolicy && !switchRequested" class="policy-switch">
          <p>当前程序保持只读。选择后可填写新规则，保存时需再次确认替换；不会重算历史订单。</p>
          <el-button :disabled="!canEdit || !loaded || loading" @click="switchRequested = true">配置并切换到基座佣金</el-button>
        </div>
        <el-form v-else :model="form" label-width="124px" label-position="left" :disabled="!canEdit || !loaded || loading || saving" class="direct-form">
          <el-form-item label="直接推荐佣金"><el-switch v-model="form.enabled" aria-label="启用直接推荐佣金" /><span class="field-help">{{ form.enabled ? '开启后按本规则计佣' : '关闭后不产生新的直接推荐佣金' }}</span></el-form-item>
          <el-form-item label="佣金比例"><el-input-number v-model="form.commissionPercent" :min="0" :max="100" :precision="2" :step="1" aria-label="佣金比例百分比" /><span class="unit">%</span></el-form-item>
          <el-form-item label="购买范围"><el-radio-group v-model="form.purchaseScope"><el-radio-button value="ALL_ORDERS">持续有效消费</el-radio-button><el-radio-button value="FIRST_PAID_ORDER">仅首笔支付交易</el-radio-button></el-radio-group></el-form-item>
          <p v-if="form.purchaseScope === 'FIRST_PAID_ORDER'" class="form-note">首次指顾客历史上第一笔支付成功的交易，包含未计佣商品的购买；同次结算拆出的子订单都属于该交易。退款、关闭或重开佣金规则均不会重置首笔资格。</p>
          <el-form-item label="结算保护期"><el-input-number v-model="form.settlementDelayDays" :min="0" :max="365" :precision="0" aria-label="佣金结算保护期天数" /><span class="unit">天</span></el-form-item>
          <p class="form-note">只按参与商品的实际支付金额计算，运费不参与。支付后记待结算佣金，到保护期释放；退款成功后按退款商品及数量冲减，已释放的佣金按账务规则追回。</p>
          <div class="form-links"><el-button v-if="canConfigureProducts" text @click="router.push('/shop/products')">配置参与佣金的商品</el-button><el-button v-if="store.hasPermission('config:bonus')" text @click="router.push('/tenant/business-modes')">配置邀请与推广资格</el-button></div>
          <el-alert v-if="saveError" :title="saveError" type="error" :closable="false" show-icon class="page-alert" />
          <div class="actions"><el-button :disabled="!dirty || saving" @click="reset">撤销修改</el-button><el-button type="primary" :disabled="!canEdit || !loaded || !dirty || loading" :loading="saving" @click="save">保存佣金规则</el-button></div>
        </el-form>
        <p v-if="!canEdit" class="form-note">修改需要平台管理员同时拥有奖金配置和财务管理权限。</p>
      </el-card>
      <div class="boundary-grid">
        <el-card shadow="never"><template #header><strong>商城基座统一负责</strong></template><ul><li>注册、邀请关系与支付时快照</li><li>直接推荐成交佣金及商品参与设置</li><li>奖金落库、防重复、保护期结算</li><li>部分退款、全额退款、追回欠款与审计</li></ul></el-card>
        <el-card shadow="never"><template #header><strong>客户独立项目负责</strong></template><ul><li>额外团队奖金、等级和复杂条件</li><li>独立程序版本、最高拨出和计算口径</li><li>客户定制制度的测试与验收</li><li>客户独立服务器、支付配置和交付</li></ul></el-card>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDirectReferralConfig, saveDirectReferralConfig } from '@/api/bonusConfig'
import { useAppStore } from '@/store'
import { useUnsavedChanges } from '@/composables/useUnsavedChanges'
import { DIRECT_REFERRAL_POLICY, DISABLED_BONUS_POLICY, directReferralForm, directReferralPayload, directReferralValidation } from '@/utils/directReferralConfig'

const router = useRouter()
const store = useAppStore()
const platformAdmin = computed(() => !store.userInfo?.merchantId)
const canView = computed(() => platformAdmin.value && store.hasPermission('config:bonus'))
const canEdit = computed(() => canView.value && store.hasPermission('finance:manage'))
const canConfigureProducts = computed(() => platformAdmin.value && store.hasPermission('shop:product'))
const loading = ref(false)
const saving = ref(false)
const loaded = ref(false)
const loadError = ref('')
const saveError = ref('')
const config = ref({})
const form = ref(directReferralForm())
const snapshot = ref(null)
const switchRequested = ref(false)
const isLegacySample = computed(() => config.value.currentPolicyCode === 'NEW_RETAIL_SIMPLE_DEFAULT')
const isExternalPolicy = computed(() => loaded.value && (config.value.readOnly === true || Boolean(config.value.currentPolicyCode && ![DIRECT_REFERRAL_POLICY, DISABLED_BONUS_POLICY].includes(config.value.currentPolicyCode))))
const dirty = computed(() => loaded.value && Boolean(snapshot.value) && (switchRequested.value || JSON.stringify(form.value) !== JSON.stringify(snapshot.value)))
useUnsavedChanges(dirty)
const statusMeta = computed(() => {
  if (loadError.value) return { label: '读取失败 · 状态未核实', type: 'danger' }
  if (!loaded.value) return { label: '读取中', type: 'info' }
  if (isLegacySample.value) return { label: '历史演示制度 · 待替换', type: 'warning' }
  if (isExternalPolicy.value) return { label: '客户独立程序', type: 'warning' }
  if (config.value.enabled === true) return { label: '直接推荐佣金 · 已开启', type: 'success' }
  return { label: '奖金未接入 · 安全关闭', type: 'info' }
})
const hydrate = (data) => {
  config.value = { ...data }
  form.value = directReferralForm(data)
  snapshot.value = { ...form.value }
  switchRequested.value = false
  loaded.value = true
}
const reset = () => {
  if (snapshot.value) form.value = { ...snapshot.value }
  switchRequested.value = false
  saveError.value = ''
}
const load = async () => {
  if (!canView.value || loading.value || dirty.value) return
  loading.value = true
  loaded.value = false
  loadError.value = ''
  try {
    const res = await getDirectReferralConfig()
    if (!res.data || typeof res.data.enabled !== 'boolean') throw new Error('佣金配置响应不完整')
    hydrate(res.data)
  } catch (error) { loadError.value = error?.message || '佣金配置读取失败，请重试' }
  finally { loading.value = false }
}
const save = async () => {
  if (!canEdit.value || !loaded.value || !dirty.value || loading.value || saving.value) return
  const draft = { ...form.value }
  const validation = directReferralValidation(draft)
  if (validation) { saveError.value = validation; return }
  const payload = directReferralPayload(draft, config.value, isExternalPolicy.value)
  saving.value = true
  saveError.value = ''
  try {
    const warning = isExternalPolicy.value ? `将替换当前“${config.value.versionName || config.value.currentPolicyCode}”程序，之后支付的订单改用基座直接推荐佣金。\n\n` : ''
    try {
      await ElMessageBox.confirm(`${warning}${draft.enabled ? `开启直接推荐佣金，比例 ${draft.commissionPercent}%` : '关闭新订单直接推荐佣金'}；${draft.purchaseScope === 'FIRST_PAID_ORDER' ? '仅顾客首笔支付交易' : '持续有效消费'}；保护期 ${draft.settlementDelayDays} 天。\n历史订单、退款追回和资金快照继续按原规则处理。`, '确认佣金规则变更', { type: 'warning', confirmButtonText: isExternalPolicy.value ? '确认替换并保存' : '确认并保存', cancelButtonText: '返回修改', customClass: 'settings-impact-confirm' })
    } catch { return }
    const res = await saveDirectReferralConfig(payload)
    if (!res.data || typeof res.data.enabled !== 'boolean') throw new Error('保存结果未核实，请保留草稿并重新核对当前版本')
    config.value = { ...res.data }
    snapshot.value = directReferralForm(res.data)
    // 确认期间若草稿被修改，不覆盖这些尚未保存的修改。
    if (JSON.stringify(form.value) === JSON.stringify(draft)) form.value = { ...snapshot.value }
    switchRequested.value = false
    loaded.value = true
    ElMessage.success('佣金规则已保存，仅之后支付的新订单使用新规则')
  } catch (error) { saveError.value = `${error?.message || '佣金规则保存失败'}。草稿已保留；若版本已变更，请撤销草稿并重新读取后再保存。` }
  finally { saving.value = false }
}
onMounted(load)
</script>

<style scoped>
.customer-bonus-page{max-width:1180px}.page-heading,.card-title{display:flex;align-items:center;justify-content:space-between;gap:18px}.page-heading{margin-bottom:16px}.page-heading h2{margin:0;font-size:22px}.page-heading p,.card-title span{margin:6px 0 0;color:#909399;font-size:13px;line-height:1.6}.page-alert,.status-card,.direct-card,.boundary-grid{margin-bottom:16px}.status-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin-bottom:16px}.status-grid>div{display:flex;flex-direction:column;gap:7px;padding:15px;background:#f7f9fc;border:1px solid #e8ecf2;border-radius:10px}.status-grid span{color:#909399;font-size:12px}.status-grid strong{overflow-wrap:anywhere;color:#303133}.boundary-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.boundary-grid ul{display:grid;gap:12px;margin:0;padding-left:20px;color:#606266;line-height:1.6}.actions{display:flex;justify-content:flex-end;gap:10px;margin-top:20px}.actions .el-button+.el-button{margin-left:0}.direct-form{max-width:820px}.field-help,.form-note,.policy-switch p{color:#606266;font-size:13px;line-height:1.7}.field-help{margin-left:12px}.form-note{margin:0 0 18px}.unit{margin-left:10px}.form-links{display:flex;flex-wrap:wrap;gap:12px}.form-links .el-button{margin-left:0;padding-left:0}.policy-switch{margin-bottom:12px}:global(.settings-impact-confirm .el-message-box__message p){white-space:pre-line;line-height:1.8}@media(max-width:900px){.status-grid{grid-template-columns:1fr 1fr}.boundary-grid{grid-template-columns:1fr}}@media(max-width:560px){.page-heading,.card-title{align-items:flex-start;flex-direction:column}.status-grid{grid-template-columns:1fr}.direct-form :deep(.el-form-item){flex-direction:column;align-items:flex-start}.direct-form :deep(.el-form-item__label){width:auto!important}.direct-form :deep(.el-form-item__content){margin-left:0!important}.field-help{margin-left:0}.actions{align-items:stretch;flex-direction:column}}
</style>
