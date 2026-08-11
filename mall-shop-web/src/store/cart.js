import { computed, reactive, watch } from 'vue'
import { cartItemKey, stockAdditionViolation } from '@/utils/stockRules'

const LEGACY_STORAGE_KEY = 'lingqi_mall_cart'
const STORAGE_PREFIX = 'lingqi_mall_cart_v2'
const GUEST_STORAGE_KEY = `${STORAGE_PREFIX}:guest`
const MEMBER_KEY = 'shop_member'
const LEGACY_TOKEN_KEY = 'shop_token'

const memberStorageKey = (member) => {
  const memberId = Number(member?.id)
  return Number.isSafeInteger(memberId) && memberId > 0
    ? `${STORAGE_PREFIX}:member:${memberId}`
    : GUEST_STORAGE_KEY
}

const storedMember = () => {
  try {
    return JSON.parse(localStorage.getItem('shop_member') || 'null')
  } catch {
    return null
  }
}

const initialMember = storedMember()
if (!initialMember) localStorage.removeItem(GUEST_STORAGE_KEY)
let activeStorageKey = memberStorageKey(initialMember)

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
  directCheckoutItems: null,
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
  state.directCheckoutItems = null
  state.lastAddedQuantity = 0
}

export const switchCartOwner = (member) => {
  const nextStorageKey = memberStorageKey(member)
  if (nextStorageKey === activeStorageKey) return
  if (!member?.id) localStorage.removeItem(GUEST_STORAGE_KEY)
  activeStorageKey = nextStorageKey
  replaceCartItems(member?.id ? readCart(nextStorageKey) : [])
}

export const clearCurrentCart = () => {
  replaceCartItems([])
  localStorage.removeItem(activeStorageKey)
}

export function useCart() {
  const count = computed(() => state.items.reduce((sum, item) => sum + item.quantity, 0))
  const total = computed(() => state.items.reduce((sum, item) => sum + item.salePrice * item.quantity, 0))

  const add = (product, quantity = 1) => {
    assertAuthenticatedCartAction()
    const cartKey = cartItemKey(product)
    const existing = state.items.find((item) => (item.cartKey || `${item.id}`) === cartKey)
    const requestedQuantity = Math.max(1, Math.floor(Number(quantity || 1)))
    const stockError = stockAdditionViolation(product.stock, requestedQuantity, existing?.quantity || 0)
    if (stockError) throw new Error(stockError)
    if (existing) {
      // 再次加购时同步服务端最新的价格、PV和库存，避免长期购物车保留旧配置。
      existing.salePrice = Math.max(0, Number(product.salePrice || 0))
      existing.pvValue = boundedPv(product.pvValue, product.salePrice)
      existing.stock = Number(product.stock || 0)
      existing.purchaseLimit = Number(product.purchaseLimit || 0)
      existing.skuName = product.skuName || ''
      existing.skuAttrs = product.skuAttrs || product.attrsJson || ''
      existing.quantity += requestedQuantity
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
        purchaseLimit: Number(product.purchaseLimit || 0),
        quantity: requestedQuantity,
      })
    }
    state.lastAddedQuantity = requestedQuantity
    state.addSequence += 1
    return cartKey
  }

  const getQuantity = (cartKey) => {
    const item = state.items.find((row) => (row.cartKey || `${row.id}`) === `${cartKey}`)
    return Number(item?.quantity || 0)
  }

  const getProductQuantity = (productId) => state.items.reduce(
    (sum, item) => Number(item.id) === Number(productId) ? sum + Number(item.quantity || 0) : sum,
    0,
  )

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
    assertAuthenticatedCartAction()
    state.directCheckoutItems = null
    state.checkoutKeys = Array.isArray(keys) ? [...new Set(keys.map(String))] : null
  }

  const beginDirectCheckout = (product, quantity = 1) => {
    assertAuthenticatedCartAction()
    const normalized = normalizeCartItem({
      ...product,
      cartKey: product.skuId ? `${product.id}-${product.skuId}` : `${product.id}`,
      quantity: Math.max(1, Number(quantity || 1)),
    })
    state.checkoutKeys = null
    state.directCheckoutItems = [normalized]
  }

  const checkoutItems = computed(() => {
    if (Array.isArray(state.directCheckoutItems)) return state.directCheckoutItems
    if (state.checkoutKeys === null) return state.items
    const selected = new Set(state.checkoutKeys)
    return state.items.filter((item) => selected.has(String(item.cartKey || item.id)))
  })

  const checkoutTotal = computed(() => checkoutItems.value.reduce(
    (sum, item) => sum + Number(item.salePrice || 0) * Number(item.quantity || 0), 0,
  ))

  const removeCheckedOutItems = () => {
    if (Array.isArray(state.directCheckoutItems)) {
      state.directCheckoutItems = null
      state.checkoutKeys = null
      return
    }
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
    getQuantity,
    getProductQuantity,
    update,
    remove,
    clear,
    beginCheckout,
    beginDirectCheckout,
    checkoutItems,
    checkoutTotal,
    removeCheckedOutItems,
  }
}

const assertAuthenticatedCartAction = () => {
  if (localStorage.getItem(MEMBER_KEY) || localStorage.getItem(LEGACY_TOKEN_KEY)) return
  throw new Error('请先登录后再操作')
}
