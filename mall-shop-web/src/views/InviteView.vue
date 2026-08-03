<template>
  <div class="page sub-page invite-page">
    <header class="sub-page-head">
      <button type="button" aria-label="返回" @click="router.back()"><ArrowLeft :size="22" /></button>
      <h2>邀请好友</h2><span></span>
    </header>

    <section v-if="loading" class="panel invite-panel"><div class="loading-state">加载中...</div></section>

    <template v-else-if="inviteInfo">
      <!-- 二维码优先展示 -->
      <section class="panel invite-panel qr-first">
        <div class="qr-container">
          <img :src="qrCodeUrl" alt="邀请二维码" class="qr-image" />
        </div>
        <p class="qr-hint">好友扫码注册，注册完成后可直接下载APP</p>
      </section>

      <!-- 邀请码和链接 -->
      <section class="panel invite-panel">
        <div class="invite-code-row">
          <div
            class="code-info copy-target"
            role="button"
            tabindex="0"
            title="点击复制邀请码"
            aria-label="复制邀请码"
            @click="copyCode"
            @keydown.enter.prevent="copyCode"
            @keydown.space.prevent="copyCode"
          >
            <span class="code-label">我的邀请码</span>
            <strong class="code-value">{{ inviteInfo.inviteCode }}</strong>
          </div>
          <button type="button" class="btn copy-btn" @click="copyCode">复制</button>
        </div>
        <div class="invite-link-row">
          <div
            class="link-info copy-target"
            role="button"
            tabindex="0"
            title="点击复制邀请链接"
            aria-label="复制邀请链接"
            @click="copyLink"
            @keydown.enter.prevent="copyLink"
            @keydown.space.prevent="copyLink"
          >
            <span class="link-label">邀请链接</span>
            <span class="link-value">{{ inviteLink }}</span>
          </div>
          <button type="button" class="btn copy-btn" @click="copyLink">复制</button>
        </div>
      </section>

      <!-- 统计 -->
      <section class="panel invite-panel">
        <div class="invite-stats">
          <div><strong>{{ inviteInfo.directAccountCount || 0 }}</strong><span>直推注册账号</span></div>
          <div><strong>{{ inviteInfo.directMemberCount || 0 }}</strong><span>直推正式会员</span></div>
        </div>
      </section>
    </template>

    <section v-else class="panel invite-panel">
      <div class="error-state">
        <p>{{ error || '请先登录' }}</p>
        <button class="btn primary" @click="router.push('/login')">去登录</button>
      </div>
    </section>

    <div v-if="toast" class="toast">{{ toast }}</div>
  </div>
</template>

<script setup>
import { ref, computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import QRCode from 'qrcode'
import { getInviteInfo } from '@/api/shop'
import { toPublicWebUrl } from '@/utils/appEnvironment'
import { copyText } from '@/utils/clipboard'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const toast = ref('')
const inviteInfo = ref(null)
let toastTimer = null

const qrCodeUrl = ref('')
const inviteLink = computed(() => inviteInfo.value?.inviteCode
  ? `${toPublicWebUrl('/register')}?inviteCode=${encodeURIComponent(inviteInfo.value.inviteCode)}`
  : '')

watch(inviteLink, async (value) => {
  qrCodeUrl.value = value
    ? await QRCode.toDataURL(value, { width: 240, margin: 1, errorCorrectionLevel: 'M' })
    : ''
}, { immediate: true })

const fetchInviteInfo = async () => {
  loading.value = true
  try {
    const res = await getInviteInfo()
    inviteInfo.value = res.data
  } catch (e) {
    error.value = e.message || '获取邀请信息失败'
  } finally {
    loading.value = false
  }
}

const showToast = (msg) => {
  toast.value = msg
  if (toastTimer) window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 2000)
}

const copyWithFeedback = async (value, label) => {
  try {
    const copied = await copyText(value)
    showToast(copied ? `${label}已复制` : '复制失败，请长按内容手动复制')
  } catch (_) {
    showToast('复制失败，请长按内容手动复制')
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
.invite-page { width: min(480px, calc(100% - 28px)); }
.sub-page-head { display: grid; grid-template-columns: 40px 1fr 40px; align-items: center; margin-bottom: 14px; }
.sub-page-head h2 { margin: 0; text-align: center; font-size: 19px; }
.sub-page-head button { width: 40px; height: 40px; display: grid; place-items: center; padding: 0; background: #fff; border: 0; border-radius: 50%; }
.invite-panel { border: 0; border-radius: 16px; margin-top: 10px; }
.qr-first { display: flex; flex-direction: column; align-items: center; padding: 24px; }
.qr-container { padding: 16px; background: #fff; border-radius: 14px; box-shadow: 0 4px 16px rgba(0,0,0,.08); }
.qr-image { width: 200px; height: 200px; display: block; }
.qr-hint { margin: 12px 0 0; color: var(--muted); font-size: 12px; text-align: center; }
.invite-code-row, .invite-link-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 0; }
.invite-code-row { border-bottom: 1px solid var(--line); }
.code-label, .link-label { display: block; font-size: 12px; color: var(--muted); margin-bottom: 4px; }
.code-value { font-size: 24px; font-weight: 800; letter-spacing: 3px; color: var(--accent, #0f766e); }
.link-value { display: block; font-size: 11px; color: var(--muted); word-break: break-all; max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.copy-target { min-width: 0; cursor: pointer; border-radius: 8px; outline: none; }
.copy-target:focus-visible { box-shadow: 0 0 0 3px rgba(239, 35, 75, .16); }
.copy-btn { flex-shrink: 0; padding: 6px 16px; background: var(--accent, #0f766e); color: #fff; border: none; border-radius: 8px; font-size: 13px; font-weight: 600; }
.invite-stats { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.invite-stats > div { display: flex; flex-direction: column; align-items: center; gap: 5px; padding: 14px; border-radius: 10px; background: var(--soft, #f5f7fa); }
.invite-stats strong { font-size: 22px; }
.invite-stats span { color: var(--muted); font-size: 13px; }
.loading-state, .error-state { text-align: center; padding: 40px; color: var(--muted); }
.toast { position: fixed; bottom: 100px; left: 50%; transform: translateX(-50%); width: max-content; max-width: calc(100vw - 40px); background: rgba(0,0,0,.78); color: #fff; padding: 10px 24px; border-radius: 20px; font-size: 13px; line-height: 1.4; text-align: center; pointer-events: none; z-index: 9999; }
</style>
