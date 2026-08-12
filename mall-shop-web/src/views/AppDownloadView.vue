<template>
  <div class="download-page">
    <section class="download-card">
      <div class="app-logo">LQ</div>
      <p v-if="registered" class="success-badge">注册成功</p>
      <h1>{{ registered ? `欢迎加入${brandName}` : `${brandName} APP` }}</h1>
      <p class="lead">{{ releaseAvailable ? '安卓版已开放下载，安装后可继续购物和查看订单。' : '安卓版仍在内部测试，正式发布前暂不提供公开下载；网页版功能不受影响。' }}</p>

      <button v-if="isAndroidDevice && releaseAvailable" class="download-btn" type="button" :disabled="loading" @click="download">
        {{ loading ? '正在获取版本...' : `下载安卓版 ${release.versionName || ''}` }}
      </button>
      <div v-else-if="releaseAvailable" class="device-tip">
        当前不是安卓设备，可复制本页面链接后在安卓手机浏览器中打开。
      </div>
      <div v-else class="device-tip">当前没有对外发布的安卓版本，请继续使用网页版。</div>

      <p v-if="error" class="error-text">{{ error }}</p>
      <dl v-if="releaseAvailable && release.versionName" class="release-info">
        <div><dt>版本</dt><dd>{{ release.versionName }}</dd></div>
        <div v-if="release.sha256"><dt>文件校验</dt><dd>{{ shortHash }}</dd></div>
      </dl>

      <div class="install-help">
        <h2>安装说明</h2>
        <ol>
          <li>下载完成后打开安装包。</li>
          <li>首次安装时，按手机提示允许本浏览器安装应用。</li>
          <li>正式上架应用市场后，可由应用市场自动更新。</li>
        </ol>
      </div>

      <RouterLink class="continue-link" :to="continuePath">暂不安装，继续使用网页版</RouterLink>
    </section>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { fetchAndroidRelease, openAndroidDownload } from '@/utils/appRelease'
import { hasShopSession } from '@/utils/shopSession'
import { currentBrandName } from '@/utils/brand'

const route = useRoute()
const shopBrand = inject('shopBrand', null)
const brandName = computed(() => shopBrand?.value?.brandName || currentBrandName())
const registered = computed(() => route.query.registered === '1')
const isAndroidDevice = /Android/i.test(navigator.userAgent)
const loading = ref(true)
const error = ref('')
const release = ref({ versionCode: 0, versionName: '', downloadUrl: '', sha256: '', published: false })
const releaseAvailable = computed(() => release.value.published === true && Boolean(release.value.downloadUrl))
const continuePath = computed(() => hasShopSession() ? '/profile' : '/')
const shortHash = computed(() => release.value.sha256
  ? `${release.value.sha256.slice(0, 12)}…${release.value.sha256.slice(-12)}`
  : '')

const loadRelease = async () => {
  loading.value = true
  error.value = ''
  try {
    const data = await fetchAndroidRelease()
    release.value = { ...data, sha256: data.sha256 || '' }
  } catch (e) {
    error.value = e.message || '安卓版本信息加载失败，请稍后再试'
  } finally {
    loading.value = false
  }
}

const download = async () => {
  try {
    await openAndroidDownload(release.value.downloadUrl)
  } catch (e) {
    error.value = e.message || '下载页面打开失败，请稍后重试'
  }
}

onMounted(loadRelease)
</script>

<style scoped>
.download-page { min-height: calc(100vh - 74px); display: grid; place-items: start center; padding: 24px 14px 100px; background: linear-gradient(160deg, #eaf8f4 0%, #fff 48%, #fff4ec 100%); }
.download-card { width: min(460px, 100%); padding: 30px 22px; text-align: center; background: rgba(255,255,255,.94); border: 1px solid rgba(15,118,110,.12); border-radius: 24px; box-shadow: 0 18px 50px rgba(15,118,110,.12); }
.app-logo { width: 76px; height: 76px; display: grid; place-items: center; margin: 0 auto 16px; color: #fff; background: linear-gradient(135deg, #0f766e, #15a58f); border-radius: 22px; box-shadow: 0 12px 24px rgba(15,118,110,.25); font-size: 27px; font-weight: 900; letter-spacing: 1px; }
.success-badge { display: inline-flex; margin: 0 0 8px; padding: 5px 12px; color: #08724f; background: #e8f8f1; border-radius: 999px; font-size: 13px; font-weight: 700; }
h1 { margin: 0; color: var(--ink, #1f2937); font-size: 25px; }
.lead { margin: 12px auto 20px; color: var(--muted, #667085); font-size: 14px; line-height: 1.75; }
.download-btn { width: 100%; min-height: 48px; padding: 12px 18px; color: #fff; background: var(--accent, #0f766e); border: 0; border-radius: 13px; font-size: 16px; font-weight: 800; cursor: pointer; }
.download-btn:disabled { opacity: .55; cursor: not-allowed; }
.device-tip { padding: 13px; color: #7a4b00; background: #fff7e6; border-radius: 12px; font-size: 13px; line-height: 1.6; }
.error-text { margin: 12px 0 0; color: #b42318; font-size: 13px; }
.release-info { margin: 16px 0 0; padding: 12px 14px; background: #f7faf9; border-radius: 12px; font-size: 12px; }
.release-info div { display: flex; justify-content: space-between; gap: 12px; padding: 4px 0; }
.release-info dt { color: var(--muted, #667085); }
.release-info dd { margin: 0; color: var(--ink, #1f2937); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; word-break: break-all; }
.install-help { margin-top: 20px; padding: 16px; text-align: left; background: #f8fafc; border-radius: 14px; }
.install-help h2 { margin: 0 0 8px; font-size: 15px; }
.install-help ol { margin: 0; padding-left: 20px; color: var(--muted, #667085); font-size: 13px; line-height: 1.8; }
.continue-link { display: inline-block; margin-top: 18px; color: var(--accent, #0f766e); font-size: 13px; text-decoration: none; }
</style>
