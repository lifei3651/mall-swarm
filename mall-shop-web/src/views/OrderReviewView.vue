<template>
  <main class="order-review-page">
    <header><button type="button" @click="back" :disabled="submitting" aria-label="返回我的订单">‹</button><h1>订单评价</h1></header>
    <p v-if="loading" class="state">正在核对订单评价资格…</p>
    <section v-else-if="completed" class="state">评价已提交，感谢你的真实反馈。</section>
    <form v-else-if="eligible" class="review-card" @submit.prevent="submit">
      <h2>商品评分</h2>
      <div class="ratings"><button v-for="star in 5" :key="star" type="button" :class="{ selected: rating === star }" :aria-pressed="rating === star" :disabled="submitting" @click="rating = star">{{ star }}星</button></div>
      <label for="review-content">评价内容</label>
      <textarea id="review-content" v-model="content" maxlength="1000" :disabled="submitting" placeholder="说说商品质量、使用体验和物流服务吧" />
      <p class="count">{{ content.length }}/1000</p>
      <button class="submit" type="submit" :disabled="submitting">{{ submitting ? '提交中…' : '提交评价' }}</button>
    </form>
    <section v-else class="state"><p>{{ hint || '请从我的订单选择已完成的商品进行评价。' }}</p><button v-if="retryable" type="button" @click="load">重新核对</button></section>
    <button class="back" type="button" @click="back" :disabled="submitting">返回我的订单</button>
    <div v-if="notice" class="notice-overlay"><section role="alertdialog" aria-modal="true" aria-labelledby="notice-title"><h2 id="notice-title">提示</h2><p>{{ notice }}</p><button type="button" autofocus @click="notice = ''">知道了</button></section></div>
  </main>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProductReviews, submitProductReview } from '@/api/shop'
import { hasShopSession } from '@/utils/shopSession'
const route = useRoute(), router = useRouter()
const validId = value => (typeof value !== 'number' || Number.isSafeInteger(value)) && /^[1-9]\d{0,18}$/.test(String(value || '')) ? String(value) : ''
const productId = computed(() => validId(route.params.id))
const reviewOrderItemId = computed(() => validId(route.query.orderItemId))
const loading = ref(false), eligible = ref(false), completed = ref(false), submitting = ref(false), retryable = ref(false)
const hint = ref(''), notice = ref(''), rating = ref(5), content = ref('')
let sequence = 0, disposed = false, loadedOwner = ''
const owner = () => hasShopSession() ? localStorage.getItem('shop_member') || '' : ''
const back = () => router.replace('/orders?tab=pending-review')
const load = async () => {
  const currentSequence = ++sequence, member = owner()
  eligible.value = false; retryable.value = false; hint.value = ''; loading.value = false
  if (!productId.value || !reviewOrderItemId.value || !member) return
  loading.value = true
  try {
    const result = await getProductReviews(productId.value, { orderItemId: reviewOrderItemId.value, pageNum: 1, pageSize: 1 })
    if (disposed || currentSequence !== sequence || member !== owner()) return
    loadedOwner = member
    eligible.value = result.data?.canReview === true
    hint.value = result.data?.reviewHint || '该订单暂不可评价或已经评价过。'
  } catch (error) {
    if (disposed || currentSequence !== sequence || member !== owner()) return
    retryable.value = true; hint.value = error?.message || '评价资格核对失败，请重试'; notice.value = hint.value
  } finally { if (!disposed && currentSequence === sequence) loading.value = false }
}
const submit = async () => {
  if (submitting.value || completed.value || !eligible.value) return
  if (!owner() || owner() !== loadedOwner) { eligible.value = false; notice.value = '登录状态已变化，请返回订单重新进入'; return }
  if (!productId.value || !reviewOrderItemId.value) return
  if (!content.value.trim() || ![1,2,3,4,5].includes(rating.value)) { notice.value = '请填写评价内容并选择1至5星评分'; return }
  const currentSequence = sequence, member = owner()
  submitting.value = true
  try {
    await submitProductReview(productId.value, { orderItemId: reviewOrderItemId.value, rating: rating.value, content: content.value.trim() })
    if (disposed || currentSequence !== sequence || member !== owner()) return
    completed.value = true; eligible.value = false; content.value = ''; notice.value = '评价提交成功'
  } catch (error) {
    if (!disposed && currentSequence === sequence && member === owner()) notice.value = error?.message || '评价提交失败，请重试'
  } finally { if (!disposed && currentSequence === sequence) submitting.value = false }
}
watch(() => [productId.value, reviewOrderItemId.value], () => {
  completed.value = false; submitting.value = false; content.value = ''; rating.value = 5; notice.value = ''; load()
}, { immediate: true })
onBeforeUnmount(() => { disposed = true; sequence++ })
</script>

<style scoped>
.order-review-page { min-height:100vh; padding:16px 16px calc(32px + env(safe-area-inset-bottom)); background:#f5f6f7; color:#20242e; }
header { display:flex; align-items:center; gap:16px; margin-bottom:20px; } h1 { font-size:18px; margin:0; } h2 { font-size:16px; margin:0 0 16px; }
button { border:0; cursor:pointer; border-radius:8px; padding:12px 16px; background:#fff; color:inherit; } button:disabled { opacity:.55; cursor:default; }
header button { font-size:28px; line-height:24px; }
.review-card,.state { padding:20px; border-radius:12px; background:#fff; line-height:1.6; }
.ratings { display:flex; gap:6px; margin-bottom:20px; } .ratings button { flex:1; min-width:0; padding:12px 0; background:#f5f6f7; } .ratings .selected { background:var(--brand-soft,#fde8ed); color:var(--brand,#e7193f); }
label { display:block; font-size:14px; margin-bottom:12px; } textarea { box-sizing:border-box; width:100%; min-height:150px; resize:vertical; border:0; border-radius:8px; padding:12px; background:#f5f6f7; font:inherit; }
.count { text-align:right; font-size:12px; color:#6b7280; } .submit { width:100%; background:var(--brand,#e7193f); color:#fff; } .back { width:100%; margin-top:16px; }
.notice-overlay { position:fixed; inset:0; z-index:1000; background:#0006; display:grid; place-items:center; padding:24px; } .notice-overlay section { width:100%; max-width:320px; box-sizing:border-box; padding:24px; border-radius:16px; background:#fff; } .notice-overlay p { overflow-wrap:anywhere; line-height:1.6; } .notice-overlay button { width:100%; background:#f5f6f7; }
</style>
