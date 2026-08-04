<template>
  <div class="page-container bonus-config">
    <div class="toolbar">
      <el-tag size="large" type="info">当前商城</el-tag>
      <el-select v-model="selectedVersionId" placeholder="选择结算版本" filterable class="version-select">
        <el-option v-for="item in versions" :key="item.id" :label="`${item.versionName} (${item.versionNo})`" :value="item.id" />
      </el-select>
    </div>

    <el-alert title="当前新零售奖金方案已固化在底层代码中。所有奖金从订单确认收货起经过7天保护期，统一进入会员余额。" type="warning" :closable="false" show-icon class="page-alert" />

    <el-tabs v-model="activeTab">
      <el-tab-pane label="固定奖金方案" name="plan">
        <div class="plan-header"><div><h3>新零售正式方案</h3><p>每件有效商品计 1 单，累计本人及无限层团队；直推只认第一代，奖金按订单实付金额计算。</p></div><el-tag type="success" size="large">代码固化 · 已启用</el-tag></div>
        <el-table :data="rankPlan" border style="width:100%">
          <el-table-column prop="rank" label="会员卡级" width="130" />
          <el-table-column prop="condition" label="自动升级条件" min-width="360" />
          <el-table-column prop="rate" label="直推奖比例" width="140" align="center"><template #default="{ row }"><strong class="rate">{{ row.rate }}</strong></template></el-table-column>
          <el-table-column prop="dividend" label="无限层团队订单分红" width="190" align="center" />
        </el-table>
        <el-alert class="plan-note" type="info" :closable="false" title="触发升级的订单仍按支付前卡级计奖；该订单完成升级后，后续所有新订单才按新卡级计奖。退款会冲减件数和业绩并允许降级；移线不调级。" />
      </el-tab-pane>

      <el-tab-pane label="规则问答" name="faq">
        <el-alert title="以下问答是当前商城实际执行口径，适合运营、客服和财务在处理订单、退款、奖金和会员关系时直接查阅。" type="info" :closable="false" show-icon class="page-alert" />
        <div class="faq-summary">
          <div><strong>奖金基数</strong><span>商品实付金额，不含运费</span></div>
          <div><strong>关系依据</strong><span>支付瞬间冻结的完整关系链</span></div>
          <div><strong>入账时间</strong><span>确认收货满 7 天且无处理中售后</span></div>
          <div><strong>退款原则</strong><span>按累计商品退款比例冲减，差额只处理一次</span></div>
        </div>
        <div v-for="section in faqSections" :key="section.key" class="faq-section">
          <div class="faq-section-title"><h3>{{ section.title }}</h3><span>{{ section.description }}</span></div>
          <el-collapse>
            <el-collapse-item v-for="(item, index) in section.items" :key="`${section.key}-${index}`" :name="`${section.key}-${index}`">
              <template #title><span class="faq-question">{{ item.question }}</span></template>
              <div class="faq-answer">{{ item.answer }}</div>
            </el-collapse-item>
          </el-collapse>
        </div>
      </el-tab-pane>

      <el-tab-pane label="奖金验证器" name="simulate">
        <el-alert title="验证器只预览计算结果，不创建订单、不入账；真实全流程测试仍需通过商城正常下单付款。" type="info" :closable="false" class="page-alert" />
        <el-form :inline="true" :model="simulateForm">
          <el-form-item label="下单会员账号"><el-input v-model="simulateForm.orderMemberKey" placeholder="登录账号/手机号" /></el-form-item>
          <el-form-item label="订单实付金额"><el-input-number v-model="simulateForm.orderAmount" :min="0" :precision="2" /></el-form-item>
          <el-form-item><el-button type="primary" @click="submitSimulation">验证计算</el-button></el-form-item>
        </el-form>
        <el-alert v-if="simulationFeedback" :title="simulationFeedback" type="warning" :closable="false" show-icon class="simulation-feedback" />
        <el-table :data="simulationResult.receivers || []" empty-text="该会员在当前规则下没有可展示的奖金接收结果" style="width:100%">
          <el-table-column prop="memberAccount" label="会员账号" width="145" /><el-table-column prop="agentName" label="获奖会员" width="140" /><el-table-column prop="bonusName" label="奖金类型" width="170" /><el-table-column prop="relationLevel" label="关系深度" width="95" />
          <el-table-column prop="rate" label="奖金比例" width="100"><template #default="{ row }">{{ percent(row.rate) }}</template></el-table-column><el-table-column prop="bonusAmount" label="奖金金额" width="120" />
          <el-table-column label="奖金入账" min-width="180"><template #default="{ row }"><el-tag>余额 {{ money(row.bonusAmount) }}</el-tag></template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { simulateBonus } from '@/api/bonusConfig'
import { listRuleVersions } from '@/api/tenant'
import { memberSearchFailureMessage, validateMemberSearch } from '@/utils/searchFeedback'

const versions = ref([])
const selectedTenantId = ref(1)
const selectedVersionId = ref(null)
const activeTab = ref('plan')
const simulateForm = ref({ orderAmount: 0 })
const simulationResult = ref({})
const simulationFeedback = ref('')

const rankPlan = [
  { rank: '会员', condition: '完成首笔有效支付订单', rate: '25%', dividend: '—' },
  { rank: 'VIP会员', condition: '本人及无限层团队累计 10 件有效商品', rate: '30%', dividend: '—' },
  { rank: '店铺', condition: '直推 5 名有效会员，且本人及无限层团队累计 50 件有效商品', rate: '37%', dividend: '—' },
  { rank: '代理', condition: '直推至少 3 名 VIP（卡级别），且本人及无限层团队累计 150 件有效商品', rate: '45%', dividend: '—' },
  { rank: '一星董事', condition: '2 个独立直属部门各有代理（卡级别），且本人及无限层团队累计 500 件有效商品', rate: '52%', dividend: '5%' },
  { rank: '二星董事', condition: '2 个独立部门各有一星董事（卡级别）', rate: '57%', dividend: '4%' },
  { rank: '三星董事', condition: '2 个独立部门各有二星董事（卡级别）', rate: '61%', dividend: '3%' },
  { rank: '合伙人', condition: '2 个独立部门各有三星董事（卡级别）', rate: '65%', dividend: '2%' },
]

const faqSections = [
  {
    key: 'base',
    title: '一、奖金和业绩怎么算',
    description: '先统一口径，再处理具体订单',
    items: [
      {
        question: '当前商城到底执行哪一套奖金规则？',
        answer: '当前只启用“新零售正式方案”，比例和计算方式固化在系统中，后台不能切换旧版三级分佣，也不能自行新增另一套规则。页面上的版本号主要用于留痕和查询，不代表可以随意改成其他版本。',
      },
      {
        question: '什么样的订单才算有效订单？运费算不算业绩？',
        answer: '订单完成有效支付后才进入会员激活、业绩和奖金计算。商品按实际购买件数累计，买 3 件就计 3 件；运费只计入订单实付和财务金额，不计入业绩件数、PV、奖金基数或团队累计金额。待支付订单取消、支付失败或超时关闭，都不产生会员、业绩和奖金。',
      },
      {
        question: '奖金是按商品原价、售价还是订单实付金额计算？',
        answer: '按订单商品实付金额计算，不按划线价、成本价或运费计算。多规格商品以顾客实际选择的 SKU 价格、成本和 PV 为准；订单支付后会保存商品、SKU、金额、PV、成本和关系链快照，后续编辑商品不会改变历史订单。',
      },
      {
        question: '会员什么时候激活？买什么商品才能成为会员？',
        answer: '商城账号完成首笔有效支付订单后自动进入会员体系并成为“会员”卡级，不区分购买哪一个商品，也不能通过商品指定卡级。后台手工授予的推广资格按后台操作记录执行，不因普通商品退款自动撤销。',
      },
      {
        question: '会员升级和降级看哪些条件？',
        answer: '卡级主要依据本人及无限层团队的有效商品件数，以及直推人数、直推 VIP 数量和独立部门的卡级条件。直推条件只统计第一代直属下级，团队件数可以统计无限层。新增有效订单会触发升级；退款会冲销对应件数和业绩，只重新计算本次订单关系快照中受影响的会员；移线本身不会调级。',
      },
      {
        question: '触发升级的那一单按升级前还是升级后比例发奖？',
        answer: '按支付前的卡级发奖。订单支付时先冻结关系链和当时卡级，再记录奖金；本单完成后才刷新卡级，所以升级订单不会追溯补差额，新的卡级只对后续新订单生效。',
      },
    ],
  },
  {
    key: 'relation',
    title: '二、推荐关系怎么处理',
    description: '用 A、B、C 的关系举例说明',
    items: [
      {
        question: 'A 推荐 B，B 推荐 C，C 下单时谁能拿到奖金？',
        answer: 'B 是 C 的第一代直属推荐人，按 B 在 C 支付瞬间的卡级获得直推奖。A 是第二代上级，只有当 A 达到有团队分红资格的董事或合伙人卡级时，才按对应等级获得团队订单分红；A 不是董事级时不会因为这笔订单自动获得“第二代直推奖”。C 本人不拿自己订单的推荐奖金。',
      },
      {
        question: '无限层团队分红是不是每一层都发一份？',
        answer: '不是简单的逐层重复发放。关系链上每个董事等级最多只取最近的一位符合该等级的会员发放一次；同一订单如果经过多位同等级董事，只发给离下单人最近的那一位。不同董事等级可以分别获得各自对应的团队分红。',
      },
      {
        question: '支付后 B 把 C 移到别的上级，已经产生的奖金会不会跟着变？',
        answer: '不会。订单支付时已经保存完整关系快照，原订单的奖金接收人、关系层级和卡级保持不变。移线只影响移线完成后的新订单，不能回溯改写历史订单、历史业绩或历史奖金。',
      },
      {
        question: 'B 取消会员资格后，B 的下级 C 怎么办？',
        answer: '取消会员资格前必须先处理待结算奖金、待结算订单资金归集和退款追回欠款。处理成功后，B 的直接下级团队整体移交给 B 原来的直属上级；如果 B 没有上级，则下级提升为根节点。历史订单、历史奖金、余额流水和余额钱包保留，只有未来订单按新关系计算。',
      },
      {
        question: '某个上级已停用、被取消会员或不在正常状态，还能拿这笔订单的奖金吗？',
        answer: '只有支付时处于正常会员状态的关系节点才会接收对应奖金。停用节点不会补发奖金；系统仍保留订单关系快照和计算证据，方便后台审计。',
      },
    ],
  },
  {
    key: 'refund',
    title: '三、退款和奖金追回怎么处理',
    description: '部分退款、全额退款都按同一套可追溯逻辑执行',
    items: [
      {
        question: 'C 退款后，B 和 A 已经产生的奖金怎么办？',
        answer: '退款审核通过后，系统按本订单“累计商品退款金额 ÷ 商品实付金额”的比例冲减本订单所有相关奖金。B 的直推奖和 A 的团队分红都会按同一比例追回；运费退款不计入业绩和奖金冲销。',
      },
      {
        question: '奖金还在待结算状态时发生退款，会怎么处理？',
        answer: '直接减少待结算奖金和待结算统计金额；如果该笔奖金被全部冲减，记录标记为“已退款”，不会进入可用余额。退款后的业绩件数和团队业绩也会同步减少。',
      },
      {
        question: '奖金已经进入余额，后来又发生退款怎么办？',
        answer: '系统先从与该笔奖金相关的余额流水和可用余额中追回；余额不足的部分会形成退款追回欠款，并单独留存追回记录。以后产生新的奖金时，系统优先抵扣这笔欠款，抵扣完后剩余部分才进入待结算，不会因为余额不足而把退款直接抹掉。',
      },
      {
        question: '同一订单分两次、三次部分退款，会不会重复追回？',
        answer: '不会。系统按累计退款比例计算截至当前应追回的总额，每次只追回“本次应追回总额 − 之前已经追回金额”的差额。全部商品退完时，商品退款最多不超过商品实付；已发货订单的原发货运费不可退。',
      },
      {
        question: 'B 退的是自己的首单，会影响 A 和 C 吗？',
        answer: 'B 自己订单上产生的 A 的相关奖金会按该订单退款规则追回，B 自己的有效件数和团队业绩也会减少。C 已经支付的其他订单属于独立订单，仍以各自支付时的关系快照为准；如果 B 因全额退款且已无其他有效支付订单被退回非会员，未来 C 的新订单关系才会按新的团队关系处理。',
      },
      {
        question: '退款后 B 一定会立刻变回非会员吗？',
        answer: '只有“首单支付自动激活”的会员，在名下已经没有任何有效支付订单时，系统才会尝试自动取消会员资格。若 B 还有其他有效支付订单，或存在待结算奖金、待结算订单资金归集、退款追回欠款，系统会保留会员资格并要求先处理未结清事项；后台会保留日志，不影响本次退款本身。',
      },
      {
        question: '售后申请中但还没有审核通过，会不会马上追回奖金？',
        answer: '处理中售后只会阻止 T+7 自动结算，不会立即冲减奖金。只有售后审核通过、形成正式退款后，系统才会冲减业绩、重算财务并执行奖金追回；售后被驳回则不产生退款追回。',
      },
    ],
  },
  {
    key: 'wallet',
    title: '四、待结算、余额和资金归集',
    description: '区分“记录产生”和“余额可用”',
    items: [
      {
        question: '订单支付后，奖金是不是马上可以提现或使用？',
        answer: '不是。支付成功后只生成待结算奖金记录，同时建立订单资金归集凭证；待结算金额不等于可用余额。顾客确认收货后，还要经过 7 天保护期，且订单没有处理中售后，系统才会自动将奖金结算进会员余额。',
      },
      {
        question: 'T+7 从什么时候开始算？谁来执行？',
        answer: '从订单确认收货时间开始计算 7 天，不是从下单或支付时间开始。系统每 10 分钟自动扫描符合条件的订单；不存在处理中售后时，奖金和订单资金归集分别自动结算。后台不支持手工提前结算。',
      },
      {
        question: '商品成本和剩余商品款也进入会员奖金余额吗？',
        answer: '不会混入会员推广奖金。商品成本和扣除奖金后的剩余商品款由独立的系统资金账户留痕和归集，同样遵守确认收货后 7 天保护期；运费不参与这两类商品款归集。',
      },
      {
        question: '余额调整和奖金结算是一回事吗？',
        answer: '不是。后台财务权限可以对商城账号余额做增加或扣减，余额调整会产生独立流水和操作日志，不会伪造会员资格、团队业绩或推广奖金。非会员账号也可以有余额，余额钱包与奖金体系相互解耦。',
      },
      {
        question: '会员可以手工要求提前结算吗？',
        answer: '不可以。当前正式规则取消月度人工结算，统一由订单确认收货满 7 天且售后保护条件满足后自动结算。后台看到待结算记录时，应先查看订单收货时间和售后状态，而不是手工改状态。',
      },
    ],
  },
  {
    key: 'audit',
    title: '五、异常、重复操作和审计',
    description: '出现争议时按订单证据链核对',
    items: [
      {
        question: '用户重复点击支付或支付回调重复到达，会不会重复发奖金？',
        answer: '不会。订单状态只允许从待支付进入已支付一次；奖金记录、订单关系快照和计算快照都有重复保护。重复请求会复用已存在结果，不会重复扣库存、重复生成奖金或重复入账。',
      },
      {
        question: '奖金计算失败或服务暂时异常怎么办？',
        answer: '订单支付不因奖金异步计算暂时失败而回滚；系统会把计算任务标记为待处理或失败并自动重试，成功后再补齐奖金记录和计算快照。运营人员应查看后台订单财务、奖金记录和任务状态，不要手工重复导入同一订单。',
      },
      {
        question: '为什么订单奖金拨出率或利润会出现风控提示？',
        answer: '系统会按实付、成本、奖金、公司分红和退款重算订单财务。当前正式规则理论奖金拨出上限为 79%（最高直推 65% 加董事分红 14%）；运营预警阈值默认更低，用于提示核查，不代表系统会自动改写奖金。',
      },
      {
        question: '后台手工调级会不会重算历史订单？',
        answer: '不会。手工调级必须填写原因，只影响之后的新订单；历史订单仍按支付时的关系和卡级快照保留。退款触发的自动降级也只作用于本次退款影响到的关系节点。',
      },
      {
        question: '出现奖金争议时，客服和财务应该查哪些记录？',
        answer: '按订单编号依次核对：订单支付和收货时间、商品实付与退款金额、订单关系快照、PV/成本明细、奖金记录及状态、退款追回流水、余额流水、资金归集记录和订单财务风险状态。不要只看会员当前卡级或当前上下级关系，因为历史订单使用的是支付瞬间快照。',
      },
    ],
  },
]

const fetchBaseData = async () => {
  await handleTenantChange()
}

const handleTenantChange = async () => {
  if (!selectedTenantId.value) return
  const versionRes = await listRuleVersions(selectedTenantId.value)
  versions.value = versionRes.data || []
  selectedVersionId.value = versions.value[0]?.id || null
}

const submitSimulation = async () => {
  const validation = validateMemberSearch(simulateForm.value.orderMemberKey, { required: true })
  if (!validation.valid) {
    simulationResult.value = {}
    simulationFeedback.value = validation.message
    return
  }
  if (!simulateForm.value.orderAmount || Number(simulateForm.value.orderAmount) <= 0) {
    simulationFeedback.value = '请输入大于0的订单实付金额'
    return
  }
  simulationFeedback.value = ''
  simulateForm.value.orderMemberKey = validation.keyword
  try {
    const res = await simulateBonus({ tenantId:selectedTenantId.value, ruleVersionId:selectedVersionId.value, ...simulateForm.value })
    simulationResult.value = res.data || {}
  } catch (error) {
    simulationResult.value = {}
    simulationFeedback.value = memberSearchFailureMessage(error, validation.keyword, '奖金验证')
  }
}
const percent = (value) => `${(Number(value || 0) * 100).toFixed(2)}%`
const money = (value) => Number(value || 0).toFixed(2)

onMounted(fetchBaseData)
</script>

<style scoped>
.bonus-config .toolbar,.tab-toolbar { display:flex; align-items:center; gap:12px; margin-bottom:16px; }
.tenant-select{width:240px}.version-select{width:280px}.page-alert{margin-bottom:16px}.plan-header{display:flex;align-items:center;justify-content:space-between;margin:4px 0 18px;padding:18px 22px;background:#f5f7fa;border-radius:8px}.plan-header h3{margin:0 0 7px;font-size:19px}.plan-header p{margin:0;color:#606266}.rate{color:#f56c6c;font-size:16px}.plan-note{margin-top:16px}.switch-grid{display:grid;grid-template-columns:repeat(2,minmax(260px,1fr));gap:4px 32px;max-width:920px}.wide-item{grid-column:1/-1}
.simulation-feedback{margin-bottom:16px}
.faq-summary { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:12px; margin:4px 0 22px; }
.faq-summary div { display:flex; flex-direction:column; gap:6px; min-height:70px; padding:14px 16px; background:linear-gradient(135deg,#f7f9fc,#fff); border:1px solid #e4e7ed; border-radius:10px; }
.faq-summary strong { color:#303133; font-size:14px; }
.faq-summary span { color:#606266; font-size:12px; line-height:18px; }
.faq-section { margin:0 0 24px; }
.faq-section-title { display:flex; align-items:baseline; gap:12px; margin:0 0 10px; }
.faq-section-title h3 { margin:0; color:#303133; font-size:17px; }
.faq-section-title span { color:#909399; font-size:12px; }
.faq-section :deep(.el-collapse) { border-top:1px solid #ebeef5; border-bottom:1px solid #ebeef5; }
.faq-section :deep(.el-collapse-item__header) { min-height:48px; height:auto; padding:12px 14px; color:#303133; font-size:14px; line-height:22px; }
.faq-section :deep(.el-collapse-item__wrap) { background:#fbfcfe; }
.faq-section :deep(.el-collapse-item__content) { padding:2px 18px 16px 42px; }
.faq-question { font-weight:600; }
.faq-answer { color:#606266; font-size:13px; line-height:24px; white-space:pre-line; }
@media (max-width:900px) { .faq-summary{grid-template-columns:repeat(2,minmax(0,1fr))}.faq-section-title{align-items:flex-start;flex-direction:column;gap:4px} }
@media (max-width:600px) { .faq-summary{grid-template-columns:1fr}.faq-section :deep(.el-collapse-item__content){padding-left:18px} }
</style>
