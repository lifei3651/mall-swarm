<template>
  <main class="live-detail-page">
    <header class="detail-header"><button type="button" aria-label="返回" @click="goBack"><ArrowLeft :size="21" /></button><strong>直播间</strong><RouterLink to="/" aria-label="返回首页"><Home :size="20" /></RouterLink></header>
    <div v-if="loading" class="detail-state"><LoaderCircle class="spin" :size="28" />正在加载直播间…</div>
    <div v-else-if="error" class="detail-state"><CircleAlert :size="30" /><strong>{{ error }}</strong><button type="button" @click="load">重新加载</button></div>
    <template v-else-if="room.room">
      <section class="live-stage">
        <img :src="room.room.coverUrl" :alt="room.room.title" @error="applyImageFallback" />
        <span class="stage-shade"></span>
        <span class="stage-state" :class="`state-${String(room.roomState).toLowerCase()}`">{{ stateLabel }}</span>
        <div class="stage-copy"><h1>{{ room.room.title }}</h1><p>{{ room.room.subtitle || '直播精选好物' }}</p><span><UserRound :size="14" />{{ room.room.anchorName || '商城主播' }}<Flame :size="14" />{{ room.room.heatCount || 0 }} 热度</span></div>
      </section>
      <section class="live-action-card">
        <div><strong>{{ actionTitle }}</strong><span>{{ actionDescription }}</span></div>
        <button v-if="canWatch" type="button" @click="openWatch"><PlayCircle :size="18" />{{ room.roomState === 'ENDED' ? '观看回放' : '进入直播' }}</button>
      </section>
      <section class="live-products">
        <div class="section-title"><div><ShoppingBag :size="19" /><h2>直播好物</h2></div><span>{{ room.products?.length || 0 }} 件</span></div>
        <div v-if="room.products?.length" class="product-grid">
          <RouterLink v-for="product in room.products" :key="product.id" :to="`/product/${product.id}`" class="product-card">
            <img :src="product.coverUrl" :alt="product.productName" loading="lazy" @error="applyImageFallback" />
            <div><strong>{{ product.productName }}</strong><span>{{ product.subtitle || '直播精选商品' }}</span><b>¥{{ money(product.salePrice) }}</b></div>
          </RouterLink>
        </div>
        <div v-else class="empty-products">关联商品已下架或暂不可售</div>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, CircleAlert, Flame, Home, LoaderCircle, PlayCircle, ShoppingBag, UserRound } from 'lucide-vue-next'
import { getLiveRoom } from '@/api/shop'
import { applyImageFallback } from '@/utils/imageFallback'
import { money } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const room = ref({})
const loading = ref(false)
const error = ref('')
const stateLabel = computed(() => ({ LIVE: '直播中', UPCOMING: '直播预告', ENDED: '精彩回放' }[room.value.roomState] || '直播间'))
const canWatch = computed(() => ['LIVE', 'ENDED'].includes(room.value.roomState) && Boolean(room.value.room?.watchUrl))
const actionTitle = computed(() => room.value.roomState === 'LIVE' ? '直播正在进行' : room.value.roomState === 'ENDED' ? '本场直播已结束' : '直播即将开始')
const actionDescription = computed(() => {
  if (room.value.roomState === 'LIVE') return '点击进入直播服务商观看页，边看边选购。'
  if (room.value.roomState === 'ENDED') return canWatch.value ? '本场提供回放，可继续查看直播商品。' : '回放暂未开放，可继续查看下方商品。'
  const value = room.value.room?.scheduledStartTime
  return value ? `计划 ${String(value).replace('T', ' ').slice(0, 16)} 开播` : '开播时间待平台确认'
})
const goBack = () => window.history.length > 1 ? router.back() : router.push('/live')
const openWatch = () => {
  const url = new URL(room.value.room.watchUrl)
  if (url.protocol !== 'https:') return
  window.open(url.href, '_blank', 'noopener,noreferrer')
}
const load = async () => {
  loading.value = true
  error.value = ''
  try { room.value = (await getLiveRoom(route.params.id)).data || {} } catch (e) { error.value = e?.message || '直播间暂时加载失败' } finally { loading.value = false }
}
onMounted(load)
</script>

<style scoped>
.live-detail-page{min-height:100vh;padding-bottom:72px;background:var(--shop-page-bg,#f5f6f8)}.detail-header{position:sticky;top:0;z-index:20;height:58px;display:grid;grid-template-columns:48px 1fr 48px;align-items:center;padding:0 max(8px,calc((100% - 980px)/2));background:rgba(255,255,255,.95);border-bottom:1px solid #eceff1;backdrop-filter:blur(12px)}.detail-header button,.detail-header>a{width:40px;height:40px;display:grid;place-items:center;color:#344054;background:transparent;border:0;border-radius:50%}.detail-header strong{text-align:center}.detail-header>a{justify-self:end}.live-stage,.live-action-card,.live-products,.detail-state{width:min(980px,calc(100% - 32px));margin:18px auto}.live-stage{position:relative;aspect-ratio:16/8;overflow:hidden;color:#fff;background:#111827;border-radius:24px}.live-stage>img{width:100%;height:100%;object-fit:cover}.stage-shade{position:absolute;inset:0;background:linear-gradient(180deg,rgba(5,12,24,.02),rgba(5,12,24,.82))}.stage-state{position:absolute;top:18px;left:18px;padding:6px 10px;background:#0f9f6e;border-radius:999px;font-size:12px;font-weight:900}.stage-state.state-live{background:#ef1742}.stage-state.state-ended{background:#475467}.stage-copy{position:absolute;left:24px;right:24px;bottom:22px}.stage-copy h1{margin:0;font-size:28px}.stage-copy p{margin:8px 0;color:rgba(255,255,255,.8)}.stage-copy span{display:flex;align-items:center;gap:5px;font-size:12px}.stage-copy span svg:nth-of-type(2){margin-left:10px}.live-action-card{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:18px 20px;background:#fff;border:1px solid #e8edf3;border-radius:18px}.live-action-card strong,.live-action-card span{display:block}.live-action-card span{margin-top:5px;color:#667085;font-size:12px}.live-action-card button{height:42px;display:flex;align-items:center;gap:6px;padding:0 18px;color:#fff;background:var(--brand-primary);border:0;border-radius:999px;font-weight:800}.live-products{padding:20px;background:#fff;border-radius:20px}.section-title{display:flex;align-items:center;justify-content:space-between;margin-bottom:14px}.section-title>div{display:flex;align-items:center;gap:7px}.section-title svg{color:var(--brand-primary)}.section-title h2{margin:0;font-size:20px}.section-title>span{color:#98a2b3;font-size:12px}.product-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.product-card{overflow:hidden;color:#1d2939;border:1px solid #eceff1;border-radius:14px;text-decoration:none}.product-card>img{width:100%;aspect-ratio:1;display:block;object-fit:cover;background:#f4f5f7}.product-card>div{padding:10px}.product-card strong,.product-card span{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.product-card strong{font-size:13px}.product-card span{margin:5px 0;color:#98a2b3;font-size:10px}.product-card b{color:var(--price-color,var(--brand-primary));font-size:16px}.empty-products,.detail-state{min-height:260px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;color:#98a2b3;background:#fff;border-radius:20px}.detail-state button{padding:9px 16px;color:#fff;background:var(--brand-primary);border:0;border-radius:999px}.spin{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:760px){.detail-header{padding:0 8px}.live-stage,.live-action-card,.live-products,.detail-state{width:calc(100% - 16px);margin:10px auto}.live-stage{aspect-ratio:4/3;border-radius:18px}.stage-state{top:12px;left:12px}.stage-copy{left:16px;right:16px;bottom:15px}.stage-copy h1{font-size:21px}.stage-copy p{font-size:12px}.live-action-card{align-items:flex-start;padding:15px}.live-action-card button{height:38px;padding:0 13px;font-size:12px}.live-products{padding:14px}.product-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}}
</style>
