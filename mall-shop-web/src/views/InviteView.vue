<template>
  <div class="page sub-page invite-page">
    <header class="sub-page-head">
      <button type="button" aria-label="返回" @click="router.back()"><ArrowLeft :size="22" /></button>
      <h2>邀请好友</h2><span></span>
    </header>

    <section class="panel invite-panel">
      <div class="invite-heading">
        <h3>专属邀请</h3>
        <p>二维码、邀请码和邀请数据集中展示</p>
      </div>
      <InviteCard />
    </section>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import InviteCard from '@/components/InviteCard.vue'

const router = useRouter()
</script>

<style scoped>
.invite-page { width:min(440px,calc(100% - 28px)); }
.sub-page-head { display:grid; grid-template-columns:40px 1fr 40px; align-items:center; margin-bottom:12px; }
.sub-page-head h2 { margin:0; text-align:center; font-size:19px; }
.sub-page-head button { display:grid; place-items:center; width:40px; height:40px; padding:0; background:#fff; border:0; border-radius:50%; }
.invite-panel { padding:20px; border:0; border-radius:20px; }
.invite-heading { margin-bottom:17px; }
.invite-heading h3 { margin:0; font-size:19px; }
.invite-heading p { margin:5px 0 0; color:var(--muted); font-size:12px; }
@media (max-height:720px) {
  .invite-page { padding-top:10px; }
  .sub-page-head { margin-bottom:8px; }
  .invite-panel { padding:16px 18px; }
  .invite-heading { margin-bottom:12px; }
}
</style>
