<template>
  <div class="page-container tenant-profile-page">
    <div class="toolbar">
      <div>
        <h2>商城资料与客服</h2>
        <p>维护前台公开展示的经营主体、客服渠道、经营地址和备案资料。</p>
      </div>
      <el-tag type="info" effect="plain">前台公开信息</el-tag>
    </div>

    <el-alert
      title="这些资料会展示在商城的经营资质和联系客服页面，请按营业执照及实际客服渠道填写。客服电话支持座机或手机号。"
      type="info"
      :closable="false"
      show-icon
      class="page-alert"
    />

    <el-card v-if="tenantForm.id" v-loading="readinessLoading" shadow="never" class="readiness-card">
      <div class="readiness-head">
        <div>
          <h3>客户交付预检</h3>
          <p>这里检查的是交给客户正式运营前的必备资料与通道；当前作为测试基座时允许未全部通过。</p>
        </div>
        <el-tag :type="readiness.ready ? 'success' : 'warning'" effect="dark">
          {{ readiness.ready ? '可以进入交付验收' : `待完成 ${Math.max(0, readiness.totalRequired - readiness.passedRequired)} 项` }}
        </el-tag>
      </div>
      <el-progress
        :percentage="readinessPercent"
        :status="readiness.ready ? 'success' : undefined"
        :stroke-width="10"
      />
      <div class="readiness-grid">
        <div v-for="item in readiness.items || []" :key="item.code" class="readiness-item" :class="{ passed: item.passed }">
          <span class="readiness-state">{{ item.passed ? '✓' : '!' }}</span>
          <div>
            <div class="readiness-title">
              <strong>{{ item.title }}</strong>
              <el-tag size="small" :type="item.required ? 'danger' : 'info'" effect="plain">{{ item.required ? '交付必备' : '客户可选' }}</el-tag>
            </div>
            <p>{{ item.detail }}</p>
            <RouterLink v-if="!item.passed && item.actionPath" :to="item.actionPath">去处理</RouterLink>
          </div>
        </div>
      </div>
    </el-card>

    <el-card v-loading="loading" shadow="never" class="profile-card">
      <el-empty v-if="!tenantForm.id && !loading" description="暂未找到商城资料" />
      <el-form v-else :model="tenantForm" label-width="130px" class="profile-form">
        <section class="form-section">
          <h3>经营主体</h3>
          <el-form-item label="经营主体名称" required>
            <el-input v-model="tenantForm.tenantName" maxlength="128" placeholder="营业执照或实际运营主体名称" />
            <div class="field-help">用于前台经营资质、用户协议和客服页面展示。</div>
          </el-form-item>
          <el-form-item label="统一社会信用代码">
            <el-input
              v-model="tenantForm.unifiedSocialCreditCode"
              maxlength="18"
              placeholder="营业执照上的18位统一社会信用代码"
              @input="normalizeCreditCode"
            />
            <div class="field-help">用于经营资质、用户服务协议和隐私政策中的主体识别。</div>
          </el-form-item>
          <el-form-item label="经营地址">
            <el-input v-model="tenantForm.companyAddress" maxlength="255" placeholder="营业执照登记地址或实际经营地址" />
          </el-form-item>
        </section>

        <section class="form-section">
          <h3>客服渠道</h3>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="客服电话"><el-input v-model="tenantForm.servicePhone" maxlength="32" placeholder="手机号或座机，例如 400-xxx-xxxx" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="客服邮箱"><el-input v-model="tenantForm.serviceEmail" maxlength="128" placeholder="用于售后咨询和隐私问题联系" /></el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="客服工作时间">
            <el-input v-model="tenantForm.serviceHours" maxlength="128" placeholder="例如：周一至周日 9:00-21:00" />
          </el-form-item>
        </section>

        <section class="form-section">
          <h3>协议与隐私资料</h3>
          <el-form-item label="第三方服务清单">
            <el-input
              v-model="tenantForm.thirdPartyServices"
              type="textarea"
              :rows="4"
              maxlength="2000"
              show-word-limit
              placeholder="例如：支付宝（支付服务）；阿里云（短信及云服务）；订单实际承运的物流公司（商品配送）"
            />
            <div class="field-help">用于隐私政策说明支付、短信、云服务和物流等必要合作方；客户实际使用的服务发生变化时请同步更新。</div>
          </el-form-item>
        </section>

        <section class="form-section">
          <h3>资质与备案</h3>
          <el-form-item label="营业执照">
            <div class="upload-row">
              <el-upload action="#" :show-file-list="false" accept="image/*" :http-request="uploadBusinessLicense">
                <div class="license-uploader">
                  <el-image v-if="tenantForm.businessLicenseUrl" :src="tenantForm.businessLicenseUrl" fit="contain" />
                  <div v-else class="upload-placeholder">点击上传<br />营业执照</div>
                </div>
              </el-upload>
              <div class="upload-actions">
                <span class="upload-help">单张不超过 5MB。请遮挡无需公开的个人信息。</span>
                <el-switch v-model="tenantForm.showBusinessLicense" :active-value="1" :inactive-value="0" active-text="展示执照图片" inactive-text="隐藏执照图片" class="license-switch" />
                <span class="upload-help">首页“经营资质”入口及主体文字信息固定公开；此开关只控制是否展示营业执照图片。</span>
              </div>
            </div>
          </el-form-item>
          <el-row :gutter="20">
            <el-col :span="12"><el-form-item label="ICP备案号"><el-input v-model="tenantForm.icpNumber" maxlength="128" placeholder="例如：粤ICP备XXXXXXXX号" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="公安备案号"><el-input v-model="tenantForm.policeRecordNumber" maxlength="128" placeholder="完成公安备案后填写" /></el-form-item></el-col>
          </el-row>
          <el-form-item label="公安备案链接"><el-input v-model="tenantForm.policeRecordUrl" maxlength="512" placeholder="仅填写 https:// 开头的安全链接" /></el-form-item>
        </section>

        <section class="form-section">
          <h3>后台备注</h3>
          <el-form-item label="备注"><el-input v-model="tenantForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="仅后台可见的设置说明" /></el-form-item>
        </section>
      </el-form>
      <template #footer>
        <div class="card-footer"><el-button type="primary" :loading="saving" :disabled="!tenantForm.id" @click="submit">保存商城资料</el-button></div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadShopImage } from '@/api/shop'
import { getCustomerDeliveryReadiness, listTenants, saveTenant } from '@/api/tenant'
import { useUnsavedChanges } from '@/composables/useUnsavedChanges'

const loading = ref(false)
const saving = ref(false)
const tenantForm = ref({})
const readinessLoading = ref(false)
const readiness = ref({ ready: false, passedRequired: 0, totalRequired: 0, items: [] })
const savedSnapshot = ref('')
const hasUnsavedChanges = computed(() => Boolean(tenantForm.value.id)
  && JSON.stringify(tenantForm.value) !== savedSnapshot.value)
useUnsavedChanges(hasUnsavedChanges, '商城主体、客服或资质资料尚未保存，确定离开吗？')
const readinessPercent = computed(() => readiness.value.totalRequired
  ? Math.round(readiness.value.passedRequired * 100 / readiness.value.totalRequired)
  : 0)

const fetchReadiness = async (tenantId) => {
  if (!tenantId) return
  readinessLoading.value = true
  try {
    readiness.value = (await getCustomerDeliveryReadiness(tenantId)).data || readiness.value
  } finally {
    readinessLoading.value = false
  }
}

const normalizeCreditCode = (value) => {
  tenantForm.value.unifiedSocialCreditCode = String(value || '')
    .toUpperCase()
    .replace(/[^0-9A-HJ-NPQRTUWXY]/g, '')
    .slice(0, 18)
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listTenants({ pageNum: 1, pageSize: 100 })
    const rows = res.data?.list || []
    const current = rows.find((row) => Number(row.id) === 1) || rows[0] || {}
    tenantForm.value = {
      ...current,
      showBusinessLicense: Number(current.showBusinessLicense ?? 1) === 0 ? 0 : 1,
    }
    savedSnapshot.value = JSON.stringify(tenantForm.value)
    await fetchReadiness(current.id)
  } finally {
    loading.value = false
  }
}

const uploadBusinessLicense = async ({ file }) => {
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('营业执照图片不能超过 5MB')
    return
  }
  const res = await uploadShopImage(file)
  tenantForm.value.businessLicenseUrl = res.data
  ElMessage.success('营业执照上传成功')
}

const submit = async () => {
  if (!tenantForm.value.tenantName?.trim()) {
    ElMessage.warning('请输入经营主体名称')
    return
  }
  const creditCode = tenantForm.value.unifiedSocialCreditCode?.trim()
  if (creditCode && !/^[0-9A-HJ-NPQRTUWXY]{18}$/.test(creditCode)) {
    ElMessage.warning('统一社会信用代码应为18位，请核对后保存')
    return
  }
  saving.value = true
  try {
    await saveTenant({
      ...tenantForm.value,
      tenantName: tenantForm.value.tenantName.trim(),
      companyAddress: tenantForm.value.companyAddress?.trim() || null,
      unifiedSocialCreditCode: creditCode || null,
      servicePhone: tenantForm.value.servicePhone?.trim() || null,
      serviceEmail: tenantForm.value.serviceEmail?.trim() || null,
      serviceHours: tenantForm.value.serviceHours?.trim() || null,
      thirdPartyServices: tenantForm.value.thirdPartyServices?.trim() || null,
      policeRecordUrl: tenantForm.value.policeRecordUrl?.trim() || null,
      showBusinessLicense: tenantForm.value.showBusinessLicense ?? 1,
    })
    ElMessage.success('商城资料已保存，前台刷新后生效')
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
.toolbar p { margin:6px 0 0; color:#909399; font-size:13px; }
.page-alert { margin-bottom:16px; }
.readiness-card { margin-bottom:16px; border:1px solid #ebeef5; }
.readiness-head { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; margin-bottom:14px; }
.readiness-head h3 { margin:0; color:#303133; font-size:17px; }
.readiness-head p { margin:6px 0 0; color:#909399; font-size:12px; line-height:1.6; }
.readiness-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px; margin-top:16px; }
.readiness-item { display:flex; align-items:flex-start; gap:10px; padding:12px; background:#fff8ed; border:1px solid #f7d9a8; border-radius:9px; }
.readiness-item.passed { background:#f1f9f4; border-color:#c9ead5; }
.readiness-state { width:22px; height:22px; display:grid; place-items:center; flex:0 0 22px; color:#fff; background:#e6a23c; border-radius:50%; font-weight:700; }
.readiness-item.passed .readiness-state { background:#67c23a; }
.readiness-title { display:flex; flex-wrap:wrap; align-items:center; gap:7px; }
.readiness-title strong { color:#303133; font-size:13px; }
.readiness-item p { margin:5px 0 0; color:#7a828c; font-size:12px; line-height:1.55; }
.readiness-item a { display:inline-block; margin-top:5px; color:var(--el-color-primary); font-size:12px; text-decoration:none; }
.profile-card { border:1px solid #ebeef5; }
.form-section { padding:4px 0 8px; }
.form-section + .form-section { margin-top:14px; padding-top:20px; border-top:1px solid #ebeef5; }
.form-section h3 { margin:0 0 18px; padding-left:10px; color:#303133; font-size:16px; line-height:1.4; border-left:4px solid var(--el-color-primary); }
.field-help { width:100%; margin-top:6px; color:#909399; font-size:12px; line-height:20px; }
.upload-row { display:flex; align-items:flex-start; gap:16px; }
.license-uploader { display:grid; width:220px; height:138px; place-items:center; overflow:hidden; background:#fafafa; border:1px dashed #c0ccda; border-radius:8px; cursor:pointer; }
.license-uploader :deep(.el-image) { width:100%; height:100%; }
.upload-placeholder { color:#909399; font-size:13px; line-height:22px; text-align:center; }
.upload-actions { display:flex; flex-direction:column; gap:8px; }
.upload-help { color:#909399; font-size:12px; line-height:20px; }
.license-switch { margin-top:4px; }
.card-footer { display:flex; justify-content:flex-end; }
@media (max-width:700px) { .toolbar,.readiness-head{align-items:flex-start;flex-direction:column;gap:10px}.readiness-grid{grid-template-columns:1fr}.upload-row{flex-direction:column}.license-uploader{width:100%}.profile-form{padding-right:0} }
</style>
