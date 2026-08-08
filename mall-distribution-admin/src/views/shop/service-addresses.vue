<template>
  <div class="page-container service-address-page">
    <div class="page-heading">
      <div>
        <h2>发货与退货地址</h2>
        <p>统一维护仓库地址；商品可选择发货地和售后退货地，审核通过后客户会看到对应退货地址。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openForm()">新增地址</el-button>
    </div>

    <el-alert title="地址修改不会改写已生成的订单和售后单；售后单会保存提交时的退货地址快照。" type="info" :closable="false" show-icon class="address-tip" />

    <el-row :gutter="16">
      <el-col v-for="group in groups" :key="group.type" :span="12">
        <el-card shadow="never" class="address-card">
          <template #header><div class="card-head"><span>{{ group.title }}</span><el-tag size="small" effect="plain">{{ group.items.length }} 个</el-tag></div></template>
          <el-empty v-if="!group.items.length" description="还没有地址" :image-size="70" />
          <div v-for="item in group.items" :key="item.id" class="address-item">
            <div class="address-item-head"><strong>{{ item.addressLabel || '未命名地址' }}</strong><el-tag v-if="item.isDefault === 1" type="success" size="small">默认</el-tag></div>
            <div class="address-contact">{{ item.contactName }} {{ item.contactPhone }}</div>
            <div class="address-text">{{ fullAddress(item) }}</div>
            <div class="address-actions">
              <el-button link type="primary" @click="openForm(item)">编辑</el-button>
              <el-button v-if="item.isDefault !== 1" link type="success" @click="setDefault(item)">设为默认</el-button>
              <el-button link type="danger" @click="disableAddress(item)">停用</el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑地址' : '新增地址'" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="94px">
        <el-form-item label="地址用途" prop="addressType"><el-radio-group v-model="form.addressType"><el-radio :value="1">发货地址</el-radio><el-radio :value="2">退货地址</el-radio></el-radio-group></el-form-item>
        <el-form-item label="地址名称"><el-input v-model="form.addressLabel" maxlength="64" placeholder="如：长沙仓、售后专用地址" /></el-form-item>
        <el-row :gutter="16"><el-col :span="12"><el-form-item label="联系人" prop="contactName"><el-input v-model="form.contactName" maxlength="64" /></el-form-item></el-col><el-col :span="12"><el-form-item label="联系电话" prop="contactPhone"><el-input v-model="form.contactPhone" maxlength="32" /></el-form-item></el-col></el-row>
        <el-form-item label="所在地区" prop="region"><el-cascader v-model="region" :options="pcaTextArr" filterable clearable style="width:100%" placeholder="请选择省 / 市 / 区县" /></el-form-item>
        <el-form-item label="详细地址" prop="detailAddress"><el-input v-model="form.detailAddress" maxlength="255" placeholder="街道、小区、楼栋、门牌号" /></el-form-item>
        <el-form-item label="设为默认"><el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" active-text="是" inactive-text="否" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存地址</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { pcaTextArr } from 'element-china-area-data'
import { listShopServiceAddresses, saveShopServiceAddress, updateShopServiceAddressStatus } from '@/api/shop'

const rows = ref([])
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const region = ref([])
const form = reactive(emptyForm())
const groups = computed(() => [
  { type: 1, title: '发货地址', items: rows.value.filter((item) => Number(item.addressType) === 1) },
  { type: 2, title: '退货地址', items: rows.value.filter((item) => Number(item.addressType) === 2) },
])
const rules = {
  addressType: [{ required: true, message: '请选择地址用途', trigger: 'change' }],
  contactName: [{ required: true, message: '请填写联系人', trigger: 'blur' }],
  contactPhone: [{ required: true, message: '请填写联系电话', trigger: 'blur' }],
  region: [{ validator: (_rule, value, callback) => value?.length === 3 ? callback() : callback(new Error('请选择完整省、市、区/县')), trigger: 'change' }],
  detailAddress: [{ required: true, message: '请填写详细地址', trigger: 'blur' }],
}
function emptyForm() { return { id: null, tenantId: 1, addressType: 1, addressLabel: '', contactName: '', contactPhone: '', province: '', city: '', district: '', detailAddress: '', isDefault: 0 } }
function fullAddress(item) { return [item.province, item.city, item.district, item.detailAddress].filter(Boolean).join(' ') }
async function fetchData() { rows.value = (await listShopServiceAddresses({ tenantId: 1 })).data || [] }
function openForm(item = null) {
  Object.assign(form, emptyForm(), item || {})
  region.value = item ? [item.province, item.city, item.district].filter(Boolean) : []
  dialogVisible.value = true
}
async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = { ...form, province: region.value[0], city: region.value[1], district: region.value[2] }
    await saveShopServiceAddress(payload)
    ElMessage.success('地址已保存')
    dialogVisible.value = false
    await fetchData()
  } finally { saving.value = false }
}
async function setDefault(item) { await updateShopServiceAddressStatus(item.id, 1); ElMessage.success('默认地址已更新'); await fetchData() }
async function disableAddress(item) {
  await ElMessageBox.confirm(`确定停用“${item.addressLabel || fullAddress(item)}”吗？停用后新商品不能选择，历史记录不受影响。`, '停用地址', { type: 'warning' })
  await updateShopServiceAddressStatus(item.id, 0)
  ElMessage.success('地址已停用')
  await fetchData()
}
onMounted(fetchData)
</script>

<style scoped>
.page-heading{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:16px;padding:4px 2px 0}.page-heading h2{margin:0;color:#303133;font-size:22px}.page-heading p{margin:6px 0 0;color:#909399;font-size:13px}.address-tip{margin-bottom:16px}.address-card{min-height:350px;border-color:#e4e7ed}.card-head{display:flex;align-items:center;justify-content:space-between;font-weight:700}.address-item{padding:14px 0;border-bottom:1px solid #ebeef5}.address-item:last-child{border-bottom:0}.address-item-head{display:flex;align-items:center;gap:8px}.address-contact{margin-top:7px;color:#606266;font-size:13px}.address-text{margin-top:5px;color:#303133;line-height:1.6}.address-actions{margin-top:8px;display:flex;gap:8px}
</style>
