import { computed, reactive, watch } from 'vue'

const LEGACY_STORAGE_KEY = 'lingqi_mall_cart'
const STORAGE_PREFIX = 'lingqi_mall_cart_v2'
const GUEST_STORAGE_KEY = `${STORAGE_PREFIX}:guest`

const memberStorageKey = (member) => {
  const memberId = Number(member?.id)
  return Number.isSafeInteger(memberId) && memberId > 0
    ? `${STORAGE_PREFIX}:member:${memberId}`
    : GUEST_STORAGE_KEY
}

const storedMember = () => {
  if (!localStorage.getItem('shop_token')) return null
  try {
    return JSON.parse(localStorage.getItem('shop_member') || 'null')
  } catch {
    return null
  }
}

let activeStorageKey = memberStorageKey(storedMember())

// 旧版购物车由所有登录账号共用，无法安全判断商品属于哪个会员。
// 升级后废弃这份公共数据，避免新账号继续读到旧账号的购物车。
localStorage.removeItem(LEGACY_STORAGE_KEY)

const boundedPv = (pv, salePrice) => Math.min(
  Math.max(0, Number(pv || 0)),
  Math.max(0, Number(salePrice || 0)),
)

const normalizeCartItem = (item) => ({
  ...item,
  salePrice: Math.max(0, Number(item?.salePrice || 0)),
  pvValue: boundedPv(item?.pvValue, item?.salePrice),
})

const readCart = (storageKey = activeStorageKey) => {
  try {
    const parsed = JSON.parse(localStorage.getItem(storageKey) || '[]')
    return Array.isArray(parsed) ? parsed.map(normalizeCartItem) : []
  } catch (e) {
    return []
  }
}

const state = reactive({
  items: readCart(),
  checkoutKeys: null,
  addSequence: 0,
  lastAddedQuantity: 0,
})

watch(
  () => state.items,
  (items) => {
    localStorage.setItem(activeStorageKey, JSON.stringify(items))
  },
  { deep: true }
)

const replaceCartItems = (items) => {
  state.items.splice(0, state.items.length, ...items)
  state.checkoutKeys = null
  state.lastAddedQuantity = 0
}

export const switchCartOwner = (member) => {
  const nextStorageKey = memberStorageKey(member)
  if (nextStorageKey === activeStorageKey) return
  activeStorageKey = nextStorageKey
  replaceCartItems(readCart(nextStorageKey))
}

export const clearCurrentCart = () => {
  replaceCartItems([])
  localStorage.removeItem(activeStorageKey)
}

export function useCart() {
  const count = computed(() => state.items.reduce((sum, item) => sum + item.quantity, 0))
  const total = computed(() => state.items.reduce((sum, item) => sum + item.salePrice * item.quantity, 0))

  const add = (product, quantity = 1) => {
    const cartKey = product.skuId ? `${product.id}-${product.skuId}` : `${product.id}`
    const existing = state.items.find((item) => (item.cartKey || `${item.id}`) === cartKey)
    if (existing) {
      // 再次加购时同步服务端最新的价格、PV和库存，避免长期购物车保留旧配置。
      existing.salePrice = Math.max(0, Number(product.salePrice || 0))
      existing.pvValue = boundedPv(product.pvValue, product.salePrice)
      existing.stock = Number(product.stock || 0)
      existing.skuName = product.skuName || ''
      existing.skuAttrs = product.skuAttrs || product.attrsJson || ''
      existing.quantity += quantity
    } else {
      state.items.push({
        id: product.id,
        skuId: product.skuId || null,
        cartKey,
        productName: product.productName,
        skuName: product.skuName || '',
        skuAttrs: product.skuAttrs || product.attrsJson || '',
        subtitle: product.subtitle,
        coverUrl: product.coverUrl,
        salePrice: Number(product.salePrice || 0),
        marketPrice: Number(product.marketPrice || 0),
        pvValue: boundedPv(product.pvValue, product.salePrice),
        stock: Number(product.stock || 0),
        quantity,
      })
    }
    state.lastAddedQuantity = quantity
    state.addSequence += 1
    return cartKey
  }

  const update = (cartKey, quantity) => {
    const item = state.items.find((row) => (row.cartKey || `${row.id}`) === `${cartKey}`)
    if (!item) return
    const maxStock = Number(item.stock || 0)
    item.quantity = Math.min(Math.max(1, quantity), maxStock > 0 ? maxStock : 1)
  }

  const remove = (cartKey) => {
    const index = state.items.findIndex((item) => (item.cartKey || `${item.id}`) === `${cartKey}`)
    if (index >= 0) state.items.splice(index, 1)
  }

  const clear = () => {
    clearCurrentCart()
  }

  const beginCheckout = (keys) => {
    state.checkoutKeys = Array.isArray(keys) ? [...new Set(keys.map(String))] : null
  }

  const checkoutItems = computed(() => {
    if (state.checkoutKeys === null) return state.items
    const selected = new Set(state.checkoutKeys)
    return state.items.filter((item) => selected.has(String(item.cartKey || item.id)))
  })

  const checkoutTotal = computed(() => checkoutItems.value.reduce(
    (sum, item) => sum + Number(item.salePrice || 0) * Number(item.quantity || 0), 0,
  ))

  const removeCheckedOutItems = () => {
    const purchased = new Set(checkoutItems.value.map((item) => String(item.cartKey || item.id)))
    for (let index = state.items.length - 1; index >= 0; index--) {
      if (purchased.has(String(state.items[index].cartKey || state.items[index].id))) state.items.splice(index, 1)
    }
    state.checkoutKeys = null
  }

  return {
    items: state.items,
    count,
    addSequence: computed(() => state.addSequence),
    lastAddedQuantity: computed(() => state.lastAddedQuantity),
    total,
    add,
    update,
    remove,
    clear,
    beginCheckout,
    checkoutItems,
    checkoutTotal,
    removeCheckedOutItems,
  }
}
