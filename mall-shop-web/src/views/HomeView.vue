<template>
  <div class="home-page" :class="`layout-${layoutTemplate}`">
    <header class="home-topbar">
      <div class="home-topbar-inner">
        <RouterLink class="home-brand" to="/" aria-label="返回商城首页">
          <img v-if="home.logoUrl" :src="home.logoUrl" :alt="`${home.brandName || '商城'} Logo`" @error="home.logoUrl = ''" />
          <span v-else class="home-brand-mark">灵启</span>
          <strong>{{ home.brandName || '灵启商城' }}</strong>
        </RouterLink>
        <form class="home-search" role="search" @submit.prevent="submitSearch" @focusin="searchFocused = true" @focusout="scheduleHideSuggestions">
          <Search :size="19" />
          <input ref="searchInput" v-model="query.keyword" type="search" placeholder="搜索商品" aria-label="搜索商品" autocomplete="off" />
          <button type="submit" aria-label="搜索"><span>搜索</span><Search :size="18" /></button>
          <div v-if="searchFocused && (recentSearches.length || !query.keyword)" class="search-suggestions" @mousedown.prevent>
            <div v-if="recentSearches.length" class="suggestion-group">
              <span>最近搜索</span>
              <button v-for="item in recentSearches" :key="`recent-${item}`" type="button" @click="applySearch(item)">{{ item }}</button>
            </div>
            <div v-if="!query.keyword" class="suggestion-group">
              <span>热门搜索</span>
              <button v-for="item in hotSearches" :key="`hot-${item}`" type="button" @click="applySearch(item)">{{ item }}</button>
            </div>
          </div>
        </form>

        <RouterLink class="home-share" to="/invite" aria-label="分享邀请二维码">
          <Share2 :size="22" />
          <span>分享</span>
        </RouterLink>
      </div>
    </header>

    <section v-if="homeLoadError" class="home-init-error" role="alert" aria-live="polite">
      <strong>商城首页暂时加载失败</strong>
      <p>{{ homeLoadError }}</p>
      <button type="button" :disabled="homeLoading" @click="reloadHome">
        {{ homeLoading ? '重新加载中…' : '重新加载' }}
      </button>
    </section>

    <!-- 按配置顺序渲染首页模块 -->
    <template v-if="!homeLoadError" v-for="mod in homeModules" :key="mod.type">
      <!-- Banner轮播 -->
      <section v-if="mod.type === 'banner' && mod.enabled && banners.length" class="home-banner-section" aria-label="商城活动">
        <div class="banner-carousel">
          <div class="banner-track" :style="{ transform: `translateX(-${bannerIndex * 100}%)` }">
            <a v-for="banner in banners" :key="banner.id" class="banner-slide" :href="banner.linkValue || '#'" @click.prevent="handleBannerClick(banner)">
              <img :src="banner.imageUrl" :alt="banner.title || '活动广告'" loading="eager" />
            </a>
          </div>
          <div v-if="banners.length > 1" class="banner-dots">
            <button v-for="(_, i) in banners" :key="i" type="button" :class="{ active: bannerIndex === i }" @click="bannerIndex = i" :aria-label="`第${i+1}张`" />
          </div>
        </div>
      </section>

      <!-- 公告 -->
      <section v-else-if="mod.type === 'notice' && mod.enabled && notices.length" class="home-notice-section" aria-label="商城公告">
        <div class="notice-scroll">
          <Megaphone :size="16" />
          <span class="notice-text">{{ notices[noticeIndex]?.title || '' }}</span>
        </div>
      </section>

      <!-- 分类 -->
      <section v-else-if="mod.type === 'category' && mod.enabled && showHomeCategories && categoryEntries.length" class="home-category-section" aria-label="商品品类">
        <div class="category-grid">
          <button
            v-for="category in categoryEntries"
            :key="category.id || category.name"
            type="button"
            class="home-category-item"
            :class="{ active: query.categoryName === category.name }"
            @click="setCategory(category.name)"
          >
            <span class="category-circle">
              <img v-if="category.image" :src="category.image" :alt="category.name" loading="eager" decoding="async" />
              <span v-else>{{ category.name.slice(0, 1) }}</span>
            </span>
            <strong>{{ category.name }}</strong>
          </button>
        </div>
      </section>

      <!-- 信任条 -->
      <section v-else-if="mod.type === 'trust' && mod.enabled && showTrustStrip" class="home-trust-strip" aria-label="商城服务保障">
        <div v-for="item in trustItems" :key="item.title" class="trust-item">
          <strong>{{ item.title }}</strong>
          <span>{{ item.description }}</span>
        </div>
      </section>

      <!-- 商品列表 -->
      <section v-else-if="mod.type === 'products' && mod.enabled" ref="productSection" class="home-product-section">
      <div class="home-product-heading">
        <div>
          <h1>{{ query.categoryName || (query.keyword ? '搜索结果' : '精选商品') }}</h1>
          <p v-if="query.keyword">关键词：{{ query.keyword }}</p>
          <p v-else>商城好物，为你精选</p>
        </div>
        <button v-if="query.categoryName || query.keyword" type="button" class="clear-filter" @click="clearFilter">查看全部</button>
      </div>

      <ProductListSkeleton v-if="loading" :count="4" variant="grid" />

      <div v-else-if="products.length" class="home-product-grid">
        <article v-for="product in products" :key="product.id" class="home-product-card">
          <RouterLink class="home-product-image" :to="`/product/${product.id}`" :aria-label="`查看${product.productName}`">
            <img :src="product.coverUrl" :alt="product.productName" loading="lazy" />
            <span v-if="product.status !== 1 || product.stock <= 0" class="home-sold-out">已售罄</span>
          </RouterLink>

          <div class="home-product-info">
            <RouterLink class="home-product-copy" :to="`/product/${product.id}`">
              <h2>{{ product.productName }}</h2>
              <p>{{ product.subtitle || '精选商城好物，品质保障，售后无忧' }}</p>
            </RouterLink>
            <div class="home-sales">已售 {{ product.salesCount }}{{ product.salesCount >= 10000 ? '+' : ' 件' }}</div>
            <div class="home-purchase-row">
              <div class="home-price">
                <span>¥</span>
                <strong>{{ priceParts(product.salePrice).integer }}</strong>
                <small>.{{ priceParts(product.salePrice).decimal }}</small>
              </div>
              <button
                type="button"
                class="home-cart-button"
                :disabled="product.status !== 1 || product.stock <= 0 || isAddingProduct(product.id)"
                :aria-label="`立即加购${product.productName}`"
                @click="addProduct(product)"
              >
                <ShoppingCart :size="16" />
                {{ product.status !== 1 || product.stock <= 0 ? '已售罄' : '立即加购' }}
              </button>
            </div>
          </div>
        </article>
      </div>

      <div v-else class="home-empty">
        <PackageOpen :size="42" />
        <strong>没有找到相关商品</strong>
        <button type="button" @click="clearFilter">查看全部商品</button>
      </div>
    </section>
    </template>

    <div v-if="toast" class="toast">{{ toast }}</div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Megaphone, PackageOpen, Search, Share2, ShoppingCart } from 'lucide-vue-next'
import { getHome, getProduct, listProducts } from '@/api/shop'
import { useCart } from '@/store/cart'
import { money } from '@/utils/format'
import { applyBrandConfig } from '@/utils/brand'
import { readDisplayExtraConfig, resolveDisplayColors, resolveHomeModules } from '@/utils/displayConfig'
import { resolveQuickCartItem } from '@/utils/quickCart'
import { checkCartPurchaseLimit } from '@/utils/purchaseLimit'
import { cartItemKey, stockAdditionViolation } from '@/utils/stockRules'
import ProductListSkeleton from '@/components/ProductListSkeleton.vue'

const router = useRouter()
const { add, getQuantity, getProductQuantity } = useCart()
const home = ref({})
const products = ref([])
const loading = ref(false)
const homeLoading = ref(false)
const homeLoadError = ref('')
const toast = ref('')
const addingProductIds = ref(new Set())
const productSection = ref(null)
const searchInput = ref(null)
const query = ref({ keyword: '', categoryName: '' })
const searchFocused = ref(false)
const recentSearches = ref([])
const hotSearches = ['护理套装', '健康生活', '复购专区']

// Banner轮播
const banners = computed(() => (home.value.banners || []).filter((b) => Number(b.status) === 1))
const bannerIndex = ref(0)
let bannerTimer = null
const startBannerAutoplay = () => {
  stopBannerAutoplay()
  if (banners.value.length > 1) {
    bannerTimer = window.setInterval(() => { bannerIndex.value = (bannerIndex.value + 1) % banners.value.length }, 4000)
  }
}
const stopBannerAutoplay = () => { if (bannerTimer) { window.clearInterval(bannerTimer); bannerTimer = null } }
const handleBannerClick = (banner) => {
  if (!banner.linkValue) return
  const linkType = String(banner.linkType || '').toLowerCase()
  if (linkType === 'product') router.push(`/product/${banner.linkValue}`)
  else if (linkType === 'category') { query.value.categoryName = banner.linkValue; fetchProducts(true) }
  else if (linkType === 'url') window.open(banner.linkValue, '_blank')
}
watch(banners, () => { bannerIndex.value = 0; startBannerAutoplay() })

// 公告
const notices = computed(() => (home.value.notices || []).filter((n) => Number(n.status) === 1))
const noticeIndex = ref(0)
let noticeTimer = null
const startNoticeRotation = () => {
  stopNoticeRotation()
  if (notices.value.length > 1) {
    noticeTimer = window.setInterval(() => { noticeIndex.value = (noticeIndex.value + 1) % notices.value.length }, 5000)
  }
}
const stopNoticeRotation = () => { if (noticeTimer) { window.clearInterval(noticeTimer); noticeTimer = null } }
watch(notices, () => { noticeIndex.value = 0; startNoticeRotation() })

// 首页模块排序
const defaultModules = [
  { type: 'banner', enabled: true, sort: 1 },
  { type: 'notice', enabled: true, sort: 2 },
  { type: 'category', enabled: true, sort: 3 },
  { type: 'trust', enabled: true, sort: 4 },
  { type: 'products', enabled: true, sort: 5 },
]
const homeModules = computed(() => {
  return resolveHomeModules(displayConfig.value, defaultModules)
})

// 颜色配置注入
const applyExtraColors = (config) => {
  const colors = resolveDisplayColors(config)
  const root = document.documentElement
  if (colors.priceColor) root.style.setProperty('--price-color', colors.priceColor)
  if (colors.headerBg) root.style.setProperty('--shop-header-bg', colors.headerBg)
  if (colors.pageBg) root.style.setProperty('--shop-page-bg', colors.pageBg)
  if (colors.cardBg) root.style.setProperty('--card-bg', colors.cardBg)
  if (colors.cardBg) root.style.setProperty('--card', colors.cardBg)
  if (colors.textColor) {
    root.style.setProperty('--text-color', colors.textColor)
    root.style.setProperty('--text', colors.textColor)
    root.style.setProperty('--ink', colors.textColor)
  }
  if (colors.mutedColor) {
    root.style.setProperty('--muted-color', colors.mutedColor)
    root.style.setProperty('--muted', colors.mutedColor)
  }
  if (colors.accentColor) {
    root.style.setProperty('--accent', colors.accentColor)
    root.style.setProperty('--brand-primary', colors.accentColor)
  }
  if (colors.lineColor) root.style.setProperty('--line', colors.lineColor)
  if (colors.buttonBg) root.style.setProperty('--shop-button-bg', colors.buttonBg)
}
const trustItems = computed(() => {
  const config = home.value.legalConfig || {}
  const items = [
    { title: '账户资金安全', description: '独立支付密码保护' },
    { title: '订单全程可查', description: '状态与物流及时同步' },
    { title: '售后保障', description: '下单后7天内可申请售后' },
    { title: '专属客服', description: config.servicePhone || '遇到问题随时咨询' },
  ]
  if (config.companyName) {
    items.splice(3, 0, { title: '经营主体', description: config.companyName })
  }
  return items
})
let suggestionsHideTimer
let productRequestId = 0

const allHomeProducts = computed(() => home.value.featuredProducts || [])
const displayConfig = computed(() => home.value.displayConfig || {})
const displayExtraConfig = computed(() => readDisplayExtraConfig(displayConfig.value))
const showHomeCategories = computed(() => Number(displayConfig.value.showHomeCategories ?? 1) === 1)
// 服务保障是说明性内容，不是首页操作入口。默认关闭，只有商家在商城视觉与页面中主动开启时展示。
const showTrustStrip = computed(() => Number(displayExtraConfig.value.showTrustStrip ?? 0) === 1)
const layoutTemplate = computed(() => ['standard', 'product-focus', 'category-focus'].includes(displayConfig.value.layoutTemplate)
  ? displayConfig.value.layoutTemplate
  : 'standard')
const categoryEntries = computed(() => {
  const configured = home.value.categoryList || []
  const rows = configured.length
    ? configured.map((category) => ({ id: category.id, name: category.categoryName, image: category.iconUrl }))
    : (home.value.categories || []).map((name) => ({ id: name, name, image: '' }))

  return rows.map((category) => ({
    ...category,
    image: category.image || allHomeProducts.value.find((product) => product.categoryName === category.name)?.coverUrl || '',
  }))
})

const priceParts = (value) => {
  const [integer, decimal] = money(value).split('.')
  return { integer, decimal }
}

const showToast = (message) => {
  toast.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 2200)
}

let toastTimer = null
const isAddingProduct = (productId) => addingProductIds.value.has(Number(productId))
const setAddingProduct = (productId, adding) => {
  const next = new Set(addingProductIds.value)
  if (adding) next.add(Number(productId))
  else next.delete(Number(productId))
  addingProductIds.value = next
}

const normalizeProduct = (product) => ({
  ...product,
  id: product.id,
  productName: product.productName || product.name || '商城商品',
  subtitle: product.subtitle || '',
  coverUrl: product.coverUrl || product.picUrl || '',
  salePrice: Number(product.salePrice || product.price || 0),
  salesCount: Math.max(0, Number(product.salesCount || 0)),
  stock: Math.max(0, Number(product.stock || 0)),
  status: Number(product.status ?? 1),
})

const fetchHome = async () => {
  const res = await getHome()
  home.value = res.data || {}
  applyBrandConfig(home.value)
  applyExtraColors(displayConfig.value)
}

const fetchProducts = async (scrollToResults = false) => {
  const requestId = ++productRequestId
  loading.value = true
  try {
    const res = await listProducts({
      keyword: query.value.keyword.trim(),
      categoryName: query.value.categoryName,
      status: 1,
      pageNum: 1,
      pageSize: 60,
    })
    if (requestId !== productRequestId) return
    products.value = (res.data?.list || []).map(normalizeProduct)
    if (scrollToResults) {
      await nextTick()
      productSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  } finally {
    if (requestId === productRequestId) loading.value = false
  }
}

const reloadHome = async () => {
  homeLoading.value = true
  homeLoadError.value = ''
  try {
    await fetchHome()
    await fetchProducts()
  } catch (e) {
    products.value = []
    homeLoadError.value = e?.message || '网络暂时不可用，请点击重新加载'
  } finally {
    homeLoading.value = false
  }
}

const submitSearch = async () => {
  searchInput.value?.blur()
  recordSearch(query.value.keyword)
  searchFocused.value = false
  await fetchProducts(true)
}

const applySearch = (keyword) => {
  query.value.keyword = keyword
  query.value.categoryName = ''
  submitSearch()
}

const recordSearch = (keyword) => {
  const normalized = String(keyword || '').trim()
  if (!normalized) return
  recentSearches.value = [normalized, ...recentSearches.value.filter((item) => item !== normalized)].slice(0, 5)
  localStorage.setItem('shop_recent_searches', JSON.stringify(recentSearches.value))
}

const scheduleHideSuggestions = () => {
  window.clearTimeout(suggestionsHideTimer)
  suggestionsHideTimer = window.setTimeout(() => { searchFocused.value = false }, 140)
}

const setCategory = (categoryName) => {
  query.value.categoryName = query.value.categoryName === categoryName ? '' : categoryName
  fetchProducts(true)
}

const clearFilter = () => {
  query.value.keyword = ''
  query.value.categoryName = ''
  fetchProducts()
}

const addProduct = async (product) => {
  if (product.status !== 1 || product.stock <= 0) return
  if (isAddingProduct(product.id)) return
  setAddingProduct(product.id, true)
  try {
    const res = await getProduct(product.id)
    const cartItem = resolveQuickCartItem(product, res.data || {})
    if (!cartItem) {
      showToast('该商品暂时缺货')
      return
    }
    const stockError = stockAdditionViolation(cartItem.stock, 1, getQuantity(cartItemKey(cartItem)))
    if (stockError) throw new Error(stockError)
    await checkCartPurchaseLimit(cartItem, 1, getProductQuantity(cartItem.id))
    add(cartItem, 1)
    showToast('已加入购物车，数量 +1')
  } catch (error) {
    showToast(error?.message || '商品信息更新失败，请稍后重试')
  } finally {
    setAddingProduct(product.id, false)
  }
}

onMounted(async () => {
  try {
    const saved = JSON.parse(localStorage.getItem('shop_recent_searches') || '[]')
    recentSearches.value = Array.isArray(saved) ? saved.filter((item) => typeof item === 'string').slice(0, 5) : []
  } catch (_) { recentSearches.value = [] }
  await reloadHome()
})
onUnmounted(() => { stopBannerAutoplay(); stopNoticeRotation(); window.clearTimeout(toastTimer) })
</script>

<style scoped>
.home-page { min-height: 100vh; padding-bottom: 58px; background: var(--shop-page-bg); }
.home-init-error { width: min(560px, calc(100% - 28px)); margin: 28px auto; padding: 30px 20px; color: var(--ink); background: var(--card-bg, #fff); border: 1px solid #f1d6dc; border-radius: 18px; box-shadow: 0 10px 26px rgba(31, 41, 55, .06); text-align: center; }
.home-init-error strong { display: block; font-size: 18px; }
.home-init-error p { margin: 9px 0 18px; color: var(--muted); font-size: 13px; line-height: 1.6; }
.home-init-error button { min-width: 116px; min-height: 40px; padding: 0 18px; color: #fff; background: var(--accent, #e7193f); border: 0; border-radius: 999px; font-size: 14px; font-weight: 700; cursor: pointer; }
.home-init-error button:disabled { opacity: .6; cursor: wait; }
.home-topbar { position: sticky; top: 0; z-index: 24; background: var(--shop-header-bg); border-bottom: 1px solid #eceff1; backdrop-filter: blur(12px); }

/* Banner轮播 */
.home-banner-section { width: min(1180px, calc(100% - 40px)); margin: 18px auto 0; }
.banner-carousel { position: relative; width: 100%; overflow: hidden; border-radius: var(--shop-card-radius); }
.banner-track { display: flex; transition: transform .4s ease; }
.banner-slide { flex: 0 0 100%; aspect-ratio: 2.5; display: block; overflow: hidden; }
.banner-slide img { width: 100%; height: 100%; object-fit: cover; }
.banner-dots { position: absolute; bottom: 12px; left: 50%; transform: translateX(-50%); display: flex; gap: 8px; }
.banner-dots button { width: 8px; height: 8px; border-radius: 50%; background: rgba(255,255,255,.5); border: 0; transition: all .2s; }
.banner-dots button.active { width: 24px; border-radius: 4px; background: #fff; }

/* 公告 */
.home-notice-section { width: min(1180px, calc(100% - 40px)); margin: 12px auto 0; padding: 10px 16px; background: #fffbeb; border: 1px solid #fef3c7; border-radius: var(--shop-card-radius); }
.notice-scroll { display: flex; align-items: center; gap: 8px; color: #92400e; font-size: 13px; overflow: hidden; }
.notice-scroll svg { flex-shrink: 0; }
.notice-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.home-topbar { position: sticky; top: 0; z-index: 24; background: var(--shop-header-bg); border-bottom: 1px solid #eceff1; backdrop-filter: blur(12px); }
.home-topbar-inner { width: min(1180px, calc(100% - 40px)); min-height: 72px; display: grid; grid-template-columns: auto minmax(280px,1fr) 64px; align-items: center; gap: 18px; margin: 0 auto; }
.home-brand { min-width: 0; display: inline-flex; align-items: center; gap: 9px; color: #182230; text-decoration: none; white-space: nowrap; }
.home-brand img,.home-brand-mark { width: 38px; height: 38px; flex: 0 0 38px; object-fit: contain; border-radius: 11px; }
.home-brand-mark { display: inline-flex; align-items: center; justify-content: center; color: #fff; background: linear-gradient(135deg,#0d3b8f,#12a9e8); font-size: 12px; font-weight: 900; letter-spacing: -1px; box-shadow: 0 6px 14px rgba(13,59,143,.2); }
.home-brand strong { overflow: hidden; text-overflow: ellipsis; font-size: 16px; }
.home-search { position: relative; height: 48px; display: grid; grid-template-columns: 42px minmax(0,1fr) 90px; align-items: center; overflow: visible; color: #969ca4; background: #fff; border: 2px solid var(--brand-primary); border-radius: 999px; }
.home-search > svg { justify-self: center; }
.home-search input { min-width: 0; height: 100%; padding: 0 4px; color: #272c32; background: transparent; border: 0; outline: 0; }
.home-search input::-webkit-search-cancel-button { cursor: pointer; }
.home-search button { height: 100%; display: inline-flex; align-items: center; justify-content: center; gap: 5px; color: #fff; background: var(--brand-primary); border: 0; border-radius: 0 999px 999px 0; font-size: 16px; font-weight: 800; }
.home-search button > svg { display: none; }
.search-suggestions { position:absolute; z-index:40; top:calc(100% + 8px); left:0; right:0; padding:12px 14px; background:#fff; border:1px solid #e7ebf0; border-radius:14px; box-shadow:0 12px 30px rgba(25,42,70,.14); }
.suggestion-group { display:flex; align-items:center; flex-wrap:wrap; gap:7px; }
.suggestion-group + .suggestion-group { margin-top:10px; padding-top:10px; border-top:1px solid #f0f2f5; }
.suggestion-group > span { flex:0 0 100%; color:#98a2b3; font-size:11px; }
.suggestion-group button { height:auto; padding:6px 10px; color:#475467; background:#f5f7fa; border:0; border-radius:999px; font-size:12px; font-weight:500; }
.home-share { height: 58px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 3px; color: #3f454c; border-radius: 10px; font-size: 12px; font-weight: 700; }
.home-share:hover { color: var(--brand-primary); background: var(--brand-primary-soft); }
@media (max-width: 760px) {
  .home-topbar-inner { width: min(100% - 24px, 620px); grid-template-columns: 38px minmax(0,1fr) 48px; gap: 10px; }
  .home-brand strong { display: none; }
  .home-brand img,.home-brand-mark { width: 34px; height: 34px; flex-basis: 34px; border-radius: 10px; }
  .home-search { height: 44px; grid-template-columns: 34px minmax(0,1fr) 64px; }
  .home-search button { font-size: 14px; }
  .home-search button span { display: none; }
  .home-search button > svg { display: block; }
  .home-banner-section { width: calc(100% - 16px); margin-top: 10px; }
  .banner-slide { aspect-ratio: 2; }
  .home-notice-section { width: calc(100% - 16px); margin-top: 8px; }
}

.home-category-section { width: min(1180px, calc(100% - 40px)); margin: 18px auto 16px; padding: 22px 20px; background: #fff; border: 1px solid #eceff1; border-radius: var(--shop-card-radius); }
.home-trust-strip { width:min(1180px,calc(100% - 40px)); display:grid; grid-template-columns:repeat(auto-fit,minmax(0,1fr)); gap:10px; margin:0 auto 18px; padding:13px 16px; background:linear-gradient(110deg,#f7fbff,#fff); border:1px solid #e4edf7; border-radius:var(--shop-card-radius); }
.trust-item { min-width:0; padding:2px 12px; border-right:1px solid #e8eef5; }
.trust-item:last-child { border-right:0; }
.trust-item strong,.trust-item span { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.trust-item strong { color:#344054; font-size:13px; }
.trust-item span { margin-top:4px; color:#98a2b3; font-size:11px; }
.category-grid { display: grid; grid-template-columns: repeat(auto-fit,minmax(86px,1fr)); gap: 18px 12px; }
.home-category-item { min-width: 0; display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 0 3px; color: #49515a; background: transparent; border: 0; }
.category-circle { width: 72px; height: 72px; display: grid; place-items: center; overflow: hidden; color: #667085; background: #f3f4f6; border: 2px solid transparent; border-radius: 50%; box-shadow: 0 4px 14px rgba(38,45,51,.07); font-size: 24px; font-weight: 800; transition: transform .2s ease,border-color .2s ease; }
.category-circle img { width: 100%; height: 100%; display: block; object-fit: cover; }
.home-category-item:hover .category-circle,.home-category-item.active .category-circle { border-color: var(--brand-primary); transform: translateY(-2px); }
.home-category-item strong { max-width: 100%; overflow: hidden; font-size: 13px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.home-category-item.active strong { color: var(--brand-primary); font-weight: 800; }
.home-page.layout-product-focus .home-product-section { margin-top: 16px; }
.home-page.layout-product-focus .home-product-grid { grid-template-columns: repeat(3,minmax(0,1fr)); gap: 18px; }
.home-page.layout-category-focus .home-category-section { background: linear-gradient(145deg,#fff,var(--brand-primary-soft)); border-color: var(--brand-primary-soft); }
.home-page.layout-category-focus .category-circle { width: 78px; height: 78px; box-shadow: 0 7px 18px rgba(38,45,51,.10); }

.home-product-section { width: min(1180px, calc(100% - 40px)); margin: 0 auto; scroll-margin-top: 88px; }
.home-product-heading { min-height: 64px; display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 4px 2px 10px; }
.home-product-heading h1 { margin: 0; color: #22272e; font-size: 23px; }
.home-product-heading p { margin: 5px 0 0; color: #9298a0; font-size: 13px; }
.clear-filter { padding: 8px 14px; color: var(--brand-primary); background: #fff; border: 1px solid var(--brand-primary-soft); border-radius: 999px; font-size: 13px; }
.home-product-grid { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 14px; }
.home-product-card { min-width: 0; display: flex; flex-direction: column; overflow: hidden; background: #fff; border: 1px solid #eceff1; border-radius: var(--shop-card-radius); transition: transform .18s ease,box-shadow .18s ease; }
.home-product-card:hover { transform: translateY(-2px); box-shadow: var(--shop-card-shadow); }
.home-product-image { position: relative; display: block; aspect-ratio: 1; overflow: hidden; background: #f7f7f7; }
.home-product-image img { display: block; width: 100%; height: 100%; object-fit: cover; transition: transform .25s ease; }
.home-product-card:hover .home-product-image img { transform: scale(1.025); }
.home-sold-out { position: absolute; inset: 0; display: grid; place-items: center; color: #fff; background: rgba(0,0,0,.43); font-weight: 800; }
.home-product-info { flex: 1; display: flex; flex-direction: column; padding: 13px; }
.home-product-copy h2 { min-height: 44px; margin: 0; color: #262b31; font-size: 16px; line-height: 1.42; display: -webkit-box; overflow: hidden; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.home-product-copy p { min-height: 36px; margin: 7px 0 0; color: #8a9098; font-size: 12px; line-height: 1.5; display: -webkit-box; overflow: hidden; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.home-sales { margin-top: 7px; color: #b66e34; font-size: 12px; }
.home-purchase-row { display: flex; align-items: flex-end; justify-content: space-between; gap: 7px; margin-top: 11px; }
.home-price { display: flex; align-items: baseline; min-width: 0; color: var(--price-color, var(--brand-primary)); white-space: nowrap; }
.home-price span { margin-right: 2px; font-size: 14px; font-weight: 800; }
.home-price strong { font-size: 27px; line-height: 1; letter-spacing: -1px; }
.home-price small { font-size: 14px; font-weight: 800; }
.home-cart-button { min-width: 96px; height: 38px; display: inline-flex; align-items: center; justify-content: center; gap: 5px; padding: 0 12px; color: #fff; background: var(--shop-button-bg, linear-gradient(135deg,var(--brand-primary),var(--brand-primary-dark))); border: 0; border-radius: 999px; font-size: 13px; font-weight: 800; white-space: nowrap; }
.home-cart-button:disabled { background: #b7bbc0; cursor: not-allowed; }
.home-empty { min-height: 340px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 9px; color: #989ea6; background: #fff; border-radius: 14px; }
.home-empty strong { color: #59616a; }
.home-empty button { padding: 8px 15px; color: var(--brand-primary); background: #fff; border: 1px solid var(--brand-primary-soft); border-radius: 999px; }
@media (max-width: 760px) {
  .home-page { padding-bottom: 82px; }
  .home-topbar-inner { width: 100%; min-height: 62px; grid-template-columns: 34px minmax(0,1fr) 46px; gap: 5px; padding: 7px 7px 7px 5px; }
  .home-search { height: 42px; grid-template-columns: 34px minmax(0,1fr) 42px; border-width: 1.5px; }
  .home-search input { font-size: 16px; touch-action: manipulation; }
  .home-search button > span { display: none; }
  .home-search button > svg { display: block; }
  .search-suggestions { top:calc(100% + 6px); left:-5px; right:-5px; }
  .home-share { height: 48px; font-size: 10px; }
  .home-category-section { width: calc(100% - 16px); margin: 9px auto 10px; padding: 14px 7px 12px; border-radius: var(--shop-card-radius); }
  .home-trust-strip { width:calc(100% - 16px); grid-template-columns:repeat(2,minmax(0,1fr)); gap:0; margin-bottom:11px; padding:10px 6px; }
  .trust-item { padding:7px 9px; border-right:0; }
  .trust-item:nth-child(-n+2) { border-bottom:1px solid #e8eef5; }
  .trust-item:nth-child(odd) { border-right:1px solid #e8eef5; }
  .category-grid { grid-template-columns: repeat(5,minmax(0,1fr)); gap: 13px 3px; }
  .home-category-item { gap: 6px; padding: 0 1px; }
  .category-circle { width: 58px; height: 58px; border-width: 1.5px; font-size: 19px; }
  .home-category-item strong { font-size: 11px; }
  .home-product-section { width: calc(100% - 16px); scroll-margin-top: 70px; }
  .home-product-heading { min-height: 55px; padding: 5px 2px 9px; }
  .home-product-heading h1 { font-size: 18px; }
  .home-product-heading p { margin-top: 3px; font-size: 11px; }
  .clear-filter { padding: 6px 11px; font-size: 11px; }
  .home-product-grid { grid-template-columns: repeat(2,minmax(0,1fr)); gap: 8px; }
  .home-page.layout-product-focus .home-product-grid { grid-template-columns: repeat(2,minmax(0,1fr)); gap: 10px; }
  .home-page.layout-category-focus .category-circle { width: 62px; height: 62px; }
  .home-product-card { border-radius: var(--shop-card-radius); }
  .home-product-info { padding: 9px; }
  .home-product-copy h2 { min-height: 38px; font-size: 14px; line-height: 1.38; }
  .home-product-copy p { min-height: 17px; margin-top: 5px; font-size: 11px; line-height: 1.45; -webkit-line-clamp: 1; }
  .home-sales { margin-top: 5px; font-size: 11px; }
  .home-purchase-row { gap: 4px; margin-top: 8px; }
  .home-price span { font-size: 12px; }
  .home-price strong { font-size: 22px; }
  .home-price small { font-size: 12px; }
  .home-cart-button { min-width: 76px; height: 34px; gap: 3px; padding: 0 9px; font-size: 12px; }
  .home-cart-button svg { display: none; }
}

@media (max-width: 370px) {
  .home-topbar-inner { grid-template-columns: 32px minmax(0,1fr) 42px; padding-left: 4px; padding-right: 6px; }
  .category-circle { width: 52px; height: 52px; }
  .home-product-info { padding: 8px; }
  .home-price strong { font-size: 20px; }
  .home-cart-button { min-width: 70px; padding: 0 7px; font-size: 11px; }
}
</style>
