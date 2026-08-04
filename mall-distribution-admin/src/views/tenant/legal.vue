<template>
  <div class="page-container legal-settings-page">
    <div class="toolbar">
      <div>
        <h2>协议与规则</h2>
        <p>注册页会展示用户服务协议和隐私政策；交易与售后规则会在注册提示、商品详情和确认订单环节提供查看入口。</p>
      </div>
      <el-tag type="warning" effect="plain">前台公开内容</el-tag>
    </div>

    <el-alert
      title="注册页的链接只是入口，正文内容仍来自这里的配置。留空时前台会提示“暂未配置”，正式上线前请按实际经营范围和售后政策完成审核。"
      type="warning"
      :closable="false"
      show-icon
      class="page-alert"
    />

    <el-card v-loading="loading" shadow="never" class="legal-card">
      <el-empty v-if="!tenantForm.id && !loading" description="暂未找到商城资料" />
      <el-form v-else :model="tenantForm" label-width="150px" class="legal-form">
        <section class="form-section">
          <h3>注册必读</h3>
          <el-form-item label="用户服务协议">
            <el-input v-model="tenantForm.userAgreement" type="textarea" :rows="10" maxlength="30000" show-word-limit placeholder="请输入经运营主体确认的完整用户服务协议" />
            <div class="field-help">注册时用户需要勾选同意，建议包含账号、下单、支付、售后、客服和争议处理说明。</div>
          </el-form-item>
          <el-form-item label="隐私政策">
            <el-input v-model="tenantForm.privacyPolicy" type="textarea" :rows="10" maxlength="30000" show-word-limit placeholder="请输入完整隐私政策" />
            <div class="field-help">注册时用户可以打开查看，建议说明收集的信息、使用目的、保存期限、第三方服务和联系方式。</div>
          </el-form-item>
        </section>

        <section class="form-section">
          <h3>交易与售后</h3>
          <el-form-item label="交易与售后规则">
            <el-input v-model="tenantForm.afterSalePolicy" type="textarea" :rows="12" maxlength="30000" show-word-limit placeholder="请输入完整交易、售后、退款、运费及不适用七天无理由的规则" />
            <div class="field-help">注册页只做查看提示；商城会在商品详情和确认订单页继续提供入口，避免用户下单后找不到规则。</div>
          </el-form-item>
        </section>
      </el-form>
      <template #footer>
        <div class="card-footer"><el-button type="primary" :loading="saving" :disabled="!tenantForm.id" @click="submit">保存协议与规则</el-button></div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listTenants, saveTenant } from '@/api/tenant'

const loading = ref(false)
const saving = ref(false)
const tenantForm = ref({})

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listTenants({ pageNum: 1, pageSize: 100 })
    const rows = res.data?.list || []
    tenantForm.value = { ...(rows.find((row) => Number(row.id) === 1) || rows[0] || {}) }
  } finally {
    loading.value = false
  }
}

const submit = async () => {
  saving.value = true
  try {
    await saveTenant({
      ...tenantForm.value,
      userAgreement: tenantForm.value.userAgreement?.trim() || null,
      privacyPolicy: tenantForm.value.privacyPolicy?.trim() || null,
      afterSalePolicy: tenantForm.value.afterSalePolicy?.trim() || null,
    })
    ElMessage.success('协议与规则已保存，前台刷新后生效')
    await fetchData()
  } finally {
    saving.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.toolbar { display:flex; align-items:center; justify-content:space-between; gap:20px; margin-bottom:16px; }
.toolbar h2 { margin:0; color:#303133; font-size:20px; }
.toolbar p { max-width:900px; margin:6px 0 0; color:#909399; font-size:13px; line-height:20px; }
.page-alert { margin-bottom:16px; }
.legal-card { border:1px solid #ebeef5; }
.form-section { padding:4px 0 8px; }
.form-section + .form-section { margin-top:14px; padding-top:20px; border-top:1px solid #ebeef5; }
.form-section h3 { margin:0 0 18px; padding-left:10px; color:#303133; font-size:16px; line-height:1.4; border-left:4px solid var(--el-color-primary); }
.field-help { width:100%; margin-top:6px; color:#909399; font-size:12px; line-height:20px; }
.card-footer { display:flex; justify-content:flex-end; }
@media (max-width:700px) { .toolbar{align-items:flex-start;flex-direction:column;gap:10px}.legal-form{padding-right:0} }
</style>
