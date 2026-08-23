<template>
  <main class="catalog-page">
    <header class="catalog-header">
      <button type="button" aria-label="返回上一页" @click="goBack"><ArrowLeft :size="21" /></button>
      <div><Radio :size="20" /><strong>直播广场</strong></div>
      <RouterLink to="/" aria-label="返回首页"><Home :size="20" /></RouterLink>
    </header>

    <section class="catalog-hero live-hero">
      <span>LIVE SHOPPING</span>
      <h1>边看边选，直播好物</h1>
      <p>直播预告、正在直播与精彩回放均由平台统一维护。</p>
    </section>

    <section class="catalog-content">
      <div v-if="loading" class="state-card"><LoaderCircle class="spin" :size="28" /><span>正在加载直播间…</span></div>
      <div v-else-if="error" class="state-card"><CircleAlert :size="28" /><strong>{{ error }}</strong><button type="button" @click="load">重新加载</button></div>
      <div v-else-if="rooms.length" class="room-grid">
        <RouterLink v-for="item in rooms" :key="item.room.id" class="room-card" :to="`/live/${item.room.id}`">
          <div class="room-image">
            <img :src="item.room.coverUrl" :alt="item.room.title" loading="lazy" @error="applyImageFallback" />
            <span class="room-state" :class="`state-${String(item.roomState).toLowerCase()}`">{{ stateLabel(item.roomState) }}</span>
            <span class="room-heat"><Flame :size="13" />{{ heat(item.room.heatCount) }}</span>
          </div>
          <div class="room-info"><h2>{{ item.room.title }}</h2><p>{{ item.room.subtitle || '直播精选好物' }}</p><span>{{ item.room.anchorName || formatTime(item.room.scheduledStartTime) }} · {{ item.products?.length || 0 }} 件商品</span></div>
        </RouterLink>
      </div>
      <div v-else class="state-card"><Radio :size="32" /><strong>暂时没有公开直播</strong><span>新的直播预告发布后会在这里出现</span><RouterLink to="/">去首页逛逛</RouterLink></div>
    </section>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, CircleAlert, Flame, Home, LoaderCircle, Radio } from 'lucide-vue-next'
import { listLiveRooms } from '@/api/shop'
import { applyImageFallback } from '@/utils/imageFallback'

const router = useRouter()
const rooms = ref([])
const loading = ref(false)
const error = ref('')
const stateLabel = (state) => ({ LIVE: '直播中', UPCOMING: '直播预告', ENDED: '精彩回放' }[state] || '直播')
const formatTime = (value) => value ? String(value).replace('T', ' ').slice(5, 16) : '开播时间待定'
const heat = (value) => Number(value || 0) >= 10000 ? `${(Number(value) / 10000).toFixed(1)}万` : String(Number(value || 0))
const goBack = () => window.history.length > 1 ? router.back() : router.push('/')
const load = async () => {
  loading.value = true
  error.value = ''
  try { rooms.value = (await listLiveRooms({ limit: 50 })).data || [] } catch (e) { error.value = e?.message || '直播广场暂时加载失败' } finally { loading.value = false }
}
onMounted(load)
</script>

<style scoped>
.catalog-page{min-height:100vh;padding-bottom:70px;background:var(--shop-page-bg,#f5f6f8)}.catalog-header{position:sticky;top:0;z-index:20;height:58px;display:grid;grid-template-columns:48px 1fr 48px;align-items:center;padding:0 max(8px,calc((100% - 1180px)/2));background:rgba(255,255,255,.94);border-bottom:1px solid #eceff1;backdrop-filter:blur(12px)}.catalog-header button,.catalog-header>a{width:40px;height:40px;display:grid;place-items:center;color:#344054;background:transparent;border:0;border-radius:50%}.catalog-header>div{display:flex;align-items:center;justify-content:center;gap:7px;color:#1d2939}.catalog-header>div svg{color:var(--brand-primary)}.catalog-header>a{justify-self:end}.catalog-hero{width:min(1180px,calc(100% - 32px));margin:18px auto;padding:34px;border-radius:24px;color:#fff}.live-hero{background:radial-gradient(circle at 85% 15%,rgba(255,255,255,.2),transparent 25%),linear-gradient(135deg,#1f2937,#ef1742)}.catalog-hero span{font-size:11px;font-weight:900;letter-spacing:2px}.catalog-hero h1{margin:8px 0 7px;font-size:30px}.catalog-hero p{margin:0;color:rgba(255,255,255,.78);font-size:13px}.catalog-content{width:min(1180px,calc(100% - 32px));margin:0 auto}.room-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:15px}.room-card{overflow:hidden;color:#1d2939;background:#fff;border:1px solid #e9edf2;border-radius:18px;text-decoration:none;box-shadow:0 8px 24px rgba(15,23,42,.05);transition:transform .2s ease}.room-card:hover{transform:translateY(-2px)}.room-image{position:relative;aspect-ratio:16/9;overflow:hidden;background:#eef0f3}.room-image img{width:100%;height:100%;object-fit:cover}.room-state,.room-heat{position:absolute;top:10px;padding:5px 8px;color:#fff;background:rgba(17,24,39,.72);border-radius:999px;font-size:11px;font-weight:800}.room-state{left:10px}.room-state.state-live{background:#ef1742}.room-state.state-upcoming{background:#0f9f6e}.room-heat{right:10px;display:flex;align-items:center;gap:3px}.room-info{padding:14px}.room-info h2{margin:0;overflow:hidden;font-size:16px;text-overflow:ellipsis;white-space:nowrap}.room-info p{min-height:38px;margin:7px 0 8px;color:#667085;font-size:12px;line-height:1.55}.room-info span{color:#98a2b3;font-size:11px}.state-card{min-height:300px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;color:#98a2b3;background:#fff;border-radius:20px}.state-card strong{color:#475467}.state-card button,.state-card>a{padding:9px 16px;color:#fff;background:var(--brand-primary);border:0;border-radius:999px;text-decoration:none}.spin{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:760px){.catalog-header{padding:0 8px}.catalog-hero,.catalog-content{width:calc(100% - 16px)}.catalog-hero{margin:10px auto;padding:24px 20px;border-radius:18px}.catalog-hero h1{font-size:24px}.room-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}.room-card{border-radius:14px}.room-image{aspect-ratio:4/3}.room-info{padding:10px}.room-info h2{font-size:14px}.room-info p{min-height:34px;margin:5px 0;font-size:11px}.room-info span{font-size:10px}}
</style>
