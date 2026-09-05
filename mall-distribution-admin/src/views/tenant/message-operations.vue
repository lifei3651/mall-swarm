<template>
  <div class="page">
    <div class="intro"><div><h2>消息运营</h2><p>个人消息与商城公告相互独立；外部失败不会影响订单、售后、资金或站内消息。</p></div><el-tag type="warning">外部通道生产默认关闭</el-tag></div>
    <el-alert title="用户通知不提供人工重发；微信发货同步仅允许授权管理员确认后重新排队，不能绕过客户配置门禁。" type="warning" :closable="false"/>
    <div class="runtime-grid">
      <div class="runtime-card"><span>外部发送总门禁</span><b>{{ runtime.externalEnabled && runtime.workerEnabled ? '已开启' : '关闭' }}</b></div>
      <div class="runtime-card"><span>通知短信</span><b>{{ runtime.smsStatus || '读取中' }}</b></div>
      <div class="runtime-card"><span>App 推送</span><b>{{ runtime.appPushStatus || '读取中' }}</b></div>
      <div class="runtime-card"><span>小程序通知</span><b>{{ runtime.miniProgramStatus || '读取中' }}</b></div>
    </div>
    <section class="sms-readiness-panel">
      <h3>微信提醒逐项配置</h3>
      <p>四类提醒分别检查。配置就绪不代表用户已授权或微信已送达，不在这里开启运行门禁。</p>
      <ul class="readiness-list"><li v-for="item in runtime.miniProgramTemplates || []" :key="item.eventType" :class="{passed:item.runtimeReady}">
        <i>{{item.runtimeReady?'✓':'!'}}</i><div><strong>{{item.title}} · {{item.templateConfigured?'模板已配置':'待配置'}}</strong><p>{{item.detail}}</p></div>
      </li></ul>
    </section>
    <WeChatShippingPanel />
    <section class="sms-readiness-panel">
      <div class="readiness-head">
        <div><h3>通知短信开通进度</h3><p>集中核对合规、服务商、模板、事件、费用和运行门禁；本页不会一键开启或发送短信。</p></div>
        <el-tag :type="smsReadiness.readyForMemberOptIn?'success':'warning'">{{smsReadiness.readyForMemberOptIn?'可供会员自主开启':'尚未满足开放条件'}}</el-tag>
      </div>
      <div class="readiness-summary">
        <span>审核模板 <b>{{smsReadiness.approvedTemplateCount||0}} / {{smsReadiness.requiredTemplateCount||6}}</b></span>
        <span>开放事件 <b>{{smsReadiness.enabledEventCount||0}}</b></span>
        <span>费用上限 <b>{{smsReadiness.configuredBudgetCount||0}} / {{smsReadiness.requiredBudgetCount||2}}</b></span>
        <span>会员已授权 <b>{{smsReadiness.activeAuthorizationCount||0}} 人</b></span>
      </div>
      <ul class="readiness-list">
        <li v-for="item in smsReadiness.items||[]" :key="item.code" :class="{passed:item.passed}">
          <i>{{item.passed?'✓':'!'}}</i><div><strong>{{item.label}}</strong><p>{{item.detail}}</p></div>
        </li>
      </ul>
    </section>
    <el-tabs v-model="tab">
      <el-tab-pane label="消息模板" name="templates">
        <el-table :data="templates" stripe><el-table-column prop="category" label="分类" width="150"/><el-table-column prop="eventType" label="触发事件" width="190"/><el-table-column prop="titleTemplate" label="标题快照模板"/><el-table-column prop="version" label="版本" width="70"/><el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.enabled===1?'success':'info'">{{row.enabled===1?'启用':'关闭'}}</el-tag></template></el-table-column><el-table-column label="操作" width="90"><template #default="{row}"><el-button link type="primary" @click="edit(row)">维护</el-button></template></el-table-column></el-table>
      </el-tab-pane>
      <el-tab-pane label="事件与渠道" name="channels">
        <el-alert title="短信与微信订阅只用于账户安全、发货、售后退款和到账等低频服务通知；微信订阅还需要用户逐次授权。" type="info" :closable="false"/>
        <el-table :data="channels" stripe><el-table-column prop="eventType" label="触发事件"/><el-table-column label="站内消息" width="130"><template #default="{row}"><el-switch :model-value="row.inAppEnabled===1" @change="v=>toggle(row,v)"/></template></el-table-column><el-table-column label="短信" width="100"><template #default="{row}"><el-tag :type="row.smsEnabled===1?'warning':'info'">{{row.smsEnabled===1?'待全套门禁':'关闭'}}</el-tag></template></el-table-column><el-table-column label="App 推送" width="110"><el-tag type="info">未接供应商</el-tag></el-table-column><el-table-column label="小程序订阅" width="140"><template #default="{row}"><el-tag :type="row.miniProgramEnabled===1?'success':'info'">{{row.miniProgramEnabled===1?'支持（受总门禁）':'不发送'}}</el-tag></template></el-table-column><el-table-column label="短信估算成本" width="130"><template #default="{row}">¥{{money(row.estimatedSmsCost)}}</template></el-table-column></el-table>
      </el-tab-pane>
      <el-tab-pane label="费用硬上限" name="budgets">
        <el-alert :title="`${runtime.budgetStatus||''}；${runtime.authorizationStatus||''}`" type="warning" :closable="false"/>
        <el-table :data="budgets" stripe><el-table-column label="控制层级" width="120"><template #default="{row}">{{scopeText[row.scopeType]||row.scopeType}}</template></el-table-column><el-table-column prop="scopeKey" label="范围"/><el-table-column label="每日上限"><template #default="{row}">¥{{money(row.dailyLimit)}}</template></el-table-column><el-table-column label="每月上限"><template #default="{row}">¥{{money(row.monthlyLimit)}}</template></el-table-column><el-table-column label="配置状态" width="150"><template #default="{row}"><el-tag :type="row.enabled===1?'success':'danger'">{{row.enabled===1?'已配置':'未配置（禁止发送）'}}</el-tag></template></el-table-column></el-table>
      </el-tab-pane>
      <el-tab-pane label="发送记录" name="deliveries">
        <el-table :data="deliveries" stripe><el-table-column prop="messageId" label="消息编号"/><el-table-column prop="channel" label="渠道"/><el-table-column label="状态"><template #default="{row}"><el-tag :type="statusType(row.status)">{{statusText[row.status]||row.status}}</el-tag></template></el-table-column><el-table-column prop="attemptCount" label="尝试次数"/><el-table-column prop="retryCount" label="重试次数"/><el-table-column label="估算/实际成本"><template #default="{row}">¥{{money(row.estimatedCost)}} / ¥{{money(row.actualCost)}}</template></el-table-column><el-table-column prop="errorCode" label="脱敏原因"/><el-table-column prop="createTime" label="记录时间"/><el-table-column label="明细" width="80"><template #default="{row}"><el-button link type="primary" @click="showAttempts(row)">查看</el-button></template></el-table-column></el-table>
        <el-pagination v-model:current-page="pageNum" :page-size="20" :total="total" layout="prev,pager,next,total" @current-change="loadDeliveries"/>
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="dialog" title="维护未来消息模板" width="620px"><el-form label-position="top"><el-form-item label="标题"><el-input v-model="form.titleTemplate" maxlength="128" show-word-limit/></el-form-item><el-form-item label="列表摘要（不得含金额、卡号、地址、手机号、退款原因或验证码）"><el-input v-model="form.summaryTemplate" maxlength="300" show-word-limit/></el-form-item><el-form-item label="正文"><el-input v-model="form.contentTemplate" type="textarea" :rows="5" maxlength="1000" show-word-limit/></el-form-item><el-form-item label="启用"><el-switch v-model="form.enabled" :active-value="1" :inactive-value="0"/></el-form-item></el-form><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">保存未来模板</el-button></template></el-dialog>
    <el-dialog v-model="attemptDialog" title="发送尝试明细（已脱敏）" width="780px"><el-table :data="attempts"><el-table-column prop="attemptNo" label="序号"/><el-table-column prop="providerCode" label="适配器"/><el-table-column label="结果"><template #default="{row}">{{statusText[row.state]||row.state}}</template></el-table-column><el-table-column prop="queryCount" label="查单次数"/><el-table-column prop="estimatedCost" label="估算成本"/><el-table-column prop="actualCost" label="实际成本"/><el-table-column prop="errorCode" label="脱敏原因"/><el-table-column prop="submittedTime" label="提交时间"/></el-table></el-dialog>
  </div>
</template>
<script setup>
import WeChatShippingPanel from './wechat-shipping-panel.vue'
import{computed,onMounted,ref}from'vue';import{ElMessage}from'element-plus';import{listMessageTemplates,updateMessageTemplate,listMessageChannels,updateInAppChannel,listMessageDeliveries,getNotificationRuntime,listNotificationBudgets,listDeliveryAttempts}from'@/api/messageOperation'
const tab=ref('templates'),templates=ref([]),channels=ref([]),deliveries=ref([]),budgets=ref([]),runtime=ref({}),attempts=ref([]),pageNum=ref(1),total=ref(0),dialog=ref(false),attemptDialog=ref(false),form=ref({})
const statusText={SUCCESS:'站内已完成',PENDING:'待领取',SENDING:'发送或查单中',ACCEPTED:'供应商已受理',DELIVERED:'已送达',RETRYABLE:'等待重试',PERMANENT:'永久失败',SUPPRESSED:'安全抑制',EXPIRED:'已过期',PREPARED:'已预留预算',SUBMITTED:'已提交供应商',UNKNOWN:'结果待查询'}
const scopeText={TENANT:'当前客户',EVENT:'业务事件',CHANNEL:'发送渠道'}
const smsReadiness=computed(()=>runtime.value.serviceSmsReadiness||{items:[],requiredTemplateCount:6,requiredBudgetCount:2})
const statusType=s=>s==='DELIVERED'||s==='SUCCESS'?'success':s==='ACCEPTED'?'primary':s==='PENDING'||s==='SENDING'||s==='RETRYABLE'?'warning':s==='PERMANENT'||s==='EXPIRED'?'danger':'info'
const money=v=>Number(v||0).toFixed(4)
const load=async()=>{const [t,c,r,b]=await Promise.all([listMessageTemplates(),listMessageChannels(),getNotificationRuntime(),listNotificationBudgets()]);templates.value=t.data||[];channels.value=c.data||[];runtime.value=r.data||{};budgets.value=b.data||[]}
const loadDeliveries=async()=>{const r=(await listMessageDeliveries({pageNum:pageNum.value,pageSize:20})).data||{};deliveries.value=r.list||[];total.value=Number(r.total||0)}
const edit=row=>{form.value={...row};dialog.value=true}
const save=async()=>{await updateMessageTemplate(form.value.id,form.value);dialog.value=false;ElMessage.success('已保存，仅影响未来生成的消息');await load()}
const toggle=async(row,value)=>{await updateInAppChannel(row.id,value);ElMessage.success(value?'站内消息已启用':'站内消息已关闭');await load()}
const showAttempts=async row=>{attempts.value=(await listDeliveryAttempts(row.id)).data||[];attemptDialog.value=true}
onMounted(()=>{load();loadDeliveries()})
</script>
<style scoped>.page{padding:20px}.intro{display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;padding:18px;background:#fff;border-radius:12px}.intro h2{margin:0 0 6px}.intro p{margin:0;color:#667085}.el-alert{margin-bottom:14px}.runtime-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin:14px 0}.runtime-card{background:#fff;border:1px solid #e8ebf0;border-radius:10px;padding:14px;display:flex;flex-direction:column;gap:8px}.runtime-card span{color:#667085}.runtime-card b{font-size:14px;line-height:1.5}.sms-readiness-panel{margin:0 0 18px;padding:20px;background:#fff;border:1px solid #e8ebf0;border-radius:14px}.readiness-head{display:flex;align-items:flex-start;justify-content:space-between;gap:20px}.readiness-head h3{margin:0 0 6px;font-size:18px}.readiness-head p{margin:0;color:#667085;font-size:13px}.readiness-summary{display:flex;flex-wrap:wrap;gap:10px 24px;margin:18px 0;padding:12px 14px;background:#f7f8fa;border-radius:10px;color:#667085;font-size:13px}.readiness-summary b{color:#1d2939}.readiness-list{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin:0;padding:0;list-style:none}.readiness-list li{display:flex;gap:10px;padding:12px;border:1px solid #f1d4d0;background:#fff8f7;border-radius:10px}.readiness-list li>i{flex:0 0 23px;height:23px;color:#b42318;background:#fee4e2;border-radius:50%;font-style:normal;text-align:center;line-height:23px}.readiness-list li.passed{border-color:#ccebd8;background:#f6fef9}.readiness-list li.passed>i{color:#067647;background:#d1fadf}.readiness-list strong{font-size:14px}.readiness-list p{margin:4px 0 0;color:#667085;font-size:12px;line-height:1.5}.el-pagination{justify-content:flex-end;margin-top:14px}@media(max-width:900px){.runtime-grid,.readiness-list{grid-template-columns:1fr 1fr}.readiness-head{flex-direction:column}}@media(max-width:640px){.runtime-grid,.readiness-list{grid-template-columns:1fr}}</style>
