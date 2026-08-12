<template>
  <div class="page-container flash-admin-page">
    <div class="heading"><div><h2>秒杀活动</h2><p>活动库存是抢购资格库存，成交时仍会二次扣减商品实物库存，避免超卖。</p></div><el-button type="primary" @click="open()">新建活动</el-button></div>
    <el-alert title="高并发保护已开启：同一会员防重复、入口限流、Redis原子抢占、数据库原子扣减。待付款订单取消或超时后会自动释放活动库存和商品库存。" type="success" :closable="false" show-icon />
    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column label="活动/商品" min-width="270"><template #default="{row}"><strong>{{row.activity.activityName}}</strong><div class="sub">{{row.product?.productName}}<template v-if="row.sku"> · {{row.sku.skuName}}</template></div></template></el-table-column>
      <el-table-column label="秒杀价" width="110"><template #default="{row}">¥{{row.activity.flashPrice}}</template></el-table-column>
      <el-table-column label="库存" width="130"><template #default="{row}">{{row.activity.availableStock}} / {{row.activity.totalStock}}</template></el-table-column>
      <el-table-column label="每人限购" width="100"><template #default="{row}">{{row.activity.perUserLimit}} 件</template></el-table-column>
      <el-table-column label="活动时间" min-width="260"><template #default="{row}">{{formatTime(row.activity.startTime)}}<br/>至 {{formatTime(row.activity.endTime)}}</template></el-table-column>
      <el-table-column label="状态" width="105"><template #default="{row}"><el-tag :type="stateType(row.activityState)">{{stateLabel(row.activityState)}}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="190" fixed="right"><template #default="{row}"><el-button link type="primary" :disabled="row.activity.availableStock!==row.activity.totalStock" @click="open(row)">编辑</el-button><el-button link :type="row.activity.status===1?'warning':'success'" @click="toggle(row)">{{row.activity.status===1?'停用':'启用'}}</el-button></template></el-table-column>
    </el-table>
    <el-dialog v-model="visible" :title="form.id?'编辑秒杀活动':'新建秒杀活动'" width="720px" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-form-item label="活动名称" required><el-input v-model="form.activityName" maxlength="80" /></el-form-item>
        <el-form-item label="活动商品" required><el-select v-model="form.productId" filterable style="width:100%" @change="productChanged"><el-option v-for="p in products" :key="p.id" :label="p.productName" :value="p.id" /></el-select></el-form-item>
        <el-form-item label="指定规格"><el-select v-model="form.skuId" clearable style="width:100%"><el-option v-for="s in skus" :key="s.id" :label="`${s.skuName}（库存 ${s.stock}，售价 ¥${s.salePrice}）`" :value="s.id" /></el-select><div class="help">多规格商品必须指定SKU；单规格商品留空。</div></el-form-item>
        <el-row :gutter="16"><el-col :span="8"><el-form-item label="秒杀价" required><el-input-number v-model="form.flashPrice" :min="0.01" :precision="2" style="width:100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="秒杀PV"><el-input-number v-model="form.flashPv" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="活动库存" required><el-input-number v-model="form.totalStock" :min="1" :step="1" style="width:100%" /></el-form-item></el-col></el-row>
        <el-form-item label="每人限购" required><el-input-number v-model="form.perUserLimit" :min="1" :step="1" /></el-form-item>
        <el-form-item label="活动时间" required><el-date-picker v-model="timeRange" type="datetimerange" start-placeholder="开始时间" end-placeholder="结束时间" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></el-form-item>
        <el-form-item label="保存后状态"><el-radio-group v-model="form.status"><el-radio-button :value="0">草稿</el-radio-button><el-radio-button :value="1">启用</el-radio-button><el-radio-button :value="2">停用</el-radio-button></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存活动</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listFlashSales, listShopProducts, listShopSkus, saveFlashSale, updateFlashSaleStatus } from '@/api/shop'
const loading=ref(false);const saving=ref(false);const rows=ref([]);const products=ref([]);const skus=ref([]);const visible=ref(false);const timeRange=ref([])
const defaults=()=>({id:null,activityName:'',productId:null,skuId:null,flashPrice:0.01,flashPv:0,totalStock:1,perUserLimit:1,status:0})
const form=ref(defaults());const formatTime=v=>v?String(v).replace('T',' ').slice(0,16):'-';const stateLabel=s=>({UPCOMING:'未开始',ACTIVE:'进行中',SOLD_OUT:'已抢完',ENDED:'已结束',DISABLED:'未启用'}[s]||s);const stateType=s=>({ACTIVE:'success',UPCOMING:'primary',SOLD_OUT:'danger',ENDED:'info',DISABLED:'warning'}[s]||'info')
const load=async()=>{loading.value=true;try{const [a,p]=await Promise.all([listFlashSales(),listShopProducts({status:1,pageNum:1,pageSize:100})]);rows.value=a.data||[];products.value=p.data?.list||[]}finally{loading.value=false}}
const productChanged=async id=>{form.value.skuId=null;skus.value=[];if(id){const res=await listShopSkus(id,{status:1});skus.value=res.data||[]}}
const open=async row=>{form.value=row?{...row.activity}:defaults();timeRange.value=row?[row.activity.startTime,row.activity.endTime]:[];await productChanged(form.value.productId);if(row)form.value.skuId=row.activity.skuId;visible.value=true}
const submit=async()=>{if(!form.value.activityName.trim()||!form.value.productId||timeRange.value.length!==2)return ElMessage.warning('请完整填写活动名称、商品和活动时间');saving.value=true;try{await saveFlashSale(form.value.id,{...form.value,startTime:timeRange.value[0],endTime:timeRange.value[1]});ElMessage.success('秒杀活动已保存');visible.value=false;await load()}finally{saving.value=false}}
const toggle=async row=>{await updateFlashSaleStatus(row.activity.id,row.activity.status===1?2:1);ElMessage.success('活动状态已更新');await load()}
onMounted(load)
</script>
<style scoped>
.heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px}.heading h2{margin:0;font-size:22px}.heading p,.sub,.help{margin:6px 0;color:#909399;font-size:12px}.el-alert{margin-bottom:16px}.help{width:100%}
</style>
