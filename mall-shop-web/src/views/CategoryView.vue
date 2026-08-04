<template>
  <div class="category-page">
    <div class="category-shell">
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

        <div v-if="loading" class="product-list skeleton-list" aria-label="商品加载中">
          <div v-for="index in 3" :key="index" class="product-skeleton">
            <span class="skeleton-image"></span>
            <span class="skeleton-lines"></span>
          </div>
        </div>

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
                  :disabled="product.status !== 1 || product.stock <= 0"
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
import { computed, onMounted, ref } from 'vue'
import { ChevronDown, ChevronUp, PackageOpen, ShoppingCart } from 'lucide-vue-next'
import { getProduct, listCategories, listCategoryProducts } from '@/api/shop'
import { useCart } from '@/store/cart'
import { money } from '@/utils/format'
import { resolveQuickCartItem } from '@/utils/quickCart'

const { add } = useCart()
const loading = ref(false)
const categoryLoading = ref(false)
const categories = ref([])
const selectedCategory = ref(null)
const products = ref([])
const sortMode = ref('default')
const toast = ref('')
let productRequestId = 0

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
  window.setTimeout(() => { toast.value = '' }, 1800)
}

const fetchCategories = async () => {
  categoryLoading.value = true
  try {
    const res = await listCategories({})
    categories.value = (res.data || []).map((category) => ({
      id: category.id,
      name: category.categoryName,
    }))
  } catch {
    categories.value = []
  } finally {
    categoryLoading.value = false
  }
}

const fetchProducts = async (categoryName = '') => {
  const requestId = ++productRequestId
  loading.value = true
  try {
    const res = await listCategoryProducts({ categoryName, status: 1, pageNum: 1, pageSize: 60 })
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
  } catch {
    if (requestId === productRequestId) products.value = []
  } finally {
    if (requestId === productRequestId) loading.value = false
  }
}

const selectCategory = (category) => {
  selectedCategory.value = category
  sortMode.value = 'default'
  fetchProducts(category?.name || '')
}

const togglePriceSort = () => {
  sortMode.value = sortMode.value === 'priceAsc' ? 'priceDesc' : 'priceAsc'
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
  await fetchCategories()
  await fetchProducts()
})
</script>

<style scoped>
.category-page {
  width: min(1180px, calc(100% - 40px));
  min-height: calc(100vh - 140px);
  margin: 0 auto;
  padding: 18px 0 58px;
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
.skeleton-list { padding: 0; }
.product-skeleton { min-height: 228px; display: grid; grid-template-columns: 214px 1fr; gap: 22px; padding: 18px 22px; border-bottom: 1px solid #eff1f2; }
.skeleton-image,.skeleton-lines { display: block; border-radius: 12px; background: linear-gradient(100deg,#f0f1f2 25%,#fafafa 45%,#f0f1f2 65%); background-size: 220% 100%; animation: shimmer 1.2s infinite linear; }
.skeleton-image { aspect-ratio: 1; }
.skeleton-lines { height: 130px; align-self: center; }
@keyframes shimmer { to { background-position-x: -220%; } }

@media (max-width: 760px) {
  .category-page { width: 100%; min-height: 100vh; padding: 0 0 calc(52px + env(safe-area-inset-bottom)); }
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
  .product-skeleton { min-height: 144px; grid-template-columns: 116px 1fr; gap: 10px; padding: 12px 10px; }
  .skeleton-lines { height: 92px; }
  .empty-state { min-height: 330px; }
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
