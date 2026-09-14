<template>
  <div class="page-container withdrawal-settings">
    <header><h2>余额与提现规则</h2><p>统一管理新提现申请、余额资格、收款渠道和打款方式。已有记录和在途申请不会被追溯改写。</p></header>
    <el-alert v-if="loadError" title="提现规则读取失败，本页没有保存任何更改" type="error" :closable="false" show-icon />
    <div v-loading="loading" class="setting-groups">
      <section><h3>服务状态</h3><SettingRow title="提现服务" description="关闭后仅阻止新申请，记录查询和在途处理仍保留。"><el-switch v-model="form.serviceEnabled" active-text="开启" inactive-text="关闭" /></SettingRow><SettingRow v-if="!form.serviceEnabled" title="会员可见说明" description="请说明暂停原因，不要填写内部技术信息。"><el-input v-model="form.disabledReason" maxlength="120" placeholder="例如：财务对账中，预计明日9:00恢复" /></SettingRow></section>
      <section><h3>资格与余额</h3><SettingRow title="余额持有人可申请" description="允许有余额但未开通推广身份的商城账号申请；不会自动授予推广资格。"><el-switch v-model="form.balanceHolderEnabled" active-text="允许" inactive-text="不允许" /></SettingRow><SettingRow title="后台人工增加余额" description="开启后，新的人工增加余额按当前规则参与提现；历史余额不自动改写。"><el-switch v-model="form.manualBalanceWithdrawable" active-text="允许提现" inactive-text="仅消费" /></SettingRow></section>
      <section><h3>收款与打款</h3><SettingRow title="财务线下打款" description="审核通过后，财务可线下转账，再到提现记录登记唯一流水号。"><el-switch v-model="form.offlinePayoutEnabled" active-text="开启" inactive-text="关闭" /></SettingRow><SettingRow title="银行卡收款" description="首期仅支持人工审核和财务线下转账，卡号在服务端加密保存并脱敏展示。"><el-switch v-model="form.bankCardEnabled" :disabled="!form.offlinePayoutEnabled" active-text="开启" inactive-text="关闭" /></SettingRow></section>
    </div>
    <footer><span>当前规则版本 {{ form.version }}</span><div><el-button :disabled="!dirty" @click="restore">撤销本页修改</el-button><el-button type="primary" :disabled="!dirty" :loading="saving" @click="save">保存规则</el-button></div></footer>
  </div>
</template>
<script setup>
import { computed, h, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getWithdrawalSettings, updateWithdrawalSettings } from '@/api/withdraw'
const SettingRow = {
  props: { title: String, description: String },
  setup(props, { slots }) {
    return () => h('div', { class: 'setting-row' }, [
      h('div', [h('strong', props.title), h('p', props.description)]),
      h('div', { class: 'setting-control' }, slots.default?.())
    ])
  }
}
const defaults = () => ({ serviceEnabled:true, disabledReason:'', balanceHolderEnabled:false, manualBalanceWithdrawable:false, bankCardEnabled:false, offlinePayoutEnabled:false, version:0 })
const form=ref(defaults()), original=ref(defaults()), loading=ref(false), saving=ref(false), loadError=ref(false)
const dirty=computed(()=>JSON.stringify(form.value)!==JSON.stringify(original.value))
const load=async()=>{loading.value=true;loadError.value=false;try{form.value={...defaults(),...(await getWithdrawalSettings()).data};original.value={...form.value}}catch{loadError.value=true}finally{loading.value=false}}
const restore=()=>{form.value={...original.value}}
const save=async()=>{if(!form.value.serviceEnabled&&!form.value.disabledReason.trim())return ElMessage.warning('请填写关闭提现服务的会员可见原因');if(form.value.bankCardEnabled&&!form.value.offlinePayoutEnabled)return ElMessage.warning('请先开启财务线下打款');let reason='';try{const result=await ElMessageBox.prompt('请填写本次调整原因。新规则只影响后续申请，会记入操作日志。','确认保存提现规则',{confirmButtonText:'确认保存',cancelButtonText:'返回检查',inputPattern:/\S{2,200}/,inputErrorMessage:'请填写2至200个字的调整原因'});reason=result.value.trim()}catch{return}saving.value=true;try{const data=(await updateWithdrawalSettings({...form.value,changeReason:reason})).data;form.value={...defaults(),...data};original.value={...form.value};ElMessage.success('提现规则已保存')}finally{saving.value=false}}
onMounted(load)
</script>
<style scoped>
header{padding-bottom:24px;border-bottom:1px solid var(--admin-border)}h2{margin:0 0 10px;font-size:22px}header p,.setting-row p{margin:0;color:var(--admin-muted);font-size:13px;line-height:1.75}.setting-groups section{padding:26px 0 8px}.setting-groups section+section{border-top:1px solid var(--admin-border)}h3{margin:0 0 8px;font-size:16px}.setting-row{display:grid;grid-template-columns:minmax(0,1fr) minmax(190px,320px);align-items:center;gap:32px;min-height:82px;padding:14px 0}.setting-row strong{display:block;margin-bottom:6px;font-size:14px}.setting-control{display:flex;justify-content:flex-end;min-width:0}.setting-control :deep(.el-input){width:100%}footer{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-top:22px;padding-top:20px;border-top:1px solid var(--admin-border)}footer span{color:var(--admin-muted);font-size:12px}footer div{display:flex;gap:10px}@media(max-width:700px){.setting-row{grid-template-columns:1fr;gap:10px}.setting-control{justify-content:flex-start}footer{align-items:flex-start;flex-direction:column}}
</style>
