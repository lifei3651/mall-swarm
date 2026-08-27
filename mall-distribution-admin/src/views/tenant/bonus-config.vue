<template>
  <div class="page-container customer-bonus-page">
    <div class="page-heading">
      <div><h2>客户奖金接入</h2><p>商城基座只管理接入状态、订单快照、资金结算和退款追回，不在这里编写客户制度。</p></div>
      <el-tag :type="statusMeta.type" size="large">{{ statusMeta.label }}</el-tag>
    </div>

    <el-alert
      title="每个客户项目使用独立奖金程序和独立服务器。新客户默认不计奖；制度开发、模拟和全流程验收完成后，才在该客户项目中启用。"
      type="warning" :closable="false" show-icon class="page-alert"
    />

    <el-card shadow="never" v-loading="loading" class="status-card">
      <template #header><div class="card-title"><strong>当前客户项目</strong><span>本页只读，避免运营人员误改资金规则</span></div></template>
      <div class="status-grid">
        <div><span>奖金程序状态</span><strong>{{ statusMeta.label }}</strong></div>
        <div><span>当前版本</span><strong>{{ activeVersion?.versionName || '未登记' }}</strong></div>
        <div><span>内部版本号</span><strong>{{ activeVersion?.versionNo || '-' }}</strong></div>
        <div><span>生效时间</span><strong>{{ activeVersion?.effectiveTime || '-' }}</strong></div>
      </div>
      <el-alert v-if="isLegacySample" type="error" :closable="false" show-icon
        title="当前仍登记为历史演示制度，只用于兼容既有测试数据。正式客户交付前必须替换为该客户独立奖金程序。" />
      <el-alert v-else-if="isDisabled" type="info" :closable="false" show-icon
        title="安全关闭状态下，普通商城仍可注册、下单、支付和售后，但不会产生客户奖金。" />
    </el-card>

    <div class="boundary-grid">
      <el-card shadow="never">
        <template #header><strong>商城基座统一负责</strong></template>
        <ul>
          <li>注册、邀请关系与支付时快照</li>
          <li>商品、订单、支付、发货和售后</li>
          <li>奖金结果落库、防重复、保护期结算</li>
          <li>部分退款、全额退款、追回欠款与审计</li>
        </ul>
      </el-card>
      <el-card shadow="never">
        <template #header><strong>客户独立项目负责</strong></template>
        <ul>
          <li>客户制度、等级、条件和计算口径</li>
          <li>普通、复购、报单等订单是否计奖</li>
          <li>奖金结果名称、接收人和金额</li>
          <li>客户制度变更后的新版本和生效时间</li>
        </ul>
      </el-card>
    </div>

    <el-card shadow="never" class="acceptance-card">
      <template #header><div class="card-title"><strong>接入验收门禁</strong><span>没有全部完成，不得开放真实客户奖金</span></div></template>
      <div class="checklist">
        <div v-for="item in checklist" :key="item"><el-icon><CircleCheck /></el-icon><span>{{ item }}</span></div>
      </div>
      <div class="actions">
        <el-button @click="router.push('/audit/orders')">查看订单资金追溯</el-button>
        <el-button @click="router.push('/commission/records')">查看已产生奖金记录</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { CircleCheck } from '@element-plus/icons-vue'
import { listRuleVersions } from '@/api/tenant'

const router = useRouter()
const loading = ref(false)
const versions = ref([])
const activeVersion = computed(() => versions.value.find(item => Number(item.status) === 1) || versions.value[0] || null)
const isDisabled = computed(() => activeVersion.value?.versionNo === 'CUSTOMER_BONUS_DISABLED' || !activeVersion.value)
const isLegacySample = computed(() => activeVersion.value?.versionNo === 'NEW_RETAIL_SIMPLE_DEFAULT')
const statusMeta = computed(() => {
  if (isLegacySample.value) return { label: '历史演示制度 · 待替换', type: 'danger' }
  if (isDisabled.value) return { label: '奖金未接入 · 安全关闭', type: 'info' }
  return { label: '客户程序已登记 · 待验收', type: 'warning' }
})
const checklist = [
  '客户制度原文已冻结，并完成歧义、循环条件和最高拨出风险审查',
  '客户独立奖金程序已接入，不修改商城通用订单、钱包和售后逻辑',
  '首购、普通购买、复购、部分退款和全额退款均有精确测试',
  '重复支付、重复回调、重复退款不会重复产生或追回奖金',
  '客户独立服务器、数据库、支付配置和发布回滚方案已完成验收',
]

const load = async () => {
  loading.value = true
  try { versions.value = (await listRuleVersions(1)).data || [] } finally { loading.value = false }
}
onMounted(load)
</script>

<style scoped>
.customer-bonus-page{max-width:1180px}.page-heading,.card-title{display:flex;align-items:center;justify-content:space-between;gap:18px}.page-heading{margin-bottom:16px}.page-heading h2{margin:0;font-size:22px}.page-heading p,.card-title span{margin:6px 0 0;color:#909399;font-size:13px}.page-alert,.status-card,.boundary-grid{margin-bottom:16px}.status-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin-bottom:16px}.status-grid>div{display:flex;flex-direction:column;gap:7px;padding:15px;background:#f7f9fc;border:1px solid #e8ecf2;border-radius:10px}.status-grid span{color:#909399;font-size:12px}.status-grid strong{overflow-wrap:anywhere;color:#303133}.boundary-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.boundary-grid ul{display:grid;gap:12px;margin:0;padding-left:20px;color:#606266;line-height:1.6}.checklist{display:grid;gap:11px}.checklist>div{display:flex;align-items:flex-start;gap:9px;color:#4b5563}.checklist .el-icon{margin-top:3px;color:#67c23a}.actions{display:flex;justify-content:flex-end;gap:10px;margin-top:20px}@media(max-width:900px){.status-grid{grid-template-columns:1fr 1fr}.boundary-grid{grid-template-columns:1fr}}@media(max-width:560px){.page-heading,.card-title{align-items:flex-start;flex-direction:column}.status-grid{grid-template-columns:1fr}.actions{align-items:stretch;flex-direction:column}}
</style>
