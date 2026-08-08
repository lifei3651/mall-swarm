<template>
  <Teleport to="body">
    <div v-if="visible" class="invite-dialog-mask" @click.self="emit('close')">
      <section class="invite-dialog" role="dialog" aria-modal="true" aria-labelledby="invite-dialog-title">
        <header>
          <div>
            <h3 id="invite-dialog-title">邀请好友</h3>
            <p>分享你的专属邀请信息</p>
          </div>
          <button type="button" aria-label="关闭邀请弹窗" @click="emit('close')"><X :size="21" /></button>
        </header>
        <InviteCard />
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { onBeforeUnmount, watch } from 'vue'
import { X } from 'lucide-vue-next'
import InviteCard from '@/components/InviteCard.vue'

const props = defineProps({ visible: { type: Boolean, default: false } })
const emit = defineEmits(['close'])
let previousOverflow = ''

watch(() => props.visible, (visible) => {
  if (visible) {
    previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = previousOverflow
  }
})

onBeforeUnmount(() => {
  document.body.style.overflow = previousOverflow
})
</script>

<style scoped>
.invite-dialog-mask { position:fixed; inset:0; z-index:10000; display:grid; place-items:center; padding:18px; background:rgba(20,27,38,.5); backdrop-filter:blur(3px); }
.invite-dialog { width:min(380px,100%); max-height:calc(100dvh - 36px); overflow:auto; padding:20px; background:#fff; border-radius:22px; box-shadow:0 24px 70px rgba(20,27,38,.26); }
.invite-dialog header { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; margin-bottom:17px; }
.invite-dialog h3 { margin:0; color:var(--ink); font-size:20px; }
.invite-dialog header p { margin:5px 0 0; color:var(--muted); font-size:12px; }
.invite-dialog header button { display:grid; place-items:center; width:36px; height:36px; flex:0 0 36px; padding:0; color:#586273; background:#f5f7fa; border:0; border-radius:50%; }
@media (max-height:720px) {
  .invite-dialog { padding:16px 18px; border-radius:18px; }
  .invite-dialog header { margin-bottom:12px; }
}
</style>
