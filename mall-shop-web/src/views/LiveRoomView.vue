<template>
  <main class="live-detail-page">
    <header class="detail-header">
      <button type="button" aria-label="返回" @click="goBack"><ArrowLeft :size="21" /></button>
      <strong>直播间</strong>
      <button v-if="room.room?.shareEnabled === 1" type="button" aria-label="分享直播" @click="shareRoom"><Share2 :size="20" /></button>
      <RouterLink v-else to="/" aria-label="返回首页"><Home :size="20" /></RouterLink>
    </header>
    <div v-if="loading" class="detail-state"><LoaderCircle class="spin" :size="28" />正在加载直播间…</div>
    <div v-else-if="error" class="detail-state"><CircleAlert :size="30" /><strong>{{ error }}</strong><button type="button" @click="load">重新加载</button></div>
    <template v-else-if="room.room">
      <section class="watch-layout">
        <div class="live-stage">
          <video v-if="canWatch" ref="videoRef" playsinline controls autoplay muted :poster="room.room.coverUrl" @error="playerFailed = true" />
          <img v-else :src="room.room.coverUrl" :alt="room.room.title" @error="applyImageFallback" />
          <span v-if="!canWatch" class="stage-shade"></span>
          <span class="stage-state" :class="`state-${String(room.roomState).toLowerCase()}`">{{ stateLabel }}</span>
          <div v-if="!canWatch" class="stage-copy"><h1>{{ room.room.title }}</h1><p>{{ room.room.subtitle || '直播精选好物' }}</p></div>
          <div v-if="playerFailed" class="player-fallback"><CircleAlert :size="28" /><strong>当前浏览器暂不支持内嵌播放</strong><button type="button" @click="openExternal">打开安全观看地址</button></div>
        </div>
        <aside class="live-chat-card">
          <div class="chat-heading"><div><MessageCircle :size="18" /><strong>直播互动</strong></div><span><Users :size="14" />{{ room.room.viewerCount || 0 }} 人正在看</span></div>
          <div ref="commentListRef" class="comment-list" aria-live="polite">
            <p v-if="!comments.length" class="comment-empty">直播评论会显示在这里</p>
            <p v-for="item in comments" :key="item.id"><strong>{{ item.displayName }}</strong><span>{{ item.content }}</span></p>
          </div>
          <form v-if="room.roomState === 'LIVE' && room.room.commentEnabled === 1" class="comment-form" @submit.prevent="sendComment">
            <input v-model.trim="commentText" maxlength="300" aria-label="直播评论" placeholder="说点什么…" />
            <button type="submit" :disabled="commentSaving || !commentText" aria-label="发送评论"><Send :size="17" /></button>
          </form>
          <p v-else class="comment-closed">{{ room.room.commentEnabled === 1 ? '开播后可以参与评论' : '平台已关闭本场评论' }}</p>
        </aside>
      </section>
      <section class="live-info-card">
        <div class="anchor-copy"><span class="anchor-avatar"><UserRound :size="20" /></span><div><strong>{{ room.room.anchorName || '商城主播' }}</strong><small>{{ liveTypeLabel }} · {{ room.room.title }}</small></div></div>
        <div class="live-metrics"><span><Flame :size="15" />{{ room.room.heatCount || 0 }} 热度</span><span>{{ actionDescription }}</span></div>
        <button v-if="room.room.shareEnabled === 1" type="button" class="share-button" @click="shareRoom"><Share2 :size="16" />分享</button>
      </section>
      <section class="live-products">
        <div class="section-title"><div><ShoppingBag :size="19" /><h2>直播好物</h2></div><span>{{ room.products?.length || 0 }} 件</span></div>
        <div v-if="room.products?.length" class="product-grid">
          <RouterLink v-for="product in room.products" :key="product.id" :to="`/product/${product.id}`" class="product-card" @click="trackProduct(product.id)">
            <img :src="product.coverUrl" :alt="product.productName" loading="lazy" @error="applyImageFallback" />
            <div><strong>{{ product.productName }}</strong><span>{{ product.subtitle || '直播精选商品' }}</span><b>¥{{ money(product.salePrice) }}</b><em>去购买</em></div>
          </RouterLink>
        </div>
        <div v-else class="empty-products">关联商品已下架或暂不可售</div>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, CircleAlert, Flame, Home, LoaderCircle, MessageCircle, Send, Share2, ShoppingBag, UserRound, Users } from 'lucide-vue-next'
import { getLiveRoom, listLiveComments, recordLiveEngagement, submitLiveComment } from '@/api/shop'
import { applyImageFallback } from '@/utils/imageFallback'
import { money } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const videoRef = ref(null)
const commentListRef = ref(null)
const room = ref({})
const comments = ref([])
const commentText = ref('')
const commentSaving = ref(false)
const loading = ref(false)
const error = ref('')
const playerFailed = ref(false)
const enteredAt = ref(Date.now())
let hls = null
let heartbeatTimer = null
let commentTimer = null

const visitorId = (() => {
  const key = 'live_visitor_id'
  try {
    const existing = localStorage.getItem(key)
    if (existing && /^[0-9a-f-]{36}$/i.test(existing)) return existing
    const value = crypto.randomUUID()
    localStorage.setItem(key, value)
    return value
  } catch { return crypto.randomUUID() }
})()

const stateLabel = computed(() => ({ CONNECTING: '正在连接', LIVE: '直播中', UPCOMING: '直播预告', ENDED: '精彩回放' }[room.value.roomState] || '直播间'))
const canWatch = computed(() => ['LIVE', 'ENDED'].includes(room.value.roomState) && Boolean(room.value.room?.watchUrl))
const liveTypeLabel = computed(() => ({ PRODUCT: '厂家商品直播', PLATFORM: '平台讲解直播', FACTORY: '工厂实景直播' }[room.value.room?.liveType] || '平台直播'))
const actionDescription = computed(() => {
  if (room.value.roomState === 'LIVE') return '直播正在进行'
  if (room.value.roomState === 'CONNECTING') return '主播正在连接视频，请稍候'
  if (room.value.roomState === 'ENDED') return canWatch.value ? '本场提供回放' : '本场直播已结束'
  const value = room.value.room?.scheduledStartTime
  return value ? `${String(value).replace('T', ' ').slice(0, 16)} 开播` : '开播时间待平台确认'
})
const duration = () => Math.max(0, Math.min(86400, Math.floor((Date.now() - enteredAt.value) / 1000)))
const goBack = () => window.history.length > 1 ? router.back() : router.push('/live')

const postEvent = async (eventType, productId = null) => {
  if (!room.value.room?.id) return
  try { await recordLiveEngagement(room.value.room.id, { visitorId, eventType, productId, durationSeconds: duration() }) } catch { /* 不阻断观看 */ }
}

const initPlayer = async () => {
  if (!canWatch.value || !videoRef.value) return
  const url = room.value.room.watchUrl
  playerFailed.value = false
  if (/\.m3u8(?:$|\?)/i.test(url)) {
    const { default: Hls } = await import('hls.js')
    if (!Hls.isSupported()) {
      videoRef.value.src = url
      videoRef.value.play().catch(() => {})
      return
    }
    hls = new Hls({ enableWorker: true, lowLatencyMode: true, backBufferLength: 30 })
    hls.loadSource(url)
    hls.attachMedia(videoRef.value)
    hls.on(Hls.Events.ERROR, (_event, data) => { if (data?.fatal) playerFailed.value = true })
  } else videoRef.value.src = url
  videoRef.value.play().catch(() => {})
}

const openExternal = () => {
  try {
    const url = new URL(room.value.room.watchUrl)
    if (url.protocol !== 'https:') return
    window.open(url.href, '_blank', 'noopener,noreferrer')
  } catch { playerFailed.value = true }
}

const loadComments = async () => {
  if (!room.value.room?.id) return
  try {
    const res = await listLiveComments(room.value.room.id, { limit: 80 })
    comments.value = [...(res.data || [])].reverse()
    await nextTick()
    if (commentListRef.value) commentListRef.value.scrollTop = commentListRef.value.scrollHeight
  } catch { /* 评论失败不影响视频 */ }
}

const sendComment = async () => {
  if (!commentText.value || commentSaving.value) return
  commentSaving.value = true
  try { await submitLiveComment(room.value.room.id, { content: commentText.value, visitorId }); commentText.value = ''; await loadComments() }
  catch (event) { if (event?.response?.status === 401) router.push({ path: '/login', query: { redirect: route.fullPath } }) }
  finally { commentSaving.value = false }
}

const shareRoom = async () => {
  await postEvent('SHARE')
  const data = { title: room.value.room.title, text: room.value.room.subtitle || '一起观看商城直播', url: window.location.href }
  if (navigator.share) { try { await navigator.share(data); return } catch {} }
  try { await navigator.clipboard.writeText(window.location.href) } catch {}
}

const trackProduct = (productId) => postEvent('PRODUCT_CLICK', productId)
const load = async () => {
  loading.value = true; error.value = ''
  try {
    room.value = (await getLiveRoom(route.params.id)).data || {}
    enteredAt.value = Date.now()
    await nextTick(); await initPlayer(); await Promise.all([postEvent('ENTER'), loadComments()])
    heartbeatTimer = window.setInterval(() => postEvent('HEARTBEAT'), 30000)
    commentTimer = window.setInterval(loadComments, 4000)
  } catch (event) { error.value = event?.message || '直播间暂时加载失败' } finally { loading.value = false }
}

onMounted(load)
onBeforeUnmount(() => {
  if (heartbeatTimer) window.clearInterval(heartbeatTimer)
  if (commentTimer) window.clearInterval(commentTimer)
  postEvent('LEAVE')
  if (hls) { hls.destroy(); hls = null }
})
</script>

<style scoped>
.live-detail-page{min-height:100vh;padding-bottom:72px;background:#f4f5f7}.detail-header{position:sticky;top:0;z-index:20;height:58px;display:grid;grid-template-columns:48px 1fr 48px;align-items:center;padding:0 max(8px,calc((100% - 1120px)/2));background:rgba(255,255,255,.95);border-bottom:1px solid #eceff1;backdrop-filter:blur(12px)}.detail-header button,.detail-header>a{width:40px;height:40px;display:grid;place-items:center;color:#344054;background:transparent;border:0;border-radius:50%}.detail-header strong{text-align:center}.detail-header>a,.detail-header>button:last-child{justify-self:end}.watch-layout,.live-info-card,.live-products,.detail-state{width:min(1120px,calc(100% - 32px));margin:18px auto}.watch-layout{display:grid;grid-template-columns:minmax(0,1fr) 320px;gap:14px}.live-stage{position:relative;aspect-ratio:16/9;overflow:hidden;color:#fff;background:#101828;border-radius:22px}.live-stage>img,.live-stage>video{width:100%;height:100%;display:block;object-fit:cover;background:#050b14}.stage-shade{position:absolute;inset:0;background:linear-gradient(180deg,rgba(5,12,24,.02),rgba(5,12,24,.84))}.stage-state{position:absolute;z-index:2;top:16px;left:16px;padding:6px 10px;background:#0f9f6e;border-radius:999px;font-size:12px;font-weight:900}.stage-state.state-live{background:#ef1742}.stage-state.state-ended{background:#475467}.stage-copy{position:absolute;left:22px;right:22px;bottom:20px}.stage-copy h1{margin:0;font-size:27px}.stage-copy p{margin:8px 0 0;color:rgba(255,255,255,.82)}.player-fallback{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;background:rgba(5,12,24,.86)}.player-fallback button{padding:10px 16px;color:#fff;background:var(--brand-primary);border:0;border-radius:999px}.live-chat-card{min-height:0;display:flex;flex-direction:column;overflow:hidden;background:#fff;border:1px solid #e8edf3;border-radius:22px}.chat-heading{height:58px;display:flex;align-items:center;justify-content:space-between;padding:0 15px;border-bottom:1px solid #eef1f4}.chat-heading>div,.chat-heading>span{display:flex;align-items:center;gap:6px}.chat-heading>span{color:#667085;font-size:11px}.comment-list{flex:1;min-height:240px;max-height:470px;overflow:auto;padding:12px 15px}.comment-list p{display:flex;gap:7px;margin:0 0 10px;font-size:12px;line-height:1.5}.comment-list strong{flex:0 0 auto;color:var(--brand-primary)}.comment-list span{color:#344054;word-break:break-word}.comment-list .comment-empty{height:100%;display:grid;place-items:center;color:#98a2b3}.comment-form{display:grid;grid-template-columns:1fr 42px;gap:7px;padding:11px;border-top:1px solid #eef1f4}.comment-form input{min-width:0;height:40px;padding:0 12px;background:#f4f6f8;border:1px solid #e4e7ec;border-radius:999px;outline:none}.comment-form button{height:40px;display:grid;place-items:center;color:#fff;background:var(--brand-primary);border:0;border-radius:50%}.comment-form button:disabled{opacity:.45}.comment-closed{margin:0;padding:16px;color:#98a2b3;border-top:1px solid #eef1f4;font-size:12px;text-align:center}.live-info-card{display:grid;grid-template-columns:minmax(0,1fr) auto auto;align-items:center;gap:20px;padding:16px 20px;background:#fff;border:1px solid #e8edf3;border-radius:18px}.anchor-copy{display:flex;align-items:center;gap:11px;min-width:0}.anchor-avatar{width:42px;height:42px;display:grid;place-items:center;color:#fff;background:var(--brand-primary);border-radius:50%}.anchor-copy strong,.anchor-copy small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.anchor-copy small{margin-top:4px;color:#667085}.live-metrics{display:flex;gap:14px;color:#667085;font-size:12px}.live-metrics span{display:flex;align-items:center;gap:4px}.share-button{height:38px;display:flex;align-items:center;gap:5px;padding:0 14px;color:var(--brand-primary);background:#fff;border:1px solid var(--brand-primary);border-radius:999px;font-weight:800}.live-products{padding:20px;background:#fff;border-radius:20px}.section-title{display:flex;align-items:center;justify-content:space-between;margin-bottom:14px}.section-title>div{display:flex;align-items:center;gap:7px}.section-title svg{color:var(--brand-primary)}.section-title h2{margin:0;font-size:20px}.section-title>span{color:#98a2b3;font-size:12px}.product-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.product-card{overflow:hidden;color:#1d2939;border:1px solid #eceff1;border-radius:14px;text-decoration:none}.product-card>img{width:100%;aspect-ratio:1;display:block;object-fit:cover;background:#f4f5f7}.product-card>div{position:relative;padding:10px}.product-card strong,.product-card span{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.product-card strong{font-size:13px}.product-card span{margin:5px 0;color:#98a2b3;font-size:10px}.product-card b{color:var(--price-color,var(--brand-primary));font-size:16px}.product-card em{position:absolute;right:10px;bottom:10px;padding:4px 8px;color:#fff;background:var(--brand-primary);border-radius:999px;font-size:10px;font-style:normal}.empty-products,.detail-state{min-height:260px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;color:#98a2b3;background:#fff;border-radius:20px}.detail-state button{padding:9px 16px;color:#fff;background:var(--brand-primary);border:0;border-radius:999px}.spin{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:840px){.detail-header{padding:0 8px}.watch-layout,.live-info-card,.live-products,.detail-state{width:calc(100% - 16px);margin:10px auto}.watch-layout{display:block}.live-stage{aspect-ratio:16/10;border-radius:16px}.live-chat-card{margin-top:9px;border-radius:16px}.comment-list{height:190px;min-height:190px}.live-info-card{grid-template-columns:minmax(0,1fr) auto;padding:13px}.live-metrics{grid-column:1/-1;justify-content:space-between}.share-button{grid-column:2;grid-row:1}.live-products{padding:14px}.product-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.stage-copy h1{font-size:21px}}
</style>
