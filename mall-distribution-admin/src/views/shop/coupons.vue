<template>
  <div class="page-container coupon-admin">
    <div class="page-heading"><div><h2>优惠券</h2><p>平台统一发行，按商品范围抵扣；已发行规则不能修改。</p></div><el-button type="primary" :disabled="!canWrite || busy" @click="edit()">新建优惠券</el-button></div>
    <el-alert title="优惠券会影响商家结算与团队奖金。每次保存和发行前需确认承担比例、奖金及退款规则。" type="warning" :closable="false" show-icon />
    <p v-if="!canWrite" class="hint">配置需要商城配置、财务管理和奖金配置三项权限；商家账号不能发行平台优惠券。</p>
    <p v-if="error" role="alert">{{ error }} <el-button link @click="load">重试</el-button></p>
    <el-table :data="rows" v-loading="loading" class="coupon-table">
      <el-table-column label="优惠券" min-width="210"><template #default="{row}"><strong>{{row.title}}</strong><div class="hint">¥{{row.amount}} · 满 ¥{{row.minimumAmount}} 可用</div></template></el-table-column>
      <el-table-column label="适用范围" min-width="200"><template #default="{row}">{{row.merchantName}}<div class="hint">{{row.scopeType === 'ALL' ? '全部商品' : '指定商品'}}</div></template></el-table-column>
      <el-table-column label="承担与奖金" min-width="210"><template #default="{row}">平台 {{100-row.merchantPercent}}% / 商家 {{row.merchantPercent}}%<div class="hint">奖金按{{row.bonusBasis === 'NET' ? '优惠后' : '优惠前'}}商品金额</div></template></el-table-column>
      <el-table-column label="已领 / 总量" width="120"><template #default="{row}">{{row.issuedCount}} / {{row.totalCount}}</template></el-table-column>
      <el-table-column label="使用期限" min-width="190"><template #default="{row}">{{time(row.startsAt)}}<div class="hint">至 {{time(row.endsAt)}}</div></template></el-table-column>
      <el-table-column label="状态" width="90"><template #default="{row}">{{stateLabel[row.status] || row.status}}</template></el-table-column>
      <el-table-column label="操作" width="160" fixed="right"><template #default="{row}"><el-button link @click="edit(row)">{{row.status === 'DRAFT' && canWrite ? '编辑' : '详情'}}</el-button><el-button v-if="canWrite" link type="primary" :disabled="busy" @click="status(row)">{{row.status === 'PUBLISHED' ? '暂停领取' : '发行'}}</el-button></template></el-table-column>
    </el-table>
    <el-pagination class="pagination-container" layout="total, prev, pager, next" :total="total" :page-size="20" v-model:current-page="page" @current-change="load" />
    <el-dialog v-model="visible" title="优惠券配置" width="min(720px, 94vw)" :close-on-click-modal="false" :show-close="!busy" :close-on-press-escape="!busy">
      <el-form label-position="top" :disabled="busy || readonly" class="coupon-form">
        <el-form-item label="优惠券名称"><el-input v-model="form.title" maxlength="60" /></el-form-item>
        <div class="form-pair"><el-form-item label="面额（元）"><el-input-number v-model="form.amount" :min="0.01" :max="999999.99" :precision="2" /></el-form-item><el-form-item label="适用商品满多少元可用"><el-input-number v-model="form.minimumAmount" :min="0" :max="999999.99" :precision="2" /></el-form-item></div>
        <el-form-item label="适用商品归属"><el-select v-model="owner" @change="ownerChanged"><el-option label="平台自营商品" value="PLATFORM" /><el-option v-for="m in merchants" :key="m.id" :label="m.merchantName" :value="String(m.id)" /></el-select></el-form-item>
        <el-form-item label="商品范围"><el-radio-group v-model="form.scopeType" @change="form.productIds=[]"><el-radio value="ALL">所选归属的全部商品</el-radio><el-radio value="PRODUCTS">指定商品</el-radio></el-radio-group></el-form-item>
        <el-form-item v-if="form.scopeType === 'PRODUCTS'" label="适用商品（至少选一项）"><el-select v-model="form.productIds" multiple filterable remote :remote-method="findProducts" :loading="productsLoading" placeholder="搜索并选择商品" @visible-change="open => open && findProducts('')"><el-option v-for="p in products" :key="p.id" :label="p.productName || p.product_name || `商品 ${p.id}`" :value="String(p.id)" /></el-select></el-form-item>
        <el-form-item label="适用业务"><el-checkbox-group v-model="form.businessTypes"><el-checkbox value="NORMAL">普通商城</el-checkbox><el-checkbox value="REPURCHASE">复购商城</el-checkbox></el-checkbox-group></el-form-item>
        <el-form-item label="优惠承担方（必选）"><el-select v-model="funding" @change="fundingChanged"><el-option label="平台承担全部优惠" value="PLATFORM" /><el-option v-if="owner !== 'PLATFORM'" label="商家承担全部优惠" value="MERCHANT" /><el-option v-if="owner !== 'PLATFORM'" label="平台和商家共同承担" value="SHARED" /></el-select></el-form-item>
        <el-form-item v-if="funding === 'SHARED'" label="商家承担比例（%）"><el-input-number v-model="form.merchantPercent" :min="1" :max="99" :precision="0" /></el-form-item>
        <el-form-item label="团队奖金基数（必选）"><el-select v-model="form.bonusBasis"><el-option label="按优惠后的商品实付金额" value="NET" /><el-option label="按优惠前的商品金额" value="GROSS" /></el-select></el-form-item>
        <p class="hint">仅影响原本参与奖金的商品和业务，不会自动开通团队奖金。运费不参与满减和奖金。</p>
        <el-form-item label="退款后的退券规则（必选）"><el-select v-model="form.refundRule"><el-option label="用券子单全部退款成功后退券，原有效期不变" value="FULL_RETURN" /><el-option label="支付后退款不退券" value="NEVER" /></el-select></el-form-item>
        <div class="form-pair"><el-form-item label="使用开始时间"><el-date-picker v-model="form.startsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item><el-form-item label="使用结束时间"><el-date-picker v-model="form.endsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item></div>
        <div class="form-pair"><el-form-item label="发行总量"><el-input-number v-model="form.totalCount" :min="1" :max="1000000" :precision="0" /></el-form-item><el-form-item label="每人领取上限"><el-input-number v-model="form.perMemberLimit" :min="1" :max="10" :precision="0" /></el-form-item></div>
        <p class="hint">每次结算限用一张，不与秒杀叠加；适用商品金额必须高于券面额。取消未支付订单会释放券；部分退款不退券。商家承担金额不能超过该商品货款。</p>
      </el-form>
      <p v-if="formError" role="alert" class="form-error">{{formError}}</p>
      <template #footer><el-button :disabled="busy" @click="visible=false">关闭</el-button><el-button v-if="!readonly" type="primary" :loading="busy" @click="save">核对影响并保存草稿</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useAppStore } from '@/store'
import { listCoupons, couponProducts, couponMerchants, saveCoupon, changeCouponStatus } from '@/api/coupons'
const store=useAppStore()
const canWrite=computed(()=>!store.userInfo?.merchantId && ['config:shop','finance:manage','config:bonus'].every(p=>store.hasPermission(p)))
const rows=ref([]),total=ref(0),page=ref(1),loading=ref(false),busy=ref(false),error=ref(''),formError=ref('')
const visible=ref(false),readonly=ref(false),form=ref({}),owner=ref('PLATFORM'),funding=ref(''),merchants=ref([]),products=ref([]),productsLoading=ref(false)
const stateLabel={DRAFT:'草稿',PUBLISHED:'领取中',PAUSED:'已暂停'}
const time=v=>String(v||'').replace('T',' ').slice(0,16)
const parse=v=>{try{return JSON.parse(v||'[]')}catch{return []}}
let searchVersion=0,loadVersion=0
async function load(){const v=++loadVersion;loading.value=true;error.value='';try{const r=await listCoupons({pageNum:page.value,pageSize:20});if(v===loadVersion){rows.value=r.data?.list||[];total.value=r.data?.total||0}}catch(e){if(v===loadVersion)error.value=e.message||'优惠券读取失败'}finally{if(v===loadVersion)loading.value=false}}
function edit(row){
  if(busy.value)return
  formError.value='';readonly.value=!canWrite.value || Boolean(row && row.status!=='DRAFT')
  form.value=row?{...row,productIds:parse(row.productIdsJson).map(String),businessTypes:parse(row.businessTypesJson)}:{title:'',scopeType:'ALL',productIds:[],businessTypes:[],amount:undefined,minimumAmount:0,merchantPercent:undefined,bonusBasis:'',refundRule:'',startsAt:'',endsAt:'',totalCount:undefined,perMemberLimit:1,version:0}
  owner.value=row?.merchantId ? String(row.merchantId):'PLATFORM'
  funding.value=row?(row.merchantPercent===0?'PLATFORM':row.merchantPercent===100?'MERCHANT':'SHARED'):''
  if(row?.merchantId && !merchants.value.some(m=>String(m.id)===owner.value))merchants.value.push({id:row.merchantId,merchantName:row.merchantName})
  products.value=form.value.productIds.map(id=>({id,productName:`商品 ${id}`}));visible.value=true;findProducts('')
}
function ownerChanged(){form.value.productIds=[];products.value=[];funding.value='';form.value.merchantPercent=undefined;findProducts('')}
function fundingChanged(){form.value.merchantPercent=funding.value==='PLATFORM'?0:funding.value==='MERCHANT'?100:undefined}
async function findProducts(keyword){const v=++searchVersion;productsLoading.value=true;try{const r=await couponProducts({merchantId:owner.value==='PLATFORM'?undefined:owner.value,keyword});if(v===searchVersion){const map=new Map(products.value.filter(p=>form.value.productIds?.includes(String(p.id))).map(p=>[String(p.id),p]));for(const p of r.data||[])map.set(String(p.id),p);products.value=[...map.values()]}}catch(e){if(v===searchVersion)formError.value=e.message||'商品读取失败'}finally{if(v===searchVersion)productsLoading.value=false}}
function impact(c){const scope=c.scopeType==='ALL'?'全部商品':`指定 ${c.productIds?.length ?? parse(c.productIdsJson).length} 个商品`;return `适用：${c.merchantName} · ${scope}\n优惠：满 ¥${c.minimumAmount} 减 ¥${c.amount}\n承担：平台 ${100-c.merchantPercent}%，商家 ${c.merchantPercent}%\n商家货款将扣除商家承担部分，平台承担部分计入平台优惠成本。\n奖金：按优惠${c.bonusBasis==='NET'?'后':'前'}商品金额，原参与资格不变。\n退款：${c.refundRule==='FULL_RETURN'?'用券子单全部退款成功后退券，原有效期不变':'支付后退款不退券'}。\n使用期限：${time(c.startsAt)} 至 ${time(c.endsAt)}\n发行后适用范围、金额和承担规则不能修改。`}
async function save(){
  if(busy.value||readonly.value)return
  formError.value=''
  const f=form.value
  if(!f.title.trim()||!funding.value||!Number.isInteger(f.merchantPercent)||!f.bonusBasis||!f.refundRule||!f.businessTypes.length||!f.startsAt||!f.endsAt||!f.totalCount||!(f.amount>0)||(f.scopeType==='PRODUCTS'&&!f.productIds.length)){formError.value='请完整选择范围、金额、承担方、奖金、有效期及退款规则';return}
  const payload=JSON.parse(JSON.stringify({...f,merchantId:owner.value==='PLATFORM'?null:owner.value,merchantName:owner.value==='PLATFORM'?'平台自营':merchants.value.find(m=>String(m.id)===owner.value)?.merchantName,impactConfirmed:true}))
  busy.value=true
  try{await ElMessageBox.confirm(impact(payload),'核对结算与团队奖金影响',{confirmButtonText:'确认并保存',cancelButtonText:'返回修改',type:'warning',customClass:'coupon-impact-confirm'});await saveCoupon(payload.id,payload);visible.value=false;await load()}catch(e){if(e!=='cancel'&&e!=='close')formError.value=e.message||'保存失败，请刷新后重试'}finally{busy.value=false}
}
async function status(row){if(busy.value||!canWrite.value)return;busy.value=true;const snapshot=JSON.parse(JSON.stringify(row));try{const next=snapshot.status==='PUBLISHED'?'PAUSED':'PUBLISHED';await ElMessageBox.confirm(`${impact(snapshot)}\n${next==='PAUSED'?'本次暂停新领取，已领取的优惠券保持原规则。':'本次将开放领取。'}`,'确认优惠券状态变更',{type:'warning',confirmButtonText:'确认',cancelButtonText:'取消',customClass:'coupon-impact-confirm'});await changeCouponStatus(snapshot.id,{status:next,version:snapshot.version,impactConfirmed:true});await load()}catch(e){if(e!=='cancel'&&e!=='close')error.value=e.message||'状态修改失败'}finally{busy.value=false}}
onMounted(async()=>{load();try{let p=1;do{const r=await couponMerchants({pageNum:p,pageSize:100});merchants.value.push(...(r.data?.list||[]));if(p>=Number(r.data?.totalPage||1))break;p++}while(p<=20)}catch(e){error.value=e.message||'商家列表读取失败'}})
</script>
<style scoped>
.coupon-table{margin-top:20px}.hint{font-size:13px;line-height:1.7;color:var(--el-text-color-secondary)}.form-error{color:var(--el-color-danger)}.form-pair{display:grid;grid-template-columns:1fr 1fr;gap:20px}.coupon-form :deep(.el-select),.coupon-form :deep(.el-input-number),.coupon-form :deep(.el-date-editor){width:100%}.coupon-form{margin-top:10px}@media(max-width:600px){.form-pair{grid-template-columns:1fr;gap:0}}
</style>
<style>.coupon-impact-confirm .el-message-box__message{white-space:pre-line;line-height:1.8}</style>
