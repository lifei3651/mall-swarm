<template>
  <div class="page checkout-page">
    <header class="checkout-page-head">
      <button type="button" class="checkout-back" aria-label="返回上一页" @click="goBack">
        <ArrowLeft :size="21" />
        <span>返回</span>
      </button>
      <h2>确认订单</h2>
      <span class="checkout-head-spacer" aria-hidden="true"></span>
    </header>

    <div v-if="items.length === 0" class="empty">
      <div>
        <p>还没有可结算商品</p>
        <RouterLink class="btn primary" to="/">去选购</RouterLink>
      </div>
    </div>

    <div v-else-if="!hasToken" class="empty">
      <div>
        <p>请先登录后再提交订单</p>
        <RouterLink class="btn primary" to="/login">去登录</RouterLink>
      </div>
    </div>

    <div v-else class="checkout-layout">
      <section class="panel checkout-form-panel">
        <div class="panel-title-row">
          <h3>收货信息</h3>
          <button type="button" class="paste-trigger" @click="showAddressPaste = !showAddressPaste">
            <ClipboardPaste :size="15" />
            {{ showAddressPaste ? '收起识别' : '粘贴识别' }}
          </button>
        </div>

        <div v-if="showAddressPaste" class="address-paste-box">
          <textarea
            v-model="addressPasteText"
            class="address-paste-input"
            rows="2"
            placeholder="粘贴：收货人、手机号、省市区和详细地址"
            @paste="handleAddressPaste"
          ></textarea>
          <div class="address-paste-actions">
            <span>{{ addressParseHint || '识别后请核对收货信息' }}</span>
            <button type="button" class="btn primary compact-btn" @click="parsePastedAddress">识别并填入</button>
          </div>
        </div>

        <div v-if="addressesLoading" class="checkout-address-state" role="status">正在读取已保存地址…</div>
        <div v-else-if="addressesLoadError" class="checkout-address-state checkout-address-error" role="alert">
          <span>{{ addressesLoadError }}</span>
          <button type="button" @click="fetchAddresses">重新加载地址</button>
        </div>
        <div v-else-if="addresses.length && defaultAddress" class="checkout-address-summary">
          <div class="checkout-address-copy">
            <div class="addr-line1"><strong>{{ selectedAddress?.receiverName || defaultAddress.receiverName }}</strong><span>{{ selectedAddress?.receiverPhone || defaultAddress.receiverPhone }}</span></div>
            <div class="addr-line2">{{ selectedAddress ? joinAddress(selectedAddress) : joinAddress(defaultAddress) }}</div>
          </div>
          <button type="button" class="address-switch" @click="addressPickerVisible = true">更换地址</button>
        </div>
        <div v-else-if="addressesLoaded" class="checkout-address-empty">
          <div><strong>请选择收货地址</strong><span>下单前请先添加收货信息</span></div>
          <button type="button" class="address-switch" @click="addressPickerVisible = true">新增地址</button>
        </div>
        <div class="form-grid checkout-form-grid">
          <div v-if="addressesLoaded && !addresses.length && !addressesLoadError" class="form-item half-item">
            <label>收货人</label>
            <input v-model="form.receiverName" class="field" placeholder="姓名" @input="form.addressId = null" />
          </div>
          <div v-if="addressesLoaded && !addresses.length && !addressesLoadError" class="form-item half-item">
            <label>手机号</label>
            <input v-model="form.receiverPhone" class="field" inputmode="tel" maxlength="11" placeholder="11位手机号" @input="handleReceiverPhoneInput" />
          </div>
          <div v-if="addressesLoaded && !addresses.length && !addressesLoadError" class="form-item full">
            <label>所在地区</label>
            <ChinaRegionSelect v-model="receiverRegion" @change="syncReceiverRegion" />
          </div>
          <div v-if="addressesLoaded && !addresses.length && !addressesLoadError" class="form-item full">
            <label>详细地址</label>
            <textarea v-model="form.receiverDetailAddress" class="textarea" placeholder="街道、小区、楼栋、门牌号" @input="form.addressId = null" style="min-height:58px"></textarea>
          </div>
          <div class="form-item full checkout-remark-item">
            <label>订单备注</label>
            <input v-model="form.remark" class="field" placeholder="选填，给商家留言" />
          </div>
        </div>

        <div class="payment-section">
          <div class="payment-title"><strong>支付方式</strong><span>请选择一种</span></div>
          <div class="payment-options">
            <button
              v-for="option in paymentOptions"
              :key="option.value"
              type="button"
              class="payment-option"
              :class="[`payment-${option.value.toLowerCase()}`, { active: form.payType === option.value }]"
              @click="form.payType = option.value"
            >
              <span class="payment-icon">{{ option.icon }}</span>
              <span class="payment-copy"><strong>{{ option.label }}</strong><small>{{ option.description }}</small></span>
              <span class="payment-check">✓</span>
            </button>
          </div>
          <p class="payment-availability-hint">当前已开通余额支付；微信支付、支付宝通道完成商户配置后会自动显示。</p>
          <p v-if="form.payType === 'BALANCE'" class="line-sub balance-hint">
            当前余额 ¥{{ money(walletSummary.balance) }}；支付时需要输入独立支付密码。
          </p>
          <p v-if="form.payType === 'BALANCE' && paymentPasswordLocked" class="payment-lock-warning" role="alert" aria-live="polite">
            {{ paymentPasswordLockHint }}
          </p>
        </div>
      </section>

      <aside class="panel">
        <h3>订单商品</h3>
        <div v-for="item in items" :key="item.cartKey || item.id" class="order-line">
          <img :src="item.coverUrl" :alt="item.productName" />
          <div>
            <p class="line-title">{{ item.productName }}</p>
            <p class="line-sub">
              {{ formatProductSpec(item) }} · x {{ item.quantity }}
              <span v-if="showPv"> · PV {{ money(item.pvValue * item.quantity) }}</span>
            </p>
          </div>
          <strong>¥{{ money(item.salePrice * item.quantity) }}</strong>
        </div>
        <div class="summary-row">
          <span>商品金额</span>
          <strong>¥{{ money(total) }}</strong>
        </div>
        <div class="summary-row">
          <span>运费</span>
          <strong>{{ freightLoading ? '计算中' : `¥${money(freightAmount)}` }}</strong>
        </div>
        <div class="summary-row">
          <span>实付金额</span>
          <strong>¥{{ money(payAmount) }}</strong>
        </div>
        <!-- 大额支付短信验证 -->
        <div v-if="needSmsVerify" class="verify-section">
          <div class="verify-hint">
            ⚠️ {{ verifyConfig.message || '订单金额较大，需要短信验证' }}
            <span v-if="maskedMemberPhone">，验证码将发送至账号绑定手机 {{ maskedMemberPhone }}</span>
          </div>
          <div class="sms-row">
            <input v-model="smsCode" class="field sms-input" placeholder="请输入验证码" maxlength="6" />
            <button class="btn sms-btn" :disabled="smsCooldown > 0" @click="sendVerifyCode">
              {{ smsCooldown > 0 ? `${smsCooldown}s` : '获取验证码' }}
            </button>
          </div>
        </div>

        <button class="btn primary submit-order-btn" :disabled="submitting || (form.payType === 'BALANCE' && paymentPasswordLocked)" @click="submit">
          {{ submitting ? '提交中...' : (form.payType === 'BALANCE' && paymentPasswordLocked ? '支付密码已锁定' : `提交订单 ¥${money(payAmount)}`) }}
        </button>
        <div v-if="error" class="checkout-toast" role="alert" aria-live="assertive">{{ error }}</div>
      </aside>
    </div>

    <!-- 地址切换面板：结算页只保留紧凑摘要，新增/管理进入地址页 -->
    <div v-if="addressPickerVisible" class="address-picker-overlay" @click.self="addressPickerVisible = false">
      <section class="address-picker-sheet" role="dialog" aria-modal="true" aria-labelledby="address-picker-title">
        <header class="address-picker-head">
          <button type="button" aria-label="关闭地址选择" @click="addressPickerVisible = false"><X :size="20" /></button>
          <h3 id="address-picker-title">选择收货地址</h3>
          <span aria-hidden="true"></span>
        </header>
        <div class="address-picker-actions">
          <button type="button" @click="openAddressPage('create')"><Plus :size="16" />新增地址</button>
          <button type="button" @click="openAddressPage('manage')"><Settings2 :size="16" />管理地址</button>
        </div>
        <div class="address-picker-list">
          <button v-for="address in addresses" :key="address.id" type="button" class="address-picker-item" :class="{ active: form.addressId === address.id }" @click="selectAddressFromPicker(address)">
            <span class="address-picker-check">{{ form.addressId === address.id ? '✓' : '' }}</span>
            <span class="address-picker-copy"><strong>{{ address.receiverName }} <em>{{ address.receiverPhone }}</em></strong><small>{{ joinAddress(address) }}</small></span>
            <span v-if="Number(address.isDefault) === 1" class="address-default-tag">默认</span>
          </button>
        </div>
      </section>
    </div>

    <!-- 支付密码弹窗 -->
    <div v-if="showPayDialog" class="dialog-overlay" @click.self="closePayDialog">
      <div :class="walletSummary.hasPaymentPassword ? 'payment-sheet' : 'dialog-box'" role="dialog" aria-modal="true" :aria-labelledby="walletSummary.hasPaymentPassword ? 'pay-dialog-title' : 'setup-pay-dialog-title'">
        <template v-if="paymentPasswordSaved">
          <div class="password-saved-dialog" role="status" aria-live="polite">
            <div class="password-saved-icon">✓</div>
            <h3>支付密码已设置</h3>
            <p>支付密码已设置成功，请继续完成本次订单支付。</p>
            <button type="button" class="btn primary password-saved-action" :disabled="payPasswordSubmitting" @click="continueAfterPasswordSaved">继续支付</button>
          </div>
        </template>
        <template v-else-if="!walletSummary.hasPaymentPassword">
          <h3 id="setup-pay-dialog-title">首次设置支付密码</h3>
          <p>设置成功后将继续支付本订单。支付密码用于余额支付、转账和提现，请勿与登录密码相同。</p>
          <div class="dialog-form">
            <label>
              <span>当前登录密码</span>
              <input v-model="setupPasswordForm.loginPassword" class="field" type="password" maxlength="32" autocomplete="current-password" placeholder="用于确认是本人操作" />
            </label>
            <label>
              <span>短信验证码</span>
              <div class="dialog-sms-row">
                <input v-model="setupPasswordForm.smsCode" class="field" inputmode="numeric" maxlength="6" autocomplete="one-time-code" placeholder="6位验证码" />
                <button type="button" class="dialog-sms-btn" :disabled="setupCodeSending || setupSmsCountdown > 0" @click="sendSetupPasswordCode">
                  {{ setupSmsCountdown > 0 ? `${setupSmsCountdown}秒` : (setupCodeSending ? '发送中' : '获取验证码') }}
                </button>
              </div>
            </label>
            <small v-if="maskedMemberPhone" class="dialog-phone-hint">验证码发送至账号绑定手机 {{ maskedMemberPhone }}</small>
            <label>
              <span>设置6位支付密码</span>
              <input v-model="setupPasswordForm.newPassword" class="field dialog-pwd-input" type="password" inputmode="numeric" maxlength="6" autocomplete="new-password" placeholder="请输入6位数字" />
            </label>
            <label>
              <span>再次确认</span>
              <input v-model="setupPasswordConfirm" class="field dialog-pwd-input" type="password" inputmode="numeric" maxlength="6" autocomplete="new-password" placeholder="请再次输入" @keydown.enter="setupPasswordAndPay" />
            </label>
          </div>
          <p v-if="setupPasswordError" class="dialog-error">{{ setupPasswordError }}</p>
          <div class="dialog-actions">
            <button class="btn secondary" :disabled="setupPasswordSubmitting" @click="closePayDialog">取消</button>
            <button class="btn primary" :disabled="setupPasswordSubmitting" @click="setupPasswordAndPay">
              {{ setupPasswordSubmitting ? '设置并支付中...' : `设置并支付 ¥${money(payAmount)}` }}
            </button>
          </div>
        </template>
        <template v-else>
          <header class="payment-sheet-header">
            <button type="button" class="payment-sheet-close" aria-label="关闭支付面板" :disabled="payPasswordSubmitting" @click="closePayDialog"><X :size="22" /></button>
            <h3 id="pay-dialog-title">确认付款</h3>
            <span aria-hidden="true"></span>
          </header>

          <div class="payment-sheet-body">
            <div class="balance-pay-mark">余</div>
            <p class="payment-method-name">余额支付</p>
            <div class="payment-amount"><small>¥</small><strong>{{ money(payAmount) }}</strong></div>
            <p class="payment-balance-copy">可用余额 ¥{{ money(walletSummary.balance) }}</p>

            <div class="payment-password-label"><span>请输入6位支付密码</span><em>{{ payPasswordInput.length }}/6</em></div>
            <button
              type="button"
              class="payment-password-grid"
              :class="{ error: payPasswordError }"
              :aria-label="`已输入${payPasswordInput.length}位支付密码`"
            >
              <span v-for="index in 6" :key="index" :class="{ filled: payPasswordInput.length >= index }"><i></i></span>
            </button>

            <div class="payment-assist-row">
              <p v-if="payPasswordError" class="payment-password-error">{{ payPasswordError }}</p>
              <p v-else class="payment-secure-copy"><ShieldCheck :size="15" />支付密码将通过加密连接验证</p>
              <RouterLink to="/profile/security/change-payment-password" @click="closePayDialog">忘记密码</RouterLink>
            </div>

            <button
              type="button"
              class="payment-confirm-button"
              :disabled="payPasswordSubmitting || payPasswordInput.length !== 6"
              @click="confirmPayWithPassword"
            >
              {{ payPasswordSubmitting ? '安全支付中…' : `确认支付 ¥${money(payAmount)}` }}
            </button>
          </div>

          <div class="payment-number-keyboard" aria-label="支付密码数字键盘">
            <button v-for="number in paymentNumberKeys" :key="number" type="button" :disabled="payPasswordSubmitting" @click="appendPayDigit(number)">{{ number }}</button>
            <span aria-hidden="true"></span>
            <button type="button" :disabled="payPasswordSubmitting" @click="appendPayDigit('0')">0</button>
            <button type="button" class="payment-key-delete" aria-label="删除一位" :disabled="payPasswordSubmitting || !payPasswordInput" @click="deletePayDigit">⌫</button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ClipboardPaste, Plus, Settings2, ShieldCheck, X } from 'lucide-vue-next'
import { getHome, getMe, getWalletSummary, listAddresses, submitOrder, quoteFreight, checkPaymentVerify, sendSmsCode, sendPaymentPasswordSmsCode, setPaymentPassword, payOrderWithBalance, createAlipayOrder, getPayConfig } from '@/api/shop'
import { useCart } from '@/store/cart'
import { money, joinAddress } from '@/utils/format'
import { formatProductSpec } from '@/utils/productSpec'
import { parseChineseAddress } from '@/utils/addressParser'
import { isValidMainlandPhone, normalizeMainlandPhone } from '@/utils/phone'
import { createIdempotencyKey } from '@/utils/idempotency'
import ChinaRegionSelect from '@/components/ChinaRegionSelect.vue'
import { hasShopSession } from '@/utils/shopSession'

const route = useRoute()
const router = useRouter()
const { checkoutItems, checkoutTotal: total, removeCheckedOutItems } = useCart()
const items = checkoutItems.value
const submitting = ref(false)
const orderRequestKey = ref('')
const balancePaymentRequestKey = ref('')
const error = ref('')
let errorTimer
const clearCheckoutError = () => {
  window.clearTimeout(errorTimer)
  error.value = ''
}
const showCheckoutError = (text) => {
  window.clearTimeout(errorTimer)
  error.value = text
  errorTimer = window.setTimeout(() => { error.value = '' }, 1800)
}
const addresses = ref([])
const addressesLoading = ref(true)
const addressesLoaded = ref(false)
const addressesLoadError = ref('')
const addressPickerVisible = ref(false)
const defaultAddress = computed(() => addresses.value.find((item) => Number(item.isDefault) === 1) || addresses.value[0])
const selectedAddress = computed(() => addresses.value.find((item) => String(item.id) === String(form.value.addressId)) || defaultAddress.value)
const displayConfig = ref({})
const hasToken = ref(hasShopSession())
const receiverRegion = ref([])
const freightAmount = ref(0)
const freightLoading = ref(false)
const showAddressPaste = ref(false)
const addressPasteText = ref('')
const addressParseHint = ref('')
const payAmount = computed(() => Number(total.value || 0) + Number(freightAmount.value || 0))
const walletSummary = ref({
  balance: 0,
  hasPaymentPassword: false,
  paymentPasswordLocked: false,
  paymentPasswordLockRemainingSeconds: 0,
})
const paymentLockRemainingSeconds = ref(0)
let paymentLockTimer
const paymentPasswordLocked = computed(() => Boolean(walletSummary.value.paymentPasswordLocked) && paymentLockRemainingSeconds.value > 0)
const paymentPasswordLockHint = computed(() => {
  const seconds = Math.max(0, paymentLockRemainingSeconds.value)
  const minutes = Math.floor(seconds / 60)
  const restSeconds = seconds % 60
  const remaining = minutes > 0 ? `${minutes}分${String(restSeconds).padStart(2, '0')}秒` : `${restSeconds}秒`
  return `支付密码已锁定，请${remaining}后再试；如需立即处理，请联系客服。`
})
// 正式微信/支付宝通道尚未配置商户参数时不能让客户选择，避免生成无法完成支付的订单。
// 接入真实支付回调后，再根据后台支付配置动态追加对应选项。
const payConfig = ref({ alipayEnabled: false })
const paymentOptions = computed(() => {
  const options = [
    { value: 'BALANCE', label: '余额', icon: '余', description: '奖金结算余额' },
  ]
  if (payConfig.value.alipayEnabled) {
    options.push({ value: 'ALIPAY', label: '支付宝', icon: '支', description: '支付宝安全支付' })
  }
  return options
})

const goBack = () => {
  if (window.history.state?.back) {
    router.back()
    return
  }
  router.replace('/cart')
}

// 大额支付验证相关
const needSmsVerify = ref(false)
const verifyConfig = ref({ enabled: false, threshold: 500, message: '' })
const smsCode = ref('')
const pendingBalanceOrderId = ref(null)
const smsCooldown = ref(0)

// 支付密码弹窗
const showPayDialog = ref(false)
const paymentPasswordSaved = ref(false)
const payPasswordInput = ref('')
const payPasswordError = ref('')
const payPasswordSubmitting = ref(false)
const paymentNumberKeys = ['1', '2', '3', '4', '5', '6', '7', '8', '9']
const memberPhone = ref('')
const maskedMemberPhone = computed(() => memberPhone.value.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2'))
const setupPasswordForm = ref({ loginPassword: '', smsCode: '', newPassword: '' })
const setupPasswordConfirm = ref('')
const setupPasswordError = ref('')
const setupPasswordSubmitting = ref(false)
const setupCodeSending = ref(false)
const setupSmsCountdown = ref(0)
let setupSmsTimer
let paymentSmsTimer

const form = ref({
  agentId: route.query.agentId ? Number(route.query.agentId) : null,
  addressId: null,
  receiverName: '',
  receiverPhone: '',
  receiverAddress: '',
  receiverProvince: '',
  receiverCity: '',
  receiverDistrict: '',
  receiverDetailAddress: '',
  payType: 'BALANCE',
  remark: '',
})

const openAddressPage = (mode) => {
  sessionStorage.setItem('checkout_draft', JSON.stringify({
    form: form.value,
    receiverRegion: receiverRegion.value,
  }))
  addressPickerVisible.value = false
  router.push({ path: '/profile/addresses', query: { select: '1', mode } })
}

const selectAddressFromPicker = (address) => {
  selectAddress(address)
  addressPickerVisible.value = false
}

const handleReceiverPhoneInput = () => {
  form.value.addressId = null
  form.value.receiverPhone = normalizeMainlandPhone(form.value.receiverPhone)
}

const showPv = computed(() => Number(displayConfig.value.showPv || 0) === 1)

const parsePastedAddress = async () => {
  const parsed = parseChineseAddress(addressPasteText.value)
  const foundFields = [parsed.receiverName, parsed.receiverPhone, parsed.province, parsed.city, parsed.district, parsed.detailAddress]
    .filter(Boolean).length
  if (!foundFields) {
    addressParseHint.value = '没有识别到有效收货信息，请检查内容'
    return
  }
  form.value.addressId = null
  if (parsed.receiverName) form.value.receiverName = parsed.receiverName
  if (parsed.receiverPhone) form.value.receiverPhone = parsed.receiverPhone
  if (parsed.province) form.value.receiverProvince = parsed.province
  if (parsed.city) form.value.receiverCity = parsed.city
  if (parsed.district) form.value.receiverDistrict = parsed.district
  if (parsed.detailAddress) form.value.receiverDetailAddress = parsed.detailAddress
  receiverRegion.value = [parsed.province, parsed.city, parsed.district].filter(Boolean)
  addressParseHint.value = foundFields >= 5 ? '已自动填入，请核对后提交' : '已识别部分信息，请补充并核对'
  if (receiverRegion.value.length === 3 && form.value.receiverDetailAddress) await refreshFreight()
}

const handleAddressPaste = () => nextTick(parsePastedAddress)

const selectAddress = (address) => {
  form.value.addressId = address.id
  form.value.receiverName = address.receiverName
  form.value.receiverPhone = address.receiverPhone
  form.value.receiverAddress = joinAddress(address)
  form.value.receiverProvince = address.province || ''
  form.value.receiverCity = address.city || ''
  form.value.receiverDistrict = address.district || ''
  form.value.receiverDetailAddress = address.detailAddress || ''
  receiverRegion.value = [address.province, address.city, address.district].filter(Boolean)
  refreshFreight()
}

const syncReceiverRegion = ([province = '', city = '', district = ''] = []) => {
  form.value.addressId = null
  form.value.receiverProvince = province
  form.value.receiverCity = city
  form.value.receiverDistrict = district
  if (province && city && district && form.value.receiverDetailAddress) {
    refreshFreight()
  }
}

const fetchAddresses = async () => {
  if (!hasToken.value) return
  addressesLoading.value = true
  addressesLoadError.value = ''
  try {
    const res = await listAddresses()
    addresses.value = Array.isArray(res.data) ? res.data : (res.data?.list || [])
    addressesLoaded.value = true
    try {
      const draft = JSON.parse(sessionStorage.getItem('checkout_draft') || 'null')
      if (draft?.form) Object.assign(form.value, draft.form)
      if (Array.isArray(draft?.receiverRegion)) receiverRegion.value = draft.receiverRegion
      sessionStorage.removeItem('checkout_draft')
    } catch (_) {}
    const requestedId = route.query.addressId
    const preferredAddress = addresses.value.find((item) => requestedId && String(item.id) === String(requestedId))
      || addresses.value.find((item) => String(item.id) === String(form.value.addressId))
      || addresses.value.find((item) => Number(item.isDefault) === 1)
      || addresses.value[0]
    if (preferredAddress) selectAddress(preferredAddress)
  } catch (e) {
    addressesLoadError.value = e.message || '地址加载失败，请重新加载'
  } finally {
    addressesLoading.value = false
  }
}

const fetchDisplayConfig = async () => {
  try {
    const res = await getHome()
    displayConfig.value = res.data?.displayConfig || {}
  } catch (e) {
    displayConfig.value = {}
  }
}

const fetchWallet = async () => {
  if (!hasToken.value) return
  try {
    const res = await getWalletSummary()
    walletSummary.value = {
      balance: 0,
      hasPaymentPassword: false,
      paymentPasswordLocked: false,
      paymentPasswordLockRemainingSeconds: 0,
      ...(res.data || {}),
    }
    syncPaymentLockCountdown()
  } catch (e) {
    walletSummary.value = {
      balance: 0,
      hasPaymentPassword: false,
      paymentPasswordLocked: false,
      paymentPasswordLockRemainingSeconds: 0,
    }
    syncPaymentLockCountdown()
  }
}

const syncPaymentLockCountdown = () => {
  window.clearInterval(paymentLockTimer)
  paymentLockRemainingSeconds.value = walletSummary.value.paymentPasswordLocked
    ? Math.max(0, Number(walletSummary.value.paymentPasswordLockRemainingSeconds || 0))
    : 0
  if (paymentLockRemainingSeconds.value <= 0) {
    walletSummary.value = { ...walletSummary.value, paymentPasswordLocked: false, paymentPasswordLockRemainingSeconds: 0 }
    return
  }
  paymentLockTimer = window.setInterval(() => {
    paymentLockRemainingSeconds.value = Math.max(0, paymentLockRemainingSeconds.value - 1)
    if (paymentLockRemainingSeconds.value === 0) {
      window.clearInterval(paymentLockTimer)
      walletSummary.value = { ...walletSummary.value, paymentPasswordLocked: false, paymentPasswordLockRemainingSeconds: 0 }
    }
  }, 1000)
}

const fetchMemberPhone = async () => {
  if (!hasToken.value) return false
  if (isValidMainlandPhone(memberPhone.value)) return true
  try {
    const res = await getMe()
    memberPhone.value = res.data?.phone || ''
    return isValidMainlandPhone(memberPhone.value)
  } catch (e) {
    memberPhone.value = ''
    return false
  }
}

const validate = () => {
  if (!form.value.receiverName) return '请填写收货人'
  if (!isValidMainlandPhone(form.value.receiverPhone)) return '请填写正确的11位手机号'
  if (receiverRegion.value.length !== 3) return '请完整选择省、市、区/县'
  if (!form.value.receiverDetailAddress) return '请填写详细收货地址'
  if (form.value.payType === 'BALANCE' && walletSummary.value.hasPaymentPassword && Number(walletSummary.value.balance || 0) < payAmount.value) return '余额不足，请先充值或接收奖金后再支付'
  return ''
}

const freightRequestData = () => ({
  addressId: form.value.addressId,
  receiverProvince: form.value.receiverProvince,
  receiverCity: form.value.receiverCity,
  receiverDistrict: form.value.receiverDistrict,
  receiverDetailAddress: form.value.receiverDetailAddress,
  items: items.map((item) => ({ productId: item.id, skuId: item.skuId, quantity: item.quantity })),
})

const refreshFreight = async () => {
  if (!hasToken.value || !items.length || receiverRegion.value.length !== 3) return
  freightLoading.value = true
  clearCheckoutError()
  try {
    const res = await quoteFreight(freightRequestData())
    freightAmount.value = Number(res.data?.freightAmount || 0)
    await checkVerify()
  } catch (e) {
    freightAmount.value = 0
    showCheckoutError(e.message || '运费计算失败')
  } finally { freightLoading.value = false }
}

// 检查是否需要短信验证
const checkVerify = async () => {
  try {
    const res = await checkPaymentVerify(payAmount.value)
    const data = res.data
    verifyConfig.value = data
    needSmsVerify.value = data.needVerify
  } catch (e) {
    // 配置获取失败，默认不需要验证
    needSmsVerify.value = false
  }
}

// 发送验证码
const sendVerifyCode = async () => {
  clearCheckoutError()
  if (!await fetchMemberPhone()) {
    showCheckoutError('账号绑定手机号不正确，请重新登录')
    return
  }
  try {
    await sendSmsCode(memberPhone.value, 6) // 6=支付确认；服务端会再次绑定当前登录会员手机号
    smsCooldown.value = 60
    window.clearInterval(paymentSmsTimer)
    paymentSmsTimer = window.setInterval(() => {
      smsCooldown.value--
      if (smsCooldown.value <= 0) window.clearInterval(paymentSmsTimer)
    }, 1000)
  } catch (e) {
    showCheckoutError(e.message || '发送失败')
  }
}

const sendSetupPasswordCode = async () => {
  setupPasswordError.value = ''
  if (!await fetchMemberPhone()) {
    setupPasswordError.value = '账号绑定手机号不正确，请重新登录'
    return
  }
  setupCodeSending.value = true
  try {
    await sendPaymentPasswordSmsCode()
    setupSmsCountdown.value = 60
    window.clearInterval(setupSmsTimer)
    setupSmsTimer = window.setInterval(() => {
      setupSmsCountdown.value--
      if (setupSmsCountdown.value <= 0) window.clearInterval(setupSmsTimer)
    }, 1000)
  } catch (e) {
    setupPasswordError.value = e.message || '验证码发送失败'
  } finally {
    setupCodeSending.value = false
  }
}

const closePayDialog = () => {
  if (payPasswordSubmitting.value || setupPasswordSubmitting.value) return
  showPayDialog.value = false
  paymentPasswordSaved.value = false
  payPasswordError.value = ''
  setupPasswordError.value = ''
}

const appendPayDigit = (digit) => {
  if (payPasswordSubmitting.value || payPasswordInput.value.length >= 6) return
  payPasswordInput.value = `${payPasswordInput.value}${digit}`.slice(0, 6)
  payPasswordError.value = ''
}

const deletePayDigit = () => {
  if (payPasswordSubmitting.value || !payPasswordInput.value) return
  payPasswordInput.value = payPasswordInput.value.slice(0, -1)
  payPasswordError.value = ''
}

const submitTrustedAlipayForm = (payHtml) => {
  const parsed = new DOMParser().parseFromString(String(payHtml || ''), 'text/html')
  const sourceForm = parsed.querySelector('form')
  if (!sourceForm) throw new Error('支付宝支付页生成失败，请稍后重试')

  let action
  try {
    action = new URL(sourceForm.getAttribute('action') || '')
  } catch {
    throw new Error('支付宝支付地址无效，请稍后重试')
  }
  const allowedHosts = new Set(['openapi.alipay.com', 'openapi.alipaydev.com'])
  if (action.protocol !== 'https:' || !allowedHosts.has(action.hostname)) {
    throw new Error('支付宝支付地址校验失败，请稍后重试')
  }

  const form = document.createElement('form')
  form.method = 'post'
  form.action = action.toString()
  form.style.display = 'none'
  sourceForm.querySelectorAll('input[name]').forEach((sourceInput) => {
    const input = document.createElement('input')
    input.type = 'hidden'
    input.name = sourceInput.getAttribute('name') || ''
    input.value = sourceInput.getAttribute('value') || ''
    form.appendChild(input)
  })
  document.body.appendChild(form)
  form.submit()
}

const setupPasswordAndPay = async () => {
  if (setupPasswordSubmitting.value || payPasswordSubmitting.value || submitting.value) return
  setupPasswordError.value = ''
  const setup = setupPasswordForm.value
  if (!setup.loginPassword) {
    setupPasswordError.value = '请输入当前登录密码'
    return
  }
  if (!/^\d{6}$/.test(setup.smsCode)) {
    setupPasswordError.value = '请输入6位短信验证码'
    return
  }
  if (!/^\d{6}$/.test(setup.newPassword)) {
    setupPasswordError.value = '支付密码必须是6位数字'
    return
  }
  if (setup.newPassword !== setupPasswordConfirm.value) {
    setupPasswordError.value = '两次输入的支付密码不一致'
    return
  }
  setupPasswordSubmitting.value = true
  try {
    const newPassword = setup.newPassword
    await setPaymentPassword({
      loginPassword: setup.loginPassword,
      smsCode: setup.smsCode,
      newPassword,
    })
    walletSummary.value = { ...walletSummary.value, hasPaymentPassword: true }
    payPasswordInput.value = newPassword
    setupPasswordForm.value = { loginPassword: '', smsCode: '', newPassword: '' }
    setupPasswordConfirm.value = ''
    window.clearInterval(setupSmsTimer)
    setupSmsCountdown.value = 0
    paymentPasswordSaved.value = true
  } catch (e) {
    if (walletSummary.value.hasPaymentPassword) {
      payPasswordError.value = e.message || '支付失败，请重新输入支付密码'
    } else {
      setupPasswordError.value = e.message || '支付密码设置失败'
    }
  } finally {
    setupPasswordSubmitting.value = false
  }
}

const continueAfterPasswordSaved = async () => {
  if (payPasswordSubmitting.value || setupPasswordSubmitting.value) return
  paymentPasswordSaved.value = false
  await confirmPayWithPassword()
}

const submit = async () => {
  if (submitting.value || payPasswordSubmitting.value) return
  clearCheckoutError()
  if (form.value.payType === 'BALANCE' && paymentPasswordLocked.value) {
    showCheckoutError(paymentPasswordLockHint.value)
    return
  }
  const validationError = validate()
  if (validationError) {
    showCheckoutError(validationError)
    return
  }

  form.value.receiverAddress = [
    form.value.receiverProvince,
    form.value.receiverCity,
    form.value.receiverDistrict,
    form.value.receiverDetailAddress,
  ].filter(Boolean).join(' ')

  // 如果需要短信验证，检查验证码
  if (needSmsVerify.value && (!smsCode.value || smsCode.value.length !== 6)) {
    showCheckoutError('请输入6位短信验证码')
    return
  }

  // 余额支付需要输入支付密码，弹出对话框
  if (form.value.payType === 'BALANCE') {
    payPasswordInput.value = ''
    paymentPasswordSaved.value = false
    payPasswordError.value = ''
    setupPasswordError.value = ''
    if (!walletSummary.value.hasPaymentPassword && !await fetchMemberPhone()) {
      setupPasswordError.value = '账号信息加载失败，请重新登录'
    }
    showPayDialog.value = true
    return
  }

  // 非余额支付直接提交
  await doSubmitOrder()
}

const confirmPayWithPassword = async () => {
  if (payPasswordSubmitting.value || submitting.value) return
  payPasswordError.value = ''
  if (!/^\d{6}$/.test(payPasswordInput.value)) {
    payPasswordError.value = '请输入6位数字支付密码'
    return
  }
  payPasswordSubmitting.value = true
  try {
    await doSubmitOrder(payPasswordInput.value)
    showPayDialog.value = false
  } catch (e) {
    payPasswordError.value = e.message || '支付失败'
    payPasswordInput.value = ''
    if (String(e.message || '').includes('锁定30分钟')) await fetchWallet()
  } finally {
    payPasswordSubmitting.value = false
  }
}

const doSubmitOrder = async (paymentPassword) => {
  if (submitting.value) return
  submitting.value = true
  clearCheckoutError()
  try {
    const orderData = {
      ...form.value,
      items: items.map((item) => ({
        productId: item.id,
        skuId: item.skuId,
        quantity: item.quantity,
      })),
    }
    if (needSmsVerify.value) orderData.smsCode = smsCode.value

    let orderId = pendingBalanceOrderId.value
    if (!orderId) {
      if (!orderRequestKey.value) orderRequestKey.value = createIdempotencyKey('order')
      const res = await submitOrder(orderData, orderRequestKey.value)
      orderId = res.data.order.id
      orderRequestKey.value = ''
      if (paymentPassword) pendingBalanceOrderId.value = orderId
    }
    if (paymentPassword) {
      // 余额支付
      if (!balancePaymentRequestKey.value) balancePaymentRequestKey.value = createIdempotencyKey('balance-pay')
      await payOrderWithBalance(orderId, paymentPassword, balancePaymentRequestKey.value)
      balancePaymentRequestKey.value = ''
      pendingBalanceOrderId.value = null
    } else if (form.value.payType === 'ALIPAY') {
      // 支付宝支付：创建支付宝订单并跳转
      const alipayRes = await createAlipayOrder(orderId)
      const payUrl = alipayRes.data?.payUrl
      if (payUrl) {
        // 仅重建支付宝官方网关表单，不执行服务端响应中的任意 HTML 或脚本。
        submitTrustedAlipayForm(payUrl)
        return // 不跳转订单详情，等待支付宝回调
      }
      throw new Error('创建支付宝订单失败')
    }
    removeCheckedOutItems()
    router.push(`/orders/${orderId}`)
  } catch (e) {
    showCheckoutError(e.message || '提交失败')
    throw e
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (!items.length) {
    router.replace('/cart')
    return
  }
  fetchDisplayConfig()
  fetchWallet()
  fetchAddresses()
  fetchMemberPhone()
  checkVerify()
  fetchPayConfig()
})

const fetchPayConfig = async () => {
  try {
    const res = await getPayConfig()
    payConfig.value = res.data || {}
  } catch {}
}
onBeforeUnmount(() => {
  window.clearInterval(setupSmsTimer)
  window.clearInterval(paymentSmsTimer)
  window.clearInterval(paymentLockTimer)
  window.clearTimeout(errorTimer)
})
</script>

<style scoped>
.checkout-page { padding-top: 10px; }
.checkout-toast { position:fixed; top:calc(18px + env(safe-area-inset-top)); left:50%; z-index:1200; max-width:min(88vw,420px); padding:11px 16px; color:#fff; background:rgba(180,35,24,.96); border-radius:10px; box-shadow:0 8px 24px rgba(15,23,42,.18); transform:translateX(-50%); font-size:13px; line-height:1.5; text-align:center; pointer-events:none; }
.checkout-page-head {
  position: sticky;
  top: 0;
  z-index: 30;
  min-height: 52px;
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr) 92px;
  align-items: center;
  margin-bottom: 12px;
  padding: 5px 8px;
  background: rgba(245, 246, 247, .94);
  border-bottom: 1px solid rgba(223, 231, 226, .9);
  backdrop-filter: blur(12px);
}
.checkout-page-head h2 { margin: 0; color: #20242b; font-size: 18px; text-align: center; }
.checkout-back {
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  justify-self: start;
  gap: 3px;
  padding: 0 11px 0 8px;
  color: #3f4751;
  background: #fff;
  border: 1px solid #e2e6e4;
  border-radius: 999px;
  box-shadow: 0 2px 8px rgba(31, 41, 55, .06);
  font-size: 13px;
  font-weight: 700;
}
.checkout-back:active { color: var(--accent, #e7193f); background: #fff5f6; border-color: #ffc6d0; }
.checkout-head-spacer { width: 92px; }
.checkout-form-panel { overflow: hidden; }
.panel-title-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.panel-title-row h3 { margin: 0; }
.paste-trigger { display: inline-flex; align-items: center; gap: 5px; padding: 6px 9px; color: var(--accent, #e7193f); background: #fff4f5; border: 1px solid #ffd3da; border-radius: 999px; font-size: 12px; font-weight: 700; }
.address-paste-box { margin-bottom: 12px; padding: 10px; background: #fff9f5; border: 1px dashed #f0ad91; border-radius: 10px; }
.address-paste-input { width: 100%; min-height: 58px; padding: 9px 10px; resize: vertical; color: var(--ink); background: #fff; border: 1px solid #eaded8; border-radius: 8px; outline: none; line-height: 1.5; }
.address-paste-input:focus { border-color: var(--accent, #e7193f); }
.address-paste-actions { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: 7px; color: var(--muted); font-size: 12px; }
.compact-btn { min-height: 32px; padding: 6px 12px; white-space: nowrap; font-size: 12px; }
.saved-addresses { margin-bottom: 12px; padding-bottom: 3px; overflow-x: auto; flex-wrap: nowrap; scrollbar-width: thin; }
.saved-addresses .chip { flex: 0 0 auto; }
.checkout-address-summary,.checkout-address-empty { display:flex; align-items:center; justify-content:space-between; gap:14px; margin-bottom:12px; padding:14px; background:#fff9fa; border:1px solid #ffd8df; border-radius:14px; }
.checkout-address-state { display:flex; align-items:center; justify-content:space-between; gap:10px; margin-bottom:12px; padding:14px; color:var(--muted); background:#fafbfc; border:1px solid #e5e7eb; border-radius:14px; font-size:13px; line-height:1.5; }
.checkout-address-state button { flex:0 0 auto; padding:6px 10px; color:var(--accent, #e7193f); background:#fff; border:1px solid #ffc6d0; border-radius:999px; font-size:12px; font-weight:700; cursor:pointer; }
.checkout-address-error { color:#b42318; background:#fff7f7; border-color:#f3c3c8; }
.checkout-address-copy { min-width:0; }
.checkout-address-copy .addr-line2 { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.checkout-address-empty { background:#fafbfc; border-color:#e5e7eb; }
.checkout-address-empty > div { display:grid; gap:4px; }
.checkout-address-empty span { color:var(--muted); font-size:12px; }
.checkout-remark-item { margin-top:2px; }
.checkout-form-grid { gap: 10px 12px; }
.checkout-form-grid :deep(.china-region-select) { gap: 8px; }
.payment-section { margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--line); }
.payment-availability-hint { margin: 9px 0 0; color: #667085; font-size: 12px; line-height: 1.6; }
.payment-title { display: flex; align-items: baseline; gap: 8px; margin-bottom: 9px; }
.payment-title span { color: var(--muted); font-size: 12px; }

.verify-section {
  margin-top: 14px;
  padding: 12px;
  background: #fff7ed;
  border-radius: 10px;
  border: 1px solid #fed7aa;
}

.verify-hint {
  font-size: 12px;
  color: #92400e;
  margin-bottom: 10px;
}

.sms-row {
  display: flex;
  gap: 10px;
}

.sms-input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid #dfe7e2;
  border-radius: 8px;
  font-size: 14px;
}

.sms-btn {
  white-space: nowrap;
  padding: 0 14px;
  background: var(--accent, #0f766e);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
}

.sms-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.payment-options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.payment-option {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 64px;
  padding: 10px 28px 10px 10px;
  border: 1.5px solid #d4dcd8;
  border-radius: 12px;
  background: #f8faf9;
  color: inherit;
  text-align: left;
  transition: border-color .16s ease, box-shadow .16s ease, background .16s ease;
}

.payment-option.active {
  border-color: var(--accent, #e7193f);
  background: color-mix(in srgb, var(--accent, #e7193f) 6%, white);
  box-shadow: 0 4px 14px color-mix(in srgb, var(--accent, #e7193f) 18%, transparent);
}

.payment-option small { display: block; margin-top: 2px; color: #89958f; white-space: nowrap; font-size: 11px; }
.payment-copy { min-width: 0; }
.payment-icon { display: grid; place-items: center; width: 34px; height: 34px; flex: 0 0 34px; border-radius: 10px; color: #fff; background: #8b9691; font-weight: 800; }
.payment-wechat .payment-icon { background: #18ad5c; }
.payment-alipay .payment-icon { background: #1677ff; }
.payment-balance .payment-icon { background: #e84b45; }
.payment-check { position: absolute; right: 8px; top: 8px; display: none; width: 17px; height: 17px; place-items: center; color: #fff; background: var(--accent, #e7193f); border-radius: 50%; font-size: 11px; font-weight: 900; }
.payment-option.active .payment-check { display: grid; }
.balance-hint { margin: 8px 0 0; }
.payment-lock-warning {
  margin: 10px 0 0;
  padding: 10px 12px;
  color: #b42318;
  background: #fff4f2;
  border: 1px solid #fecdca;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.55;
}

.default-address-card { padding: 12px 14px; background: #f8faf9; border: 1.5px solid #d4dcd8; border-radius: 12px; cursor: pointer; transition: border-color .15s; }
.default-address-card.selected { border-color: var(--accent, #e7193f); background: color-mix(in srgb, var(--accent, #e7193f) 4%, white); }
.addr-line1 { display: flex; align-items: center; gap: 10px; font-size: 14px; }
.addr-line1 strong { font-size: 15px; }
.addr-line1 span { color: var(--muted); font-size: 13px; }
.address-switch { margin-left: auto; padding: 5px 8px; color: var(--accent, #e7193f); background: transparent; border: 0; border-radius: 8px; font-size: 12px; cursor: pointer; }
.address-switch:active { background: color-mix(in srgb, var(--accent, #e7193f) 8%, transparent); }
.addr-line2 { margin-top: 4px; color: #4b5563; font-size: 13px; line-height: 1.5; }
.other-addresses-hint { margin-top: 8px; color: var(--muted); font-size: 12px; cursor: pointer; text-align: center; }
.other-addresses-list { display: flex; gap: 8px; margin-top: 8px; flex-wrap: wrap; }
.address-picker-overlay { position:fixed; inset:0; z-index:900; display:grid; align-items:end; background:rgba(15,23,42,.42); backdrop-filter:blur(2px); }
.address-picker-sheet { width:min(520px,100%); max-height:min(76dvh,620px); overflow:auto; margin:0 auto; padding-bottom:max(12px,env(safe-area-inset-bottom)); background:#fff; border-radius:22px 22px 0 0; box-shadow:0 -16px 45px rgba(15,23,42,.18); }
.address-picker-head { min-height:52px; display:grid; grid-template-columns:42px 1fr 42px; align-items:center; border-bottom:1px solid #eef0f2; }
.address-picker-head h3 { margin:0; text-align:center; font-size:16px; }
.address-picker-head button { width:38px; height:38px; display:grid; place-items:center; justify-self:center; color:#667085; background:transparent; border:0; border-radius:50%; }
.address-picker-actions { display:grid; grid-template-columns:1fr 1fr; gap:10px; padding:14px 16px 8px; }
.address-picker-actions button { min-height:42px; display:inline-flex; align-items:center; justify-content:center; gap:6px; color:var(--accent,#e7193f); background:#fff5f6; border:1px solid #ffd3da; border-radius:11px; font-size:13px; font-weight:700; }
.address-picker-actions button:last-child { color:#475467; background:#f8fafc; border-color:#e4e7ec; }
.address-picker-list { display:grid; gap:8px; padding:8px 16px 4px; }
.address-picker-item { display:flex; align-items:center; gap:10px; width:100%; padding:12px; color:inherit; text-align:left; background:#fff; border:1px solid #e5e7eb; border-radius:12px; }
.address-picker-item.active { background:#fff8f9; border-color:var(--accent,#e7193f); }
.address-picker-check { width:21px; height:21px; flex:0 0 21px; display:grid; place-items:center; color:#fff; background:#d0d5dd; border-radius:50%; font-size:13px; font-weight:800; }
.address-picker-item.active .address-picker-check { background:var(--accent,#e7193f); }
.address-picker-copy { min-width:0; display:grid; gap:4px; }
.address-picker-copy strong { font-size:14px; }
.address-picker-copy em { margin-left:8px; color:var(--muted); font-size:12px; font-style:normal; font-weight:400; }
.address-picker-copy small { overflow:hidden; color:#667085; text-overflow:ellipsis; white-space:nowrap; font-size:12px; }
.address-default-tag { margin-left:auto; padding:3px 6px; color:var(--accent,#e7193f); background:#fff0f2; border-radius:5px; font-size:10px; font-weight:700; }
.submit-order-btn { width: 100%; margin-top: 14px; min-height: 42px; font-size: 14px; }

/* 支付密码弹窗 */
.dialog-overlay { position: fixed; inset: 0; z-index: 1000; display: grid; place-items: center; background: rgba(0,0,0,.45); backdrop-filter: blur(2px); }
.dialog-box { width: min(380px, calc(100% - 32px)); max-height: calc(100dvh - 24px); padding: 24px; overflow-y: auto; background: #fff; border-radius: 18px; box-shadow: 0 20px 60px rgba(0,0,0,.2); }
.dialog-box h3 { margin: 0 0 8px; font-size: 18px; text-align: center; }
.dialog-box p { margin: 0 0 16px; color: var(--muted); font-size: 13px; text-align: center; line-height: 1.6; }
.password-saved-dialog { padding: 30px 24px 24px; text-align: center; }
.password-saved-icon { width: 48px; height: 48px; display: grid; place-items: center; margin: 0 auto 12px; color: #fff; background: #16a36a; border-radius: 50%; font-size: 28px; font-weight: 800; }
.password-saved-dialog h3 { margin: 0 0 8px; color: var(--ink); font-size: 20px; }
.password-saved-dialog p { margin: 0 0 20px; color: var(--muted); font-size: 13px; line-height: 1.6; }
.password-saved-action { width: 100%; min-height: 44px; }
.dialog-pay-info { font-size: 15px !important; color: var(--ink) !important; }
.dialog-pay-info strong { color: var(--accent, #e7193f); font-size: 20px; }
.dialog-pwd-input { width: 100%; text-align: center; font-size: 20px; letter-spacing: 8px; padding: 12px; }
.dialog-error { color: #b42318 !important; font-size: 12px !important; margin-top: 4px !important; }
.dialog-form { display: grid; gap: 11px; margin-top: 14px; }
.dialog-form label { display: grid; gap: 6px; color: var(--ink); font-size: 12px; font-weight: 700; }
.dialog-form .field { width: 100%; }
.dialog-sms-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; }
.dialog-sms-btn { min-width: 102px; padding: 0 11px; color: var(--accent, #e7193f); background: #fff; border: 1px solid #d8e0e8; border-radius: 10px; font-size: 12px; font-weight: 700; }
.dialog-sms-btn:disabled { color: var(--muted); background: #f5f7f9; }
.dialog-phone-hint { margin-top: -4px; color: var(--muted); font-size: 11px; }
.dialog-actions { display: flex; gap: 10px; margin-top: 16px; }
.dialog-actions button { flex: 1; }

.payment-sheet {
  width: min(430px, 100%);
  max-height: calc(100dvh - 10px);
  align-self: end;
  overflow-y: auto;
  background: #fff;
  border-radius: 24px 24px 0 0;
  box-shadow: 0 -18px 60px rgba(15, 23, 42, .2);
  animation: payment-sheet-up .22s ease-out;
  overscroll-behavior: contain;
}
@keyframes payment-sheet-up { from { opacity: .65; transform: translateY(36px); } to { opacity: 1; transform: translateY(0); } }
.payment-sheet-header { min-height: 56px; display: grid; grid-template-columns: 48px minmax(0, 1fr) 48px; align-items: center; border-bottom: 1px solid #f0f1f2; }
.payment-sheet-header h3 { margin: 0; color: #20242b; font-size: 17px; text-align: center; }
.payment-sheet-close { width: 42px; height: 42px; display: grid; place-items: center; justify-self: center; padding: 0; color: #555d66; background: transparent; border: 0; border-radius: 50%; }
.payment-sheet-close:active { background: #f3f4f6; }
.payment-sheet-close:disabled { opacity: .45; }
.payment-sheet-body { padding: 16px 22px 15px; }
.balance-pay-mark { width: 38px; height: 38px; display: grid; place-items: center; margin: 0 auto 5px; color: #fff; background: linear-gradient(145deg, #f05a63, var(--accent, #e7193f)); border-radius: 13px; box-shadow: 0 7px 18px rgba(231, 25, 63, .2); font-size: 15px; font-weight: 900; }
.payment-method-name { margin: 0; color: #6f7781; font-size: 12px; text-align: center; }
.payment-amount { display: flex; align-items: baseline; justify-content: center; gap: 3px; margin-top: 3px; color: #15191f; font-variant-numeric: tabular-nums; }
.payment-amount small { font-size: 20px; font-weight: 800; }
.payment-amount strong { font-size: 35px; line-height: 1.18; letter-spacing: -.8px; }
.payment-balance-copy { margin: 2px 0 15px; color: #989fa8; font-size: 11px; text-align: center; }
.payment-password-label { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; color: #4c535c; font-size: 12px; }
.payment-password-label em { color: #a0a6ad; font-style: normal; font-variant-numeric: tabular-nums; }
.payment-password-grid { width: 100%; height: 52px; display: grid; grid-template-columns: repeat(6, 1fr); overflow: hidden; padding: 0; background: #fff; border: 1.5px solid #d8dde3; border-radius: 12px; transition: border-color .16s ease, box-shadow .16s ease; }
.payment-password-grid:focus-visible { outline: none; border-color: var(--accent, #e7193f); box-shadow: 0 0 0 3px rgba(231, 25, 63, .09); }
.payment-password-grid.error { border-color: #e5484d; box-shadow: 0 0 0 3px rgba(229, 72, 77, .08); }
.payment-password-grid span { display: grid; place-items: center; border-right: 1px solid #e4e7eb; }
.payment-password-grid span:last-child { border-right: 0; }
.payment-password-grid i { width: 10px; height: 10px; opacity: 0; background: #20242b; border-radius: 50%; transform: scale(.55); transition: opacity .12s ease, transform .12s ease; }
.payment-password-grid span.filled i { opacity: 1; transform: scale(1); }
.payment-assist-row { min-height: 31px; display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.payment-assist-row p { min-width: 0; display: flex; align-items: center; gap: 4px; margin: 0; font-size: 11px; line-height: 1.4; }
.payment-assist-row a { flex: 0 0 auto; color: var(--accent, #e7193f); font-size: 11px; font-weight: 700; }
.payment-secure-copy { color: #8c949d; }
.payment-secure-copy svg { color: #32a66a; }
.payment-password-error { color: #d92d20; }
.payment-confirm-button { width: 100%; min-height: 47px; padding: 0 18px; color: #fff; background: linear-gradient(135deg, var(--accent, #e7193f), #d80f35); border: 0; border-radius: 13px; box-shadow: 0 8px 18px rgba(231, 25, 63, .18); font-size: 15px; font-weight: 800; }
.payment-confirm-button:disabled { color: #a6aab0; background: #eceef1; box-shadow: none; }
.payment-number-keyboard { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1px; padding: 1px 0 max(1px, env(safe-area-inset-bottom)); background: #d9dde2; border-top: 1px solid #d9dde2; }
.payment-number-keyboard button, .payment-number-keyboard > span { min-height: 49px; display: grid; place-items: center; padding: 0; color: #1f242a; background: #fff; border: 0; border-radius: 0; font-size: 22px; font-weight: 600; font-variant-numeric: tabular-nums; }
.payment-number-keyboard button:active { background: #e9ecf0; }
.payment-number-keyboard button:disabled { color: #adb2b8; }
.payment-number-keyboard > span, .payment-number-keyboard .payment-key-delete { background: #eef0f3; }
.payment-number-keyboard .payment-key-delete { font-size: 24px; font-weight: 400; }

@media (max-width: 640px) {
  .checkout-page { width: 100%; padding-top: 0; }
  .checkout-page-head { grid-template-columns: 78px minmax(0, 1fr) 78px; margin-bottom: 10px; padding: 5px 10px; }
  .checkout-page-head h2 { font-size: 17px; }
  .checkout-back { min-height: 38px; padding: 0 10px 0 7px; }
  .checkout-head-spacer { width: 78px; }
  .checkout-layout, .checkout-page > .empty { width: calc(100% - 28px); margin-left: auto; margin-right: auto; }
  .checkout-form-panel { padding: 14px; }
  .checkout-form-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 9px 8px; }
  .checkout-form-grid .full { grid-column: 1 / -1; }
  .checkout-form-grid .optional-item { grid-column: span 1; }
  .checkout-form-grid :deep(.china-region-select) { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 5px; }
  .checkout-form-grid :deep(.china-region-select .field) { padding: 0 5px; font-size: 12px; }
  .payment-options { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 6px; }
  .payment-option { justify-content: center; min-height: 70px; padding: 8px 4px; flex-direction: column; gap: 4px; text-align: center; }
  .payment-option small { display: none; }
  .payment-icon { width: 29px; height: 29px; flex-basis: 29px; border-radius: 8px; font-size: 12px; }
  .payment-copy strong { font-size: 12px; }
  .payment-check { right: 5px; top: 5px; width: 15px; height: 15px; font-size: 9px; }
  .address-paste-actions { align-items: flex-start; }
}

@media (max-width: 370px) {
  .checkout-form-grid .optional-item { grid-column: 1 / -1; }
  .payment-copy strong { font-size: 11px; }
  .payment-sheet-body { padding: 13px 16px 12px; }
  .payment-amount strong { font-size: 32px; }
  .payment-balance-copy { margin-bottom: 12px; }
  .payment-number-keyboard button, .payment-number-keyboard > span { min-height: 46px; }
}
</style>
