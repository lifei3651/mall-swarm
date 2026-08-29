<template>
  <div class="page-container business-mode-page">
    <div class="heading"><div><h2>团队、秒杀与复购模式</h2><p>这里只选择业务进入方式，不填写客户奖金名称、层级、比例或计算公式。</p></div><el-tag type="warning" effect="plain">新客户默认关闭</el-tag></div>
    <el-alert title="邀请关系只记录谁邀请了谁，推广资格决定该账号是否进入客户团队制度，两者已经分开。公开商城不会展示奖金制度。" type="warning" :closable="false" show-icon />
    <el-card v-loading="loading" shadow="never">
      <el-form :model="form" label-width="145px">
        <section><h3>推广资格</h3>
          <el-form-item label="开通方式">
            <el-radio-group v-model="form.promotionJoinMode" class="mode-options">
              <el-radio-button value="DISABLED">关闭</el-radio-button>
              <el-radio-button value="AUTO_ON_INVITE">受邀即开通</el-radio-button>
              <el-radio-button value="MANUAL_REVIEW">后台审核</el-radio-button>
              <el-radio-button value="FIRST_PAID_ORDER">首笔有效订单</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <p v-if="form.promotionJoinMode === 'DISABLED'">只保留一次性邀请关系，不自动产生推广身份；适合尚未完成客户制度开发的新项目。</p>
          <p v-else-if="form.promotionJoinMode === 'AUTO_ON_INVITE'">用户扫码注册或在团队端首次绑定有效邀请人后，立即开通基础推广资格。</p>
          <p v-else-if="form.promotionJoinMode === 'MANUAL_REVIEW'">系统先保存邀请关系，管理员审核客户要求的资料后，再在会员管理中开通。</p>
          <el-alert v-else title="这是老商城兼容方式。请确认客户业务及合规要求确实以购买作为资格条件，再用于新客户。" type="error" :closable="false" show-icon />
        </section>
        <section><h3>限时秒杀</h3>
          <el-form-item label="启用秒杀专区"><el-switch v-model="form.flashSaleEnabled" :active-value="1" :inactive-value="0" /></el-form-item>
          <el-form-item label="秒杀奖金处理"><el-radio-group v-model="form.flashSaleBonusMode"><el-radio-button value="NONE">不计奖</el-radio-button><el-radio-button v-if="form.flashSaleBonusMode === 'STANDARD'" value="STANDARD" disabled>历史兼容状态</el-radio-button><el-radio-button value="CUSTOM">客户奖金程序（未接入禁下单）</el-radio-button></el-radio-group></el-form-item>
          <p>活动价格、库存、开始结束时间和每人限购在“商品与库存 → 秒杀活动”中维护。</p>
        </section>
        <section><h3>会员复购商城</h3>
          <el-form-item label="启用复购商城"><el-switch v-model="form.repurchaseMallEnabled" :active-value="1" :inactive-value="0" /></el-form-item>
          <el-form-item label="进入资格"><el-radio-group v-model="form.repurchaseEligibilityMode"><el-radio-button value="PAID_MEMBER">已开通推广资格</el-radio-button><el-radio-button value="AGENT">代理及以上</el-radio-button><el-radio-button value="ALL_MEMBER">全部注册会员</el-radio-button></el-radio-group></el-form-item>
          <el-form-item label="复购奖金处理"><el-radio-group v-model="form.repurchaseBonusMode"><el-radio-button value="NONE">不计奖</el-radio-button><el-radio-button v-if="form.repurchaseBonusMode === 'STANDARD'" value="STANDARD" disabled>历史兼容状态</el-radio-button><el-radio-button value="CUSTOM">客户奖金程序（未接入禁下单）</el-radio-button></el-radio-group></el-form-item>
          <p>复购商品池、复购价、复购PV和复购限购在商品编辑页的“销售渠道”中维护。</p>
        </section>
      </el-form>
      <template #footer><div class="footer"><el-button type="primary" :loading="saving" @click="save">保存业务模式</el-button></div></template>
    </el-card>
  </div>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listTenants, saveTenant } from '@/api/tenant'
const loading=ref(false);const saving=ref(false);const form=ref({promotionJoinMode:'DISABLED',flashSaleEnabled:0,flashSaleBonusMode:'NONE',repurchaseMallEnabled:0,repurchaseEligibilityMode:'PAID_MEMBER',repurchaseBonusMode:'NONE'})
const load=async()=>{loading.value=true;try{const res=await listTenants({pageNum:1,pageSize:100});const row=(res.data?.list||[]).find(item=>Number(item.id)===1)||(res.data?.list||[])[0];if(row)form.value={...form.value,...row}}finally{loading.value=false}}
const save=async()=>{if(!form.value.id)return; saving.value=true;try{await saveTenant(form.value);ElMessage.success('业务模式已保存，前台入口已同步');await load()}finally{saving.value=false}}
onMounted(load)
</script>
<style scoped>
.heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px}.heading h2{margin:0;font-size:22px}.heading p,section p{color:#909399;font-size:13px;line-height:1.7}.el-alert{margin-bottom:16px}section{padding:8px 0 20px}section+section{border-top:1px solid #ebeef5;padding-top:24px}section h3{padding-left:10px;border-left:4px solid var(--el-color-primary)}.mode-options{display:flex;flex-wrap:wrap}.footer{text-align:right}@media(max-width:800px){:deep(.el-radio-group){display:flex;flex-wrap:wrap}}
</style>
