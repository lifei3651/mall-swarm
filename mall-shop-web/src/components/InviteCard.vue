<template>
  <div class="invite-card-content" :aria-busy="loading">
    <div v-if="loading" class="invite-card-state">正在生成邀请信息...</div>

    <template v-else-if="inviteInfo">
      <div class="invite-qr-shell">
        <img :src="qrCodeUrl" alt="邀请注册二维码" class="invite-qr" />
      </div>
      <p class="invite-tip">好友扫码或填写邀请码即可注册</p>

      <button type="button" class="invite-code" aria-label="复制邀请码" @click="copyCode">
        <span>邀请码</span>
        <strong>{{ inviteInfo.inviteCode }}</strong>
        <em><Copy :size="15" />复制</em>
      </button>

      <div class="invite-stats" aria-label="邀请数据">
        <div>
          <UsersRound :size="20" />
          <strong>{{ inviteInfo.directAccountCount || 0 }}</strong>
          <span>注册账号</span>
        </div>
        <div>
          <BadgeCheck :size="20" />
          <strong>{{ inviteInfo.directMemberCount || 0 }}</strong>
          <span>正式会员</span>
        </div>
      </div>

      <button type="button" class="invite-share-button" @click="copyLink">
        <Link :size="18" />复制邀请链接
      </button>
    </template>

    <div v-else class="invite-card-state error-state">
      <p>{{ error || '邀请信息暂时无法加载' }}</p>
      <button type="button" @click="fetchInviteInfo">重新加载</button>
    </div>

    <div v-if="toast" class="invite-toast" role="status">{{ toast }}</div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { BadgeCheck, Copy, Link, UsersRound } from 'lucide-vue-next'
import QRCode from 'qrcode'
import { getInviteInfo } from '@/api/shop'
import { copyText } from '@/utils/clipboard'
import { toPublicWebUrl } from '@/utils/appEnvironment'

const loading = ref(true)
const error = ref('')
const toast = ref('')
const inviteInfo = ref(null)
const qrCodeUrl = ref('')
let toastTimer = null

const inviteLink = computed(() => inviteInfo.value?.inviteCode
  ? `${toPublicWebUrl('/register')}?inviteCode=${encodeURIComponent(inviteInfo.value.inviteCode)}`
  : '')

watch(inviteLink, async (value) => {
  qrCodeUrl.value = value
    ? await QRCode.toDataURL(value, { width: 220, margin: 1, errorCorrectionLevel: 'M' })
    : ''
}, { immediate: true })

const fetchInviteInfo = async () => {
  loading.value = true
  error.value = ''
  try {
    inviteInfo.value = (await getInviteInfo()).data
  } catch (e) {
    inviteInfo.value = null
    error.value = e.message || '获取邀请信息失败'
  } finally {
    loading.value = false
  }
}

const showToast = (message) => {
  toast.value = message
  if (toastTimer) window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 1800)
}

const copyWithFeedback = async (value, label) => {
  try {
    const copied = await copyText(value)
    showToast(copied ? `${label}已复制` : '复制失败，请长按内容复制')
  } catch (_) {
    showToast('复制失败，请长按内容复制')
  }
}

const copyCode = () => copyWithFeedback(inviteInfo.value?.inviteCode, '邀请码')
const copyLink = () => copyWithFeedback(inviteLink.value, '邀请链接')

onMounted(fetchInviteInfo)
onBeforeUnmount(() => {
  if (toastTimer) window.clearTimeout(toastTimer)
})
</script>

<style scoped>
.invite-card-content { position:relative; display:flex; flex-direction:column; align-items:center; width:100%; }
.invite-qr-shell { padding:12px; background:#fff; border:1px solid #edf0f3; border-radius:18px; box-shadow:0 8px 24px rgba(29,36,48,.08); }
.invite-qr { display:block; width:174px; height:174px; }
.invite-tip { margin:12px 0 16px; color:var(--muted); font-size:12px; text-align:center; }
.invite-code { display:grid; grid-template-columns:auto minmax(0,1fr) auto; align-items:center; gap:10px; width:100%; min-height:62px; padding:10px 14px; color:var(--ink); background:#fff7f8; border:1px solid rgba(239,35,75,.12); border-radius:14px; text-align:left; }
.invite-code span { color:var(--muted); font-size:12px; }
.invite-code strong { overflow:hidden; color:var(--brand-primary); font-size:20px; letter-spacing:2px; text-overflow:ellipsis; white-space:nowrap; }
.invite-code em { display:inline-flex; align-items:center; gap:4px; color:var(--brand-primary); font-size:12px; font-style:normal; font-weight:700; }
.invite-stats { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px; width:100%; margin-top:12px; }
.invite-stats > div { display:grid; grid-template-columns:auto 1fr; align-items:center; gap:2px 8px; min-width:0; padding:12px 14px; color:var(--brand-primary); background:var(--soft,#f5f7fa); border-radius:14px; }
.invite-stats svg { grid-row:1 / 3; }
.invite-stats strong { color:var(--ink); font-size:20px; line-height:1.05; }
.invite-stats span { color:var(--muted); font-size:11px; }
.invite-share-button { display:flex; align-items:center; justify-content:center; gap:7px; width:100%; min-height:46px; margin-top:14px; color:#fff; background:var(--brand-primary); border:0; border-radius:14px; font-size:14px; font-weight:700; }
.invite-card-state { display:grid; place-items:center; min-height:340px; color:var(--muted); font-size:13px; text-align:center; }
.error-state { gap:12px; }
.error-state p { margin:0; }
.error-state button { min-height:40px; padding:0 18px; color:#fff; background:var(--brand-primary); border:0; border-radius:12px; }
.invite-toast { position:fixed; bottom:96px; left:50%; z-index:10020; width:max-content; max-width:calc(100vw - 40px); padding:9px 18px; color:#fff; background:rgba(20,27,38,.86); border-radius:999px; font-size:12px; transform:translateX(-50%); pointer-events:none; }
@media (max-height:720px) {
  .invite-qr { width:150px; height:150px; }
  .invite-tip { margin:9px 0 12px; }
  .invite-code { min-height:56px; }
  .invite-stats > div { padding:10px 12px; }
  .invite-share-button { min-height:42px; margin-top:11px; }
}
</style>
