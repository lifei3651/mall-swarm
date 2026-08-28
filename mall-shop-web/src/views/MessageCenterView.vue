<template>
  <main class="message-page">
    <header><div><span>个人消息</span><h1>消息中心</h1><p>商城公告不计入这里的未读数</p></div><div class="header-actions"><RouterLink to="/support">客服工单</RouterLink><button v-if="unread.total" @click="readAll">全部已读</button></div></header>
    <section class="sms-preference" aria-labelledby="service-sms-title">
      <div>
        <span>可选提醒</span>
        <h2 id="service-sms-title">重要进度短信</h2>
        <p>仅用于订单发货、售后退款和账号安全变化，不含营销内容。</p>
        <small>{{ smsPreference.statusText }}<template v-if="smsPreference.maskedPhone"> · {{ smsPreference.maskedPhone }}</template></small>
      </div>
      <button
        type="button"
        class="preference-switch"
        role="switch"
        :aria-checked="smsPreference.enabled"
        :aria-label="smsPreference.enabled?'关闭重要进度短信':'开启重要进度短信'"
        :disabled="smsPreferenceBusy||(!smsPreference.available&&!smsPreference.enabled)"
        :class="{enabled:smsPreference.enabled}"
        @click="requestSmsPreferenceChange"
      ><i></i></button>
    </section>
    <p v-if="smsPreferenceNotice" class="preference-notice" role="status">{{ smsPreferenceNotice }}</p>
    <nav aria-label="消息分类">
      <button v-for="item in categories" :key="item.key" :class="{active:category===item.key}" @click="selectCategory(item.key)">
        {{ item.label }}<em v-if="count(item.key)">{{ showCount(count(item.key)) }}</em>
      </button>
    </nav>
    <div class="category-action"><button v-if="category && count(category)" @click="readCategory">本分类全部已读</button></div>
    <section :aria-busy="loading">
      <RouterLink v-for="message in messages" :key="message.id" :to="`/messages/${message.id}`" :class="['message-card',{unread:message.isRead!==1}]">
        <i></i><div><strong>{{ message.title }}</strong><p>{{ message.summary }}</p><time>{{ formatTime(message.occurredTime||message.createTime) }}</time></div><span>›</span>
      </RouterLink>
      <p v-if="!loading&&!messages.length" class="empty">当前分类暂无个人消息</p>
      <button v-if="pageNum<totalPage" class="more" @click="load(false)">加载更多</button>
    </section>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <ConfirmDialog
      :visible="smsConfirmVisible"
      title="开启重要进度短信？"
      message="开启后，商城可向当前绑定手机号发送订单发货、售后退款和账号安全变化提醒，不会用于营销。你可以随时在消息中心关闭。"
      confirm-text="同意并开启"
      :busy="smsPreferenceBusy"
      @confirm="saveSmsPreference(true)"
      @cancel="smsConfirmVisible=false"
    />
  </main>
</template>
<script setup>
import { computed,onBeforeUnmount,onMounted,ref } from 'vue'
import { getMessageUnread,getServiceSmsPreference,listMemberMessages,markAllMessagesRead,markMessageCategoryRead,updateServiceSmsPreference } from '@/api/shop'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { connectOrderRealtime } from '@/utils/orderRealtime'
const categories=[{key:null,label:'全部'},{key:'ORDER_LOGISTICS',label:'订单物流'},{key:'AFTER_SALE_REFUND',label:'售后退款'},{key:'WALLET_FUNDS',label:'钱包资金'},{key:'ACCOUNT_SECURITY',label:'账户安全'},{key:'SERVICE',label:'服务通知'}]
const category=ref(null),messages=ref([]),unread=ref({total:0,categories:{}}),pageNum=ref(0),totalPage=ref(1),loading=ref(false),error=ref('');let stop;let poll
const smsPreference=ref({available:false,enabled:false,maskedPhone:'',statusText:'正在确认服务状态'})
const smsPreferenceBusy=ref(false),smsPreferenceNotice=ref(''),smsConfirmVisible=ref(false)
const count=(key)=>key?Number(unread.value.categories?.[key]||0):Number(unread.value.total||0)
const showCount=(value)=>value>99?'99+':value
const refreshUnread=async()=>{unread.value=(await getMessageUnread()).data||unread.value}
const load=async(reset=true)=>{loading.value=true;error.value='';try{const next=reset?1:pageNum.value+1;const res=(await listMemberMessages({category:category.value||undefined,pageNum:next,pageSize:20})).data||{};messages.value=reset?(res.list||[]):[...messages.value,...(res.list||[])];pageNum.value=Number(res.pageNum||next);totalPage.value=Number(res.totalPage||1);await refreshUnread()}catch(e){error.value=e.message||'消息加载失败'}finally{loading.value=false}}
const selectCategory=(key)=>{category.value=key;load(true)}
const readCategory=async()=>{await markMessageCategoryRead(category.value);await load(true)}
const readAll=async()=>{await markAllMessagesRead();await load(true)}
const formatTime=(value)=>value?String(value).replace('T',' ').slice(0,16):''
const loadSmsPreference=async()=>{try{smsPreference.value=(await getServiceSmsPreference()).data||smsPreference.value}catch(e){smsPreference.value.statusText='暂时无法读取短信设置，站内消息不受影响'}}
const requestSmsPreferenceChange=()=>{if(smsPreference.value.enabled){saveSmsPreference(false);return}if(smsPreference.value.available)smsConfirmVisible.value=true}
const saveSmsPreference=async(enabled)=>{smsPreferenceBusy.value=true;smsPreferenceNotice.value='';try{smsPreference.value=(await updateServiceSmsPreference({enabled,consent:enabled})).data||smsPreference.value;smsConfirmVisible.value=false;smsPreferenceNotice.value=enabled?'已开启重要进度短信':'已关闭重要进度短信'}catch(e){smsPreferenceNotice.value=e.message||'短信设置保存失败'}finally{smsPreferenceBusy.value=false}}
// SSE 是刷新提示而不是事实来源；低频查询让其他终端的已读操作也能同步到当前页面。
onMounted(()=>{load();loadSmsPreference();poll=setInterval(()=>load(true),30000);stop=connectOrderRealtime({onEvent:()=>load(true)})})
onBeforeUnmount(()=>{stop?.();clearInterval(poll)})
</script>
<style scoped>
.message-page{width:min(760px,calc(100% - 24px));margin:0 auto;padding:22px 0 90px}.message-page>header{display:flex;justify-content:space-between;align-items:flex-end;padding:22px;color:#fff;background:linear-gradient(135deg,#18243d,var(--brand-primary,#e7193f));border-radius:20px}.message-page header span{font-size:12px;opacity:.72}.message-page h1{margin:5px 0;font-size:25px}.message-page header p{margin:0;font-size:12px;opacity:.75}.message-page button{border:0;border-radius:10px;cursor:pointer}.message-page button:disabled{cursor:not-allowed;opacity:.55}.message-page header button{padding:9px 12px;color:#fff;background:rgba(255,255,255,.18)}.sms-preference{display:flex;gap:16px;justify-content:space-between;align-items:center;margin-top:12px;padding:17px 18px;background:#fff;border:1px solid #edf0f3;border-radius:16px}.sms-preference span{color:var(--brand-primary,#e7193f);font-size:11px;font-weight:700}.sms-preference h2{margin:3px 0 4px;color:#1d2939;font-size:17px}.sms-preference p{margin:0;color:#667085;font-size:12px;line-height:1.6}.sms-preference small{display:block;margin-top:6px;color:#98a2b3;font-size:11px}.preference-switch{position:relative;flex:0 0 48px;width:48px;height:28px;padding:0;background:#d0d5dd;border-radius:999px!important;transition:.2s}.preference-switch i{position:absolute;top:3px;left:3px;width:22px;height:22px;background:#fff;border-radius:50%;box-shadow:0 1px 3px rgba(16,24,40,.2);transition:.2s}.preference-switch.enabled{background:var(--brand-primary,#e7193f)}.preference-switch.enabled i{transform:translateX(20px)}.preference-notice{margin:7px 3px 0;color:#475467;font-size:12px}nav{display:flex;gap:8px;overflow:auto;padding:15px 0 7px}nav button{position:relative;flex:0 0 auto;padding:9px 13px;color:#667085;background:#fff}nav button.active{color:#fff;background:var(--brand-primary,#e7193f)}nav em{margin-left:5px;font-style:normal}.category-action{min-height:34px;text-align:right}.category-action button{padding:7px 10px;color:#475467;background:#fff}.message-card{display:grid;grid-template-columns:8px 1fr auto;gap:11px;align-items:start;margin-bottom:10px;padding:17px;color:#344054;background:#fff;border:1px solid #edf0f3;border-radius:15px}.message-card>i{width:7px;height:7px;margin-top:7px;background:transparent;border-radius:50%}.message-card.unread>i{background:#e5484d}.message-card strong{font-size:15px}.message-card p{margin:7px 0;color:#667085;font-size:13px;line-height:1.55}.message-card time{color:#98a2b3;font-size:11px}.message-card>span{color:#98a2b3;font-size:22px}.empty{padding:50px;text-align:center;color:#98a2b3}.more{display:block;margin:16px auto;padding:10px 18px}.error{color:#b42318}
.header-actions{display:flex;gap:8px;align-items:center}.header-actions a{padding:9px 12px;color:#fff;background:rgba(255,255,255,.18);border-radius:10px;font-size:13px}
</style>
