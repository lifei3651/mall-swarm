<template>
  <div class="home-page" :class="`layout-${layoutTemplate}`">
    <header class="home-topbar">
      <div class="home-topbar-inner">
        <RouterLink class="home-brand" to="/" aria-label="返回商城首页">
          <img v-if="home.logoUrl" :src="home.logoUrl" :alt="`${home.brandName || '商城'} Logo`" @error="home.logoUrl = ''" />
          <span v-else class="home-brand-mark">灵启</span>
          <strong>{{ home.brandName || '灵启商城' }}</strong>
        </RouterLink>
        <form class="home-search" role="search" @submit.prevent="submitSearch">
          <Search :size="19" />
          <input ref="searchInput" v-model="query.keyword" type="search" placeholder="搜索商品" aria-label="搜索商品" />
          <button type="submit" aria-label="搜索"><span>搜索</span><Search :size="18" /></button>
        </form>

        <RouterLink class="home-share" to="/invite" aria-label="分享邀请二维码">
          <Share2 :size="22" />
          <span>分享</span>
        </RouterLink>
      </div>
    </header>

    <section v-if="showHomeCategories && categoryEntries.length" class="home-category-section" aria-label="商品品类">
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

    <section ref="productSection" class="home-product-section">
      <div class="home-product-heading">
        <div>
          <h1>{{ query.categoryName || (query.keyword ? '搜索结果' : '精选商品') }}</h1>
          <p v-if="query.keyword">关键词：{{ query.keyword }}</p>
          <p v-else>商城好物，为你精选</p>
        </div>
        <button v-if="query.categoryName || query.keyword" type="button" class="clear-filter" @click="clearFilter">查看全部</button>
      </div>

      <div v-if="loading" class="home-product-grid home-skeleton-grid" aria-label="商品加载中">
        <div v-for="index in 4" :key="index" class="home-product-skeleton">
          <span></span><i></i><i></i>
        </div>
      </div>

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
                :disabled="product.status !== 1 || product.stock <= 0"
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

    <div v-if="toast" class="toast">{{ toast }}</div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { PackageOpen, Search, Share2, ShoppingCart } from 'lucide-vue-next'
import { getHome, getProduct, listProducts } from '@/api/shop'
import { useCart } from '@/store/cart'
import { money } from '@/utils/format'
import { applyBrandConfig } from '@/utils/brand'
import { resolveQuickCartItem } from '@/utils/quickCart'

const { add } = useCart()
const home = ref({})
const products = ref([])
const loading = ref(false)
const toast = ref('')
const productSection = ref(null)
const searchInput = ref(null)
const query = ref({ keyword: '', categoryName: '' })
let productRequestId = 0

const allHomeProducts = computed(() => home.value.featuredProducts || [])
const displayConfig = computed(() => home.value.displayConfig || {})
const showHomeCategories = computed(() => Number(displayConfig.value.showHomeCategories ?? 1) === 1)
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
  window.setTimeout(() => { toast.value = '' }, 1800)
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

const submitSearch = async () => {
  searchInput.value?.blur()
  await fetchProducts(true)
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
  try {
    const res = await getProduct(product.id)
    const cartItem = resolveQuickCartItem(product, res.data || {})
    if (!cartItem) {
      showToast('该商品暂时缺货')
      return
    }
    add(cartItem, 1)
    showToast('已加入购物车，数量 +1')
  } catch (error) {
    showToast(error?.message || '商品信息更新失败，请稍后重试')
  }
}

onMounted(async () => {
  await fetchHome()
  await fetchProducts()
})
</script>

<style scoped>
.home-page { min-height: 100vh; padding-bottom: 58px; background: var(--shop-page-bg); }
.home-topbar { position: sticky; top: 0; z-index: 24; background: var(--shop-header-bg); border-bottom: 1px solid #eceff1; backdrop-filter: blur(12px); }
.home-topbar-inner { width: min(1180px, calc(100% - 40px)); min-height: 72px; display: grid; grid-template-columns: auto minmax(280px,1fr) 64px; align-items: center; gap: 18px; margin: 0 auto; }
.home-brand { min-width: 0; display: inline-flex; align-items: center; gap: 9px; color: #182230; text-decoration: none; white-space: nowrap; }
.home-brand img,.home-brand-mark { width: 38px; height: 38px; flex: 0 0 38px; object-fit: contain; border-radius: 11px; }
.home-brand-mark { display: inline-flex; align-items: center; justify-content: center; color: #fff; background: linear-gradient(135deg,#0d3b8f,#12a9e8); font-size: 12px; font-weight: 900; letter-spacing: -1px; box-shadow: 0 6px 14px rgba(13,59,143,.2); }
.home-brand strong { overflow: hidden; text-overflow: ellipsis; font-size: 16px; }
.home-search { height: 48px; display: grid; grid-template-columns: 42px minmax(0,1fr) 90px; align-items: center; overflow: hidden; color: #969ca4; background: #fff; border: 2px solid var(--brand-primary); border-radius: 999px; }
.home-search > svg { justify-self: center; }
.home-search input { min-width: 0; height: 100%; padding: 0 4px; color: #272c32; background: transparent; border: 0; outline: 0; }
.home-search input::-webkit-search-cancel-button { cursor: pointer; }
.home-search button { height: 100%; display: inline-flex; align-items: center; justify-content: center; gap: 5px; color: #fff; background: var(--brand-primary); border: 0; font-size: 16px; font-weight: 800; }
.home-search button > svg { display: none; }
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
}

.home-category-section { width: min(1180px, calc(100% - 40px)); margin: 18px auto 16px; padding: 22px 20px; background: #fff; border: 1px solid #eceff1; border-radius: var(--shop-card-radius); }
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
.home-price { display: flex; align-items: baseline; min-width: 0; color: var(--brand-primary); white-space: nowrap; }
.home-price span { margin-right: 2px; font-size: 14px; font-weight: 800; }
.home-price strong { font-size: 27px; line-height: 1; letter-spacing: -1px; }
.home-price small { font-size: 14px; font-weight: 800; }
.home-cart-button { min-width: 96px; height: 38px; display: inline-flex; align-items: center; justify-content: center; gap: 5px; padding: 0 12px; color: #fff; background: linear-gradient(135deg,var(--brand-primary),var(--brand-primary-dark)); border: 0; border-radius: 999px; font-size: 13px; font-weight: 800; white-space: nowrap; }
.home-cart-button:disabled { background: #b7bbc0; cursor: not-allowed; }
.home-empty { min-height: 340px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 9px; color: #989ea6; background: #fff; border-radius: 14px; }
.home-empty strong { color: #59616a; }
.home-empty button { padding: 8px 15px; color: var(--brand-primary); background: #fff; border: 1px solid var(--brand-primary-soft); border-radius: 999px; }
.home-skeleton-grid { pointer-events: none; }
.home-product-skeleton { overflow: hidden; padding-bottom: 16px; background: #fff; border-radius: 13px; }
.home-product-skeleton span,.home-product-skeleton i { display: block; background: #eceeef; }
.home-product-skeleton span { aspect-ratio: 1; }
.home-product-skeleton i { width: calc(100% - 24px); height: 13px; margin: 12px 12px 0; border-radius: 7px; }
.home-product-skeleton i:last-child { width: 62%; }
@media (max-width: 760px) {
  .home-page { padding-bottom: 82px; }
  .home-topbar-inner { width: 100%; min-height: 62px; grid-template-columns: 34px minmax(0,1fr) 46px; gap: 5px; padding: 7px 7px 7px 5px; }
  .home-search { height: 42px; grid-template-columns: 34px minmax(0,1fr) 42px; border-width: 1.5px; }
  .home-search input { font-size: 16px; touch-action: manipulation; }
  .home-search button > span { display: none; }
  .home-search button > svg { display: block; }
  .home-share { height: 48px; font-size: 10px; }
  .home-category-section { width: calc(100% - 16px); margin: 9px auto 10px; padding: 14px 7px 12px; border-radius: var(--shop-card-radius); }
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
