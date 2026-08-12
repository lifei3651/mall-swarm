<template>
  <div class="page-container legal-settings-page">
    <div class="toolbar">
      <div>
        <h2>协议与规则</h2>
        <p>注册页展示用户服务协议和隐私政策；交易与售后规则会在注册、商品详情和确认订单环节提供查看入口。</p>
      </div>
      <el-tag type="warning" effect="plain">前台公开内容</el-tag>
    </div>

    <el-alert
      title="系统已生成三份基础内容，并自动引用商城资料中的经营主体、信用代码、客服和第三方服务信息。客户已自行修改的正文不会被覆盖。"
      type="info"
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
            <div class="textarea-toolbar">
              <el-button type="primary" link @click="restoreTemplate('userAgreement', '用户服务协议')">恢复平台默认内容</el-button>
              <span class="toolbar-hint">主体资料变更后，前台会自动显示最新资料，无需重写正文</span>
            </div>
            <el-input v-model="tenantForm.userAgreement" type="textarea" :rows="10" maxlength="30000" show-word-limit placeholder="请输入经经营主体确认的完整用户服务协议" />
            <div class="field-help">包含账号、下单、支付、售后、客服、会员推广边界和争议处理说明。</div>
          </el-form-item>
          <el-form-item label="隐私政策">
            <div class="textarea-toolbar">
              <el-button type="primary" link @click="restoreTemplate('privacyPolicy', '隐私政策')">恢复平台默认内容</el-button>
              <span class="toolbar-hint">第三方服务清单从“商城资料与客服”自动带入</span>
            </div>
            <el-input v-model="tenantForm.privacyPolicy" type="textarea" :rows="10" maxlength="30000" show-word-limit placeholder="请输入完整隐私政策" />
            <div class="field-help">说明处理的信息、用途、保存期限、安全措施、第三方服务、用户权利和联系方式。</div>
          </el-form-item>
        </section>

        <section class="form-section">
          <h3>交易与售后</h3>
          <el-form-item label="售后期限起算">
            <el-radio-group v-model="tenantForm.afterSaleWindowMode" class="window-mode-group">
              <el-radio value="RECEIVED">
                <span class="window-mode-title">签收后起算（推荐）</span>
                <span class="window-mode-help">未确认收货前不会因时间经过而关闭入口；确认收货后开始计算。</span>
              </el-radio>
              <el-radio value="ORDER_CREATED">
                <span class="window-mode-title">下单后起算（兼容模式）</span>
                <span class="window-mode-help">从订单创建时间开始计算，仅用于客户明确采用原业务口径的场景。</span>
              </el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="售后入口有效期">
            <el-input-number v-model="tenantForm.afterSaleWindowDays" :min="7" :max="365" :step="1" />
            <span class="days-suffix">天</span>
            <div class="field-help">当前规则：{{ afterSaleWindowSummary }}。该设置控制客户自助申请入口，不排除法定或商家承诺的其他售后权利。</div>
          </el-form-item>
          <el-form-item label="交易与售后规则">
            <div class="textarea-toolbar">
              <el-button type="primary" link @click="restoreTemplate('afterSalePolicy', '交易与售后规则')">恢复平台默认内容</el-button>
              <span class="toolbar-hint">客服电话、邮箱、时间和经营地址会自动显示最新资料</span>
            </div>
            <el-input v-model="tenantForm.afterSalePolicy" type="textarea" :rows="12" maxlength="30000" show-word-limit placeholder="请输入完整交易、售后、退款、运费及法定退货规则" />
            <div class="field-help">页面售后入口期限不会替代消费者依法享有的售后权利。</div>
          </el-form-item>
        </section>

        <section class="form-section">
          <h3>常见问题（FAQ）</h3>
          <el-alert title="以下问答用于前台常见问题展示。会员等级、奖金比例和团队模型以每个客户最终确认并发布的规则为准。" type="info" :closable="false" show-icon class="faq-alert" />
          <div v-for="(faq, index) in faqList" :key="index" class="faq-item">
            <div class="faq-header">
              <span class="faq-index">Q{{ index + 1 }}</span>
              <el-button type="danger" link @click="faqList.splice(index, 1)">删除</el-button>
            </div>
            <el-input v-model="faq.question" placeholder="问题" maxlength="200" class="faq-question" />
            <el-input v-model="faq.answer" type="textarea" :rows="2" placeholder="答案" maxlength="1000" />
          </div>
          <el-button type="primary" plain class="faq-button" @click="faqList.push({ question: '', answer: '' })">添加问答</el-button>
          <el-button type="success" plain class="faq-button" @click="fillDefaultFaqs">填充默认问答</el-button>
        </section>
      </el-form>
      <template #footer>
        <div class="card-footer"><el-button type="primary" :loading="saving" :disabled="!tenantForm.id" @click="submit">保存协议与规则</el-button></div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getLegalTemplates, listTenants, saveTenant } from '@/api/tenant'

const loading = ref(false)
const saving = ref(false)
const tenantForm = ref({})
const faqList = ref([])
const defaultTemplates = ref({})
const afterSaleWindowSummary = computed(() => {
  const prefix = tenantForm.value.afterSaleWindowMode === 'ORDER_CREATED' ? '下单后' : '签收后'
  return `${prefix}${Number(tenantForm.value.afterSaleWindowDays || 7)}天`
})

const restoreTemplate = async (field, label) => {
  const template = defaultTemplates.value[field]
  if (!template) {
    ElMessage.warning('默认内容暂未加载，请刷新页面后重试')
    return
  }
  if (tenantForm.value[field]?.trim()) {
    try {
      await ElMessageBox.confirm(`恢复平台默认内容会覆盖当前${label}，是否继续？`, '确认恢复', { type: 'warning' })
    } catch {
      return
    }
  }
  tenantForm.value[field] = template
  ElMessage.success(`已恢复平台默认${label}`)
}

const defaultFaqData = [
  { question: '如何注册账号？', answer: '进入“我的”页面后选择注册，按提示填写邀请码、手机号、登录账号和短信验证码即可。' },
  { question: '如何下单购买商品？', answer: '浏览商品并加入购物车，确认收货地址、商品数量、运费和支付方式后提交订单并完成支付。' },
  { question: '支持哪些支付方式？', answer: '支付方式以订单确认页面实际展示为准；已完成配置的支付通道会自动显示。' },
  { question: '订单多久发货？', answer: '发货时间以商品详情、订单页面及商家实际处理进度为准，发货后可在订单详情查看物流信息。' },
  { question: '如何申请售后？', answer: '在订单详情中选择需要处理的商品和数量，填写原因后提交申请。页面入口关闭后，如仍在法定或商家承诺期限内，可联系客服处理。' },
  { question: '退款多久到账？', answer: '退款审核完成后按原支付方式处理，具体到账时间由支付渠道决定；余额支付部分会退回商城余额。' },
  { question: '会员和奖金规则是什么？', answer: '会员等级、奖金比例、团队结构、PV、复购、区域或董事等奖励，以当前客户最终确认并发布的规则为准，不承诺固定收益。' },
  { question: '订单退款后奖励怎么处理？', answer: '订单取消、拒收、退款或部分退款时，与实际无效交易对应的业绩、PV和奖励会取消、冲正、冻结或追回；已提现后发生退款的，按适用规则形成应追回记录并依法处理。' },
  { question: '如何联系客服？', answer: '客服电话：{{servicePhone}}\n客服邮箱：{{serviceEmail}}\n客服时间：{{serviceHours}}' },
]

const fillDefaultFaqs = async () => {
  if (faqList.value.length > 0) {
    try {
      await ElMessageBox.confirm('填充默认问答会覆盖当前问答，是否继续？', '确认填充', { type: 'warning' })
    } catch {
      return
    }
  }
  faqList.value = defaultFaqData.map(item => ({ ...item }))
  ElMessage.success('已填充默认常见问题')
}

const fetchData = async () => {
  loading.value = true
  try {
    const [tenantRes, templateRes] = await Promise.all([
      listTenants({ pageNum: 1, pageSize: 100 }),
      getLegalTemplates(),
    ])
    defaultTemplates.value = templateRes.data || {}
    const rows = tenantRes.data?.list || []
    tenantForm.value = { ...(rows.find((row) => Number(row.id) === 1) || rows[0] || {}) }
    tenantForm.value.afterSaleWindowMode = tenantForm.value.afterSaleWindowMode || 'RECEIVED'
    tenantForm.value.afterSaleWindowDays = Number(tenantForm.value.afterSaleWindowDays || 7)
    try {
      faqList.value = tenantForm.value.faqs ? JSON.parse(tenantForm.value.faqs) : []
    } catch {
      faqList.value = []
    }
  } finally {
    loading.value = false
  }
}

const submit = async () => {
  saving.value = true
  try {
    const validFaqs = faqList.value.filter(faq => faq.question?.trim() && faq.answer?.trim())
    await saveTenant({
      ...tenantForm.value,
      userAgreement: tenantForm.value.userAgreement?.trim() || null,
      privacyPolicy: tenantForm.value.privacyPolicy?.trim() || null,
      afterSalePolicy: tenantForm.value.afterSalePolicy?.trim() || null,
      afterSaleWindowMode: tenantForm.value.afterSaleWindowMode || 'RECEIVED',
      afterSaleWindowDays: Number(tenantForm.value.afterSaleWindowDays || 7),
      faqs: validFaqs.length > 0 ? JSON.stringify(validFaqs) : null,
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
.window-mode-group { display:flex; align-items:stretch; flex-direction:column; gap:10px; }
.window-mode-group :deep(.el-radio) { height:auto; margin-right:0; padding:10px 12px; border:1px solid #dcdfe6; border-radius:8px; }
.window-mode-group :deep(.el-radio.is-checked) { background:#ecf5ff; border-color:var(--el-color-primary); }
.window-mode-title { display:block; color:#303133; font-weight:600; }
.window-mode-help { display:block; margin-top:3px; color:#909399; font-size:12px; line-height:18px; white-space:normal; }
.days-suffix { margin-left:8px; color:#606266; }
.textarea-toolbar { display:flex; align-items:center; gap:12px; margin-bottom:8px; }
.toolbar-hint { color:#909399; font-size:12px; }
.faq-alert { margin-bottom:16px; }
.faq-item { padding:12px; margin-bottom:12px; background:#f8f9fa; border-radius:8px; border:1px solid #ebeef5; }
.faq-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:8px; }
.faq-index { font-weight:600; color:var(--el-color-primary); }
.faq-question { margin-bottom:8px; }
.faq-button { margin-top:12px; }
.card-footer { display:flex; justify-content:flex-end; }
@media (max-width:700px) { .toolbar{align-items:flex-start;flex-direction:column;gap:10px}.legal-form{padding-right:0}.textarea-toolbar{align-items:flex-start;flex-direction:column;gap:4px} }
</style>
