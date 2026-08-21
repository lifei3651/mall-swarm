<template>
  <div class="product-detail-page">
    <div v-if="loading" class="detail-state">
      <div class="loading-ring"></div>
      <span>正在加载商品详情...</span>
    </div>

    <div v-else-if="!product" class="detail-state error-state">
      <PackageX :size="46" />
      <h2>商品详情加载失败</h2>
      <p>{{ errorMessage || '商品不存在或已经下架' }}</p>
      <div class="state-actions">
        <button class="plain-button" @click="goBack">返回上一页</button>
        <button class="primary-button" @click="fetchProduct">重新加载</button>
      </div>
    </div>

    <template v-else>
      <section class="gallery-section">
        <button class="floating-back" type="button" aria-label="返回" @click="goBack"><ArrowLeft :size="24" /></button>
        <button class="floating-share" type="button" aria-label="分享" @click="shareProduct"><Share2 :size="22" /></button>
        <div ref="galleryScroller" class="gallery-scroller" @scroll.passive="onGalleryScroll">
          <div v-for="(image, index) in mainImages" :key="image + index" class="gallery-slide">
            <img :src="image" :alt="`${product.productName} 主图${index + 1}`" @error="applyImageFallback" />
          </div>
        </div>
        <span class="image-count">{{ activeImageIndex + 1 }}/{{ mainImages.length }}</span>
      </section>

      <section class="price-section">
        <div class="price-main"><span>¥</span><strong>{{ money(displayProduct.salePrice) }}</strong></div>
        <del v-if="Number(displayProduct.marketPrice || 0) > Number(displayProduct.salePrice || 0)">¥{{ money(displayProduct.marketPrice) }}</del>
        <span v-if="showPv && Number(displayProduct.pvValue || 0) > 0" class="pv-badge">PV {{ money(displayProduct.pvValue) }}</span>
        <span class="sales-count">已售 {{ product.salesCount || 0 }}+</span>
      </section>

      <section class="product-title-section">
        <span class="seller-badge">{{ product.merchantName || '平台自营' }}</span>
        <h1>{{ product.productName }}</h1>
        <p v-if="product.subtitle">{{ product.subtitle }}</p>
      </section>

      <section class="purchase-section">
        <div class="compact-row">
          <span class="row-label">发货</span>
          <div class="row-value"><strong>{{ product.deliveryAddress || '商家仓库' }}</strong><small>{{ product.deliveryTime || '付款后尽快发货' }}</small></div>
        </div>
        <div class="compact-row">
          <span class="row-label">运费</span>
          <div class="row-value"><strong>{{ freightLabel }}</strong></div>
        </div>

        <div v-if="skus.length" class="sku-block">
          <div class="block-title"><strong>选择规格</strong><span v-if="selectedSku">已选：{{ selectedSku.skuName }}</span></div>
          <div class="sku-options">
            <button
              v-for="sku in skus"
              :key="sku.id"
              type="button"
              class="sku-option"
              :class="{ active: selectedSkuId === sku.id }"
              :disabled="Number(sku.stock || 0) <= 0"
              @click="selectSku(sku)"
            >
              <img v-if="sku.imageUrl" :src="sku.imageUrl" :alt="sku.skuName" @error="applyImageFallback" />
              <span>{{ sku.skuName }}</span>
              <em v-if="Number(sku.stock || 0) <= 0">缺货</em>
            </button>
          </div>
          <div v-if="selectedSkuAttrs.length" class="selected-attrs">
            <span v-for="item in selectedSkuAttrs" :key="item">{{ item }}</span>
          </div>
        </div>

        <div class="quantity-row">
          <strong>购买数量</strong>
          <div class="quantity-control">
            <button type="button" aria-label="减少购买数量" :disabled="quantity <= 1" @click="decreaseQuantity"><Minus :size="17" aria-hidden="true" /></button>
            <span aria-live="polite" :aria-label="`当前购买数量${quantity}件`">{{ quantity }}</span>
            <button type="button" aria-label="增加购买数量" :disabled="quantity >= currentStock" @click="increaseQuantity"><Plus :size="17" aria-hidden="true" /></button>
          </div>
          <small>库存 {{ currentStock }} 件</small>
          <small v-if="Number(displayProduct.purchaseLimit || 0) > 0" class="purchase-limit-hint">每位会员限购 {{ displayProduct.purchaseLimit }} 件</small>
        </div>
      </section>

      <section v-if="serviceGuarantees.length" class="content-section guarantee-section">
        <div class="section-heading-row">
          <div><h2>服务保障</h2><p>具体服务以本商品说明和商城规则为准</p></div>
          <ShieldCheck :size="24" />
        </div>
        <div class="guarantee-list">
          <div v-for="item in serviceGuarantees" :key="`${item.title}-${item.icon}`" class="guarantee-item">
            <div class="guarantee-icon"><component :is="guaranteeIcon(item.icon)" :size="23" /></div>
            <div><h3>{{ item.title }}</h3><p>{{ item.description }}</p></div>
          </div>
        </div>
      </section>

      <section class="content-section review-section">
        <div class="review-heading">
          <div>
            <h2>商品评价 <small>({{ reviewData.reviewCount || 0 }})</small></h2>
            <p v-if="reviewData.reviewCount">综合评分 <strong>{{ reviewData.averageRating }}</strong> / 5</p>
            <p v-else>暂无评价，真实确认收货后可评价</p>
          </div>
          <button class="write-review-button" type="button" @click="openReviewForm">写评价</button>
        </div>

        <div v-if="reviewData.reviewCount" class="rating-distribution">
          <div class="dist-score-block">
            <strong>{{ reviewData.averageRating }}</strong>
            <span>/ 5</span>
            <div class="dist-stars">
              <Star v-for="star in 5" :key="star" :size="12" :fill="star <= Math.round(reviewData.averageRating) ? '#ef4444' : '#e5e7eb'" :color="star <= Math.round(reviewData.averageRating) ? '#ef4444' : '#e5e7eb'" />
            </div>
          </div>
          <div class="dist-bars">
            <div v-for="star in 5" :key="star" class="dist-row">
              <span>{{ 6 - star }} 星</span>
              <div class="dist-bar-track">
                <div class="dist-bar-fill" :style="{ width: barPercent(6 - star) + '%' }"></div>
              </div>
              <small>{{ countForStar(6 - star) }}</small>
            </div>
          </div>
        </div>

        <div v-if="reviewFormVisible" class="review-form">
          <div class="rating-picker">
            <span>商品评分</span>
            <button v-for="star in 5" :key="star" type="button" :aria-label="`${star}星`" @click="reviewForm.rating = star">
              <Star :size="27" :fill="star <= reviewForm.rating ? '#ef4444' : 'transparent'" :color="star <= reviewForm.rating ? '#ef4444' : '#cbd5e1'" />
            </button>
          </div>
          <textarea v-model="reviewForm.content" maxlength="1000" placeholder="分享商品质量、使用体验和物流服务吧" />
          <div class="review-form-footer"><span>{{ reviewForm.content.length }}/1000</span><button type="button" :disabled="reviewSubmitting" @click="submitReviewForm">{{ reviewSubmitting ? '提交中...' : '提交评价' }}</button></div>
        </div>

        <div v-if="reviews.length" class="review-list">
          <article v-for="review in reviews" :key="review.id" class="review-item">
            <div class="review-user">
              <div class="review-avatar"><img v-if="review.reviewerAvatar" :src="review.reviewerAvatar" alt="买家头像" @error="applyImageFallback" /><UserRound v-else :size="20" /></div>
              <div><strong>{{ review.reviewerName }}</strong><div class="review-stars"><Star v-for="star in 5" :key="star" :size="14" :fill="star <= review.rating ? '#ef4444' : '#e5e7eb'" :color="star <= review.rating ? '#ef4444' : '#e5e7eb'" /></div></div>
              <time>{{ formatDate(review.createTime) }}</time>
            </div>
            <p>{{ review.content }}</p>
          </article>
          <button v-if="reviews.length < Number(reviewData.page?.total || 0)" class="load-more" type="button" :disabled="reviewLoading" @click="loadMoreReviews">{{ reviewLoading ? '加载中...' : '查看更多评价' }}</button>
        </div>
        <div v-else-if="!reviewLoading" class="empty-copy">暂时还没有商品评价</div>
      </section>

      <section class="content-section introduction-section">
        <div class="section-title"><span></span><h2>商品介绍</h2><span></span></div>
        <p v-if="product.detail" class="detail-copy">{{ product.detail }}</p>
        <div v-if="detailImages.length" class="detail-images">
          <img v-for="(image, index) in detailImages" :key="image + index" :src="image" :alt="`${product.productName} 详情图${index + 1}`" loading="lazy" @error="applyImageFallback" />
        </div>
        <div v-if="!product.detail && !detailImages.length" class="empty-copy">商家暂未上传图文介绍</div>
      </section>

      <section class="content-section after-sale-section">
        <div class="section-title"><span></span><h2>售后说明</h2><span></span></div>
        <div class="after-sale-card">
          <p class="after-sale-lead">请在下单前阅读，具体服务以商品页面、订单状态和商城交易规则为准。</p>
          <p v-for="(line, index) in afterSalePolicyLines" :key="index" class="after-sale-line">{{ line }}</p>
          <p class="after-sale-footnote">如需帮助，请通过订单售后入口联系客服。</p>
        </div>
      </section>

      <div class="mobile-buy-bar">
        <RouterLink class="mini-action" to="/"><Store :size="21" /><span>首页</span></RouterLink>
        <RouterLink class="mini-action detail-cart-link" to="/cart">
          <span class="detail-cart-icon"><ShoppingCart :size="21" /><i v-if="count">{{ count > 99 ? '99+' : count }}</i></span>
          <span>购物车</span>
        </RouterLink>
        <button class="main-action cart-action" :disabled="soldOut || purchaseActionPending" @click="addToCart">加入购物车</button>
        <button class="main-action buy-action" :disabled="soldOut || purchaseActionPending" @click="buyNow">{{ soldOut ? '暂时缺货' : '立即购买' }}</button>
      </div>
    </template>

    <div v-if="toast" class="toast">{{ toast }}</div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  Share2,
  BadgeCheck,
  Ban,
  HeartHandshake,
  Minus,
  PackageCheck,
  PackageX,
  Plus,
  RotateCcw,
  ShieldCheck,
  ShoppingCart,
  Star,
  Store,
  Truck,
  UserRound,
  Zap,
} from 'lucide-vue-next'
import { getProduct, getProductReviews, submitProductReview } from '@/api/shop'
import { useCart } from '@/store/cart'
import { checkCartPurchaseLimit } from '@/utils/purchaseLimit'
import { hasShopSession } from '@/utils/shopSession'
import { requireShopSession } from '@/utils/authNavigation'
import { cartItemKey, resolveCurrentStock, stockAdditionViolation, stockQuantityViolation } from '@/utils/stockRules'
import { money } from '@/utils/format'
import { toPublicWebUrl } from '@/utils/appEnvironment'
import { applyImageFallback } from '@/utils/imageFallback'

const route = useRoute()
const router = useRouter()
const { add, count, beginDirectCheckout, getQuantity, getProductQuantity } = useCart()

// 详情页可能通过分享链接或刷新直接打开，此时浏览器没有可返回的历史记录。
// 有上一页时返回原页面；没有上一页时回到商城首页，避免点击后无任何反馈。
const goBack = () => {
  if (window.history.state?.back) {
    router.back()
    return
  }
  router.replace({ name: 'Home' })
}

const galleryScroller = ref(null)
const product = ref(null)
const displayConfig = ref({})
const skus = ref([])
const selectedSkuId = ref(null)
const quantity = ref(1)
const loading = ref(false)
const errorMessage = ref('')
const toast = ref('')
const purchaseActionPending = ref(false)
const activeImageIndex = ref(0)
const reviewData = ref({ reviewCount: 0, averageRating: 0, canReview: false, reviewHint: '', page: { list: [], total: 0 } })
const reviews = ref([])
const reviewPage = ref(1)
const reviewLoading = ref(false)
const reviewSubmitting = ref(false)
const reviewFormVisible = ref(false)
const reviewForm = ref({ rating: 5, content: '' })

const parseArray = (value) => {
  if (Array.isArray(value)) return value.filter(Boolean)
  try { const parsed = JSON.parse(value || '[]'); return Array.isArray(parsed) ? parsed.filter(Boolean) : [] } catch { return [] }
}

const parseObject = (value) => {
  if (value && typeof value === 'object' && !Array.isArray(value)) return value
  try { const parsed = JSON.parse(value || '{}'); return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {} } catch { return {} }
}

const legacyGuarantees = {
  '七天无理由': { icon: 'return', description: '符合平台规则且商品完好的，可在商城当前配置的售后期限内申请无理由退货。' },
  '正品保障': { icon: 'shield', description: '严控商品来源与质量，为消费者提供品质保障。' },
  '极速退款': { icon: 'refund', description: '售后审核通过后，平台将尽快完成退款处理。' },
  '破损包赔': { icon: 'package', description: '商品运输途中发生破损，可凭有效凭证申请售后处理。' },
  '运费险': { icon: 'truck', description: '符合条件的退货订单可按保险规则获得退货运费补偿。' },
}

const guaranteeIcons = { shield: ShieldCheck, return: RotateCcw, package: PackageCheck, refund: Zap, ban: Ban, truck: Truck, heart: HeartHandshake, badge: BadgeCheck }
const guaranteeIcon = (name) => guaranteeIcons[name] || ShieldCheck
const defaultAfterSalePolicy = '1. 签收商品时请先检查外包装和商品状态，如有破损、错发或漏发，请及时联系客服。\n2. 商品售后申请须符合商城交易与售后规则，并提供必要的订单信息和凭证。\n3. 退款金额以订单实际支付金额和审核结果为准，处理进度可在订单详情中查看。\n4. 退货运费承担方式以售后审核结果和商品页面说明为准。\n5. 不同商品可能存在特殊保存、使用或售后要求，请以商品详情和客服说明为准。'
const boundedPv = (pv, salePrice) => Math.min(
  Math.max(0, Number(pv || 0)),
  Math.max(0, Number(salePrice || 0)),
)
const selectedSku = computed(() => skus.value.find((sku) => sku.id === selectedSkuId.value) || null)
const showPv = computed(() => Number(displayConfig.value.showPv || 0) === 1)
const mainImages = computed(() => {
  const images = [product.value?.coverUrl, ...parseArray(product.value?.galleryUrls)].filter(Boolean)
  return [...new Set(images.length ? images : [''])]
})
const detailImages = computed(() => parseArray(product.value?.detailImages))
const afterSalePolicyLines = computed(() => (String(product.value?.afterSalePolicy || '').trim() || defaultAfterSalePolicy)
  .split(/\r?\n/)
  .map((line) => line.trim())
  .filter(Boolean))
const serviceGuarantees = computed(() => parseArray(product.value?.serviceTags).map((item) => {
  if (typeof item === 'string') {
    const preset = legacyGuarantees[item] || {}
    return { enabled: true, icon: preset.icon || 'shield', title: item, description: preset.description || '以商城售后规则及商品实际情况为准。' }
  }
  return { enabled: item?.enabled !== false, icon: item?.icon || 'shield', title: item?.title || '', description: item?.description || '' }
}).filter((item) => item.enabled && item.title))
const selectedSkuAttributeEntries = computed(() => Object.entries(parseObject(selectedSku.value?.attrsJson)))
const selectedSkuAttrs = computed(() => selectedSkuAttributeEntries.value.map(([key, value]) => `${key}：${value}`))
const displayProduct = computed(() => selectedSku.value ? {
  ...product.value,
  skuId: selectedSku.value.id,
  skuName: selectedSku.value.skuName,
  skuAttrs: selectedSku.value.attrsJson,
  coverUrl: selectedSku.value.imageUrl || product.value.coverUrl,
  salePrice: selectedSku.value.salePrice,
  marketPrice: selectedSku.value.marketPrice,
  pvValue: boundedPv(Number(selectedSku.value.pvValue || 0) > 0 ? selectedSku.value.pvValue : product.value.pvValue, selectedSku.value.salePrice),
  stock: selectedSku.value.stock,
} : (product.value ? { ...product.value, pvValue: boundedPv(product.value.pvValue, product.value.salePrice) } : {}))
const currentStock = computed(() => Math.max(0, Number(displayProduct.value.stock || 0)))
const soldOut = computed(() => product.value?.status !== 1 || currentStock.value <= 0)
const freightLabel = computed(() => {
  const type = Number(product.value?.freightType || 0)
  if (type === 1) return `统一运费 ¥${money(product.value?.freightAmount)}`
  if (type === 2) return `满 ¥${money(product.value?.freeShippingAmount)} 包邮，未满 ¥${money(product.value?.freightAmount)}`
  if (type === 3) return '按配送地区计算，部分地区暂不配送'
  return '全国包邮'
})

let toastTimer = null
const showToast = (message) => {
  toast.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 2200)
}
const shareProduct = async () => {
  const url = toPublicWebUrl(`/product/${route.params.id}`)
  const title = product.value?.productName || '商品推荐'
  if (navigator.share) {
    try { await navigator.share({ title, url }) } catch {}
  } else {
    try { await navigator.clipboard.writeText(url); showToast('链接已复制') } catch { showToast('分享失败') }
  }
}
const onGalleryScroll = (event) => {
  const width = event.currentTarget.clientWidth || 1
  activeImageIndex.value = Math.max(0, Math.min(mainImages.value.length - 1, Math.round(event.currentTarget.scrollLeft / width)))
}

const fetchReviews = async (reset = true) => {
  if (!product.value?.id) return
  reviewLoading.value = true
  try {
    const pageNum = reset ? 1 : reviewPage.value
    const res = await getProductReviews(product.value.id, { pageNum, pageSize: 5 })
    reviewData.value = res.data || reviewData.value
    const list = res.data?.page?.list || []
    reviews.value = reset ? list : [...reviews.value, ...list]
    if (reset) reviewPage.value = 1
  } finally { reviewLoading.value = false }
}

const fetchProduct = async () => {
  loading.value = true
  errorMessage.value = ''
  product.value = null
  try {
    const res = await getProduct(route.params.id)
    product.value = res.data?.product || res.data || null
    displayConfig.value = res.data?.displayConfig || {}
    skus.value = res.data?.skus || []
    selectedSkuId.value = skus.value.find((sku) => Number(sku.stock || 0) > 0)?.id || skus.value[0]?.id || null
    quantity.value = 1
    activeImageIndex.value = 0
    await fetchReviews(true)
  } catch (error) {
    errorMessage.value = error?.message || '商品详情加载失败'
  } finally { loading.value = false }
}

const selectSku = (sku) => { selectedSkuId.value = sku.id; quantity.value = 1 }
const decreaseQuantity = () => { quantity.value = Math.max(1, quantity.value - 1) }
const increaseQuantity = () => { quantity.value = Math.min(currentStock.value, quantity.value + 1) }
const addToCart = async () => {
  if (!requireShopSession(router, route.fullPath, '请先登录后再加入购物车')) return
  if (soldOut.value) return showToast('该商品暂时缺货')
  if (purchaseActionPending.value) return
  purchaseActionPending.value = true
  try {
    const detail = (await getProduct(displayProduct.value.id)).data || {}
    const latestProduct = { ...displayProduct.value, stock: resolveCurrentStock(displayProduct.value, detail) }
    const stockError = stockAdditionViolation(latestProduct.stock, quantity.value, getQuantity(cartItemKey(latestProduct)))
    if (stockError) throw new Error(stockError)
    await checkCartPurchaseLimit(latestProduct, quantity.value, getProductQuantity(latestProduct.id))
    add(latestProduct, quantity.value)
    showToast(`已加入购物车，数量 +${quantity.value}`)
  } catch (error) {
    showToast(error?.message || '当前商品暂时无法加入购物车')
  } finally {
    purchaseActionPending.value = false
  }
}
const buyNow = async () => {
  if (!requireShopSession(router, route.fullPath, '请先登录后再购买商品')) return
  if (soldOut.value) return showToast('该商品暂时缺货')
  if (purchaseActionPending.value) return
  purchaseActionPending.value = true
  try {
    const detail = (await getProduct(displayProduct.value.id)).data || {}
    const latestProduct = { ...displayProduct.value, stock: resolveCurrentStock(displayProduct.value, detail) }
    const stockError = stockQuantityViolation(latestProduct.stock, quantity.value)
    if (stockError) throw new Error(stockError)
    await checkCartPurchaseLimit(latestProduct, quantity.value, 0)
    beginDirectCheckout(latestProduct, quantity.value)
    router.push('/checkout')
  } catch (error) {
    showToast(error?.message || '当前商品暂时无法购买')
  } finally {
    purchaseActionPending.value = false
  }
}

const openReviewForm = () => {
  if (!hasShopSession()) {
    router.push({ name: 'Login', query: { redirect: route.fullPath } })
    return
  }
  if (!reviewData.value.canReview) return showToast(reviewData.value.reviewHint || '购买并确认收货后可以评价')
  reviewFormVisible.value = !reviewFormVisible.value
}
const submitReviewForm = async () => {
  if (!reviewForm.value.content.trim()) return showToast('请填写评价内容')
  reviewSubmitting.value = true
  try {
    await submitProductReview(product.value.id, { rating: reviewForm.value.rating, content: reviewForm.value.content.trim() })
    reviewForm.value = { rating: 5, content: '' }
    reviewFormVisible.value = false
    await fetchReviews(true)
    showToast('评价提交成功')
  } catch (error) { showToast(error?.message || '评价提交失败') }
  finally { reviewSubmitting.value = false }
}
const loadMoreReviews = async () => { reviewPage.value += 1; await fetchReviews(false) }
const formatDate = (value) => value ? String(value).replace('T', ' ').slice(0, 10) : ''

const starCounts = computed(() => ({
  5: Number(reviewData.value.star5Count || 0),
  4: Number(reviewData.value.star4Count || 0),
  3: Number(reviewData.value.star3Count || 0),
  2: Number(reviewData.value.star2Count || 0),
  1: Number(reviewData.value.star1Count || 0),
}))
const countForStar = (star) => starCounts.value[star] || 0
const barPercent = (star) => {
  const max = Math.max(1, ...Object.values(starCounts.value))
  return Math.round((countForStar(star) / max) * 100)
}

watch(() => route.params.id, fetchProduct, { immediate: true })
onBeforeUnmount(() => window.clearTimeout(toastTimer))
</script>

<style scoped>
.product-detail-page { width:min(760px,100%); min-height:100vh; margin:0 auto; padding-bottom:96px; background:#f3f4f6; color:#20242b; }
.detail-state { min-height:70vh; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:14px; padding:30px; text-align:center; background:#fff; color:#6b7280; }
.detail-state h2,.detail-state p { margin:0; }
.state-actions { display:flex; gap:10px; margin-top:8px; }
.plain-button,.primary-button { min-height:42px; padding:0 18px; border-radius:22px; border:1px solid #d1d5db; background:#fff; }
.primary-button { color:#fff; background:var(--brand-primary); border-color:var(--brand-primary); }
.loading-ring { width:36px; height:36px; border:3px solid #e5e7eb; border-top-color:var(--brand-primary); border-radius:50%; animation:spin .8s linear infinite; }
@keyframes spin { to { transform:rotate(360deg); } }

.gallery-section { position:relative; overflow:hidden; aspect-ratio:1/1; background:#fff; }
.gallery-scroller { width:100%; height:100%; display:flex; overflow-x:auto; scroll-snap-type:x mandatory; scroll-behavior:smooth; scrollbar-width:none; overscroll-behavior-x:contain; }
.gallery-scroller::-webkit-scrollbar { display:none; }
.gallery-slide { width:100%; height:100%; flex:0 0 100%; scroll-snap-align:start; scroll-snap-stop:always; }
.gallery-slide img { width:100%; height:100%; display:block; object-fit:contain; background:#fff; }
.floating-back { position:absolute; z-index:4; left:16px; top:16px; width:42px; height:42px; display:grid; place-items:center; padding:0; color:#222; background:rgba(255,255,255,.9); border:0; border-radius:50%; box-shadow:0 2px 10px rgba(0,0,0,.1); }
.floating-share { position:absolute; z-index:4; right:16px; top:16px; width:42px; height:42px; display:grid; place-items:center; padding:0; color:#222; background:rgba(255,255,255,.9); border:0; border-radius:50%; box-shadow:0 2px 10px rgba(0,0,0,.1); }
.image-count { position:absolute; z-index:3; right:16px; bottom:16px; min-width:50px; height:30px; display:grid; place-items:center; padding:0 11px; color:#fff; background:rgba(28,28,28,.58); border-radius:17px; font-size:14px; }

.price-section { min-height:84px; display:flex; align-items:flex-end; gap:10px; padding:18px 18px 16px; margin-top:8px; background:#fff; }
.price-main { display:flex; align-items:baseline; color:var(--brand-primary); font-weight:900; }
.price-main span { margin-right:3px; font-size:20px; }
.price-main strong { font-size:38px; line-height:1; letter-spacing:-1px; }
.price-section del { color:#9ca3af; font-size:13px; }
.pv-badge { padding:3px 7px; color:var(--brand-primary); background:var(--brand-primary-soft); border-radius:999px; font-size:11px; font-weight:800; }
.sales-count { margin-left:auto; color:#6b7280; font-size:13px; }

.product-title-section { padding:18px; background:#fff; border-top:1px solid #f1f1f1; }
.seller-badge { display:inline-flex; align-items:center; min-height:24px; margin-bottom:9px; padding:2px 9px; color:#8a5a20; background:#fff7e8; border:1px solid #f3dfb9; border-radius:999px; font-size:12px; font-weight:700; }
.product-title-section h1 { margin:0; font-size:22px; line-height:1.45; }
.product-title-section p { margin:9px 0 0; color:#7b818b; font-size:14px; line-height:1.65; }
.purchase-section { padding:8px 18px 20px; margin-top:8px; background:#fff; }
.compact-row { min-height:62px; display:flex; gap:16px; align-items:flex-start; padding:13px 0; border-bottom:1px solid #f0f1f2; }
.row-label { width:34px; flex:0 0 auto; color:#8a9099; font-size:14px; }
.row-value { min-width:0; display:flex; flex-direction:column; gap:6px; font-size:14px; }
.row-value small { color:#8a9099; }
.sku-block { padding-top:18px; }
.block-title { display:flex; justify-content:space-between; gap:10px; font-size:15px; }
.block-title span { color:#8a9099; font-size:12px; }
.sku-options { display:flex; flex-wrap:wrap; gap:10px; margin-top:12px; }
.sku-option { min-height:42px; display:inline-flex; align-items:center; gap:7px; padding:5px 14px; color:#333; background:#f7f7f7; border:1px solid transparent; border-radius:7px; }
.sku-option.active { color:var(--brand-primary); background:var(--brand-primary-soft); border-color:var(--brand-primary); }
.sku-option:disabled { opacity:.48; cursor:not-allowed; }
.sku-option img { width:31px; height:31px; object-fit:cover; border-radius:4px; }
.sku-option em { color:#999; font-size:11px; font-style:normal; }
.selected-attrs { display:flex; flex-wrap:wrap; gap:7px; margin-top:10px; }
.selected-attrs span { padding:4px 7px; color:#6b7280; background:#f3f4f6; border-radius:4px; font-size:12px; }
.quantity-row { display:flex; align-items:center; gap:12px; padding-top:20px; }
.quantity-row strong { font-size:15px; }
.quantity-row small { color:#9ca3af; }
.quantity-row .purchase-limit-hint { color:var(--brand-primary); }
.quantity-control { height:36px; display:grid; grid-template-columns:36px 44px 36px; margin-left:auto; overflow:hidden; border:1px solid #e5e7eb; border-radius:6px; }
.quantity-control button,.quantity-control span { display:grid; place-items:center; padding:0; background:#fff; border:0; }
.quantity-control span { border-left:1px solid #e5e7eb; border-right:1px solid #e5e7eb; }

.content-section { margin-top:8px; background:#fff; }
.section-title { display:grid; grid-template-columns:minmax(30px,1fr) auto minmax(30px,1fr); align-items:center; gap:15px; padding:22px 18px; }
.section-title h2 { margin:0; font-size:19px; }
.section-title span { height:1px; background:linear-gradient(90deg,transparent,#d1d5db); }
.section-title span:last-child { background:linear-gradient(90deg,#d1d5db,transparent); }
.detail-copy { margin:0; padding:0 18px 20px; color:#525866; line-height:1.9; white-space:pre-wrap; }
.detail-images img { display:block; width:100%; height:auto; }
.empty-copy { padding:28px 18px; color:#9ca3af; text-align:center; }

.guarantee-section { padding:0 18px 22px; }
.section-heading-row { min-height:76px; display:flex; align-items:center; justify-content:space-between; gap:16px; border-bottom:1px solid #f0f1f2; }
.section-heading-row h2 { margin:0; font-size:19px; }
.section-heading-row p { margin:6px 0 0; color:#9ca3af; font-size:12px; }
.section-heading-row > svg { color:var(--brand-primary); }
.guarantee-list { padding-top:4px; }
.guarantee-item { display:grid; grid-template-columns:42px minmax(0,1fr); gap:12px; padding:16px 0; border-bottom:1px solid #f2f3f4; }
.guarantee-icon { width:38px; height:38px; display:grid; place-items:center; color:var(--brand-primary); background:var(--brand-primary-soft); border-radius:50%; }
.guarantee-item h3 { margin:0 0 7px; font-size:16px; }
.guarantee-item p { margin:0; color:#8a9099; font-size:13px; line-height:1.7; }
.after-sale-section { padding:0 18px 24px; }
.after-sale-card { padding:16px; background:#fafafa; border-radius:10px; }
.after-sale-lead { margin:0 0 12px; color:#525866; font-size:13px; line-height:1.7; }
.after-sale-line { margin:8px 0 0; color:#6b7280; font-size:13px; line-height:1.75; white-space:pre-wrap; }
.after-sale-footnote { margin:14px 0 0; padding-top:12px; color:#9ca3af; border-top:1px solid #eceff1; font-size:12px; line-height:1.7; }

.review-section { padding:0 18px 24px; }
.review-heading { min-height:88px; display:flex; align-items:center; justify-content:space-between; gap:14px; border-bottom:1px solid #f0f1f2; }
.review-heading h2 { margin:0; font-size:19px; }
.review-heading h2 small { color:#8a9099; font-size:13px; font-weight:500; }
.review-heading p { margin:7px 0 0; color:#8a9099; font-size:12px; }
.review-heading p strong { color:var(--brand-primary); }
.write-review-button { min-width:82px; height:36px; color:var(--brand-primary); background:#fff; border:1px solid var(--brand-primary); border-radius:18px; }

.rating-distribution { display: grid; grid-template-columns: 92px minmax(0, 1fr); gap: 20px; padding: 18px 0 10px; border-bottom: 1px solid #f0f1f2; }
.dist-score-block { display: flex; flex-direction: column; align-items: center; justify-content: center; }
.dist-score-block strong { font-size: 38px; color: #262b31; line-height: 1; }
.dist-score-block > span { color: #8a9099; font-size: 13px; margin: 4px 0 6px; }
.dist-stars { display: flex; gap: 1px; }
.dist-bars { display: flex; flex-direction: column; gap: 6px; padding: 4px 0; }
.dist-row { display: grid; grid-template-columns: 28px minmax(60px, 1fr) 28px; align-items: center; gap: 8px; font-size: 12px; color: #525866; }
.dist-row small { text-align: right; color: #8a9099; }
.dist-bar-track { height: 8px; background: #f0f1f2; border-radius: 4px; overflow: hidden; }
.dist-bar-fill { height: 100%; background: linear-gradient(90deg, var(--brand-primary), var(--brand-primary-dark, #c73a2b)); border-radius: 4px; transition: width .3s ease; }

.review-form { padding:16px; margin:16px 0 4px; background:#fafafa; border-radius:10px; }
.rating-picker { display:flex; align-items:center; gap:5px; margin-bottom:12px; }
.rating-picker > span { margin-right:8px; font-size:14px; font-weight:700; }
.rating-picker button { display:grid; place-items:center; padding:0; background:none; border:0; }
.review-form textarea { width:100%; min-height:108px; padding:12px; resize:vertical; background:#fff; border:1px solid #e5e7eb; border-radius:8px; outline:none; line-height:1.6; }
.review-form textarea:focus { border-color:var(--brand-primary); }
.review-form-footer { display:flex; align-items:center; justify-content:space-between; margin-top:10px; color:#9ca3af; font-size:12px; }
.review-form-footer button { height:36px; padding:0 18px; color:#fff; background:var(--brand-primary); border:0; border-radius:18px; font-weight:700; }
.review-form-footer button:disabled { opacity:.55; }
.review-item { padding:18px 0; border-bottom:1px solid #f0f1f2; }
.review-user { display:grid; grid-template-columns:38px minmax(0,1fr) auto; align-items:center; gap:10px; }
.review-avatar { width:38px; height:38px; display:grid; place-items:center; overflow:hidden; color:#94a3b8; background:#f1f5f9; border-radius:50%; }
.review-avatar img { width:100%; height:100%; object-fit:cover; }
.review-user strong { font-size:14px; }
.review-stars { display:flex; gap:1px; margin-top:4px; }
.review-user time { align-self:start; padding-top:3px; color:#9ca3af; font-size:12px; }
.review-item > p { margin:13px 0 0; color:#383d46; line-height:1.75; white-space:pre-wrap; }
.load-more { width:100%; height:42px; margin-top:14px; color:#6b7280; background:#fff; border:0; }

.mobile-buy-bar { position:fixed; z-index:36; left:50%; bottom:0; width:min(760px,100%); height:70px; display:grid; grid-template-columns:58px 58px minmax(100px,1fr) minmax(100px,1fr); gap:7px; padding:8px 10px; transform:translateX(-50%); background:#fff; border-top:1px solid #e5e7eb; box-shadow:0 -4px 18px rgba(0,0,0,.07); }
.mini-action { display:flex; flex-direction:column; align-items:center; justify-content:center; gap:2px; color:#555; font-size:11px; }
.main-action { border:0; border-radius:24px; font-size:15px; font-weight:800; }
.cart-action { color:#b45309; background:#f8c27d; }
.detail-cart-icon { position:relative; display:inline-flex; }
.detail-cart-icon i { position:absolute; top:-8px; right:-12px; min-width:17px; height:17px; display:grid; place-items:center; padding:0 4px; color:#fff; background:#ef334e; border:2px solid #fff; border-radius:999px; font-size:10px; font-style:normal; font-weight:800; line-height:1; }
.buy-action { color:#fff; background:var(--brand-primary); }
.main-action:disabled { opacity:.5; cursor:not-allowed; }
@media (max-width:920px) { .mobile-buy-bar { bottom:0; } }
@media (max-width:520px) {
  .floating-back { left:12px; top:12px; width:38px; height:38px; }
  .price-section { padding-left:14px; padding-right:14px; }
  .price-main strong { font-size:34px; }
  .product-title-section,.purchase-section,.guarantee-section,.review-section,.after-sale-section { padding-left:14px; padding-right:14px; }
  .product-title-section h1 { font-size:20px; }
  .mobile-buy-bar { grid-template-columns:52px 52px minmax(88px,1fr) minmax(88px,1fr); gap:5px; padding-left:7px; padding-right:7px; }
}
</style>
