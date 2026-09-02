<template>
  <div class="page-container merchant-profile-page">
    <div class="page-heading">
      <div>
        <h2>经营与结算资料</h2>
        <p>请由经营主体自行填写。平台认证通过前，不能提交商品上架审核。</p>
      </div>
      <el-tag :type="auditState.type" effect="light" size="large">{{ auditState.label }}</el-tag>
    </div>

    <el-alert :title="auditState.title" :description="auditState.description" :type="auditState.type" :closable="false" show-icon class="status-alert" />

    <el-card shadow="never" class="profile-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="126px" :disabled="loading">
        <el-divider content-position="left">经营主体</el-divider>
        <el-form-item label="商户名称"><el-input :model-value="merchant.merchantName" disabled /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactName" maxlength="64" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" maxlength="32" /></el-form-item>
        <el-form-item label="经营主体" prop="legalEntityName"><el-input v-model="form.legalEntityName" maxlength="128" placeholder="营业执照上的企业名称" /></el-form-item>
        <el-form-item label="统一信用代码" prop="unifiedSocialCreditCode"><el-input v-model="form.unifiedSocialCreditCode" maxlength="18" placeholder="18位统一社会信用代码" /></el-form-item>

        <el-divider content-position="left">收款与开票资料</el-divider>
        <el-form-item label="收款户名" prop="bankAccountName"><el-input v-model="form.bankAccountName" maxlength="128" /></el-form-item>
        <el-form-item label="开户银行" prop="bankName"><el-input v-model="form.bankName" maxlength="128" placeholder="请填写银行及支行名称" /></el-form-item>
        <el-form-item label="银行账号" prop="bankAccountNo"><el-input v-model="form.bankAccountNo" maxlength="64" autocomplete="off" /></el-form-item>
        <el-form-item label="发票抬头" prop="invoiceTitle"><el-input v-model="form.invoiceTitle" maxlength="128" /></el-form-item>
        <el-form-item label="纳税人识别号" prop="taxpayerIdentificationNo"><el-input v-model="form.taxpayerIdentificationNo" maxlength="18" placeholder="18位纳税人识别号" /></el-form-item>
      </el-form>
      <div class="submit-row">
        <span>提交后平台会核验资料；已通过认证的商户再次提交，也需要重新认证。</span>
        <el-button type="primary" :loading="saving" @click="submit">提交平台认证</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getCurrentMerchantProfile, submitCurrentMerchantProfile } from '@/api/merchant'

const loading = ref(false)
const saving = ref(false)
const formRef = ref()
const merchant = ref({})
const form = reactive({ contactName: '', contactPhone: '', legalEntityName: '', unifiedSocialCreditCode: '', bankAccountName: '', bankName: '', bankAccountNo: '', invoiceTitle: '', taxpayerIdentificationNo: '' })
const creditCodePattern = /^[0-9A-HJ-NPQRTUWXY]{18}$/
const rules = {
  legalEntityName: [{ required: true, message: '请填写经营主体', trigger: 'blur' }],
  unifiedSocialCreditCode: [{ required: true, message: '请填写统一信用代码', trigger: 'blur' }, { validator: (_, value, done) => creditCodePattern.test((value || '').toUpperCase()) ? done() : done(new Error('请填写18位统一社会信用代码')), trigger: 'blur' }],
  bankAccountName: [{ required: true, message: '请填写收款户名', trigger: 'blur' }],
  bankName: [{ required: true, message: '请填写开户银行', trigger: 'blur' }],
  bankAccountNo: [{ required: true, message: '请填写银行账号', trigger: 'blur' }],
  invoiceTitle: [{ required: true, message: '请填写发票抬头', trigger: 'blur' }],
  taxpayerIdentificationNo: [{ required: true, message: '请填写纳税人识别号', trigger: 'blur' }, { validator: (_, value, done) => creditCodePattern.test((value || '').toUpperCase()) ? done() : done(new Error('请填写18位纳税人识别号')), trigger: 'blur' }],
}
const auditState = computed(() => ({
  APPROVED: { label: '已认证', type: 'success', title: '平台认证已通过', description: '现在可以提交商品上架审核；商品审核通过后才会正式上架。' },
  REJECTED: { label: '需补充', type: 'error', title: '平台认证未通过', description: '请按平台反馈核对资料后重新提交。' },
  PENDING: { label: '待认证', type: 'warning', title: '资料等待平台认证', description: '此期间不能提交商品上架审核或进行新销售。' },
}[merchant.value.auditStatus] || { label: '待提交', type: 'info', title: '请提交入驻资料', description: '请完整填写经营主体、收款与开票资料后提交平台认证。' }))

const assignMerchant = (value) => {
  merchant.value = value || {}
  Object.assign(form, {
    contactName: merchant.value.contactName || '', contactPhone: merchant.value.contactPhone || '',
    legalEntityName: merchant.value.legalEntityName || '', unifiedSocialCreditCode: merchant.value.unifiedSocialCreditCode || '',
    bankAccountName: merchant.value.bankAccountName || '', bankName: merchant.value.bankName || '', bankAccountNo: merchant.value.bankAccountNo || '',
    invoiceTitle: merchant.value.invoiceTitle || '', taxpayerIdentificationNo: merchant.value.taxpayerIdentificationNo || '',
  })
}
const load = async () => {
  loading.value = true
  try { assignMerchant((await getCurrentMerchantProfile()).data) } finally { loading.value = false }
}
const submit = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    const result = await submitCurrentMerchantProfile({ ...form, unifiedSocialCreditCode: form.unifiedSocialCreditCode.toUpperCase(), taxpayerIdentificationNo: form.taxpayerIdentificationNo.toUpperCase() })
    assignMerchant(result.data)
    ElMessage.success('资料已提交，请等待平台认证')
  } finally { saving.value = false }
}
onMounted(load)
</script>

<style scoped>
.merchant-profile-page{max-width:1060px;margin:0 auto}.page-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin-bottom:18px}.page-heading h2{margin:0;color:#1f2937}.page-heading p{margin:7px 0 0;color:#667085}.status-alert{margin-bottom:18px}.profile-card{border-radius:14px}.submit-row{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-top:26px;padding-top:18px;border-top:1px solid #edf1f6;color:#667085;font-size:13px;line-height:1.6}@media(max-width:640px){.page-heading,.submit-row{align-items:flex-start;flex-direction:column}.submit-row .el-button{width:100%}}
</style>
