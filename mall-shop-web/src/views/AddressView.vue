<template>
  <div class="page sub-page address-page">
    <header class="sub-page-head">
      <button type="button" aria-label="返回" @click="router.back()"><ArrowLeft :size="22" /></button>
      <h2>{{ selecting ? '选择收货地址' : '收货地址' }}</h2>
      <button type="button" aria-label="新增地址" @click="startCreate"><Plus :size="22" /></button>
    </header>

    <div v-if="selecting" class="address-mode-actions">
      <button type="button" class="address-mode-primary" @click="startCreate"><Plus :size="16" />新增地址</button>
      <button type="button" @click="enterManage">管理地址</button>
    </div>

    <section v-if="addresses.length" class="address-list">
      <article v-for="address in addresses" :key="address.id" class="address-card">
        <div class="address-person"><strong>{{ address.receiverName }}</strong><span>{{ address.receiverPhone }}</span><em v-if="Number(address.isDefault) === 1">默认</em></div>
        <p>{{ joinAddress(address) }}</p>
        <div class="address-actions">
          <button v-if="selecting" type="button" class="use-address" @click="useAddress(address)">使用此地址</button>
          <template v-if="!selecting || managing">
            <button v-if="Number(address.isDefault) !== 1" type="button" @click="makeDefault(address)">设为默认</button>
            <span v-else>结算时优先使用</span>
            <button type="button" @click="startEdit(address)">编辑</button>
            <button type="button" class="delete" @click="removeAddress(address)">删除</button>
          </template>
          <template v-else>
            <button type="button" @click="startEdit(address)">编辑</button>
            <button type="button" class="delete" @click="removeAddress(address)">删除</button>
          </template>
        </div>
      </article>
    </section>
    <div v-else-if="!loading" class="empty address-empty"><MapPin :size="38" /><p>还没有收货地址</p><button class="btn primary" @click="startCreate">新增地址</button></div>

    <section v-if="editorVisible" class="panel address-editor">
      <div class="editor-head"><h3>{{ form.id ? '编辑地址' : '新增地址' }}</h3><button type="button" @click="editorVisible = false"><X :size="20" /></button></div>
      <button class="paste-button" type="button" @click="showPaste = !showPaste"><ClipboardPaste :size="16" />{{ showPaste ? '收起智能识别' : '粘贴收货信息，自动识别' }}</button>
      <div v-if="showPaste" class="paste-box"><textarea v-model="pasteText" rows="3" placeholder="粘贴姓名、手机号、省市区和详细地址"></textarea><button type="button" @click="parseAddress">识别并填入</button></div>
      <div class="form-grid">
        <div class="form-item" :class="{ invalid: !!fieldErrors.receiverName }">
          <label for="address-receiver-name">收货人 <span class="required">*</span></label>
          <input id="address-receiver-name" ref="receiverNameInput" v-model="form.receiverName" class="field" :class="{ invalid: !!fieldErrors.receiverName }" aria-describedby="address-receiver-name-error" :aria-invalid="!!fieldErrors.receiverName" placeholder="姓名" @input="clearFieldError('receiverName')" />
          <p v-if="fieldErrors.receiverName" id="address-receiver-name-error" class="field-error">{{ fieldErrors.receiverName }}</p>
        </div>
        <div class="form-item" :class="{ invalid: !!fieldErrors.receiverPhone }">
          <label for="address-receiver-phone">手机号 <span class="required">*</span></label>
          <input id="address-receiver-phone" ref="receiverPhoneInput" v-model="form.receiverPhone" class="field" :class="{ invalid: !!fieldErrors.receiverPhone }" inputmode="tel" maxlength="11" aria-describedby="address-receiver-phone-error" :aria-invalid="!!fieldErrors.receiverPhone" placeholder="11位手机号" @input="handlePhoneInput" @blur="validatePhoneField" />
          <p v-if="fieldErrors.receiverPhone" id="address-receiver-phone-error" class="field-error">{{ fieldErrors.receiverPhone }}</p>
        </div>
        <div class="form-item full" :class="{ invalid: !!fieldErrors.region }">
          <label>所在地区 <span class="required">*</span></label>
          <ChinaRegionSelect v-model="region" :class="{ invalid: !!fieldErrors.region }" @change="syncRegion" />
          <p v-if="fieldErrors.region" class="field-error">{{ fieldErrors.region }}</p>
        </div>
        <div class="form-item full" :class="{ invalid: !!fieldErrors.detailAddress }">
          <label for="address-detail">详细地址 <span class="required">*</span></label>
          <textarea id="address-detail" ref="detailAddressInput" v-model="form.detailAddress" class="textarea" :class="{ invalid: !!fieldErrors.detailAddress }" aria-describedby="address-detail-error" :aria-invalid="!!fieldErrors.detailAddress" placeholder="街道、小区、楼栋、门牌号" @input="clearFieldError('detailAddress')"></textarea>
          <p v-if="fieldErrors.detailAddress" id="address-detail-error" class="field-error">{{ fieldErrors.detailAddress }}</p>
        </div>
      </div>
      <button class="default-toggle" :class="{ active: form.isDefault === 1 }" type="button" @click="form.isDefault = 1"><span><Check v-if="form.isDefault === 1" :size="15" /></span>设为默认收货地址</button>
      <button class="btn primary save-button" :disabled="saving" @click="submitAddress">{{ saving ? '保存中' : '保存地址' }}</button>
    </section>

    <div v-if="deleteTarget" class="confirm-overlay" role="presentation" @click.self="cancelDelete">
      <section class="confirm-dialog" role="alertdialog" aria-modal="true" aria-labelledby="delete-address-title">
        <div class="confirm-icon"><Trash2 :size="20" /></div>
        <h3 id="delete-address-title">删除收货地址？</h3>
        <p>确定删除“{{ deleteTarget.receiverName }} {{ deleteTarget.receiverPhone }}”吗？删除后无法恢复。</p>
        <div class="confirm-actions">
          <button type="button" class="confirm-cancel" :disabled="deletingAddress" @click="cancelDelete">取消</button>
          <button type="button" class="confirm-delete" :disabled="deletingAddress" @click="confirmDelete">{{ deletingAddress ? '删除中' : '删除' }}</button>
        </div>
      </section>
    </div>

    <div v-if="message" class="form-toast" :class="{ error: messageType === 'error' }" role="status" aria-live="polite">{{ message }}</div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Check, ClipboardPaste, MapPin, Plus, Trash2, X } from 'lucide-vue-next'
import { deleteAddress, listAddresses, saveAddress } from '@/api/shop'
import { joinAddress } from '@/utils/format'
import { parseChineseAddress } from '@/utils/addressParser'
import { isValidMainlandPhone, normalizeMainlandPhone } from '@/utils/phone'
import ChinaRegionSelect from '@/components/ChinaRegionSelect.vue'

const router = useRouter()
const route = useRoute()
const selecting = computed(() => route.query.select === '1')
const managing = ref(false)
const loading = ref(true)
const saving = ref(false)
const addresses = ref([])
const editorVisible = ref(false)
const showPaste = ref(false)
const pasteText = ref('')
const region = ref([])
const message = ref('')
const messageType = ref('success')
const messageTimer = ref(null)
const fieldErrors = ref({ receiverName: '', receiverPhone: '', region: '', detailAddress: '' })
const receiverNameInput = ref(null)
const receiverPhoneInput = ref(null)
const detailAddressInput = ref(null)
const deleteTarget = ref(null)
const deletingAddress = ref(false)
const emptyForm = () => ({ id: null, receiverName: '', receiverPhone: '', province: '', city: '', district: '', detailAddress: '', isDefault: 0 })
const form = ref(emptyForm())
const notify = (text, type = 'error') => {
  window.clearTimeout(messageTimer.value)
  message.value = text
  messageType.value = type
  messageTimer.value = window.setTimeout(() => { message.value = '' }, 1800)
}
const clearFieldError = (field) => { fieldErrors.value[field] = '' }
const handlePhoneInput = () => {
  form.value.receiverPhone = normalizeMainlandPhone(form.value.receiverPhone)
  clearFieldError('receiverPhone')
}
const validatePhoneField = () => {
  if (form.value.receiverPhone && !isValidMainlandPhone(form.value.receiverPhone)) fieldErrors.value.receiverPhone = '请输入正确的11位手机号'
}

const fetchAddresses = async () => {
  loading.value = true
  try { addresses.value = (await listAddresses()).data || [] }
  catch (e) { notify(e.message || '地址加载失败') }
  finally { loading.value = false }
}
const startCreate = () => {
  form.value = { ...emptyForm(), isDefault: addresses.value.length ? 0 : 1 }
  region.value = []
  pasteText.value = ''
  showPaste.value = false
  fieldErrors.value = { receiverName: '', receiverPhone: '', region: '', detailAddress: '' }
  editorVisible.value = true
}
const useAddress = (address) => {
  if (!selecting.value) return
  router.replace({ path: '/checkout', query: { addressId: address.id } })
}
const enterManage = () => { managing.value = true }
const startEdit = (address) => {
  form.value = { ...emptyForm(), ...address, isDefault: Number(address.isDefault || 0) }
  region.value = [address.province, address.city, address.district].filter(Boolean)
  pasteText.value = ''
  showPaste.value = false
  fieldErrors.value = { receiverName: '', receiverPhone: '', region: '', detailAddress: '' }
  editorVisible.value = true
}
const syncRegion = ([province = '', city = '', district = ''] = []) => {
  Object.assign(form.value, { province, city, district })
  clearFieldError('region')
}
const parseAddress = () => {
  const parsed = parseChineseAddress(pasteText.value)
  if (![parsed.receiverName, parsed.receiverPhone, parsed.province, parsed.city, parsed.district, parsed.detailAddress].some(Boolean)) return notify('没有识别到有效收货信息')
  Object.assign(form.value, {
    receiverName: parsed.receiverName || form.value.receiverName,
    receiverPhone: parsed.receiverPhone || form.value.receiverPhone,
    province: parsed.province || form.value.province,
    city: parsed.city || form.value.city,
    district: parsed.district || form.value.district,
    detailAddress: parsed.detailAddress || form.value.detailAddress,
  })
  region.value = [form.value.province, form.value.city, form.value.district].filter(Boolean)
}
const focusInvalidField = (field) => {
  const target = { receiverName: receiverNameInput, receiverPhone: receiverPhoneInput, detailAddress: detailAddressInput }[field]?.value
  const regionTarget = document.querySelector('.address-editor .china-region-select')
  const element = target || regionTarget
  if (!element) return
  element.scrollIntoView({ behavior: 'smooth', block: 'center' })
  if (typeof element.focus === 'function') element.focus({ preventScroll: true })
}
const validate = () => {
  const errors = { receiverName: '', receiverPhone: '', region: '', detailAddress: '' }
  if (!form.value.receiverName.trim()) errors.receiverName = '请输入收货人姓名'
  if (!isValidMainlandPhone(form.value.receiverPhone)) errors.receiverPhone = '请输入正确的11位手机号'
  if (region.value.length !== 3) errors.region = '请选择完整的省、市、区/县'
  if (!form.value.detailAddress.trim()) errors.detailAddress = '请输入详细收货地址'
  fieldErrors.value = errors
  const firstInvalid = Object.keys(errors).find((field) => errors[field])
  if (firstInvalid) {
    notify('请先完善标红的收货信息')
    nextTick(() => focusInvalidField(firstInvalid))
    return false
  }
  return true
}
const submitAddress = async () => {
  if (!validate()) return
  saving.value = true
  try {
    await saveAddress(form.value)
    editorVisible.value = false
    await fetchAddresses()
    notify('地址保存成功', 'success')
  } catch (e) { notify(e.message || '地址保存失败') }
  finally { saving.value = false }
}
const makeDefault = async (address) => {
  try { await saveAddress({ ...address, isDefault: 1 }); await fetchAddresses(); notify('默认地址已更新', 'success') }
  catch (e) { notify(e.message || '默认地址设置失败') }
}
const removeAddress = (address) => { deleteTarget.value = address }
const cancelDelete = () => {
  if (!deletingAddress.value) deleteTarget.value = null
}
const confirmDelete = async () => {
  const address = deleteTarget.value
  if (!address || deletingAddress.value) return
  deletingAddress.value = true
  try {
    await deleteAddress(address.id)
    deleteTarget.value = null
    await fetchAddresses()
    notify('地址已删除', 'success')
  } catch (e) { notify(e.message || '地址删除失败') }
  finally { deletingAddress.value = false }
}

onMounted(fetchAddresses)
onBeforeUnmount(() => window.clearTimeout(messageTimer.value))
</script>

<style scoped>
.address-page { width:min(680px,calc(100% - 28px)); }
.sub-page-head { display:grid; grid-template-columns:40px 1fr 40px; align-items:center; margin-bottom:14px; }
.sub-page-head h2 { margin:0; text-align:center; font-size:19px; }
.sub-page-head button { width:40px; height:40px; display:grid; place-items:center; padding:0; background:#fff; border:0; border-radius:50%; }
.address-list { display:grid; gap:10px; }
.address-mode-actions { display:grid; grid-template-columns:1fr 1fr; gap:10px; margin-bottom:12px; }
.address-mode-actions button { min-height:40px; display:inline-flex; align-items:center; justify-content:center; gap:6px; color:#475467; background:#fff; border:1px solid #e4e7ec; border-radius:10px; font-size:13px; font-weight:700; }
.address-mode-actions .address-mode-primary { color:var(--brand-primary); background:var(--brand-primary-soft); border-color:var(--brand-primary); }
.address-card { padding:16px; background:#fff; border-radius:15px; box-shadow:0 4px 15px rgba(31,41,55,.05); }
.address-person { display:flex; align-items:center; gap:10px; }.address-person strong{font-size:16px}.address-person span{color:var(--muted);font-size:13px}.address-person em{padding:2px 6px;color:var(--brand-primary);background:var(--brand-primary-soft);border-radius:4px;font-size:10px;font-style:normal;font-weight:700}
.address-card p { margin:11px 0 13px; color:#4b5563; font-size:13px; line-height:1.65; }
.address-actions { display:flex; align-items:center; gap:14px; padding-top:11px; border-top:1px solid #f0f1f2; }
.address-actions span { margin-right:auto; color:#9a6b19; font-size:11px; }.address-actions button{padding:0;color:#5e6671;background:none;border:0;font-size:12px}.address-actions button:first-child{margin-right:auto;color:var(--brand-primary)}.address-actions button.delete{color:#b42318;margin-right:0}.address-actions button.use-address{margin-left:auto;color:var(--brand-primary);font-weight:700}
.address-empty { min-height:230px; border:0; border-radius:15px; }.address-empty p{margin:8px 0}
.address-editor { margin-top:13px; border:0; border-radius:16px; }
.editor-head { display:flex; align-items:center; justify-content:space-between; }.editor-head h3{margin:0}.editor-head button{display:grid;place-items:center;padding:5px;background:none;border:0;color:#8a9099}
.paste-button { display:flex; align-items:center; justify-content:center; gap:8px; margin:14px 0; padding:12px 16px; color:var(--brand-primary); background:var(--brand-primary-soft); border:1px solid var(--brand-primary); border-radius:12px; font-size:14px; font-weight:600; width:100%; }
.paste-box { display:grid; gap:8px; padding:10px; margin-bottom:13px; background:#fff9f5; border:1px dashed #f0ad91; border-radius:10px; }.paste-box textarea{width:100%;padding:9px;resize:vertical;border:1px solid var(--line);border-radius:8px}.paste-box button{justify-self:end;padding:7px 11px;color:#fff;background:var(--brand-primary);border:0;border-radius:8px;font-size:12px}
.default-toggle { display:flex; align-items:center; gap:8px; margin-top:14px; padding:0; color:#5f6772; background:none; border:0; font-size:13px; }.default-toggle span{width:20px;height:20px;display:grid;place-items:center;border:1px solid #c9ced4;border-radius:50%}.default-toggle.active span{color:#fff;background:var(--brand-primary);border-color:var(--brand-primary)}
.save-button { width:100%; margin-top:16px; }
.form-toast { position:fixed; top:calc(18px + env(safe-area-inset-top)); left:50%; z-index:1200; max-width:min(88vw,420px); padding:11px 16px; color:#fff; background:rgba(8,114,79,.96); border-radius:10px; box-shadow:0 8px 24px rgba(15,23,42,.18); transform:translateX(-50%); font-size:13px; text-align:center; pointer-events:none; }
.form-toast.error { background:rgba(180,35,24,.96); }
.required { color:#d92d20; }
.field-error { margin:6px 0 0; color:#b42318; font-size:12px; line-height:1.45; }
.form-item.invalid label { color:#b42318; }
.field.invalid { border-color:#d92d20; box-shadow:0 0 0 2px rgba(217,45,32,.08); }
.address-editor :deep(.china-region-select.invalid .field) { border-color:#d92d20; box-shadow:0 0 0 2px rgba(217,45,32,.08); }
.confirm-overlay { position:fixed; inset:0; z-index:30; display:grid; place-items:center; padding:24px; background:rgba(15,23,42,.48); }
.confirm-dialog { width:min(360px,100%); padding:24px 20px 18px; background:#fff; border-radius:18px; box-shadow:0 18px 50px rgba(15,23,42,.2); text-align:center; }
.confirm-icon { display:grid; width:44px; height:44px; margin:0 auto 12px; place-items:center; color:#b42318; background:#fff1f0; border-radius:50%; }
.confirm-dialog h3 { margin:0; color:#17202e; font-size:18px; }
.confirm-dialog p { margin:10px 0 20px; color:#667085; font-size:13px; line-height:1.7; word-break:break-all; }
.confirm-actions { display:grid; grid-template-columns:1fr 1fr; gap:10px; }
.confirm-actions button { min-height:42px; border-radius:10px; font-size:14px; font-weight:700; }
.confirm-cancel { color:#475467; background:#fff; border:1px solid #d0d5dd; }.confirm-delete { color:#fff; background:#d92d20; border:1px solid #d92d20; }
.confirm-actions button:disabled { cursor:not-allowed; opacity:.55; }
@media (max-width:560px){.address-page{padding-top:10px}.address-editor{padding:16px 14px}.address-editor .form-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.address-editor .form-grid .full{grid-column:1/-1}.address-editor :deep(.china-region-select){grid-template-columns:repeat(3,minmax(0,1fr));gap:5px}.address-editor :deep(.china-region-select .field){padding:0 5px;font-size:12px}}
</style>
