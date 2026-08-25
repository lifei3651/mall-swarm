<template>
  <div class="category-page">
    <header class="cat-search-bar">
      <RouterLink class="category-brand" to="/" aria-label="返回商城首页">
        <img v-if="brand.logoUrl" :src="brand.logoUrl" :alt="`${brand.brandName} Logo`" @error="brand.logoUrl = ''" />
        <span v-else class="category-brand-mark">灵启</span>
        <strong>{{ brand.brandName }}</strong>
      </RouterLink>
      <form class="cat-search" role="search" @submit.prevent="submitSearch">
        <Search :size="19" />
        <input v-model="query.keyword" type="search" placeholder="搜索商品" aria-label="搜索商品" />
        <button type="submit" aria-label="搜索"><span>搜索</span><Search :size="18" /></button>
      </form>
    </header>

    <section v-if="categoryGuideActive" class="category-guide" :class="`guide-${categoryGuide.template}`">
      <p v-if="guideFallbackMessage" class="guide-fallback" role="status">{{ guideFallbackMessage }}</p>

      <template v-if="categoryGuide.template === 'directory'">
        <div class="guide-directory-shell">
          <aside v-if="categoryGuide.modules.primaryCategories" class="guide-primary-nav" aria-label="一级分类">
            <button type="button" :class="{ active: !selectedCategory }" @click="selectCategory(null)">全部商品</button>
            <button v-for="category in categories" :key="category.id" type="button" :class="{ active: selectedCategory?.id === category.id }" @click="selectCategory(category)">{{ category.name }}</button>
          </aside>
          <main class="guide-directory-content">
            <article v-if="guideHeroProduct" class="guide-directory-hero">
              <img :src="guideHeroProduct.coverUrl" :alt="guideHeroProduct.productName" />
              <div><strong>{{ selectedCategory?.name || '全部商品' }}</strong><span>{{ guideHeroProduct.subtitle || '精选好物，安心选购' }}</span></div>
            </article>
            <section v-if="categoryGuide.modules.subcategories" class="guide-subcategories">
              <h2>精选子分类</h2>
              <div>
                <button v-for="category in directoryQuickCategories" :key="category.id" type="button" @click="selectCategory(category)">
                  <img v-if="category.image" :src="category.image" :alt="category.name" />
                  <span>{{ category.name }}</span>
                </button>
                <p v-if="!directoryQuickCategories.length">暂未配置更多分类，可直接浏览下方商品。</p>
              </div>
            </section>
            <section v-if="categoryGuide.modules.hotProducts" class="guide-product-section">
              <div class="guide-section-heading"><h2>热销好物</h2><span>按真实销量排序</span></div>
              <div class="guide-product-grid">
                <article v-for="product in hotGuideProducts" :key="product.id" class="guide-product-card">
                  <RouterLink :to="`/product/${product.id}`"><img :src="product.coverUrl" :alt="product.productName" /></RouterLink>
                  <strong>{{ product.productName }}</strong><small>{{ product.subtitle || '品质好物，售后无忧' }}</small>
                  <div><b>¥{{ money(product.salePrice) }}</b><button type="button" :disabled="product.status !== 1 || product.stock <= 0 || isAddingProduct(product.id)" :aria-label="`加入购物车：${product.productName}`" @click="addProduct(product)"><ShoppingCart :size="16" /></button></div>
                </article>
              </div>
            </section>
          </main>
        </div>
      </template>

      <template v-else-if="categoryGuide.template === 'showcase'">
        <header class="guide-title"><span></span><h1>全部品类</h1></header>
        <section v-if="categoryGuide.modules.heroCategories" class="guide-showcase-grid">
          <button v-for="category in visualCategories" :key="category.id" type="button" @click="selectCategory(category)">
            <img v-if="category.image" :src="category.image" :alt="category.name" />
            <span><strong>{{ category.name }}</strong><small>{{ category.description }}</small></span>
          </button>
          <p v-if="!visualCategories.length" class="guide-inline-empty">尚未配置视觉品类，已为你保留商品浏览入口。</p>
        </section>
        <section v-if="categoryGuide.modules.shelves" class="guide-shelf">
          <div class="guide-section-heading"><h2>{{ selectedCategory?.name || '精选品类' }}</h2><button type="button" @click="selectCategory(null)">查看全部分类 ›</button></div>
          <div class="guide-shelf-tabs"><button type="button" :class="{ active: !selectedCategory }" @click="selectCategory(null)">全部</button><button v-for="category in categories" :key="category.id" type="button" :class="{ active: selectedCategory?.id === category.id }" @click="selectCategory(category)">{{ category.name }}</button></div>
        </section>
        <section v-if="categoryGuide.modules.recommendedProducts" class="guide-product-section">
          <div class="guide-product-grid">
            <article v-for="product in guideProducts" :key="product.id" class="guide-product-card">
              <RouterLink :to="`/product/${product.id}`"><img :src="product.coverUrl" :alt="product.productName" /></RouterLink>
              <strong>{{ product.productName }}</strong><small>{{ product.subtitle || '精选推荐，品质保障' }}</small>
              <div><b>¥{{ money(product.salePrice) }}</b><button type="button" :disabled="product.status !== 1 || product.stock <= 0 || isAddingProduct(product.id)" :aria-label="`加入购物车：${product.productName}`" @click="addProduct(product)"><ShoppingCart :size="16" /></button></div>
            </article>
          </div>
        </section>
      </template>

      <template v-else>
        <header class="guide-title scenario-title"><h1>今天想买什么？</h1></header>
        <section v-if="categoryGuide.modules.scenarios" class="guide-scenarios">
          <button v-for="(scenario, index) in shoppingScenarios" :key="scenario.category.id" type="button" @click="selectCategory(scenario.category)">
            <img v-if="scenario.image" :src="scenario.image" :alt="scenario.title" />
            <span><strong>{{ scenario.title }}</strong><small>{{ scenario.description }}</small><b>›</b></span>
          </button>
          <p v-if="!shoppingScenarios.length" class="guide-inline-empty">购物场景正在准备中，可继续使用搜索或浏览人气商品。</p>
        </section>
        <section v-if="categoryGuide.modules.quickEntries" class="guide-quick-entry">
          <h2>也可以按品类找</h2>
          <div><button v-for="category in categories.slice(0, 6)" :key="category.id" type="button" @click="selectCategory(category)"><img v-if="category.image" :src="category.image" :alt="category.name" /><span>{{ category.name }}</span></button></div>
        </section>
        <section v-if="categoryGuide.modules.popularProducts" class="guide-product-section">
          <div class="guide-section-heading"><h2>本周人气好物</h2><button type="button" @click="selectCategory(null)">查看更多 ›</button></div>
          <div class="guide-product-grid">
            <article v-for="product in hotGuideProducts" :key="product.id" class="guide-product-card">
              <RouterLink :to="`/product/${product.id}`"><img :src="product.coverUrl" :alt="product.productName" /></RouterLink>
              <strong>{{ product.productName }}</strong><small>{{ product.subtitle || '人气精选，放心选购' }}</small>
              <div><b>¥{{ money(product.salePrice) }}</b><button type="button" :disabled="product.status !== 1 || product.stock <= 0 || isAddingProduct(product.id)" :aria-label="`加入购物车：${product.productName}`" @click="addProduct(product)"><ShoppingCart :size="16" /></button></div>
            </article>
          </div>
        </section>
      </template>

      <section v-if="!hasEnabledGuideModule" class="guide-safe-fallback">
        <strong>分类导购模块已全部隐藏</strong><span>系统保留商品浏览与交易入口，避免出现空白页面。</span>
        <div class="guide-product-grid"><article v-for="product in guideProducts" :key="product.id" class="guide-product-card"><RouterLink :to="`/product/${product.id}`"><img :src="product.coverUrl" :alt="product.productName" /></RouterLink><strong>{{ product.productName }}</strong><div><b>¥{{ money(product.salePrice) }}</b></div></article></div>
      </section>
    </section>

    <div v-else class="category-shell">
      <aside class="category-sidebar" aria-label="商品分类">
        <button
          type="button"
          class="category-item"
          :class="{ active: !selectedCategory }"
          @click="selectCategory(null)"
        >
          <span>全部</span>
        </button>
        <button
          v-for="category in categories"
          :key="category.id"
          type="button"
          class="category-item"
          :class="{ active: selectedCategory?.id === category.id }"
          @click="selectCategory(category)"
        >
          <span>{{ category.name }}</span>
        </button>
        <div v-if="!categories.length && !categoryLoading" class="category-empty">暂无分类</div>
      </aside>

      <main class="category-content">
        <div class="product-toolbar">
          <strong>{{ selectedCategory?.name || '全部' }}</strong>
          <div class="sort-tabs" aria-label="商品排序">
            <button type="button" :class="{ active: sortMode === 'default' }" @click="sortMode = 'default'">综合</button>
            <button type="button" :class="{ active: sortMode === 'sales' }" @click="sortMode = 'sales'">销量</button>
            <button
              type="button"
              :class="{ active: sortMode === 'priceAsc' || sortMode === 'priceDesc' }"
              @click="togglePriceSort"
            >
              价格
              <span class="sort-arrows" :class="sortMode">
                <ChevronUp :size="11" />
                <ChevronDown :size="11" />
              </span>
            </button>
          </div>
        </div>

        <ProductListSkeleton v-if="loading" :count="3" variant="list" />

        <div v-else-if="displayedProducts.length" class="product-list">
          <article v-for="product in displayedProducts" :key="product.id" class="category-product-card">
            <RouterLink class="product-image-link" :to="`/product/${product.id}`" :aria-label="`查看${product.productName}`">
              <img :src="product.coverUrl" :alt="product.productName" loading="lazy" />
              <span v-if="product.status !== 1 || product.stock <= 0" class="sold-out-mask">已售罄</span>
            </RouterLink>

            <div class="category-product-info">
              <RouterLink class="product-copy-link" :to="`/product/${product.id}`">
                <h2>{{ product.productName }}</h2>
                <p>{{ product.subtitle || '精选商城好物，品质保障，售后无忧' }}</p>
              </RouterLink>

              <div class="sales-row">
                <span>已售 {{ product.salesCount }}{{ product.salesCount >= 10000 ? '+' : ' 件' }}</span>
              </div>

              <div class="purchase-row">
                <div class="category-price">
                  <span>¥</span>
                  <strong>{{ priceParts(product.salePrice).integer }}</strong>
                  <small>.{{ priceParts(product.salePrice).decimal }}</small>
                </div>
                <button
                  type="button"
                  class="quick-cart-button"
                  :disabled="product.status !== 1 || product.stock <= 0 || isAddingProduct(product.id)"
                  :aria-label="`立即加购${product.productName}`"
                  @click="addProduct(product)"
                >
                  <ShoppingCart :size="17" />
                  <span class="cart-label-full">{{ product.status !== 1 || product.stock <= 0 ? '已售罄' : '立即加购' }}</span>
                  <span class="cart-label-short">{{ product.status !== 1 || product.stock <= 0 ? '售罄' : '加购' }}</span>
                </button>
              </div>
            </div>
          </article>
        </div>

        <div v-else class="empty-state">
          <PackageOpen :size="42" />
          <strong>该分类暂无商品</strong>
          <span>可以看看其他分类</span>
        </div>
      </main>
    </div>

    <div v-if="toast" class="toast">{{ toast }}</div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronDown, ChevronUp, PackageOpen, Search, ShoppingCart } from 'lucide-vue-next'
import { getHome, getProduct, listCategories, listCategoryProducts } from '@/api/shop'
import { useCart } from '@/store/cart'
import { money } from '@/utils/format'
import { resolveQuickCartItem } from '@/utils/quickCart'
import { checkCartPurchaseLimit } from '@/utils/purchaseLimit'
import { cartItemKey, stockAdditionViolation } from '@/utils/stockRules'
import { currentBrandLogo, currentBrandName } from '@/utils/brand'
import ProductListSkeleton from '@/components/ProductListSkeleton.vue'
import { requireShopSession } from '@/utils/authNavigation'
import { resolveCategoryGuideConfig } from '@/utils/displayConfig'

const route = useRoute()
const router = useRouter()
const { add, getQuantity, getProductQuantity } = useCart()
const loading = ref(false)
const categoryLoading = ref(false)
const categories = ref([])
const selectedCategory = ref(null)
const products = ref([])
const allProducts = ref([])
const displayConfig = ref({})
const sortMode = ref('default')
const toast = ref('')
const addingProductIds = ref(new Set())
const query = ref({ keyword: '' })
const brand = ref({ brandName: currentBrandName(), logoUrl: currentBrandLogo() })
let productRequestId = 0

const categoryGuide = computed(() => resolveCategoryGuideConfig(displayConfig.value))
const categoryGuideActive = computed(() => displayConfig.value.layoutTemplate === 'category-focus')
const guideProducts = computed(() => displayedProducts.value.slice(0, 8))
const hotGuideProducts = computed(() => [...displayedProducts.value]
  .sort((a, b) => Number(b.salesCount || 0) - Number(a.salesCount || 0))
  .slice(0, 8))
const guideHeroProduct = computed(() => guideProducts.value.find((product) => product.coverUrl) || null)
const productCoverForCategory = (category) => allProducts.value.find((product) => product.categoryName === category.name && product.coverUrl)?.coverUrl || category.image || ''
const directoryQuickCategories = computed(() => categories.value
  .filter((category) => category.id !== selectedCategory.value?.id)
  .slice(0, 6)
  .map((category) => ({ ...category, image: productCoverForCategory(category) })))
const visualCategories = computed(() => categories.value.slice(0, 6).map((category) => ({
  ...category,
  image: productCoverForCategory(category),
  description: category.remark || '精选好物 · 品质保障',
})))
const scenarioLabels = [
  ['日常补充', '轻松选到每天需要的品质好物'],
  ['精致生活', '按真实品类发现更适合自己的商品'],
  ['送礼优选', '从热销商品中快速挑选心意之选'],
]
const shoppingScenarios = computed(() => categories.value.slice(0, 3).map((category, index) => ({
  category,
  title: scenarioLabels[index][0],
  description: `${category.name} · ${scenarioLabels[index][1]}`,
  image: productCoverForCategory(category),
})))
const enabledGuideModuleKeys = computed(() => ({
  directory: ['primaryCategories', 'subcategories', 'hotProducts'],
  showcase: ['heroCategories', 'shelves', 'recommendedProducts'],
  scenario: ['scenarios', 'quickEntries', 'popularProducts'],
}[categoryGuide.value.template] || []))
const hasEnabledGuideModule = computed(() => enabledGuideModuleKeys.value.some((key) => categoryGuide.value.modules[key]))
const guideFallbackMessage = computed(() => {
  if (!categories.value.length && !allProducts.value.length) return '分类和商品尚未配置，页面已安全保留搜索、购物车与账号入口。'
  if (!categories.value.length) return '尚未配置分类，已自动使用在售商品作为安全兜底。'
  if (!allProducts.value.length) return '当前暂无在售商品，分类入口仍可正常浏览。'
  return ''
})

const syncBrand = (event) => {
  brand.value = { ...brand.value, ...(event.detail || {}) }
}

const displayedProducts = computed(() => {
  const list = [...products.value]
  if (sortMode.value === 'sales') return list.sort((a, b) => b.salesCount - a.salesCount)
  if (sortMode.value === 'priceAsc') return list.sort((a, b) => a.salePrice - b.salePrice)
  if (sortMode.value === 'priceDesc') return list.sort((a, b) => b.salePrice - a.salePrice)
  return list
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

const fetchCategories = async () => {
  categoryLoading.value = true
  try {
    const res = await listCategories({})
    categories.value = (res.data || []).map((category) => ({
      id: category.id,
      name: category.categoryName,
      image: category.iconUrl || '',
      remark: category.remark || '',
    }))
  } catch {
    categories.value = []
  } finally {
    categoryLoading.value = false
  }
}

const fetchProducts = async (categoryName = '', keyword = '') => {
  const requestId = ++productRequestId
  loading.value = true
  try {
    const res = await listCategoryProducts({ categoryName, keyword, status: 1, pageNum: 1, pageSize: 60 })
    if (requestId !== productRequestId) return
    products.value = (res.data?.list || []).map((product) => ({
      ...product,
      id: product.id,
      productName: product.productName || product.name || '商城商品',
      subtitle: product.subtitle || '',
      coverUrl: product.coverUrl || product.picUrl || '',
      salePrice: Number(product.salePrice || product.price || 0),
      marketPrice: Number(product.marketPrice || 0),
      salesCount: Math.max(0, Number(product.salesCount || 0)),
      stock: Math.max(0, Number(product.stock || 0)),
      status: Number(product.status ?? 1),
    }))
    if (!categoryName && !keyword) allProducts.value = [...products.value]
  } catch {
    if (requestId === productRequestId) products.value = []
  } finally {
    if (requestId === productRequestId) loading.value = false
  }
}

const submitSearch = () => {
  fetchProducts(selectedCategory.value?.name || '', query.value.keyword.trim())
}

const selectCategory = (category) => {
  selectedCategory.value = category
  sortMode.value = 'default'
  query.value.keyword = ''
  fetchProducts(category?.name || '')
}

const togglePriceSort = () => {
  sortMode.value = sortMode.value === 'priceAsc' ? 'priceDesc' : 'priceAsc'
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
  window.addEventListener('shop-brand-updated', syncBrand)
  try {
    displayConfig.value = (await getHome()).data?.displayConfig || {}
  } catch {
    displayConfig.value = {}
  }
  await Promise.all([fetchCategories(), fetchProducts()])
})
onBeforeUnmount(() => {
  window.removeEventListener('shop-brand-updated', syncBrand)
  window.clearTimeout(toastTimer)
})
</script>

<style scoped>
.category-page {
  width: min(1180px, calc(100% - 40px));
  min-height: calc(100vh - 140px);
  margin: 0 auto;
  padding: 18px 0 58px;
}
.cat-search-bar { display: grid; grid-template-columns: auto minmax(280px,1fr); align-items: center; gap: 18px; }
.category-brand { min-width: 0; display: inline-flex; align-items: center; gap: 9px; color: #182230; text-decoration: none; white-space: nowrap; }
.category-brand img,.category-brand-mark { width: 36px; height: 36px; flex: 0 0 36px; object-fit: contain; border-radius: 10px; }
.category-brand-mark { display: inline-flex; align-items: center; justify-content: center; color: #fff; background: linear-gradient(135deg,#0d3b8f,#12a9e8); font-size: 11px; font-weight: 900; letter-spacing: -1px; }
.category-brand strong { overflow: hidden; text-overflow: ellipsis; font-size: 15px; }
@media (max-width: 760px) {
  .cat-search-bar { grid-template-columns: 34px minmax(0,1fr); gap: 10px; }
  .category-brand strong { display: none; }
  .category-brand img,.category-brand-mark { width: 34px; height: 34px; flex-basis: 34px; }
}

.cat-search-bar {
  position: sticky;
  top: 0;
  z-index: 20;
  padding: 0 0 14px;
  background: var(--shop-page-bg, #f3f4f6);
}

.cat-search {
  height: 48px;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 90px;
  align-items: center;
  overflow: hidden;
  color: #969ca4;
  background: #fff;
  border: 2px solid var(--brand-primary);
  border-radius: 999px;
}

.cat-search > svg {
  justify-self: center;
}

.cat-search input {
  min-width: 0;
  height: 100%;
  padding: 0 4px;
  color: #272c32;
  background: transparent;
  border: 0;
  outline: 0;
}

.cat-search input::-webkit-search-cancel-button {
  cursor: pointer;
}

.cat-search button {
  height: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  color: #fff;
  background: var(--brand-primary);
  border: 0;
  font-size: 16px;
  font-weight: 800;
}

.cat-search button > svg {
  display: none;
}

.category-shell {
  display: grid;
  grid-template-columns: 166px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.category-sidebar {
  position: sticky;
  top: 10px;
  max-height: calc(100vh - 24px);
  overflow-y: auto;
  background: #f5f6f7;
  border: 1px solid #ebedef;
  border-radius: 12px;
  scrollbar-width: thin;
}

.category-item {
  position: relative;
  width: 100%;
  min-height: 58px;
  padding: 9px 14px;
  color: #40464f;
  background: transparent;
  border: 0;
  font-size: 15px;
  line-height: 1.4;
  text-align: left;
  transition: color .18s ease, background .18s ease;
}

.category-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  width: 3px;
  height: 0;
  background: var(--brand-primary);
  border-radius: 0 3px 3px 0;
  transform: translateY(-50%);
  transition: height .18s ease;
}

.category-item:hover { color: var(--brand-primary); background: #fff; }
.category-item.active { color: var(--brand-primary); background: #fff; font-weight: 800; }
.category-item.active::before { height: 28px; }
.category-empty { padding: 30px 10px; color: #9aa0a8; font-size: 13px; text-align: center; }

.category-content {
  min-width: 0;
  overflow: visible;
  background: #fff;
  border: 1px solid #ebedef;
  border-radius: 12px;
}

.product-toolbar {
  position: sticky;
  top: 0;
  z-index: 8;
  min-height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 0 22px;
  background: rgba(255,255,255,.97);
  border-bottom: 1px solid #eceff1;
  backdrop-filter: blur(10px);
}

.product-toolbar > strong { color: #20242a; font-size: 18px; }
.sort-tabs { display: flex; align-items: stretch; align-self: stretch; }
.sort-tabs button { min-width: 86px; display: inline-flex; align-items: center; justify-content: center; gap: 4px; padding: 0 18px; color: #747b85; background: transparent; border: 0; font-size: 14px; }
.sort-tabs button:hover,.sort-tabs button.active { color: var(--brand-primary); font-weight: 700; }
.sort-arrows { display: inline-flex; flex-direction: column; gap: 0; color: #bbc0c5; line-height: 7px; }
.sort-arrows svg { margin: -2px 0; }
.sort-arrows.priceAsc svg:first-child,.sort-arrows.priceDesc svg:last-child { color: var(--brand-primary); }

.product-list { display: flex; flex-direction: column; }
.category-product-card { min-height: 228px; display: grid; grid-template-columns: 214px minmax(0,1fr); gap: 22px; padding: 18px 22px; background: #fff; border-bottom: 1px solid #eff1f2; transition: background .18s ease; }
.category-product-card:last-child { border-bottom: 0; }
.category-product-card:hover { background: #fffdfd; }

.product-image-link { position: relative; display: block; align-self: center; overflow: hidden; aspect-ratio: 1; background: #f7f7f7; border: 1px solid #eceeef; border-radius: 12px; }
.product-image-link img { display: block; width: 100%; height: 100%; object-fit: cover; transition: transform .28s ease; }
.category-product-card:hover .product-image-link img { transform: scale(1.025); }
.sold-out-mask { position: absolute; inset: 0; display: grid; place-items: center; color: #fff; background: rgba(0,0,0,.45); font-size: 16px; font-weight: 800; }

.category-product-info { min-width: 0; display: flex; flex-direction: column; padding: 7px 0 3px; }
.product-copy-link { display: block; min-width: 0; }
.product-copy-link h2 { margin: 0; color: #252a31; font-size: 22px; line-height: 1.38; font-weight: 800; display: -webkit-box; overflow: hidden; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.product-copy-link p { margin: 9px 0 0; color: #8b9199; font-size: 14px; line-height: 1.6; display: -webkit-box; overflow: hidden; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.sales-row { margin-top: 12px; color: #b66e34; font-size: 14px; }
.purchase-row { display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; margin-top: auto; }
.category-price { display: flex; align-items: baseline; color: #ec1f3f; white-space: nowrap; }
.category-price > span { margin-right: 3px; font-size: 18px; font-weight: 800; }
.category-price strong { font-size: 34px; line-height: 1; letter-spacing: -1px; }
.category-price small { font-size: 19px; font-weight: 800; }
.quick-cart-button { min-width: 132px; height: 46px; display: inline-flex; align-items: center; justify-content: center; gap: 7px; padding: 0 20px; color: #fff; background: linear-gradient(135deg,var(--brand-primary),var(--brand-primary-dark)); border: 0; border-radius: 999px; box-shadow: var(--shop-card-shadow); font-size: 16px; font-weight: 800; white-space: nowrap; }
.quick-cart-button:hover:not(:disabled) { background: linear-gradient(135deg,#e91f3d,#d8092c); transform: translateY(-1px); }
.quick-cart-button:disabled { color: #fff; background: #b9bdc2; box-shadow: none; cursor: not-allowed; }
.cart-label-short { display: none; }

.empty-state { min-height: 420px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: #9ba1a8; }
.empty-state strong { margin-top: 7px; color: #626972; font-size: 16px; }
.empty-state span { font-size: 13px; }

/* 分类导购三种子版型共享同一品牌语言，仅改变信息架构。 */
.category-guide {
  --guide-blue: #1556a3;
  --guide-red: #e5484d;
  --guide-bg: #f6f7f9;
  --guide-ink: #1b2430;
  --guide-muted: #6b7280;
  --guide-line: #e8ecf1;
  min-height: 620px;
  padding: 4px 0 18px;
  color: var(--guide-ink);
  background: var(--guide-bg);
}
.guide-fallback { margin: 0 0 12px; padding: 11px 14px; color: #7a4b00; background: #fff8e6; border: 1px solid #f2d9a0; border-radius: 14px; font-size: 13px; }
.guide-directory-shell { display: grid; grid-template-columns: 180px minmax(0,1fr); gap: 12px; align-items: start; }
.guide-primary-nav { position: sticky; top: 64px; overflow: hidden; background: #fff; border: 1px solid var(--guide-line); border-radius: 14px; box-shadow: 0 7px 22px rgba(27,36,48,.05); }
.guide-primary-nav button { position: relative; width: 100%; min-height: 64px; padding: 10px 18px; color: var(--guide-ink); background: #fff; border: 0; border-bottom: 1px solid var(--guide-line); font-size: 16px; text-align: left; }
.guide-primary-nav button:last-child { border-bottom: 0; }
.guide-primary-nav button.active { color: var(--guide-blue); font-weight: 800; }
.guide-primary-nav button.active::before { content: ''; position: absolute; left: 0; top: 17px; bottom: 17px; width: 4px; background: var(--guide-blue); border-radius: 0 4px 4px 0; }
.guide-directory-content { min-width: 0; padding: 12px; background: #fff; border: 1px solid var(--guide-line); border-radius: 14px; box-shadow: 0 7px 22px rgba(27,36,48,.05); }
.guide-directory-hero { position: relative; min-height: 230px; overflow: hidden; background: #eef2f7; border-radius: 14px; }
.guide-directory-hero img { width: 100%; height: 230px; object-fit: cover; }
.guide-directory-hero div { position: absolute; left: 0; right: 0; bottom: 0; padding: 42px 22px 18px; color: #fff; background: linear-gradient(transparent,rgba(13,28,48,.78)); }
.guide-directory-hero strong,.guide-directory-hero span { display: block; }
.guide-directory-hero strong { font-size: 26px; }
.guide-directory-hero span { margin-top: 5px; font-size: 14px; }
.guide-subcategories,.guide-product-section,.guide-shelf,.guide-quick-entry { margin-top: 20px; }
.guide-subcategories h2,.guide-section-heading h2,.guide-quick-entry h2 { margin: 0; font-size: 22px; }
.guide-subcategories > div { display: grid; grid-template-columns: repeat(6,minmax(0,1fr)); gap: 10px; margin-top: 12px; }
.guide-subcategories button { min-width: 0; padding: 10px 7px; color: var(--guide-ink); background: #fff; border: 1px solid var(--guide-line); border-radius: 14px; }
.guide-subcategories img { width: 100%; aspect-ratio: 1; object-fit: cover; border-radius: 10px; }
.guide-subcategories button span { display: block; margin-top: 7px; overflow: hidden; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.guide-section-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.guide-section-heading > span { color: var(--guide-muted); font-size: 12px; }
.guide-section-heading > button { color: var(--guide-muted); background: transparent; border: 0; }
.guide-product-grid { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 12px; }
.guide-product-card { min-width: 0; overflow: hidden; padding-bottom: 12px; background: #fff; border: 1px solid var(--guide-line); border-radius: 14px; box-shadow: 0 5px 16px rgba(27,36,48,.05); }
.guide-product-card > a { display: block; aspect-ratio: 1; overflow: hidden; }
.guide-product-card img { width: 100%; height: 100%; object-fit: cover; }
.guide-product-card > strong,.guide-product-card > small { display: block; margin: 10px 12px 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.guide-product-card > small { margin-top: 4px; color: var(--guide-muted); }
.guide-product-card > div { display: flex; align-items: center; justify-content: space-between; gap: 7px; margin: 12px 12px 0; }
.guide-product-card b { color: var(--guide-red); font-size: 21px; }
.guide-product-card > div button { width: 36px; height: 36px; display: grid; place-items: center; color: #fff; background: var(--guide-blue); border: 0; border-radius: 50%; }
.guide-product-card > div button:disabled { background: #aeb5bf; }
.guide-title { display: flex; align-items: center; gap: 10px; margin: 8px 0 14px; }
.guide-title > span { width: 5px; height: 32px; background: var(--guide-blue); border-radius: 999px; }
.guide-title h1 { margin: 0; font-size: 28px; }
.guide-showcase-grid { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 14px; }
.guide-showcase-grid > button { position: relative; min-height: 280px; overflow: hidden; padding: 0; color: #fff; background: #e9edf2; border: 0; border-radius: 14px; box-shadow: 0 7px 20px rgba(27,36,48,.08); text-align: left; }
.guide-showcase-grid img { width: 100%; height: 100%; object-fit: cover; }
.guide-showcase-grid button > span { position: absolute; left: 0; right: 0; bottom: 0; padding: 42px 18px 16px; background: linear-gradient(transparent,rgba(13,28,48,.82)); }
.guide-showcase-grid strong,.guide-showcase-grid small { display: block; }
.guide-showcase-grid strong { font-size: 24px; }
.guide-showcase-grid small { margin-top: 6px; font-size: 14px; }
.guide-shelf-tabs { display: flex; gap: 9px; overflow-x: auto; padding-bottom: 4px; }
.guide-shelf-tabs button { flex: 0 0 auto; padding: 8px 18px; color: var(--guide-ink); background: #fff; border: 1px solid var(--guide-line); border-radius: 999px; }
.guide-shelf-tabs button.active { color: #fff; background: var(--guide-blue); border-color: var(--guide-blue); }
.scenario-title { margin-top: 10px; }
.guide-scenarios { display: grid; gap: 14px; }
.guide-scenarios > button { position: relative; min-height: 270px; overflow: hidden; padding: 0; background: #fff; border: 1px solid var(--guide-line); border-radius: 14px; box-shadow: 0 6px 20px rgba(27,36,48,.06); text-align: left; }
.guide-scenarios img { width: 100%; height: 270px; object-fit: cover; }
.guide-scenarios button > span { position: absolute; inset: 0; display: flex; flex-direction: column; justify-content: center; align-items: flex-start; padding: 26px 48% 26px 36px; background: linear-gradient(90deg,rgba(255,255,255,.96),rgba(255,255,255,.58),transparent); }
.guide-scenarios strong { font-size: 28px; }
.guide-scenarios small { margin-top: 9px; color: var(--guide-muted); font-size: 15px; }
.guide-scenarios b { width: 34px; height: 34px; display: grid; place-items: center; margin-top: 18px; color: #fff; background: var(--guide-blue); border-radius: 50%; font-size: 22px; }
.guide-quick-entry > div { display: grid; grid-template-columns: repeat(6,minmax(0,1fr)); gap: 12px; margin-top: 12px; }
.guide-quick-entry button { min-width: 0; padding: 12px 8px; background: #fff; border: 1px solid var(--guide-line); border-radius: 14px; }
.guide-quick-entry img { width: 100%; aspect-ratio: 1; object-fit: cover; border-radius: 10px; }
.guide-quick-entry span { display: block; margin-top: 7px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.guide-inline-empty { grid-column: 1/-1; padding: 30px; color: var(--guide-muted); background: #fff; border: 1px dashed var(--guide-line); border-radius: 14px; text-align: center; }
.guide-safe-fallback { padding: 24px; background: #fff; border: 1px solid var(--guide-line); border-radius: 14px; }
.guide-safe-fallback > strong,.guide-safe-fallback > span { display: block; }
.guide-safe-fallback > span { margin: 6px 0 16px; color: var(--guide-muted); }

@media (max-width: 760px) {
  .category-page { width: 100%; min-height: 100vh; padding: 0 0 calc(52px + env(safe-area-inset-bottom)); }
  .cat-search-bar { top: 0; padding: 8px 8px 10px; }
  .cat-search { height: 42px; grid-template-columns: 34px minmax(0, 1fr) 42px; border-width: 1.5px; }
  .cat-search input { font-size: 16px; touch-action: manipulation; }
  .cat-search button > span { display: none; }
  .cat-search button > svg { display: block; }
  .category-shell { grid-template-columns: 90px minmax(0,1fr); gap: 0; }
  .category-sidebar { top: 0; max-height: calc(100dvh - 52px - env(safe-area-inset-bottom)); border-width: 0 1px 0 0; border-radius: 0; }
  .category-item { min-height: 54px; padding: 8px 9px; font-size: 13px; text-align: center; }
  .category-item.active::before { height: 26px; }
  .category-content { border: 0; border-radius: 0; }
  .product-toolbar { top: 0; min-height: 48px; padding: 0 10px 0 12px; }
  .product-toolbar > strong { font-size: 15px; }
  .sort-tabs button { min-width: 0; padding: 0 9px; font-size: 13px; }
  .category-product-card { min-height: 144px; grid-template-columns: 116px minmax(0,1fr); gap: 10px; padding: 12px 10px; }
  .product-image-link { align-self: start; border-radius: 10px; }
  .category-product-info { padding: 1px 0; }
  .product-copy-link h2 { font-size: 15px; line-height: 1.36; }
  .product-copy-link p { margin-top: 5px; font-size: 12px; line-height: 1.45; -webkit-line-clamp: 1; }
  .sales-row { margin-top: 6px; font-size: 12px; }
  .purchase-row { gap: 7px; }
  .category-price > span { margin-right: 1px; font-size: 14px; }
  .category-price strong { font-size: 25px; }
  .category-price small { font-size: 14px; }
  .quick-cart-button { min-width: 88px; height: 36px; gap: 4px; padding: 0 11px; font-size: 13px; box-shadow: none; }
  .empty-state { min-height: 330px; }
  .category-guide { padding: 0 8px 18px; }
  .guide-directory-shell { grid-template-columns: 82px minmax(0,1fr); gap: 8px; }
  .guide-primary-nav { top: 60px; border-radius: 12px; }
  .guide-primary-nav button { min-height: 56px; padding: 8px 6px; font-size: 13px; text-align: center; }
  .guide-directory-content { padding: 8px; border-radius: 12px; }
  .guide-directory-hero,.guide-directory-hero img { min-height: 140px; height: 140px; }
  .guide-directory-hero div { padding: 34px 14px 13px; }
  .guide-directory-hero strong { font-size: 20px; }
  .guide-subcategories,.guide-product-section,.guide-shelf,.guide-quick-entry { margin-top: 15px; }
  .guide-subcategories h2,.guide-section-heading h2,.guide-quick-entry h2 { font-size: 18px; }
  .guide-subcategories > div { grid-template-columns: repeat(3,minmax(0,1fr)); gap: 7px; }
  .guide-product-grid { grid-template-columns: repeat(2,minmax(0,1fr)); gap: 8px; }
  .guide-product-card > strong,.guide-product-card > small { margin-left: 9px; margin-right: 9px; }
  .guide-product-card > div { margin: 10px 9px 0; }
  .guide-product-card b { font-size: 18px; }
  .guide-title { margin: 7px 4px 11px; }
  .guide-title h1 { font-size: 24px; }
  .guide-showcase-grid { grid-template-columns: repeat(2,minmax(0,1fr)); gap: 8px; }
  .guide-showcase-grid > button { min-height: 205px; }
  .guide-showcase-grid strong { font-size: 20px; }
  .guide-showcase-grid small { font-size: 12px; }
  .guide-scenarios { gap: 9px; }
  .guide-scenarios > button,.guide-scenarios img { min-height: 170px; height: 170px; }
  .guide-scenarios button > span { padding: 20px 42% 20px 20px; }
  .guide-scenarios strong { font-size: 23px; }
  .guide-scenarios small { font-size: 12px; }
  .guide-scenarios b { width: 30px; height: 30px; margin-top: 11px; }
  .guide-quick-entry > div { grid-template-columns: repeat(4,minmax(0,1fr)); gap: 8px; }
}

@media (max-width: 390px) {
  .category-shell { grid-template-columns: 82px minmax(0,1fr); }
  .category-item { padding: 7px 5px; font-size: 12px; }
  .product-toolbar { padding-left: 9px; }
  .sort-tabs button { padding: 0 6px; font-size: 12px; }
  .category-product-card { grid-template-columns: 105px minmax(0,1fr); gap: 6px; padding: 10px 8px; }
  .product-copy-link h2 { font-size: 14px; }
  .product-copy-link p { font-size: 11px; }
  .sales-row { font-size: 11px; }
  .category-price strong { font-size: 22px; }
  .category-price small { font-size: 12px; }
  .purchase-row { gap: 4px; }
  .quick-cart-button { min-width: 50px; height: 34px; padding: 0 4px; font-size: 12px; }
  .quick-cart-button svg { display: none; }
  .cart-label-full { display: none; }
  .cart-label-short { display: inline; }
}
</style>
