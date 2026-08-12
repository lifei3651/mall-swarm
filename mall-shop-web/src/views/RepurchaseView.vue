<template>
  <main class="repurchase-page">
    <header>
      <button type="button" aria-label="返回" @click="$router.back()">‹</button>
      <div><h1>会员复购商城</h1><p>复购商品、复购价格与普通商城订单独立结算</p></div>
    </header>
    <form role="search" @submit.prevent="load"><input v-model="keyword" aria-label="搜索复购商品" placeholder="搜索复购商品" /><button>搜索</button></form>
    <p v-if="error" class="state error" role="alert">{{ error }}</p>
    <p v-else-if="loading" class="state">商品加载中…</p>
    <section v-else-if="products.length" class="product-grid">
      <article v-for="product in products" :key="product.id">
        <img :src="product.coverUrl" :alt="product.productName" />
        <div><span>复购专享</span><h2>{{ product.productName }}</h2><p>{{ product.subtitle || '会员复购专属商品' }}</p>
          <strong>¥{{ money(product.repurchasePrice) }}</strong>
          <button type="button" :disabled="product.stock <= 0" @click="choose(product)">{{ product.stock > 0 ? '选择规格并购买' : '已售罄' }}</button>
        </div>
      </article>
    </section>
    <p v-else class="state">暂无复购商品</p>
    <div v-if="dialogProduct" class="dialog-mask" @click.self="dialogProduct=null">
      <section class="dialog" role="dialog" aria-modal="true" aria-label="选择复购规格">
        <button class="close" aria-label="关闭" @click="dialogProduct=null">×</button>
        <h2>{{ dialogProduct.productName }}</h2>
        <select v-if="skus.length" v-model="selectedSkuId" aria-label="商品规格"><option v-for="sku in skus" :key="sku.id" :value="sku.id" :disabled="sku.stock<=0">{{ sku.skuName }} · ¥{{ money(sku.repurchasePrice || dialogProduct.repurchasePrice) }}</option></select>
        <label>数量 <input v-model.number="quantity" type="number" min="1" :max="maxQuantity" /></label>
        <button class="confirm" :disabled="maxQuantity<=0" @click="buy">确认购买</button>
      </section>
    </div>
  </main>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getRepurchaseProduct, listRepurchaseProducts } from '@/api/shop'
import { useCart } from '@/store/cart'
import { money } from '@/utils/format'
const router=useRouter(); const { beginDirectCheckout }=useCart()
const keyword=ref(''); const products=ref([]); const loading=ref(false); const error=ref('')
const dialogProduct=ref(null); const skus=ref([]); const selectedSkuId=ref(null); const quantity=ref(1)
const selectedSku=computed(()=>skus.value.find(s=>String(s.id)===String(selectedSkuId.value)))
const maxQuantity=computed(()=>Math.max(0,Math.min(Number(selectedSku.value?.stock ?? dialogProduct.value?.stock ?? 0),Number(dialogProduct.value?.repurchasePurchaseLimit || 999999))))
const load=async()=>{loading.value=true;error.value='';try{const res=await listRepurchaseProducts({keyword:keyword.value});products.value=res.data||[]}catch(e){error.value=e.message||'复购商城加载失败'}finally{loading.value=false}}
const choose=async(product)=>{try{const res=await getRepurchaseProduct(product.id);dialogProduct.value=res.data.product;skus.value=res.data.skus||[];selectedSkuId.value=skus.value.find(s=>s.stock>0)?.id||null;quantity.value=1}catch(e){error.value=e.message||'商品加载失败'}}
const buy=()=>{const sku=selectedSku.value;beginDirectCheckout({...dialogProduct.value,skuId:sku?.id||null,skuName:sku?.skuName||'',salePrice:Number(sku?.repurchasePrice||dialogProduct.value.repurchasePrice),pvValue:Number(sku?.repurchasePv||dialogProduct.value.repurchasePv||0),stock:Number(sku?.stock??dialogProduct.value.stock),purchaseLimit:Number(dialogProduct.value.repurchasePurchaseLimit||0),businessType:'REPURCHASE'},Math.min(Math.max(1,quantity.value),maxQuantity.value));router.push('/checkout')}
load()
</script>

<style scoped>
.repurchase-page{min-height:100vh;padding:0 16px 70px;background:#f6f7f9}.repurchase-page>header{max-width:1080px;display:flex;align-items:center;gap:14px;margin:auto;padding:24px 0}.repurchase-page>header button{width:42px;height:42px;border:0;border-radius:50%;background:#fff;font-size:30px}.repurchase-page h1{margin:0;font-size:26px}.repurchase-page header p,article p{margin:5px 0;color:#667085}.repurchase-page>form{max-width:600px;display:flex;margin:0 auto 22px}.repurchase-page>form input{flex:1;height:44px;padding:0 14px;border:1px solid #d8dee8;border-radius:10px 0 0 10px}.repurchase-page>form button{width:80px;color:#fff;background:#7b4b24;border:0;border-radius:0 10px 10px 0}.product-grid{max-width:1080px;display:grid;grid-template-columns:repeat(auto-fit,minmax(250px,1fr));gap:16px;margin:auto}.product-grid article{overflow:hidden;background:#fff;border-radius:17px}.product-grid img{width:100%;aspect-ratio:1.35;object-fit:cover}.product-grid article>div{padding:16px}.product-grid span{color:#8a572c;font-size:12px}.product-grid h2{margin:7px 0;font-size:17px}.product-grid strong{display:block;margin:15px 0;color:#a5511c;font-size:23px}.product-grid button,.confirm{width:100%;height:42px;color:#fff;background:#8a572c;border:0;border-radius:9px;font-weight:800}.state{padding:70px;text-align:center;color:#667085}.error{color:#b42318}.dialog-mask{position:fixed;inset:0;z-index:80;display:grid;place-items:center;padding:20px;background:rgba(0,0,0,.4)}.dialog{position:relative;width:min(420px,100%);padding:24px;background:#fff;border-radius:18px}.dialog h2{margin-top:0}.dialog select,.dialog input{width:100%;height:42px;margin:10px 0;padding:0 10px;border:1px solid #d8dee8;border-radius:9px}.close{position:absolute;top:12px;right:12px;border:0;background:none;font-size:25px}.confirm{margin-top:15px}
</style>
