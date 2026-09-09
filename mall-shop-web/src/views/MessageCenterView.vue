<template>
  <main class="message-page">
    <header class="plain-header"><h1>{{ settingsVisible ? '提醒设置' : '消息中心' }}</h1><button :disabled="smsPreferenceBusy" @click="settingsVisible=!settingsVisible">{{ settingsVisible ? '返回消息' : '提醒设置' }}</button></header>
    <section v-if="settingsVisible" class="sms-preference" aria-labelledby="service-sms-title">
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
    <p v-if="settingsVisible && smsPreferenceNotice" class="preference-notice" role="status">{{ smsPreferenceNotice }}</p>
    <template v-if="!settingsVisible">
    <nav aria-label="消息分类">
      <button v-for="item in categories" :key="item.key" :class="{active:category===item.key}" @click="selectCategory(item.key)">
        {{ item.label }}<em v-if="count(item.key)">{{ showCount(count(item.key)) }}</em>
      </button>
    </nav>
    <div class="category-action"><button v-if="category && count(category)" @click="readCategory">本类已读</button><button v-else-if="!category && unread.total" @click="readAll">全部已读</button></div>
    <section :aria-busy="loading">
      <RouterLink v-for="message in messages" :key="message.id" :to="`/messages/${message.id}`" :class="['message-card',{unread:message.isRead!==1}]">
        <i></i><div><strong>{{ message.title }}</strong><p>{{ message.summary }}</p><time>{{ formatTime(message.occurredTime||message.createTime) }}</time></div><span>›</span>
      </RouterLink>
      <p v-if="!loading&&!messages.length" class="empty">当前分类暂无个人消息</p>
      <button v-if="pageNum<totalPage" class="more" @click="load(false)">加载更多</button>
    </section>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    </template>
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
const categories=[{key:null,label:'全部'},{key:'ORDER_LOGISTICS',label:'订单'},{key:'AFTER_SALE_REFUND',label:'售后'},{key:'WALLET_FUNDS',label:'资金'},{key:'SERVICE',label:'服务'}]
const settingsVisible=ref(false)
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
.message-page{width:min(760px,calc(100% - 24px));margin:0 auto;padding:12px 0 90px;color:var(--ink,#1d2939)}
.message-page>header{display:flex;align-items:center;justify-content:space-between;gap:16px;min-height:54px}
.message-page h1{margin:0;font-size:20px;font-weight:600}
.message-page button{min-height:44px;border:0;cursor:pointer;font:inherit;font-size:13px}
.message-page button:disabled{opacity:.55;cursor:not-allowed}
.plain-header button,.category-action button{padding:10px 8px;color:#667085;background:transparent}
nav{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));background:#fff;border-radius:6px;margin-top:12px}
nav button{position:relative;padding:14px 0;color:#667085;background:transparent}
nav button.active{color:var(--brand-primary,#e7193f);box-shadow:inset 0 -2px var(--brand-primary,#e7193f);font-weight:600}
nav em{position:absolute;top:0;right:2px;min-width:12px;padding:1px 3px;border-radius:6px;background:#f2f4f7;color:#475467;font-size:10px;font-style:normal}
.category-action{min-height:20px;text-align:right}
.message-card{display:grid;grid-template-columns:6px minmax(0,1fr) auto;gap:10px;padding:18px 14px;color:#344054;background:#fff;border-bottom:1px solid #edf0f3}
.message-card>i{width:6px;height:6px;margin-top:7px;border-radius:50%;background:transparent}
.message-card.unread>i{background:var(--brand-primary,#e7193f)}
.message-card strong{font-size:15px;font-weight:500;overflow-wrap:anywhere}
.message-card p{margin:7px 0;color:#667085;font-size:13px;line-height:1.6;overflow-wrap:anywhere}
.message-card time{color:#667085;font-size:11px}
.message-card>span{color:#98a2b3;font-size:22px}
.empty{padding:60px 16px;text-align:center;color:#667085}
.more{display:block;margin:16px auto;padding:10px 18px;border-radius:6px;background:#fff}
.error{color:#b42318}
.sms-preference{display:flex;gap:16px;align-items:center;margin-top:12px;padding:18px;background:#fff;border-radius:6px}
.sms-preference>div{flex:1;min-width:0}.sms-preference span{display:none}
.sms-preference h2{margin:0 0 8px;font-size:16px;font-weight:500}
.sms-preference p,.sms-preference small{color:#667085;font-size:12px;line-height:1.6}
.sms-preference p{margin:0 0 8px}
.message-page .preference-switch{position:relative;flex:0 0 48px;width:48px;height:28px;min-height:28px;background:#d0d5dd;border-radius:99px}
.preference-switch i{position:absolute;top:3px;left:3px;width:22px;height:22px;background:white;border-radius:50%}
.preference-switch.enabled{background:var(--brand-primary,#e7193f)}.preference-switch.enabled i{transform:translateX(20px)}
.preference-notice{color:#475467;font-size:13px}
</style>
