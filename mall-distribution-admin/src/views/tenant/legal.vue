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
            <div class="textarea-toolbar">
              <el-button type="primary" link @click="fillDefaultAgreement">填充默认模板</el-button>
              <span class="toolbar-hint">保存后会按“商城资料与客服”中的经营主体自动展示，主体变更无需重写协议</span>
            </div>
            <el-input v-model="tenantForm.userAgreement" type="textarea" :rows="10" maxlength="30000" show-word-limit placeholder="请输入经运营主体确认的完整用户服务协议" />
            <div class="field-help">注册时用户需要勾选同意，建议包含账号、下单、支付、售后、客服和争议处理说明。</div>
          </el-form-item>
          <el-form-item label="隐私政策">
            <div class="textarea-toolbar">
              <el-button type="primary" link @click="fillDefaultPrivacy">填充默认模板</el-button>
              <span class="toolbar-hint">保存后会按“商城资料与客服”中的经营主体自动展示，主体变更无需重写协议</span>
            </div>
            <el-input v-model="tenantForm.privacyPolicy" type="textarea" :rows="10" maxlength="30000" show-word-limit placeholder="请输入完整隐私政策" />
            <div class="field-help">注册时用户可以打开查看，建议说明收集的信息、使用目的、保存期限、第三方服务和联系方式。</div>
          </el-form-item>
        </section>

        <section class="form-section">
          <h3>交易与售后</h3>
          <el-form-item label="交易与售后规则">
            <div class="textarea-toolbar">
              <el-button type="primary" link @click="fillDefaultAfterSale">填充默认模板</el-button>
              <span class="toolbar-hint">客服电话和邮箱会从“商城资料与客服”自动带入</span>
            </div>
            <el-input v-model="tenantForm.afterSalePolicy" type="textarea" :rows="12" maxlength="30000" show-word-limit placeholder="请输入完整交易、售后、退款、运费及不适用七天无理由的规则" />
            <div class="field-help">注册页只做查看提示；商城会在商品详情和确认订单页继续提供入口，避免用户下单后找不到规则。</div>
          </el-form-item>
        </section>

        <section class="form-section">
          <h3>常见问题（FAQ）</h3>
          <el-alert title="以下问答用于前台常见问题展示，帮助用户了解商城规则。奖金规则务必写清楚，有疑问请联系负责人确认。" type="info" :closable="false" show-icon style="margin-bottom:16px" />
          <div v-for="(faq, index) in faqList" :key="index" class="faq-item">
            <div class="faq-header">
              <span class="faq-index">Q{{ index + 1 }}</span>
              <el-button type="danger" link @click="faqList.splice(index, 1)">删除</el-button>
            </div>
            <el-input v-model="faq.question" placeholder="问题" maxlength="200" style="margin-bottom:8px" />
            <el-input v-model="faq.answer" type="textarea" :rows="2" placeholder="答案" maxlength="1000" />
          </div>
          <el-button type="primary" plain @click="faqList.push({ question: '', answer: '' })" style="margin-top:12px">添加问答</el-button>
          <el-button type="success" plain @click="fillDefaultFaqs" style="margin-top:12px">填充默认问答</el-button>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { listTenants, saveTenant } from '@/api/tenant'

const loading = ref(false)
const saving = ref(false)
const tenantForm = ref({})
const faqList = ref([])

const defaultAgreement = `一、总则
1.1 本协议是您与{company}（以下简称"本商城"）之间关于使用本商城服务所订立的协议。
1.2 您在注册、登录或使用本商城服务前，请仔细阅读本协议。您点击同意或使用本商城服务，即表示您已充分理解并同意本协议的全部内容。

二、账号注册与管理
2.1 您应提供真实、准确、完整的注册信息，并及时更新。
2.2 您应妥善保管账号和密码，因您保管不善造成的损失由您自行承担。
2.3 您不得将账号转让、赠与或借给他人使用。

三、商品与订单
3.1 本商城展示的商品信息仅供参考，实际以商品实物为准。
3.2 您提交订单后，请在规定时间内完成支付，逾期订单将自动取消。
3.3 订单商品的价格、库存等信息以订单确认页面为准。

四、支付与配送
4.1 本商城支持的支付方式以结算页面显示为准。
4.2 商品配送范围、时效及运费以订单确认页面为准。
4.3 您签收商品时应当场验收，如有问题请及时联系客服。

五、售后服务
5.1 本商城提供的售后服务以《交易与售后规则》为准。
5.2 退款金额以实际支付金额和审核结果为准。

六、用户行为规范
6.1 您不得利用本商城从事违法违规活动。
6.2 您不得恶意刷单、虚假交易或利用系统漏洞获取不当利益。
6.3 违反行为规范的，本商城有权暂停或终止您的账号使用。

七、知识产权
7.1 本商城的所有内容（包括但不限于文字、图片、标识）均受法律保护。
7.2 未经本商城书面许可，您不得复制、转载或使用上述内容。

八、免责声明
8.1 因不可抗力、系统维护、网络故障等原因导致的服务中断，本商城不承担责任。
8.2 本商城对第三方链接的内容不承担责任。

九、争议解决
9.1 本协议的解释和执行适用中华人民共和国法律。
9.2 因本协议产生的争议，双方应友好协商解决；协商不成的，可向本商城所在地人民法院提起诉讼。

十、其他
10.1 本商城有权根据业务需要修改本协议，修改后的协议将在商城公示。
10.2 您继续使用本商城服务即视为同意修改后的协议。

客服电话：{phone}
客服邮箱：{email}`

const defaultPrivacy = `一、信息收集
1.1 注册信息：手机号、昵称、头像等您主动填写的信息。
1.2 订单信息：收货地址、联系方式、购买商品等交易相关信息。
1.3 设备信息：设备型号、操作系统、网络状态等用于保障服务正常运行的信息。
1.4 日志信息：访问时间、浏览页面、操作记录等用于改进服务的信息。

二、信息使用
2.1 用于账号注册、登录和身份验证。
2.2 用于订单处理、商品配送和售后服务。
2.3 用于发送订单状态变更、物流更新等服务通知。
2.4 用于改进商城功能、优化用户体验。
2.5 用于风险控制和安全保障。

三、信息共享
3.1 未经您同意，本商城不会向第三方共享您的个人信息，但以下情况除外：
  - 为完成订单配送，向物流服务商提供收货信息；
  - 为完成支付结算，向支付服务商提供必要信息；
  - 根据法律法规或政府要求必须提供的。
3.2 本商城可能接入第三方服务（如支付、物流），这些服务有各自的隐私政策。

四、信息保护
4.1 本商城采用加密存储、访问控制等技术手段保护您的信息安全。
4.2 本商城定期进行安全检查，防止信息泄露、损毁或丢失。
4.3 如发生信息安全事件，本商城将及时通知您并采取补救措施。

五、信息保存
5.1 您的信息将在提供服务所需的期限内保存。
5.2 注销账号后，本商城将在合理期限内删除或匿名化您的个人信息。

六、您的权利
6.1 您可以查看、修改或删除您的个人信息。
6.2 您可以注销账号，注销后相关信息将被删除或匿名化。
6.3 您可以联系客服行使上述权利。

七、未成年人保护
7.1 本商城不向未满18周岁的未成年人提供服务。
7.2 如您是未成年人，请在监护人陪同下阅读本政策并使用服务。

八、政策更新
8.1 本商城有权更新本政策，更新后将在商城公示。
8.2 继续使用本商城服务即视为同意更新后的政策。

九、联系方式
如您对本政策有任何疑问，请联系：
客服电话：{phone}
客服邮箱：{email}`

const defaultAfterSale = `一、交易规则
1.1 商品价格以订单确认页面显示为准。
1.2 订单提交后请在30分钟内完成支付，逾期自动取消。
1.3 商品配送范围覆盖中国大陆地区（港澳台除外）。

二、配送规则
2.1 订单支付成功后，商家将在48小时内发货（特殊商品除外）。
2.2 运费标准以商品详情页和结算页面显示为准。
2.3 您签收商品时应当场验收，如有破损或与订单不符，请拒收并联系客服。

三、退换货规则
3.1 七天无理由退货：
  - 自下单之日起7天内，商品完好且不影响二次销售的，可申请无理由退货。
  - 以下商品不适用七天无理由退货：定制商品、鲜活易腐商品、已拆封的数码产品、贴身衣物等。
3.2 质量问题退换货：
  - 商品存在质量问题的，自签收之日起15天内可申请退换货。
  - 请提供质量问题的照片或视频作为凭证。
3.3 退换货流程：
  - 提交退换货申请 → 商家审核 → 寄回商品 → 商家验收 → 退款/换货。

四、退款规则
4.1 退款金额以实际支付金额为准，优惠券、积分等不退还。
4.2 退款将在商家审核通过后3-7个工作日内原路退回。
4.3 使用余额支付的订单，退款金额将退回余额账户。

五、不适用退换货的情况
5.1 因您个人原因导致商品损坏或影响二次销售的。
5.2 商品标签、包装被拆除或损坏的。
5.3 超过退换货期限的。
5.4 商品页面明确标注不支持退换货的。

六、会员权益与奖励
6.1 会员奖励、团队业绩、账户余额等属于平台按规则核算的账户权益，不等同于商品售价或保证收益。
6.2 订单发生取消、退款、拒收或售后退款时，与该订单对应的未结算奖励将取消；已经入账的奖励由平台按原规则冲正，必要时从账户余额中扣回。
6.3 邀请关系以系统首次确认的有效邀请记录为准，禁止自买自卖、虚假交易、拆单套奖、刷单或利用漏洞获取不当利益。
6.4 平台可因风控、合规审查或异常交易暂缓结算，并在核验完成后按规则处理；具体比例、冷却期和结算条件以当期公示规则为准。

七、售后服务
7.1 如您对商品或服务有任何疑问，请联系客服：
  - 客服电话：{phone}
  - 客服邮箱：{email}
7.2 客服工作时间：周一至周日 9:00-21:00。

八、争议处理
8.1 如您与商家发生争议，本商城将协助双方协商解决。
8.2 协商不成的，可向本商城所在地消费者协会投诉或向人民法院提起诉讼。

九、特别说明
9.1 本规则中的"签收"以物流系统显示的签收时间为准。
9.2 本商城将依法保护消费者合法权益；规则与法律法规不一致的，以法律法规为准。
9.3 本规则的更新将在商城公示，更新后的规则仅适用于更新后产生的交易。`

const fillTemplate = (template) => {
  return template
    // 保留占位符，由前台展示时实时替换，避免经营主体或客服资料变更后协议仍显示旧信息。
    .replace(/\{company\}/g, '{{companyName}}')
    .replace(/\{phone\}/g, '{{servicePhone}}')
    .replace(/\{email\}/g, '{{serviceEmail}}')
}

const fillDefaultAgreement = async () => {
  if (tenantForm.value.userAgreement?.trim()) {
    try { await ElMessageBox.confirm('当前已有内容，填充默认模板将覆盖现有内容，是否继续？', '提示', { type: 'warning' }) } catch { return }
  }
  tenantForm.value.userAgreement = fillTemplate(defaultAgreement)
  ElMessage.success('已填充默认用户服务协议模板')
}

const fillDefaultPrivacy = async () => {
  if (tenantForm.value.privacyPolicy?.trim()) {
    try { await ElMessageBox.confirm('当前已有内容，填充默认模板将覆盖现有内容，是否继续？', '提示', { type: 'warning' }) } catch { return }
  }
  tenantForm.value.privacyPolicy = fillTemplate(defaultPrivacy)
  ElMessage.success('已填充默认隐私政策模板')
}

const fillDefaultAfterSale = async () => {
  if (tenantForm.value.afterSalePolicy?.trim()) {
    try { await ElMessageBox.confirm('当前已有内容，填充默认模板将覆盖现有内容，是否继续？', '提示', { type: 'warning' }) } catch { return }
  }
  tenantForm.value.afterSalePolicy = fillTemplate(defaultAfterSale)
  ElMessage.success('已填充默认交易与售后规则模板')
}

const defaultFaqData = [
  { question: '如何注册账号？', answer: '点击"我的"页面，选择注册，输入手机号获取验证码即可完成注册。' },
  { question: '如何下单购买商品？', answer: '浏览商品加入购物车，确认收货地址和商品信息后提交订单并完成支付即可。' },
  { question: '支持哪些支付方式？', answer: '目前支持余额支付。您可以在"我的-余额"中充值或接收奖金后使用余额支付订单。' },
  { question: '订单多久发货？', answer: '订单支付成功后，商家将在48小时内发货（特殊商品除外）。您可以在订单详情中查看物流信息。' },
  { question: '如何申请退换货？', answer: '自下单之日起7天内，商品完好且不影响二次销售的，可在订单详情中申请售后。超过期限后请联系商城客服，由后台按实际情况处理退款。' },
  { question: '退款多久到账？', answer: '退款审核通过后，3-7个工作日内原路退回。使用余额支付的订单，退款将退回余额账户。' },
  { question: '什么是余额？如何获得？', answer: '余额是商城账户中的虚拟资金，可用于支付订单。余额可通过奖金结算、会员转账等方式获得。' },
  { question: '如何提现？', answer: '在"我的-余额"页面点击"余额提现"，填写收款信息和提现金额，验证支付密码和短信验证码后提交申请。提现需完成首笔有效订单成为会员。' },
  { question: '提现多久到账？', answer: '提现申请提交后，后台将在1-3个工作日内审核并打款。审核拒绝的金额将自动退回余额。' },
  { question: '奖金规则是什么？', answer: '直推奖：邀请人获得被邀请人订单金额的一定比例（按邀请人等级：25%/30%/37%/45%/52%/57%/61%/65%）。团队分红：一星董事5%、二星董事4%、三星董事3%、合伙人2%，对无限层团队新增有效订单独立取得。奖金在确认收货满7天后进入可结算状态。' },
  { question: '订单退款后奖金怎么处理？', answer: '订单取消、退款、拒收或售后退款时，尚未结算的订单奖励会取消；已经入账的奖励按原订单冲正，必要时从账户余额扣回。A推荐B、B推荐C时，各层奖励均以对应有效订单为依据，不因层级关系绕过退款和风控规则。' },
  { question: '如何联系客服？', answer: '您可以通过以下方式联系我们：\n客服电话：{phone}\n客服邮箱：{email}\n工作时间：周一至周日 9:00-21:00' },
]

const fillDefaultFaqs = async () => {
  if (faqList.value.length > 0) {
    try { await ElMessageBox.confirm('当前已有问答内容，填充默认问答将覆盖现有内容，是否继续？', '提示', { type: 'warning' }) } catch { return }
  }
  faqList.value = defaultFaqData.map(item => ({
    question: item.question,
    answer: fillTemplate(item.answer)
  }))
  ElMessage.success('已填充默认常见问题')
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listTenants({ pageNum: 1, pageSize: 100 })
    const rows = res.data?.list || []
    tenantForm.value = { ...(rows.find((row) => Number(row.id) === 1) || rows[0] || {}) }
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
.textarea-toolbar { display:flex; align-items:center; gap:12px; margin-bottom:8px; }
.toolbar-hint { color:#909399; font-size:12px; }
.faq-item { padding:12px; margin-bottom:12px; background:#f8f9fa; border-radius:8px; border:1px solid #ebeef5; }
.faq-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:8px; }
.faq-index { font-weight:600; color:var(--el-color-primary); }
.card-footer { display:flex; justify-content:flex-end; }
@media (max-width:700px) { .toolbar{align-items:flex-start;flex-direction:column;gap:10px}.legal-form{padding-right:0} }
</style>
