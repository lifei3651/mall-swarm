<template>
  <main class="message-page">
    <header><div><span>个人消息</span><h1>消息中心</h1><p>商城公告不计入这里的未读数</p></div><button v-if="unread.total" @click="readAll">全部已读</button></header>
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
  </main>
</template>
<script setup>
import { computed,onBeforeUnmount,onMounted,ref } from 'vue'
import { getMessageUnread,listMemberMessages,markAllMessagesRead,markMessageCategoryRead } from '@/api/shop'
import { connectOrderRealtime } from '@/utils/orderRealtime'
const categories=[{key:null,label:'全部'},{key:'ORDER_LOGISTICS',label:'订单物流'},{key:'AFTER_SALE_REFUND',label:'售后退款'},{key:'WALLET_FUNDS',label:'钱包资金'},{key:'ACCOUNT_SECURITY',label:'账户安全'},{key:'SERVICE',label:'服务通知'}]
const category=ref(null),messages=ref([]),unread=ref({total:0,categories:{}}),pageNum=ref(0),totalPage=ref(1),loading=ref(false),error=ref('');let stop;let poll
const count=(key)=>key?Number(unread.value.categories?.[key]||0):Number(unread.value.total||0)
const showCount=(value)=>value>99?'99+':value
const refreshUnread=async()=>{unread.value=(await getMessageUnread()).data||unread.value}
const load=async(reset=true)=>{loading.value=true;error.value='';try{const next=reset?1:pageNum.value+1;const res=(await listMemberMessages({category:category.value||undefined,pageNum:next,pageSize:20})).data||{};messages.value=reset?(res.list||[]):[...messages.value,...(res.list||[])];pageNum.value=Number(res.pageNum||next);totalPage.value=Number(res.totalPage||1);await refreshUnread()}catch(e){error.value=e.message||'消息加载失败'}finally{loading.value=false}}
const selectCategory=(key)=>{category.value=key;load(true)}
const readCategory=async()=>{await markMessageCategoryRead(category.value);await load(true)}
const readAll=async()=>{await markAllMessagesRead();await load(true)}
const formatTime=(value)=>value?String(value).replace('T',' ').slice(0,16):''
// SSE 是刷新提示而不是事实来源；低频查询让其他终端的已读操作也能同步到当前页面。
onMounted(()=>{load();poll=setInterval(()=>load(true),30000);stop=connectOrderRealtime({onEvent:()=>load(true)})})
onBeforeUnmount(()=>{stop?.();clearInterval(poll)})
</script>
<style scoped>
.message-page{width:min(760px,calc(100% - 24px));margin:0 auto;padding:22px 0 90px}.message-page>header{display:flex;justify-content:space-between;align-items:flex-end;padding:22px;color:#fff;background:linear-gradient(135deg,#18243d,var(--brand-primary,#e7193f));border-radius:20px}.message-page header span{font-size:12px;opacity:.72}.message-page h1{margin:5px 0;font-size:25px}.message-page header p{margin:0;font-size:12px;opacity:.75}.message-page button{border:0;border-radius:10px;cursor:pointer}.message-page header button{padding:9px 12px;color:#fff;background:rgba(255,255,255,.18)}nav{display:flex;gap:8px;overflow:auto;padding:15px 0 7px}nav button{position:relative;flex:0 0 auto;padding:9px 13px;color:#667085;background:#fff}nav button.active{color:#fff;background:var(--brand-primary,#e7193f)}nav em{margin-left:5px;font-style:normal}.category-action{min-height:34px;text-align:right}.category-action button{padding:7px 10px;color:#475467;background:#fff}.message-card{display:grid;grid-template-columns:8px 1fr auto;gap:11px;align-items:start;margin-bottom:10px;padding:17px;color:#344054;background:#fff;border:1px solid #edf0f3;border-radius:15px}.message-card>i{width:7px;height:7px;margin-top:7px;background:transparent;border-radius:50%}.message-card.unread>i{background:#e5484d}.message-card strong{font-size:15px}.message-card p{margin:7px 0;color:#667085;font-size:13px;line-height:1.55}.message-card time{color:#98a2b3;font-size:11px}.message-card>span{color:#98a2b3;font-size:22px}.empty{padding:50px;text-align:center;color:#98a2b3}.more{display:block;margin:16px auto;padding:10px 18px}.error{color:#b42318}
</style>
