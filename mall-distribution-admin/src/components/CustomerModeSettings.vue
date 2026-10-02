<template>
  <section class="customer-mode-settings">
    <h3>商城经营模式</h3>
    <p v-if="!model.businessMode">当前沿用存量客户配置。选择新模式不会改写历史关系和账务；已有业务数据时需要客户转换方案。</p>
    <el-form-item label="经营模式">
      <el-radio-group v-model="model.businessMode" :disabled="!canEdit || model.modeChangeAllowed === false">
        <el-radio-button value="NORMAL">普通商城</el-radio-button>
        <el-radio-button value="AGENCY">直销／代理模式</el-radio-button>
      </el-radio-group>
    </el-form-item>
    <p v-if="model.modeChangeAllowed === false">已有账号、订单或奖金责任，当前不能直接切换模式；可继续维护原配置。</p>
    <p v-if="model.businessMode === 'NORMAL'">商家直接销售，都是顾客；不开放代理招募、代理资格和新的代理奖励。</p>
    <template v-if="model.businessMode === 'AGENCY'">
      <el-alert title="需按客户购买门槛取得代理资格。以下只保存规则草稿，金额、商品、开通方式和奖励制度由具体客户确定；填满也不会启用。" type="info" :closable="false" />
      <p role="status">配置状态：{{ statusText }}</p>
      <p v-if="model.agencyConfigStatus?.missingItems?.length">待客户确定：{{ model.agencyConfigStatus.missingItems.join('、') }}</p>
      <el-button v-if="!model.agencyRuleDraft" :disabled="!canEdit" @click="model.agencyRuleDraft = {}">记录客户规则草稿</el-button>
      <template v-else>
        <el-form-item label="购买门槛种类"><el-select v-model="model.agencyRuleDraft.thresholdType" clearable placeholder="待客户确定" :disabled="!canEdit"><el-option value="SINGLE_ORDER" label="单笔实付满额" /><el-option value="CUMULATIVE" label="累计实付满额" /><el-option value="SPECIFIED_PRODUCTS" label="购买指定商品" /></el-select></el-form-item>
        <el-form-item v-if="model.agencyRuleDraft.thresholdType !== 'SPECIFIED_PRODUCTS'" label="购买金额"><el-input-number v-model="model.agencyRuleDraft.thresholdAmount" :min="0.01" :max="9999999999.99" :precision="2" placeholder="待客户确定" :disabled="!canEdit" /></el-form-item>
        <el-form-item v-else label="指定商品"><el-select v-model="model.agencyRuleDraft.productIds" multiple filterable allow-create default-first-option placeholder="填写客户指定商品编号，仅作草稿" :disabled="!canEdit" @change="normalizeProducts" /></el-form-item>
        <el-form-item label="资格开通方式"><el-select v-model="model.agencyRuleDraft.openingMethod" clearable placeholder="待客户确定" :disabled="!canEdit"><el-option value="AUTOMATIC" label="达标自动开通" /><el-option value="MANUAL_REVIEW" label="达标后后台审核" /></el-select></el-form-item>
        <el-form-item label="目标资格等级"><el-input-number v-model="model.agencyRuleDraft.targetLevel" :min="1" :max="8" :precision="0" placeholder="待客户确定" :disabled="!canEdit" /></el-form-item>
        <el-form-item v-for="item in notes" :key="item.key" :label="item.label"><el-input v-model="model.agencyRuleDraft[item.key]" type="textarea" maxlength="1000" show-word-limit placeholder="待具体客户确定，仅作草稿" :disabled="!canEdit" /></el-form-item>
      </template>
    </template>
  </section>
</template>
<script setup>
import { computed } from 'vue'
const props = defineProps({ model: { type: Object, required: true }, canEdit: Boolean })
const notes = [{ key: 'attributionRule', label: '邀请与代理归属' }, { key: 'refundRule', label: '退款后资格处理' }, { key: 'rewardPolicy', label: '客户奖励制度' }]
const labels = { LEGACY: '沿用存量配置', NOT_REQUIRED: '无需代理规则', NOT_CONFIGURED: '未配置', INCOMPLETE: '草稿不完整', PENDING_IMPLEMENTATION: '草稿已记录，待客户实现与验证' }
const statusText = computed(() => ['LEGACY', 'NOT_REQUIRED'].includes(props.model.agencyConfigStatus?.state) ? '待保存并检查客户规则缺项' : labels[props.model.agencyConfigStatus?.state] || '未配置；保存后检查缺项')
const normalizeProducts = (values) => { props.model.agencyRuleDraft.productIds = values.map(value => String(value).trim()).filter(value => /^[1-9]\d{0,18}$/.test(value) && BigInt(value) <= 9223372036854775807n) }
</script>
<style scoped>
h3{font-size:16px}p{font-size:13px;color:var(--admin-muted);line-height:1.8}.el-alert{margin-bottom:16px}
</style>
