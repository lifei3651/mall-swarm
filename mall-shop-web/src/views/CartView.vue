<template>
  <div class="page cart-page">
    <header class="cart-header">
      <div class="cart-header-left">
        <h2>购物车</h2>
        <span class="cart-count">{{ count }} 件</span>
      </div>
      <div v-if="items.length" class="cart-header-actions">
        <button v-if="manageMode" class="clear-btn" type="button" @click="clearConfirmVisible = true">清空</button>
        <button class="manage-btn" type="button" @click="toggleManageMode">
          {{ manageMode ? '完成' : '管理' }}
        </button>
      </div>
    </header>

    <div v-if="items.length === 0" class="empty">
      <div>
        <p>购物车为空</p>
        <RouterLink class="btn primary" to="/">去选购</RouterLink>
      </div>
    </div>

    <div v-else class="cart-layout">
      <section class="panel cart-items-panel">
        <div
          v-for="item in items"
          :key="item.cartKey || item.id"
          class="cart-item"
          :class="{ selected: selectedKeys.has(item.cartKey || item.id), 'is-managing': manageMode }"
        >
          <button v-if="manageMode" class="item-check" :class="{ checked: selectedKeys.has(item.cartKey || item.id) }" @click="toggleSelect(item.cartKey || item.id)">
            <Check v-if="selectedKeys.has(item.cartKey || item.id)" :size="14" />
          </button>
          <div class="item-image-wrap">
            <img :src="item.coverUrl" :alt="item.productName" loading="lazy" />
          </div>
          <div class="item-info">
            <p class="line-title" :title="item.productName">{{ item.productName }}</p>
            <p class="line-sub">{{ formatProductSpec(item) }}</p>
            <div class="price-row">
              <span class="price">¥{{ money(item.salePrice) }}</span>
              <span v-if="showPv" class="line-sub">合计PV {{ money(item.pvValue * item.quantity) }}</span>
            </div>
          </div>
          <div class="item-actions">
            <div class="quantity">
              <button aria-label="减少数量" @click="changeQuantity(item, -1)">-</button>
              <span>{{ item.quantity }}</span>
              <button :disabled="isQuantityChecking(item)" aria-label="增加数量" @click="changeQuantity(item, 1)">+</button>
            </div>
          </div>
        </div>
      </section>

      <aside class="panel cart-summary-panel">
        <div class="summary-row">
          <span>商品金额</span>
          <strong>¥{{ money(manageMode ? selectedTotal : total) }}</strong>
        </div>
        <div class="summary-row">
          <span>运费</span>
          <strong>结算时计算</strong>
        </div>
        <div class="summary-row">
          <span>应付</span>
          <strong>¥{{ money(manageMode ? selectedTotal : total) }}</strong>
        </div>
        <template v-if="manageMode">
          <div class="manage-actions">
            <button class="btn secondary" :disabled="selectedKeys.size === 0" @click="requestRemoveSelected">删除({{ selectedKeys.size }})</button>
            <button class="btn primary" :disabled="selectedKeys.size === 0" @click="requestCheckoutSelected">结算({{ selectedKeys.size }})</button>
          </div>
        </template>
        <template v-else>
          <button class="btn primary checkout-btn" type="button" @click="requestCheckoutAll">去结算</button>
        </template>
      </aside>
    </div>

    <ConfirmDialog
      :visible="clearConfirmVisible"
      title="确认清空购物车？"
      :message="`将删除购物车中的全部 ${count} 件商品，清空后无法撤销。`"
      confirm-text="确认清空"
      cancel-text="暂不清空"
      icon-type="cart"
      is-danger
      @confirm="confirmClearCart"
      @cancel="clearConfirmVisible = false"
    />

    <ConfirmDialog
      :visible="Boolean(pendingAction)"
      :title="actionDialog.title"
      :message="actionDialog.message"
      :confirm-text="actionDialog.confirmText"
      :cancel-text="actionDialog.cancelText"
      :icon-type="actionDialog.iconType"
      :is-danger="actionDialog.isDanger"
      @confirm="confirmPendingAction"
      @cancel="pendingAction = ''"
    />
    <div v-if="toast" class="toast" role="status">{{ toast }}</div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Check } from 'lucide-vue-next'
import { getHome } from '@/api/shop'
import { useCart } from '@/store/cart'
import { money } from '@/utils/format'
import { formatProductSpec } from '@/utils/productSpec'
import { checkCartPurchaseLimit } from '@/utils/purchaseLimit'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const router = useRouter()
const { items, count, total, getProductQuantity, update, remove, clear: clearCart, beginCheckout } = useCart()
const displayConfig = ref({})
const showPv = computed(() => Number(displayConfig.value.showPv || 0) === 1)
const manageMode = ref(false)
const clearConfirmVisible = ref(false)
const pendingAction = ref('')
const selectedKeys = reactive(new Set())
const toast = ref('')
const quantityCheckingKeys = ref(new Set())
let toastTimer = null

const showToast = (message) => {
  toast.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 2200)
}

const changeQuantity = async (item, delta) => {
  const key = item.cartKey || item.id
  if (delta < 0) {
    update(key, item.quantity - 1)
    return
  }
  if (isQuantityChecking(item)) return
  setQuantityChecking(item, true)
  try {
    await checkCartPurchaseLimit(item, 1, getProductQuantity(item.id))
    update(key, item.quantity + 1)
  } catch (error) {
    showToast(error?.message || '当前商品已达到可购买数量上限')
  } finally {
    setQuantityChecking(item, false)
  }
}

const isQuantityChecking = (item) => quantityCheckingKeys.value.has(String(item.cartKey || item.id))
const setQuantityChecking = (item, checking) => {
  const key = String(item.cartKey || item.id)
  const next = new Set(quantityCheckingKeys.value)
  if (checking) next.add(key)
  else next.delete(key)
  quantityCheckingKeys.value = next
}

const toggleManageMode = () => {
  manageMode.value = !manageMode.value
  if (!manageMode.value) selectedKeys.clear()
}

const toggleSelect = (key) => {
  if (selectedKeys.has(key)) selectedKeys.delete(key)
  else selectedKeys.add(key)
}

const selectedTotal = computed(() => {
  return items.reduce((sum, item) => {
    const key = item.cartKey || item.id
    return selectedKeys.has(key) ? sum + item.salePrice * item.quantity : sum
  }, 0)
})

const selectedQuantity = computed(() => items.reduce((sum, item) => {
  const key = item.cartKey || item.id
  return selectedKeys.has(key) ? sum + Number(item.quantity || 0) : sum
}, 0))

const actionDialog = computed(() => {
  return {
    title: '确认删除选中商品？',
    message: `将删除选中的 ${selectedKeys.size} 种商品，共 ${selectedQuantity.value} 件。删除后无法撤销。`,
    confirmText: '确认删除',
    cancelText: '保留商品',
    iconType: 'delete',
    isDanger: true,
  }
})

const removeSelected = () => {
  const keysToRemove = [...selectedKeys]
  keysToRemove.forEach((key) => remove(key))
  selectedKeys.clear()
}

const requestRemoveSelected = () => {
  if (selectedKeys.size) pendingAction.value = 'remove-selected'
}

const validateCheckoutItems = async (rows) => {
  const quantities = new Map()
  rows.forEach((item) => quantities.set(item.id, (quantities.get(item.id) || 0) + Number(item.quantity || 0)))
  for (const item of rows) {
    if (quantities.has(item.id)) {
      await checkCartPurchaseLimit(item, 0, quantities.get(item.id))
      quantities.delete(item.id)
    }
  }
}

const requestCheckoutSelected = async () => {
  if (!selectedKeys.size) return
  const rows = items.filter((item) => selectedKeys.has(item.cartKey || item.id))
  try {
    await validateCheckoutItems(rows)
    checkoutSelected()
  } catch (error) {
    showToast(error?.message || '选中商品暂时无法结算')
  }
}

const requestCheckoutAll = async () => {
  if (!items.length) return
  try {
    await validateCheckoutItems(items)
    checkoutAll()
  } catch (error) {
    showToast(error?.message || '购物车中有商品暂时无法结算')
  }
}

const confirmClearCart = () => {
  clearCart()
  selectedKeys.clear()
  manageMode.value = false
  clearConfirmVisible.value = false
}

const checkoutSelected = () => {
  beginCheckout([...selectedKeys])
  router.push('/checkout')
}
const checkoutAll = () => {
  beginCheckout(items.map((item) => item.cartKey || item.id))
  router.push('/checkout')
}

const confirmPendingAction = () => {
  const action = pendingAction.value
  pendingAction.value = ''
  if (action === 'remove-selected') {
    removeSelected()
    return
  }
}

onMounted(async () => {
  try { displayConfig.value = (await getHome()).data?.displayConfig || {} } catch { displayConfig.value = {} }
})
onBeforeUnmount(() => window.clearTimeout(toastTimer))
</script>

<style scoped>
.cart-page { width: min(760px, calc(100% - 28px)); padding-top: 10px; }
.cart-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.cart-header-left { display: flex; align-items: baseline; gap: 8px; }
.cart-header h2 { margin: 0; font-size: 22px; }
.cart-count { color: var(--muted); font-size: 13px; }
.cart-header-actions { display:flex; align-items:center; gap:8px; }
.clear-btn { padding:6px 10px; color:#b42318; background:#fff5f4; border:0; border-radius:999px; font-size:13px; font-weight:600; }
.manage-btn { padding: 6px 16px; color: var(--accent, #e7193f); background: none; border: 1px solid var(--accent, #e7193f); border-radius: 999px; font-size: 13px; font-weight: 600; }

.cart-layout { display: grid; gap: 12px; }
.cart-items-panel { border: 0; border-radius: 16px; padding: 0; overflow: hidden; }
.cart-item { display:grid; grid-template-columns:88px minmax(0,1fr) auto; align-items:center; gap:13px; padding:14px; border-bottom:1px solid var(--line); }
.cart-item.is-managing { grid-template-columns:22px 88px minmax(0,1fr) auto; }
.cart-item:last-child { border-bottom: 0; }
.cart-item.selected { background: #fef2f2; }
.item-check { width: 22px; height: 22px; display: grid; place-items: center; border: 2px solid #d1d5db; border-radius: 50%; background: #fff; padding: 0; cursor: pointer; }
.item-check.checked { color: #fff; background: var(--accent, #e7193f); border-color: var(--accent, #e7193f); }
.item-image-wrap { width:88px; height:88px; overflow:hidden; background:#f5f6f7; border:1px solid #f0f1f2; border-radius:12px; }
.cart-item .item-image-wrap img { display:block; width:100%; height:100%; object-fit:cover; object-position:center; transform:scale(1.06); }
.item-info { min-width: 0; }
.item-info .line-title { display:-webkit-box; min-height:40px; margin:0 0 4px; overflow:hidden; color:#222934; font-size:14px; font-weight:700; line-height:20px; overflow-wrap:anywhere; -webkit-box-orient:vertical; -webkit-line-clamp:2; }
.item-info .line-sub { margin:0 0 6px; overflow:hidden; color:var(--muted); font-size:12px; text-overflow:ellipsis; white-space:nowrap; }
.price-row { display: flex; align-items: baseline; gap: 8px; }
.price { font-size: 16px; font-weight: 700; color: var(--accent, #e7193f); }
.item-actions { display: grid; justify-items: end; gap: 10px; }
.quantity { display: flex; align-items: center; gap: 0; border: 1px solid var(--line); border-radius: 8px; overflow: hidden; }
.quantity button { width: 32px; height: 32px; display: grid; place-items: center; background: #f8faf9; border: 0; font-size: 16px; cursor: pointer; }
.quantity button:disabled { opacity:.45; cursor:wait; }
.quantity span { width: 36px; text-align: center; font-size: 14px; font-weight: 600; }

.cart-summary-panel { border: 0; border-radius: 16px; }
.summary-row { display: flex; justify-content: space-between; padding: 8px 0; font-size: 14px; }
.summary-row strong { font-size: 15px; }
.checkout-btn { width: 100%; margin-top: 14px; }
.manage-actions { display: flex; gap: 10px; margin-top: 14px; }
.manage-actions button { flex: 1; }

@media (max-width: 560px) {
  .cart-item { grid-template-columns:76px minmax(0,1fr); align-items:start; gap:10px; padding:13px 12px; }
  .cart-item.is-managing { grid-template-columns:22px 76px minmax(0,1fr); }
  .item-image-wrap { width:76px; height:76px; }
  .cart-item .item-actions { grid-column:2 / -1; justify-self:end; }
  .cart-item.is-managing .item-actions { grid-column:3 / -1; }
  .item-info .line-title { min-height:38px; font-size:14px; line-height:19px; }
  .price-row { flex-wrap:wrap; row-gap:2px; }
}
</style>
