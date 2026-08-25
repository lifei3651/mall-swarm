<template>
  <main class="new-page">
    <header class="new-header"><button type="button" aria-label="返回" @click="goBack"><ArrowLeft :size="21" /></button><div><Sparkles :size="19" /><strong>新品速递</strong></div><RouterLink to="/" aria-label="返回首页"><Home :size="20" /></RouterLink></header>
    <section class="new-hero"><span>JUST ARRIVED</span><h1>新鲜上架，抢先体验</h1><p>这里汇集近期首次上架商品和运营精选新品；展示期结束不会影响商品正常销售。</p></section>
    <section class="new-content">
      <div v-if="loading" class="new-state"><LoaderCircle class="spin" :size="28" />正在整理新品…</div>
      <div v-else-if="disabled" class="new-state"><PackageOpen :size="32" /><strong>新品页面暂未开放</strong><RouterLink to="/">返回商城首页</RouterLink></div>
      <div v-else-if="error" class="new-state"><CircleAlert :size="28" /><strong>{{ error }}</strong><button type="button" @click="load">重新加载</button></div>
      <div v-else-if="products.length" class="new-grid">
        <RouterLink v-for="product in products" :key="product.id" :to="`/product/${product.id}`" class="new-card">
          <div class="new-image"><img :src="product.coverUrl" :alt="product.productName" loading="lazy" @error="applyImageFallback" /><span><Sparkles :size="12" />新品</span></div>
          <div class="new-copy"><h2>{{ product.productName }}</h2><p>{{ product.subtitle || '新品上架，品质好物' }}</p><div><b>¥{{ money(product.salePrice) }}</b><small>已售 {{ product.salesCount || 0 }}</small></div></div>
        </RouterLink>
      </div>
      <div v-else class="new-state"><PackageOpen :size="32" /><strong>近期开售新品还在准备中</strong><RouterLink to="/">查看全部商品</RouterLink></div>
    </section>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, CircleAlert, Home, LoaderCircle, PackageOpen, Sparkles } from 'lucide-vue-next'
import { getHome, listNewArrivals } from '@/api/shop'
import { applyImageFallback } from '@/utils/imageFallback'
import { money } from '@/utils/format'

const router = useRouter()
const products = ref([])
const loading = ref(false)
const error = ref('')
const disabled = ref(false)
const goBack = () => window.history.length > 1 ? router.back() : router.push('/')
const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const home = (await getHome()).data || {}
    if (Number(home.displayConfig?.newArrivalsEnabled ?? 1) !== 1) {
      disabled.value = true
      products.value = []
      return
    }
    disabled.value = false
    products.value = (await listNewArrivals({ limit: 60 })).data || []
  } catch (e) { error.value = e?.message || '新品列表暂时加载失败' } finally { loading.value = false }
}
onMounted(load)
</script>

<style scoped>
.new-page{min-height:100vh;padding-bottom:72px;background:var(--shop-page-bg,#f5f6f8)}.new-header{position:sticky;top:0;z-index:20;height:58px;display:grid;grid-template-columns:48px 1fr 48px;align-items:center;padding:0 max(8px,calc((100% - 1180px)/2));background:rgba(255,255,255,.95);border-bottom:1px solid #eceff1;backdrop-filter:blur(12px)}.new-header button,.new-header>a{width:40px;height:40px;display:grid;place-items:center;color:#344054;background:transparent;border:0;border-radius:50%}.new-header>div{display:flex;align-items:center;justify-content:center;gap:7px}.new-header>div svg{color:#7357e6}.new-header>a{justify-self:end}.new-hero{width:min(1180px,calc(100% - 32px));margin:18px auto;padding:34px;color:#fff;background:radial-gradient(circle at 85% 15%,rgba(255,255,255,.22),transparent 25%),linear-gradient(135deg,#44318d,#8c67ec);border-radius:24px}.new-hero span{font-size:11px;font-weight:900;letter-spacing:2px}.new-hero h1{margin:8px 0 7px;font-size:30px}.new-hero p{margin:0;color:rgba(255,255,255,.78);font-size:13px}.new-content{width:min(1180px,calc(100% - 32px));margin:0 auto}.new-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px}.new-card{overflow:hidden;color:#1d2939;background:#fff;border:1px solid #e8edf3;border-radius:17px;text-decoration:none;box-shadow:0 8px 24px rgba(15,23,42,.04);transition:transform .2s ease}.new-card:hover{transform:translateY(-2px)}.new-image{position:relative;aspect-ratio:1;overflow:hidden;background:#f2f3f5}.new-image img{width:100%;height:100%;object-fit:cover}.new-image span{position:absolute;top:10px;left:10px;display:flex;align-items:center;gap:3px;padding:5px 8px;color:#fff;background:#7357e6;border-radius:999px;font-size:11px;font-weight:800}.new-copy{padding:13px}.new-copy h2{min-height:42px;margin:0;font-size:15px;line-height:1.4;display:-webkit-box;overflow:hidden;-webkit-box-orient:vertical;-webkit-line-clamp:2}.new-copy p{overflow:hidden;margin:6px 0;color:#98a2b3;font-size:11px;text-overflow:ellipsis;white-space:nowrap}.new-copy>div{display:flex;align-items:flex-end;justify-content:space-between}.new-copy b{color:var(--price-color,var(--brand-primary));font-size:19px}.new-copy small{color:#98a2b3}.new-state{min-height:300px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;color:#98a2b3;background:#fff;border-radius:20px}.new-state strong{color:#475467}.new-state button,.new-state>a{padding:9px 16px;color:#fff;background:var(--brand-primary);border:0;border-radius:999px;text-decoration:none}.spin{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:760px){.new-header{padding:0 8px}.new-hero,.new-content{width:calc(100% - 16px)}.new-hero{margin:10px auto;padding:24px 20px;border-radius:18px}.new-hero h1{font-size:24px}.new-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.new-card{border-radius:14px}.new-copy{padding:10px}.new-copy h2{min-height:38px;font-size:14px}.new-copy b{font-size:17px}}
</style>
