<template>
  <div class="page coupons-page">
    <header class="coupon-head"><button type="button" aria-label="返回" @click="router.back()"><ArrowLeft :size="22" /></button><h2>优惠券</h2><span></span></header>
    <div class="coupon-tabs" role="tablist"><button v-for="t in tabs" :key="t.key" type="button" role="tab" :aria-selected="tab === t.key" :class="{active:tab===t.key}" @click="switchTab(t.key)">{{t.label}}</button></div>
    <p v-if="error" role="alert" class="coupon-error">{{error}} <button type="button" @click="load">重试</button></p>
    <p v-if="loading" role="status" class="coupon-empty">正在读取优惠券…</p>
    <p v-else-if="!rows.length && !error" class="coupon-empty">{{tab==='mine'?'还没有优惠券，去领券中心看看':'暂无可领取优惠券'}}</p>
    <article v-for="c in rows" :key="c.claimId || c.id" class="coupon-card">
      <div class="coupon-price"><strong><small>¥</small>{{money(c.amount)}}</strong><span>{{Number(c.minimumAmount)>0?`满 ¥${money(c.minimumAmount)} 可用`:'无门槛'}}</span></div>
      <div class="coupon-copy"><h3>{{c.title}}</h3><p>{{c.scopeLabel}}</p></div>
      <div class="coupon-meta"><p>{{date(c.startsAt)}} — {{date(c.endsAt)}}</p><p>{{couponBusinessLabel(c.businessTypes)}} · 不与秒杀叠加</p></div>
      <div class="coupon-bottom"><button type="button" @click="showProducts(c)">适用商品 ›</button><button v-if="tab==='catalog'" type="button" class="claim-button" :disabled="!c.usable || Boolean(claiming)" @click="claim(c)">{{claiming===String(c.id)?'领取中…':c.reason||(claimed.has(String(c.id))?'继续领取':'领取')}}</button><span v-else class="coupon-status">{{statusLabels[c.status]||c.status}}</span></div>
      <p class="coupon-rule">{{c.refundRule==='FULL_RETURN'?'用券子单全部退款成功后退券，原有效期不变':'支付后退款不退券'}}；部分退款不退券。</p>
    </article>
    <div class="coupon-pager"><button type="button" :disabled="page<=1 || loading" @click="turn(-1)">上一页</button><span>{{page}} / {{Math.max(1,pages)}}</span><button type="button" :disabled="page>=pages || loading" @click="turn(1)">下一页</button></div>
    <div v-if="productTarget" class="coupon-overlay" @click.self="productTarget=null"><section class="coupon-sheet" role="dialog" aria-modal="true" aria-label="适用商品"><div class="coupon-sheet-head"><h3>适用商品</h3><button type="button" @click="productTarget=null">关闭</button></div><p class="coupon-rule">{{productTarget.scopeLabel}}；购买时仍需满足券的业务、金额及有效期条件。</p><p v-if="productsLoading">正在读取…</p><p v-else-if="!products.length">暂无上架的适用商品</p><p v-if="productError" role="alert">{{productError}}</p><RouterLink v-for="p in products" :key="p.id" :to="`/product/${p.id}`" class="coupon-product"><img :src="p.coverUrl" alt="" /><span>{{p.productName}}</span><strong>¥{{money(p.salePrice)}}</strong></RouterLink><button v-if="productPage<productPages" type="button" :disabled="productsLoading" @click="loadProducts(true)">更多商品</button></section></div>
  </div>
</template>
<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import { listCoupons, claimCoupon, couponProducts } from '@/api/coupons'
import { createIdempotencyKey } from '@/utils/idempotency'
import { money } from '@/utils/format'
import { couponBusinessLabel } from '@surface-commerce-policy'
const router=useRouter(),route=useRoute(),tab=ref(route.query.tab==='catalog'?'catalog':'mine'),tabs=[{key:'mine',label:'我的优惠券'},{key:'catalog',label:'领券中心'}]
const rows=ref([]),page=ref(1),pages=ref(1),loading=ref(false),error=ref(''),claiming=ref(''),claimed=ref(new Set())
const statusLabels={AVAILABLE:'未使用',RESERVED:'待支付占用',USED:'已使用',EXPIRED:'已过期'}
const productTarget=ref(null),products=ref([]),productsLoading=ref(false),productError=ref(''),productPage=ref(1),productPages=ref(1)
const keys=new Map();let version=0,productVersion=0,disposed=false
const date=v=>String(v||'').replace('T',' ').slice(0,16)
async function load(){const v=++version;loading.value=true;error.value='';try{const r=await listCoupons(tab.value==='mine',{pageNum:page.value,pageSize:20});if(!disposed&&v===version){rows.value=r.data?.list||[];pages.value=Number(r.data?.totalPage||1)}}catch(e){if(!disposed&&v===version)error.value=e.message||'读取失败'}finally{if(v===version)loading.value=false}}
function switchTab(value){if(value===tab.value)return;tab.value=value;page.value=1;rows.value=[];load()}
function turn(delta){page.value+=delta;load()}
async function claim(c){if(claiming.value||!c.usable)return;const id=String(c.id);claiming.value=id;error.value='';if(!keys.has(id))keys.set(id,createIdempotencyKey('coupon').replace(/[^A-Za-z0-9_-]/g,'_').slice(0,80));try{await claimCoupon(id,keys.get(id));if(disposed)return;claimed.value.add(id);keys.delete(id);await load()}catch(e){if(!disposed)error.value=e.message||'领取失败，请重试'}finally{claiming.value=''}}
function showProducts(c){productTarget.value=c;productPage.value=0;products.value=[];loadProducts(true)}
async function loadProducts(){const target=productTarget.value;if(!target)return;const v=++productVersion;productsLoading.value=true;productError.value='';try{const r=await couponProducts(target.id,{pageNum:productPage.value+1,pageSize:20});if(disposed||v!==productVersion||productTarget.value!==target)return;products.value.push(...(r.data?.list||[]));productPages.value=Number(r.data?.totalPage||1);productPage.value++}catch(e){if(v===productVersion)productError.value=e.message||'商品读取失败'}finally{if(v===productVersion)productsLoading.value=false}}
onMounted(load);onBeforeUnmount(()=>{disposed=true;version++;productVersion++})
</script>
<style scoped>
.page.coupons-page { --brand: var(--brand-primary, #e7193f); width:100%; padding:16px 14px 90px; }
.coupon-card .coupon-copy h3 { display:-webkit-box; -webkit-box-orient:vertical; -webkit-line-clamp:2; overflow:hidden; font-size:15px; }
.coupon-meta { grid-column:1/-1; color:#858b94; font-size:12px; line-height:1.6; }
.coupon-meta p { margin:0; }
.coupons-page{max-width:680px;margin:auto;padding:16px 16px 90px}.coupon-head{display:grid;grid-template-columns:44px 1fr 44px;align-items:center;margin-bottom:16px}.coupon-head h2{font-size:17px;font-weight:600;text-align:center;margin:0}.coupon-head button,.coupon-sheet button,.coupon-bottom button{border:0;background:none;color:inherit}.coupon-tabs{display:grid;grid-template-columns:1fr 1fr;border-bottom:1px solid #e8e9eb;margin-bottom:18px}.coupon-tabs button{min-height:48px;background:none;border:0;border-bottom:2px solid transparent;font-size:15px;color:#747982}.coupon-tabs .active{color:var(--brand);border-color:var(--brand);font-weight:600}.coupon-card{background:#fff;border:1px solid #e8e9eb;border-radius:12px;padding:18px;margin:12px 0;display:grid;grid-template-columns:108px minmax(0,1fr);gap:12px}.coupon-price{display:flex;flex-direction:column;justify-content:center;gap:8px;border-right:1px dashed #e4e6e8}.coupon-price strong{color:var(--brand);font-size:25px;overflow-wrap:anywhere}.coupon-price small{font-size:14px}.coupon-price span,.coupon-copy p{font-size:12px;line-height:1.6;color:#737982;margin:4px 0}.coupon-copy h3{font-size:16px;line-height:1.5;margin:0;overflow-wrap:anywhere}.coupon-bottom,.coupon-rule{grid-column:1/-1}.coupon-bottom{display:flex;align-items:center;justify-content:space-between;border-top:1px solid #f1f2f3;padding-top:12px;font-size:13px}.coupon-bottom .claim-button{background:var(--brand);color:#fff;border-radius:18px;padding:8px 18px;min-height:36px}.claim-button:disabled{opacity:.55}.coupon-rule{font-size:12px;color:#8c9198;line-height:1.7;margin:0}.coupon-status{color:#747982}.coupon-pager{display:flex;justify-content:center;align-items:center;gap:18px;margin-top:24px;font-size:13px}.coupon-pager button{border:0;background:#fff;border-radius:6px;padding:10px}.coupon-pager button:disabled{opacity:.4}.coupon-empty{text-align:center;color:#8c9198;margin:60px 0}.coupon-error{color:#bd243b;font-size:14px}.coupon-overlay{position:fixed;inset:0;z-index:120;background:#0006;display:flex;align-items:flex-end;justify-content:center}.coupon-sheet{background:#fff;width:100%;max-width:680px;max-height:76vh;overflow:auto;padding:20px 20px calc(24px + env(safe-area-inset-bottom));border-radius:18px 18px 0 0;box-sizing:border-box}.coupon-sheet-head{display:flex;justify-content:space-between;align-items:center}.coupon-sheet-head h3{font-size:17px}.coupon-product{display:flex;align-items:center;gap:12px;padding:14px 0;border-bottom:1px solid #eee;color:inherit;text-decoration:none;font-size:14px}.coupon-product img{width:56px;height:56px;object-fit:cover;border-radius:8px}.coupon-product span{flex:1;min-width:0;overflow-wrap:anywhere}.coupon-product strong{color:var(--brand)}
</style>
