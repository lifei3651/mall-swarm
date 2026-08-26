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

      </div>
    </header>

    <section v-if="homeLoadError" class="home-init-error" role="alert" aria-live="polite">
      <strong>商城首页暂时加载失败</strong>
      <p>{{ homeLoadError }}</p>
      <button type="button" :disabled="homeLoading" @click="reloadHome">
        {{ homeLoading ? '重新加载中…' : '重新加载' }}
      </button>
    </section>

    <nav v-if="businessEntries.length" class="business-entry-nav" aria-label="特色商城入口">
      <RouterLink v-for="entry in businessEntries" :key="entry.path" :to="entry.path" :class="entry.kind">
        <strong>{{ entry.title }}</strong><span>{{ entry.description }}</span><b>进入 ›</b>
      </RouterLink>
    </nav>

    <!-- 按配置顺序渲染首页模块 -->
    <template v-if="!homeLoadError" v-for="mod in homeModules" :key="mod.type">
      <!-- Banner轮播 -->
      <section v-if="mod.type === 'banner' && mod.enabled && banners.length" class="home-banner-section" aria-label="商城活动">
        <div class="banner-carousel">
          <div class="banner-track" :style="{ transform: `translateX(-${bannerIndex * 100}%)` }">
            <a v-for="banner in banners" :key="banner.id" class="banner-slide" :href="banner.linkValue || '#'" @click.prevent="handleBannerClick(banner)">
              <img :src="banner.imageUrl" :alt="banner.title || '活动广告'" loading="eager" @error="applyImageFallback" />
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
              <img v-if="category.image" :src="category.image" :alt="category.name" loading="eager" decoding="async" @error="applyImageFallback" />
              <span v-else>{{ category.name.slice(0, 1) }}</span>
            </span>
            <strong>{{ category.name }}</strong>
          </button>
        </div>
      </section>

      <!-- 两个运营模块保持独立开关与入口，并在首页合并为一行双卡片。 -->
      <section v-else-if="isFeatureAnchor(mod)" class="home-feature-row" :class="{ 'is-single': visibleFeatureModules.length === 1 }" aria-label="直播广场与新品速递">
        <article v-if="showLiveFeature" class="home-feature-section home-live-section" :style="{ order: featureModuleOrder('live') }" aria-label="直播广场">
          <div class="discovery-heading">
            <div><Radio :size="18" /><h2>直播广场</h2><span v-if="liveRoomLiveCount">{{ liveRoomLiveCount }} 场直播中</span></div>
            <RouterLink to="/live">全部 <ChevronRight :size="15" /></RouterLink>
          </div>
          <div class="discovery-grid">
            <RouterLink v-for="item in liveRooms.slice(0, 1)" :key="item.room.id" class="discovery-feature" :to="`/live/${item.room.id}`">
              <img :src="item.room.coverUrl" :alt="item.room.title" loading="lazy" @error="applyImageFallback" />
              <span class="discovery-shade"></span>
              <span class="live-state" :class="`state-${String(item.roomState).toLowerCase()}`">{{ liveStateLabel(item.roomState) }}</span>
              <span class="live-copy"><strong>{{ item.room.title }}</strong><small>{{ item.room.anchorName || formatLiveTime(item.room.scheduledStartTime) }}</small></span>
              <span class="live-heat"><Flame :size="14" /> {{ formatHeat(item.room.heatCount) }}</span>
            </RouterLink>
          </div>
        </article>

        <article v-if="showNewArrivalsFeature" class="home-feature-section home-new-arrivals-section" :style="{ order: featureModuleOrder('newArrivals') }" aria-label="新品速递">
          <div class="discovery-heading">
            <div><Sparkles :size="18" /><h2>新品速递</h2><span>首次上架好物</span></div>
            <RouterLink to="/new-arrivals">全部 <ChevronRight :size="15" /></RouterLink>
          </div>
          <div class="discovery-grid">
            <RouterLink v-for="product in newArrivals.slice(0, 1)" :key="product.id" class="discovery-feature" :to="`/product/${product.id}`">
              <img :src="product.coverUrl" :alt="product.productName" loading="lazy" @error="applyImageFallback" />
              <span class="discovery-shade"></span>
              <span class="new-badge"><Sparkles :size="13" /> NEW</span>
              <span class="live-copy"><strong>{{ product.productName }}</strong><small>{{ product.subtitle || '新品首发，品质上新' }}</small></span>
              <span class="new-price">首发价 ¥{{ money(product.salePrice) }}</span>
            </RouterLink>
          </div>
        </article>
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
      <div v-if="layoutTemplate !== 'campaign-feed' || query.categoryName || query.keyword" class="home-product-heading">
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
            <img :src="product.coverUrl" :alt="product.productName" loading="lazy" @error="applyImageFallback" />
            <span v-if="product.status !== 1 || product.stock <= 0" class="home-sold-out">已售罄</span>
          </RouterLink>

          <div v-if="campaignActivity(product)" class="campaign-activity-band">
            <strong>{{ campaignActivity(product).activityState === 'ACTIVE' ? '限时活动进行中' : '限时活动即将开始' }}</strong>
            <span>{{ campaignCountdown(campaignActivity(product)) }}</span>
          </div>

          <div class="home-product-info">
            <RouterLink class="home-product-copy" :to="`/product/${product.id}`">
              <h2>{{ product.productName }}</h2>
              <p>{{ product.subtitle || '精选商城好物，品质保障，售后无忧' }}</p>
            </RouterLink>
            <div class="home-sales">已售 {{ product.salesCount }}{{ product.salesCount >= 10000 ? '+' : ' 件' }}</div>
            <div class="home-purchase-row">
              <div class="home-price">
                <span>¥</span>
                <strong>{{ priceParts(campaignPrice(product)).integer }}</strong>
                <small>.{{ priceParts(campaignPrice(product)).decimal }}</small>
              </div>
              <button
                v-if="!campaignActivity(product)"
                type="button"
                class="home-cart-button"
                :disabled="product.status !== 1 || product.stock <= 0 || isAddingProduct(product.id)"
                :aria-label="`立即加购${product.productName}`"
                @click="addProduct(product)"
              >
                <ShoppingCart :size="16" />
                {{ product.status !== 1 || product.stock <= 0 ? '已售罄' : '立即加购' }}
              </button>
              <button v-else type="button" class="home-cart-button campaign-buy-button" @click="openCampaign(campaignActivity(product))">
                <ShoppingCart :size="16" />
                {{ campaignActivity(product).activityState === 'ACTIVE' ? '去抢购' : '去看看' }}
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
import { useRoute, useRouter } from 'vue-router'
import { ChevronRight, Flame, Megaphone, PackageOpen, Radio, Search, ShoppingCart, Sparkles } from 'lucide-vue-next'
import { getHome, getProduct, listFlashSales, listProducts } from '@/api/shop'
import { useCart } from '@/store/cart'
import { money } from '@/utils/format'
import { applyBrandConfig } from '@/utils/brand'
import { readDisplayExtraConfig, resolveHomeModules } from '@/utils/displayConfig'
import { resolveQuickCartItem } from '@/utils/quickCart'
import { checkCartPurchaseLimit } from '@/utils/purchaseLimit'
import { cartItemKey, stockAdditionViolation } from '@/utils/stockRules'
import { requireShopSession } from '@/utils/authNavigation'
import ProductListSkeleton from '@/components/ProductListSkeleton.vue'
import { applyImageFallback } from '@/utils/imageFallback'
import { resolveBusinessEntries } from '@surface-commerce-policy'

const router = useRouter()
const route = useRoute()
const { add, getQuantity, getProductQuantity } = useCart()
const home = ref({})
const products = ref([])
const loading = ref(false)
const homeLoading = ref(false)
const homeLoadError = ref('')
const toast = ref('')
const addingProductIds = ref(new Set())
const flashSales = ref([])
const campaignClock = ref(Date.now())
const productSection = ref(null)
const searchInput = ref(null)
const query = ref({ keyword: '', categoryName: '' })
const searchFocused = ref(false)
const recentSearches = ref([])
const hotSearches = ['护理套装', '健康生活', '品质好物']
const businessEntries = computed(() => resolveBusinessEntries(home.value.businessConfig || {}))

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
  const linkType = String(banner.linkType || '').toLowerCase()
  if (linkType === 'brand_culture') {
    router.push('/brand-culture')
    return
  }
  if (!banner.linkValue) return
  if (linkType === 'product') router.push(`/product/${banner.linkValue}`)
  else if (linkType === 'category') { query.value.categoryName = banner.linkValue; fetchProducts(true) }
  else if (linkType === 'url') window.open(banner.linkValue, '_blank', 'noopener,noreferrer')
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
  { type: 'live', enabled: true, sort: 4 },
  { type: 'newArrivals', enabled: true, sort: 5 },
  { type: 'trust', enabled: true, sort: 6 },
  { type: 'products', enabled: true, sort: 7 },
]
const homeModules = computed(() => {
  return resolveHomeModules(displayConfig.value, defaultModules)
})

const trustItems = computed(() => {
  const config = home.value.legalConfig || {}
  const items = [
    { title: '账户资金安全', description: '独立支付密码保护' },
    { title: '订单全程可查', description: '状态与物流及时同步' },
    { title: '售后保障', description: '售后期限以商城规则和订单提示为准' },
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
const liveRooms = computed(() => home.value.liveRooms || [])
const newArrivals = computed(() => (home.value.newArrivals || []).map(normalizeProduct))
const visibleFeatureModules = computed(() => homeModules.value.filter((module) => {
  if (!module.enabled) return false
  if (module.type === 'live') return liveRooms.value.length > 0
  if (module.type === 'newArrivals') return newArrivals.value.length > 0
  return false
}))
const showLiveFeature = computed(() => visibleFeatureModules.value.some((module) => module.type === 'live'))
const showNewArrivalsFeature = computed(() => visibleFeatureModules.value.some((module) => module.type === 'newArrivals'))
const isFeatureAnchor = (module) => module?.type === visibleFeatureModules.value[0]?.type
const featureModuleOrder = (type) => visibleFeatureModules.value.findIndex((module) => module.type === type) + 1
const liveRoomLiveCount = computed(() => liveRooms.value.filter((item) => item.roomState === 'LIVE').length)
const liveStateLabel = (state) => ({ CONNECTING: '正在连接', LIVE: '直播中', UPCOMING: '直播预告', ENDED: '精彩回放' }[state] || '直播')
const formatLiveTime = (value) => value ? String(value).replace('T', ' ').slice(5, 16) : '开播时间待定'
const formatHeat = (value) => {
  const count = Math.max(0, Number(value || 0))
  return count >= 10000 ? `${(count / 10000).toFixed(count >= 100000 ? 0 : 1)}万热度` : `${count}热度`
}
const displayConfig = computed(() => home.value.displayConfig || {})
const displayExtraConfig = computed(() => readDisplayExtraConfig(displayConfig.value))
const showHomeCategories = computed(() => Number(displayConfig.value.showHomeCategories ?? 1) === 1)
// 服务保障是说明性内容，不是首页操作入口。默认关闭，只有商家在商城视觉与页面中主动开启时展示。
const showTrustStrip = computed(() => Number(displayExtraConfig.value.showTrustStrip ?? 0) === 1)
const layoutTemplate = computed(() => ['standard', 'product-focus', 'category-focus', 'campaign-feed'].includes(displayConfig.value.layoutTemplate)
  ? displayConfig.value.layoutTemplate
  : 'standard')
const campaignActivities = computed(() => {
  const activities = new Map()
  flashSales.value
    .filter((item) => ['ACTIVE', 'UPCOMING'].includes(item?.activityState) && item?.activity?.productId)
    .forEach((item) => {
      const productId = Number(item.activity.productId)
      const current = activities.get(productId)
      if (!current || (current.activityState !== 'ACTIVE' && item.activityState === 'ACTIVE')) {
        activities.set(productId, item)
      }
    })
  return activities
})
const campaignActivity = (product) => layoutTemplate.value === 'campaign-feed'
  ? campaignActivities.value.get(Number(product.id))
  : null
const campaignPrice = (product) => campaignActivity(product)?.activity?.flashPrice ?? product.salePrice
const campaignCountdown = (row) => {
  const target = row?.activityState === 'ACTIVE' ? row?.activity?.endTime : row?.activity?.startTime
  const seconds = Math.max(0, Math.floor((new Date(target).getTime() - campaignClock.value) / 1000))
  if (!Number.isFinite(seconds) || seconds <= 0) return row?.activityState === 'ACTIVE' ? '即将结束' : '即将开始'
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const remain = seconds % 60
  const prefix = row?.activityState === 'ACTIVE' ? '距结束' : '距开始'
  return `${prefix} ${days ? `${days}天 ` : ''}${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(remain).padStart(2, '0')}`
}
const openCampaign = (activity) => router.push({ path: '/flash-sale', query: { activityId: activity?.activity?.id } })
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
  if (layoutTemplate.value === 'campaign-feed') {
    try { flashSales.value = (await listFlashSales()).data || [] } catch { flashSales.value = [] }
  } else {
    flashSales.value = []
  }
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
  if (!requireShopSession(router, route.fullPath, '请先登录后再加入购物车')) return
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
  campaignTimer = window.setInterval(() => { campaignClock.value = Date.now() }, 1000)
  try {
    const saved = JSON.parse(localStorage.getItem('shop_recent_searches') || '[]')
    recentSearches.value = Array.isArray(saved) ? saved.filter((item) => typeof item === 'string').slice(0, 5) : []
  } catch (_) { recentSearches.value = [] }
  await reloadHome()
})
let campaignTimer = null
onUnmounted(() => { stopBannerAutoplay(); stopNoticeRotation(); window.clearTimeout(toastTimer); window.clearInterval(campaignTimer) })
</script>

<style scoped>
.home-page { min-height: 100vh; padding-bottom: 58px; background: var(--shop-page-bg); }
.business-entry-nav{width:min(1180px,calc(100% - 40px));display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:12px;margin:16px auto}.business-entry-nav a{position:relative;display:flex;flex-direction:column;min-height:94px;padding:19px 110px 16px 20px;overflow:hidden;color:#fff;border-radius:16px;text-decoration:none;box-shadow:0 8px 22px rgba(25,35,55,.12)}.business-entry-nav a.flash{background:linear-gradient(135deg,#d70f36,#ff6948)}.business-entry-nav a.member-zone{background:linear-gradient(135deg,#7b4b24,#c37b3d)}.business-entry-nav strong{font-size:19px}.business-entry-nav span{margin-top:6px;font-size:12px;opacity:.86}.business-entry-nav b{position:absolute;right:20px;top:35px;font-size:14px}@media(max-width:760px){.business-entry-nav{width:calc(100% - 16px);margin:10px auto}.business-entry-nav a{min-height:82px;padding:15px 96px 13px 16px}}
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
.home-feature-row { width: min(1180px, calc(100% - 40px)); display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px; margin:0 auto 18px; }
.home-feature-row.is-single { grid-template-columns:minmax(0,1fr); }
.home-feature-section { min-width:0; }
.discovery-grid { display: grid; grid-template-columns: minmax(0,1fr); }
.discovery-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 10px; padding: 0 2px; }
.discovery-heading > div { min-width: 0; display: flex; align-items: center; gap: 7px; }
.discovery-heading svg { color: var(--brand-primary); }
.discovery-heading h2 { margin: 0; color: #22272e; font-size: 19px; }
.discovery-heading span { overflow: hidden; color: #98a2b3; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.discovery-heading > a { display: inline-flex; align-items: center; flex: 0 0 auto; color: #667085; font-size: 12px; font-weight: 700; text-decoration: none; }
.discovery-feature { position: relative; display: block; aspect-ratio: 16/9; overflow: hidden; color: #fff; background: #eef0f3; border-radius: var(--shop-card-radius); box-shadow: 0 8px 24px rgba(15,23,42,.08); }
.discovery-feature > img { width: 100%; height: 100%; display: block; object-fit: cover; transition: transform .3s ease; }
.discovery-feature:hover > img { transform: scale(1.025); }
.discovery-shade { position: absolute; inset: 0; background: linear-gradient(180deg,rgba(5,12,24,.04) 35%,rgba(5,12,24,.78) 100%); }
.live-state,.new-badge { position: absolute; top: 12px; left: 12px; display: inline-flex; align-items: center; gap: 4px; padding: 5px 9px; color: #fff; background: rgba(17,24,39,.76); border-radius: 999px; font-size: 11px; font-weight: 800; backdrop-filter: blur(6px); }
.live-state.state-live { background: #ef1742; }
.live-state.state-upcoming { background: #0f9f6e; }
.new-badge { background: #7357e6; }
.live-copy { position: absolute; left: 14px; right: 118px; bottom: 13px; min-width: 0; }
.live-copy strong,.live-copy small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.live-copy strong { font-size: 16px; }
.live-copy small { margin-top: 5px; color: rgba(255,255,255,.78); font-size: 11px; }
.live-heat,.new-price { position: absolute; right: 14px; bottom: 14px; display: flex; align-items: center; gap: 3px; color: #fff; font-size: 11px; font-weight: 700; }
.new-price { font-size: 12px; }
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
.home-page.layout-campaign-feed .home-topbar { background:color-mix(in srgb,var(--brand-primary) 14%,#eafff3 86%); border-bottom:0; }
.home-page.layout-campaign-feed .home-product-section { max-width:900px; }
.home-page.layout-campaign-feed .home-product-grid { grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px; }
.home-page.layout-campaign-feed .home-product-card { border:0; border-radius:20px; box-shadow:0 8px 22px rgba(15,23,42,.07); }
.home-page.layout-campaign-feed .home-product-image { aspect-ratio:2.05/1; }
.campaign-activity-band { display:flex; align-items:center; justify-content:space-between; gap:8px; padding:11px 14px; color:#fff; background:linear-gradient(90deg,#ef3d25,#ff8617); }
.campaign-activity-band strong { font-size:14px; }
.campaign-activity-band span { font-size:12px; font-variant-numeric:tabular-nums; white-space:nowrap; }
.campaign-buy-button { background:#19a83d; }

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
  .home-feature-row { width:calc(100% - 16px); gap:10px; margin-bottom:14px; }
  .discovery-heading { margin-bottom: 7px; }
  .discovery-heading > div { gap:4px; }
  .discovery-heading h2 { font-size: 16px; }
  .discovery-heading span { display: none; }
  .discovery-heading > a { font-size:11px; }
  .discovery-feature { aspect-ratio: 9/10; border-radius: 15px; }
  .live-copy { right: 10px; bottom: 33px; }
  .live-copy strong { font-size: 14px; }
  .live-copy small { font-size: 10px; }
  .live-heat,.new-price { right: auto; left: 14px; bottom: 12px; }
  .live-state,.new-badge { top: 9px; left: 9px; padding: 4px 7px; font-size: 10px; }
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
  .home-page.layout-campaign-feed .home-topbar { position:relative; }
  .home-page.layout-campaign-feed .home-topbar-inner { grid-template-columns:minmax(0,1fr); gap:8px; min-height:116px; padding:13px 14px 15px; }
  .home-page.layout-campaign-feed .home-brand { gap:8px; }
  .home-page.layout-campaign-feed .home-brand strong { display:block; font-size:18px; }
  .home-page.layout-campaign-feed .home-brand img,.home-page.layout-campaign-feed .home-brand-mark { width:38px; height:38px; flex-basis:38px; }
  .home-page.layout-campaign-feed .home-search { width:100%; height:44px; }
  .home-page.layout-campaign-feed .home-category-section { width:100%; margin:0 0 8px; padding:9px 8px 7px; overflow:hidden; background:#fff; border:0; border-radius:0; }
  .home-page.layout-campaign-feed .category-grid { display:flex; gap:18px; overflow-x:auto; padding:0 4px 4px; scrollbar-width:none; }
  .home-page.layout-campaign-feed .category-grid::-webkit-scrollbar { display:none; }
  .home-page.layout-campaign-feed .home-category-item { flex:0 0 auto; padding:6px 0 5px; }
  .home-page.layout-campaign-feed .category-circle { display:none; }
  .home-page.layout-campaign-feed .home-category-item strong { overflow:visible; font-size:14px; }
  .home-page.layout-campaign-feed .home-category-item.active strong { color:var(--brand-primary); }
  .home-page.layout-campaign-feed .home-banner-section { width:calc(100% - 16px); }
  .home-page.layout-campaign-feed .banner-carousel { border-radius:16px; }
  .home-page.layout-campaign-feed .banner-slide { aspect-ratio:1.55; }
  .home-page.layout-campaign-feed .home-product-section { width:calc(100% - 16px); }
  .home-page.layout-campaign-feed .home-product-grid { grid-template-columns:1fr; gap:14px; }
  .home-page.layout-campaign-feed .home-product-card { border-radius:18px; }
  .home-page.layout-campaign-feed .home-product-info { padding:11px 14px 14px; }
  .home-page.layout-campaign-feed .home-product-copy h2 { min-height:0; font-size:16px; line-height:1.45; }
  .home-page.layout-campaign-feed .home-product-copy p { min-height:0; -webkit-line-clamp:2; }
  .home-page.layout-campaign-feed .home-sales { margin-left:auto; text-align:right; }
  .home-page.layout-campaign-feed .home-cart-button { min-width:98px; background:#19a83d; }
  .campaign-activity-band { padding:10px 13px; }
  .campaign-activity-band strong { font-size:14px; }
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
