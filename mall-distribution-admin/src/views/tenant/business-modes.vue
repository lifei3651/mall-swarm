<template>
  <div class="page-container business-mode-page">
    <div class="heading"><div><h2>商城业务模块</h2><p>设置邀请、余额交易、多商户、推广资格、秒杀、复购与优惠券。直接推荐佣金比例与范围在客户奖金接入中配置。</p></div></div>
    <el-alert title="邀请关系记录订单归属，推广资格决定谁可以获得佣金；佣金规则独立配置。普通顾客购物可给符合资格的邀请人计佣，无需自动升级。" type="info" :closable="false" show-icon />
    <el-card v-loading="loading" shadow="never">
      <el-form :model="form" label-position="left" label-width="132px" :disabled="loading || saving || !form.id">
        <CustomerModeSettings :model="form" :can-edit="canEditInvitation" />
        <section><h3>资金与商户</h3>
          <el-form-item label="余额新交易" class="toggle-row"><span class="toggle-state">{{ Number(form.balanceTransactionsEnabled) === 1 ? '已开启' : '已关闭' }}</span><el-switch v-model="form.balanceTransactionsEnabled" aria-label="启用余额新交易" :active-value="1" :inactive-value="0" /></el-form-item>
          <p>关闭后停止新的余额下单、付款、转账和人工加款；历史余额、原路退款、奖金入账、提现退出及流水追溯不删除。关闭前请核对待支付余额订单。</p>
          <el-form-item label="多商户新业务" class="toggle-row"><span class="toggle-state">{{ Number(form.multiMerchantEnabled) === 1 ? '已开启' : '仅平台自营' }}</span><el-switch v-model="form.multiMerchantEnabled" aria-label="启用多商户新业务" :active-value="1" :inactive-value="0" /></el-form-item>
          <p>关闭后原商户商品从前台隐藏，不能新上架或下单；商品和商户记录不删除。切换前创建的订单仍可支付、退款、履约、结算与审计。</p>
        </section>
        <section v-if="!form.businessMode"><h3>邀请与推广资格</h3>
          <el-form-item label="邀请功能" class="toggle-row"><span class="toggle-state">{{ Number(form.invitationEnabled) === 1 ? '邀请开启（邀请码选填）' : '普通商城（无邀请）' }}</span><el-switch v-model="form.invitationEnabled" aria-label="启用邀请关系，注册无需邀请码" :active-value="1" :inactive-value="0" :disabled="!canEditInvitation" /></el-form-item>
          <p v-if="Number(form.invitationEnabled) === 1">邀请商城：已注册顾客分享公开页面时带本人邀请码；新顾客无需邀请码即可注册；分享链接自动带入邀请人。先注册后打开分享时，未绑定账号自动绑定一次；已经绑定的账号不更换邀请人。邀请关系不等于奖金资格。</p>
          <p v-else>普通商城模式：不显示新注册的邀请入口，新分享不带邀请码，旧邀请码也不能建立新的邀请关系；普通注册和购物不受影响，历史关系、订单和账务保留。</p>
          <p v-if="!canEditInvitation">调整邀请功能需要商城设置权限。</p>
          <el-form-item label="默认绑定主账号" class="toggle-row"><el-switch v-model="form.defaultInviterEnabled" :active-value="1" :inactive-value="0" :disabled="!canEditInvitation" /></el-form-item>
          <el-form-item v-if="Number(form.defaultInviterEnabled) === 1" label="主账号邀请码"><el-input v-model="form.defaultInviterCode" maxlength="8" placeholder="指定普通商城账号的8位本人邀请码" :disabled="!canEditInvitation" /></el-form-item>
          <p>仅邀请功能开启时生效：有效分享优先；无分享的新注册账号默认绑定指定主账号。关闭则暂时无邀请人。默认绑定后不再换上级，不批量绑定旧账号；不能指定后台管理员或系统资金账号。</p>
          <el-form-item label="推广资格开通">
            <el-radio-group v-model="form.promotionJoinMode" class="mode-options">
              <el-radio-button value="DISABLED">关闭</el-radio-button>
              <el-radio-button value="AUTO_ON_INVITE">受邀即开通</el-radio-button>
              <el-radio-button value="MANUAL_REVIEW">后台审核</el-radio-button>
              <el-radio-button value="FIRST_PAID_ORDER">首笔有效订单</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <p>推广资格与邀请开关独立：邀请商城的已注册购物账号可邀请他人，只有取得推广资格的邀请人才能获得佣金；购买者可保持普通客户。</p>
          <p>新客户基座默认关闭邀请、采用后台审核开通推广资格；需要普通商城时同时关闭推广资格和佣金，需要邀请商城时再开启邀请并配置佣金。</p>
          <p v-if="form.promotionJoinMode === 'DISABLED'">不自动开通推广资格；邀请功能开启时仍可保留一次性邀请关系。</p>
          <p v-else-if="form.promotionJoinMode === 'AUTO_ON_INVITE'">邀请功能开启时，用户在注册时绑定邀请人，或先注册后首次通过有效分享绑定邀请人，都会立即开通基础推广资格；无分享的新注册按已启用的默认主账号配置首次绑定时也适用。邀请功能关闭时不产生新的受邀开通。</p>
          <p v-else-if="form.promotionJoinMode === 'MANUAL_REVIEW'">购物不会自动开通推广资格。管理员审核客户要求的资料后，在会员管理中明确开通。</p>
          <el-alert v-else title="这是老商城兼容方式。请确认客户业务及合规要求确实以购买作为资格条件，再用于新客户。" type="error" :closable="false" show-icon />
        </section>
        <section><h3>限时秒杀</h3>
          <el-form-item label="秒杀专区" class="toggle-row"><span class="toggle-state">{{ Number(form.flashSaleEnabled) === 1 ? '已开启' : '已关闭' }}</span><el-switch v-model="form.flashSaleEnabled" aria-label="启用秒杀专区" :active-value="1" :inactive-value="0" /></el-form-item>
          <el-form-item label="秒杀奖金处理"><el-radio-group v-model="form.flashSaleBonusMode"><el-radio-button value="NONE">不计奖</el-radio-button><el-radio-button value="STANDARD">按普通商城奖金规则</el-radio-button></el-radio-group></el-form-item>
          <p>活动价格、库存、开始结束时间和每人限购在“营销运营 → 秒杀活动”中维护。</p>
        </section>
        <section><h3>会员复购区</h3>
          <el-form-item label="复购区" class="toggle-row"><span class="toggle-state">{{ Number(form.repurchaseMallEnabled) === 1 ? '已开启' : '已关闭' }}</span><el-switch v-model="form.repurchaseMallEnabled" aria-label="启用复购区" :active-value="1" :inactive-value="0" /></el-form-item>
          <el-form-item label="进入资格"><el-radio-group v-model="form.repurchaseEligibilityMode"><el-radio-button value="PAID_MEMBER">已开通推广资格</el-radio-button><el-radio-button value="AGENT">代理及以上</el-radio-button><el-radio-button value="ALL_MEMBER">全部注册会员</el-radio-button></el-radio-group></el-form-item>
          <el-form-item label="复购奖金处理"><el-radio-group v-model="form.repurchaseBonusMode"><el-radio-button value="NONE">不计奖</el-radio-button><el-radio-button value="STANDARD">按复购奖金规则</el-radio-button></el-radio-group></el-form-item>
          <p>复购商品池、复购价、复购PV和复购限购在商品编辑页的“销售渠道”中维护。</p>
        </section>
        <section><h3>优惠券</h3>
          <el-form-item label="优惠券模块" class="toggle-row"><span class="toggle-state">{{ Number(form.couponEnabled) === 1 ? '已开启' : '已关闭' }}</span><el-switch v-model="form.couponEnabled" aria-label="启用优惠券模块" :active-value="1" :inactive-value="0" /></el-form-item>
          <p>关闭后停止新建、发行、领券及新订单用券；已发券和历史用券订单保留查询，支付核销、取消与退款逆流程仍照原订单规则处理。重新开启不会延长旧券有效期。</p>
        </section>
      </el-form>
      <template #footer><div class="footer"><span>{{ changes.length ? `有 ${changes.length} 项未保存` : '暂无未保存的修改' }}</span><div><el-button :disabled="!changes.length || saving" @click="reset">撤销修改</el-button><el-button type="primary" :disabled="!form.id || !changes.length || loading" :loading="saving" @click="save">保存设置</el-button></div></div></template>
    </el-card>
  </div>
</template>
<script setup>
import CustomerModeSettings from '@/components/CustomerModeSettings.vue'
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTenantBusinessModes, saveTenantBusinessModes } from '@/api/tenant'
import { useUnsavedChanges } from '@/composables/useUnsavedChanges'
import { businessModeChanges } from '@/utils/businessModeChanges'
import { useAppStore } from '@/store'
const store=useAppStore()
const canEditInvitation=computed(()=>store.hasPermission('config:shop'))
const loading=ref(false);const saving=ref(false);const form=ref({invitationEnabled:0,defaultInviterEnabled:0,defaultInviterCode:'',balanceTransactionsEnabled:1,multiMerchantEnabled:1,promotionJoinMode:'MANUAL_REVIEW',flashSaleEnabled:0,flashSaleBonusMode:'NONE',repurchaseMallEnabled:0,repurchaseEligibilityMode:'PAID_MEMBER',repurchaseBonusMode:'NONE',couponEnabled:1})
const snapshot = ref(null)
const visibleBonusMode = (value) => ['STANDARD', 'CUSTOM'].includes(String(value || '').toUpperCase()) ? 'STANDARD' : 'NONE'
const changes = computed(() => snapshot.value ? businessModeChanges(snapshot.value, form.value) : [])
useUnsavedChanges(computed(() => changes.value.length > 0))
const reset = () => { if (snapshot.value) form.value = JSON.parse(JSON.stringify(snapshot.value)) }
const load = async () => {
  loading.value = true
  try {
    const res = await getTenantBusinessModes(1)
    const row = res.data
    if (row) {
      form.value = { ...form.value, ...row, flashSaleBonusMode: visibleBonusMode(row.flashSaleBonusMode), repurchaseBonusMode: visibleBonusMode(row.repurchaseBonusMode) }
      snapshot.value = JSON.parse(JSON.stringify(form.value))
    }
  } finally { loading.value = false }
}
const save = async () => {
  if (!form.value.id || !changes.value.length || saving.value) return
  const payload = JSON.parse(JSON.stringify(form.value))
  payload.expectedModeRevision = snapshot.value.modeRevision
  payload.defaultInviterCode = String(payload.defaultInviterCode || '').trim().toUpperCase()
  const summary = businessModeChanges(snapshot.value, payload)
  saving.value = true
  try {
    try {
      await ElMessageBox.confirm(
        summary.map(item => `${item.title}：${item.before} → ${item.after}`).join('\n') + '\n\n可能影响新邀请关系、新余额交易、新商户业务、会员资格、商品可购买范围、优惠券领取使用及奖金处理。旧订单和历史资金仍需按原流程处理，请核对后保存。',
        '确认业务规则变更', { type:'warning', confirmButtonText:'确认并保存', cancelButtonText:'返回修改', customClass:'settings-impact-confirm' },
      )
    } catch { return }
    const result = await saveTenantBusinessModes(payload.id, payload)
    const saved = result?.data?.id ? result.data : payload
    const hasNewEdits = businessModeChanges(payload, form.value).length > 0
    snapshot.value = JSON.parse(JSON.stringify(saved))
    if (!hasNewEdits) form.value = saved
    else {
      form.value.modeRevision = saved.modeRevision
      form.value.agencyConfigStatus = saved.agencyConfigStatus
      form.value.modeChangeAllowed = saved.modeChangeAllowed
    }
    window.dispatchEvent(new Event('customer-business-mode-changed'))
    ElMessage.success('业务模式已保存')
  } finally { saving.value = false }
}
onMounted(load)
</script>
<style scoped>
.toggle-row :deep(.el-form-item__content){justify-content:flex-end;gap:12px}.toggle-state{color:var(--admin-muted);font-size:13px}
@media(max-width:1200px){:deep(.el-form-item:not(.toggle-row)){flex-direction:column;align-items:stretch}:deep(.el-form-item:not(.toggle-row)>.el-form-item__label){width:auto!important;justify-content:flex-start}:deep(.el-form-item:not(.toggle-row)>.el-form-item__content){margin-left:0!important}}
.heading{margin-bottom:24px}.heading h2{margin:0 0 10px;font-size:22px}.heading p,section p{color:var(--admin-muted);font-size:13px;line-height:1.8}.el-alert{margin-bottom:24px}section{padding:8px 0 24px}section+section{border-top:1px solid var(--admin-border);padding-top:24px}section h3{font-size:16px;margin:0 0 20px;font-weight:600}.mode-options{display:flex;flex-wrap:wrap}.footer{display:flex;align-items:center;justify-content:space-between;gap:16px}.footer>span{font-size:13px;color:var(--admin-muted)}.footer>div{display:flex;gap:10px}.footer .el-button+.el-button{margin-left:0}:deep(.el-card){border:0}:deep(.el-card__body){padding:0}:deep(.el-card__footer){padding:20px 0}:deep(.el-form-item__label){font-weight:500}:deep(.el-radio-group){display:flex;flex-wrap:wrap;row-gap:8px}:deep(.el-radio-button__inner){white-space:normal;line-height:1.5}:global(.settings-impact-confirm .el-message-box__message p){white-space:pre-line;line-height:1.8}@media(max-width:540px){.footer{align-items:flex-start;flex-direction:column}}
</style>
