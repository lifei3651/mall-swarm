<template>
  <RouterLink to="/messages" class="message-entry">
    <span class="message-entry-icon"><Bell :size="25" /><em v-if="unread.total">{{ badge }}</em></span>
    <strong>消息中心</strong><span>订单、资金与安全通知</span>
  </RouterLink>
</template>
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Bell } from 'lucide-vue-next'
import { getMessageUnread } from '@/api/shop'
import { connectOrderRealtime } from '@/utils/orderRealtime'
const unread=ref({total:0,categories:{}});let stop;let poll
const badge=computed(()=>Number(unread.value.total)>99?'99+':String(unread.value.total||''))
const refresh=async()=>{try{unread.value=(await getMessageUnread()).data||unread.value}catch{}}
// 实时连接只负责加速刷新；固定低频回源保证跨设备已读和断线期间状态最终一致。
onMounted(()=>{refresh();poll=setInterval(refresh,30000);stop=connectOrderRealtime({onEvent:refresh})})
onBeforeUnmount(()=>{stop?.();clearInterval(poll)})
</script>
<style scoped>
.message-entry{position:relative;display:flex;flex-direction:column;gap:8px;padding:19px;color:var(--brand-primary,#e7193f);background:#fff;border-radius:16px}.message-entry strong{color:#253044;font-size:14px}.message-entry>span:last-child{color:#98a2b3;font-size:12px}.message-entry-icon{position:relative;width:max-content}.message-entry-icon em{position:absolute;top:-10px;left:17px;min-width:19px;height:19px;padding:0 5px;color:#fff;background:#e5484d;border:2px solid #fff;border-radius:11px;font-size:10px;font-style:normal;line-height:15px;text-align:center}
</style>
