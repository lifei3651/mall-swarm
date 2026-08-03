<template>
  <div class="page-container bonus-config">
    <div class="toolbar">
      <el-tag size="large" type="info">当前商城</el-tag>
      <el-select v-model="selectedVersionId" placeholder="选择结算版本" filterable class="version-select">
        <el-option v-for="item in versions" :key="item.id" :label="`${item.versionName} (${item.versionNo})`" :value="item.id" />
      </el-select>
    </div>

    <el-alert title="当前新零售奖金方案已固化在底层代码中。所有奖金从订单确认收货起经过7天保护期，统一进入会员余额。" type="warning" :closable="false" show-icon class="page-alert" />

    <el-tabs v-model="activeTab">
      <el-tab-pane label="固定奖金方案" name="plan">
        <div class="plan-header"><div><h3>新零售正式方案</h3><p>每件有效商品计 1 单，累计本人及无限层团队；直推只认第一代，奖金按订单实付金额计算。</p></div><el-tag type="success" size="large">代码固化 · 已启用</el-tag></div>
        <el-table :data="rankPlan" border style="width:100%">
          <el-table-column prop="rank" label="会员卡级" width="130" />
          <el-table-column prop="condition" label="自动升级条件" min-width="360" />
          <el-table-column prop="rate" label="直推奖比例" width="140" align="center"><template #default="{ row }"><strong class="rate">{{ row.rate }}</strong></template></el-table-column>
          <el-table-column prop="dividend" label="无限层团队订单分红" width="190" align="center" />
        </el-table>
        <el-alert class="plan-note" type="info" :closable="false" title="触发升级的订单仍按支付前卡级计奖；该订单完成升级后，后续所有新订单才按新卡级计奖。退款会冲减件数和业绩并允许降级；移线不调级。" />
      </el-tab-pane>

      <el-tab-pane label="奖金验证器" name="simulate">
        <el-alert title="验证器只预览计算结果，不创建订单、不入账；真实全流程测试仍需通过商城正常下单付款。" type="info" :closable="false" class="page-alert" />
        <el-form :inline="true" :model="simulateForm">
          <el-form-item label="下单会员账号"><el-input v-model="simulateForm.orderMemberKey" placeholder="登录账号/手机号" /></el-form-item>
          <el-form-item label="订单实付金额"><el-input-number v-model="simulateForm.orderAmount" :min="0" :precision="2" /></el-form-item>
          <el-form-item><el-button type="primary" @click="submitSimulation">验证计算</el-button></el-form-item>
        </el-form>
        <el-alert v-if="simulationFeedback" :title="simulationFeedback" type="warning" :closable="false" show-icon class="simulation-feedback" />
        <el-table :data="simulationResult.receivers || []" empty-text="该会员在当前规则下没有可展示的奖金接收结果" style="width:100%">
          <el-table-column prop="memberAccount" label="会员账号" width="145" /><el-table-column prop="agentName" label="获奖会员" width="140" /><el-table-column prop="bonusName" label="奖金类型" width="170" /><el-table-column prop="relationLevel" label="关系深度" width="95" />
          <el-table-column prop="rate" label="奖金比例" width="100"><template #default="{ row }">{{ percent(row.rate) }}</template></el-table-column><el-table-column prop="bonusAmount" label="奖金金额" width="120" />
          <el-table-column label="奖金入账" min-width="180"><template #default="{ row }"><el-tag>余额 {{ money(row.bonusAmount) }}</el-tag></template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { simulateBonus } from '@/api/bonusConfig'
import { listRuleVersions } from '@/api/tenant'
import { memberSearchFailureMessage, validateMemberSearch } from '@/utils/searchFeedback'

const versions = ref([])
const selectedTenantId = ref(1)
const selectedVersionId = ref(null)
const activeTab = ref('plan')
const simulateForm = ref({ orderAmount: 0 })
const simulationResult = ref({})
const simulationFeedback = ref('')

const rankPlan = [
  { rank: '会员', condition: '完成首笔有效支付订单', rate: '25%', dividend: '—' },
  { rank: 'VIP会员', condition: '本人及无限层团队累计 10 件有效商品', rate: '30%', dividend: '—' },
  { rank: '店铺', condition: '直推 5 名有效会员，且本人及无限层团队累计 50 件有效商品', rate: '37%', dividend: '—' },
  { rank: '代理', condition: '直推至少 3 名 VIP（卡级别），且本人及无限层团队累计 150 件有效商品', rate: '45%', dividend: '—' },
  { rank: '一星董事', condition: '2 个独立直属部门各有代理（卡级别），且本人及无限层团队累计 500 件有效商品', rate: '52%', dividend: '5%' },
  { rank: '二星董事', condition: '2 个独立部门各有一星董事（卡级别）', rate: '57%', dividend: '4%' },
  { rank: '三星董事', condition: '2 个独立部门各有二星董事（卡级别）', rate: '61%', dividend: '3%' },
  { rank: '合伙人', condition: '2 个独立部门各有三星董事（卡级别）', rate: '65%', dividend: '2%' },
]

const fetchBaseData = async () => {
  await handleTenantChange()
}

const handleTenantChange = async () => {
  if (!selectedTenantId.value) return
  const versionRes = await listRuleVersions(selectedTenantId.value)
  versions.value = versionRes.data || []
  selectedVersionId.value = versions.value[0]?.id || null
}

const submitSimulation = async () => {
  const validation = validateMemberSearch(simulateForm.value.orderMemberKey, { required: true })
  if (!validation.valid) {
    simulationResult.value = {}
    simulationFeedback.value = validation.message
    return
  }
  if (!simulateForm.value.orderAmount || Number(simulateForm.value.orderAmount) <= 0) {
    simulationFeedback.value = '请输入大于0的订单实付金额'
    return
  }
  simulationFeedback.value = ''
  simulateForm.value.orderMemberKey = validation.keyword
  try {
    const res = await simulateBonus({ tenantId:selectedTenantId.value, ruleVersionId:selectedVersionId.value, ...simulateForm.value })
    simulationResult.value = res.data || {}
  } catch (error) {
    simulationResult.value = {}
    simulationFeedback.value = memberSearchFailureMessage(error, validation.keyword, '奖金验证')
  }
}
const percent = (value) => `${(Number(value || 0) * 100).toFixed(2)}%`
const money = (value) => Number(value || 0).toFixed(2)

onMounted(fetchBaseData)
</script>

<style scoped>
.bonus-config .toolbar,.tab-toolbar { display:flex; align-items:center; gap:12px; margin-bottom:16px; }
.tenant-select{width:240px}.version-select{width:280px}.page-alert{margin-bottom:16px}.plan-header{display:flex;align-items:center;justify-content:space-between;margin:4px 0 18px;padding:18px 22px;background:#f5f7fa;border-radius:8px}.plan-header h3{margin:0 0 7px;font-size:19px}.plan-header p{margin:0;color:#606266}.rate{color:#f56c6c;font-size:16px}.plan-note{margin-top:16px}.switch-grid{display:grid;grid-template-columns:repeat(2,minmax(260px,1fr));gap:4px 32px;max-width:920px}.wide-item{grid-column:1/-1}
.simulation-feedback{margin-bottom:16px}
</style>
