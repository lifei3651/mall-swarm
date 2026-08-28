<template>
  <div class="login-page">
    <main class="login-shell">
      <aside class="developer-panel">
        <div class="developer-brand">
          <span class="developer-logo-wrap">
            <img :src="lingqiLogo" alt="长沙灵启软件开发有限公司 Logo" />
          </span>
          <div>
            <strong>长沙灵启软件开发有限公司</strong>
            <span>商城系统定制与数字化经营服务商</span>
          </div>
        </div>

        <div class="developer-copy">
          <span class="service-badge">成熟底座 · 按需定制 · 私有部署</span>
          <h2>让经营更清晰，<br />让系统更可靠。</h2>
          <p>
            专注商城系统定制、H5 与安卓应用、经营管理后台、客户服务器私有化部署及持续运维，
            为企业提供从业务梳理到上线维护的一体化技术服务。
          </p>
          <div class="service-list" aria-label="技术服务范围">
            <span>商城系统定制</span>
            <span>移动端应用</span>
            <span>私有化部署</span>
            <span>持续技术维护</span>
          </div>
        </div>

        <div class="service-statement">
          <strong>技术服务说明</strong>
          <p>
            本管理系统由长沙灵启软件开发有限公司提供软件开发、部署实施与技术维护服务。
            商城商品经营、订单履约、售后服务及用户运营由本商城经营主体负责。
          </p>
        </div>
      </aside>

      <section class="login-panel">
        <div class="login-content">
          <div class="login-eyebrow">{{ portalEyebrow }}</div>
          <div class="login-brand">
            <img :src="loginLogoSrc" :alt="`${brand.brandName} Logo`" @error="handleLoginLogoError" />
            <div>
              <h1>{{ loginHeading }}</h1>
              <p class="portal-description">{{ portalDescription }}</p>
            </div>
          </div>
          <el-alert
            v-if="sessionNotice"
            class="session-notice"
            :title="sessionNotice"
            type="warning"
            show-icon
            :closable="false"
          />
          <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
            <el-form-item prop="username">
              <el-input v-model="form.username" :placeholder="accountPlaceholder" clearable />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="form.password" placeholder="密码" type="password" show-password clearable />
            </el-form-item>
            <el-form-item prop="captchaCode">
              <div class="captcha-row">
                <el-input v-model="form.captchaCode" placeholder="图形验证码" maxlength="4" clearable />
                <button type="button" class="captcha-refresh" title="看不清？换一张" aria-label="刷新图形验证码" @click="refreshCaptcha">
                  <img class="captcha-image" :src="captchaImage" alt="图形验证码" />
                  <span>换一张</span>
                </button>
              </div>
            </el-form-item>
            <el-button type="primary" :loading="loading" class="login-button" @click="handleLogin">
              登录{{ portalButtonLabel }}
            </el-button>
          </el-form>
        </div>

        <div class="mobile-provider">
          <img :src="lingqiLogo" alt="长沙灵启软件开发有限公司 Logo" />
          <span>开发与技术维护：长沙灵启软件开发有限公司</span>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getLoginCaptcha, login } from '@/api/auth'
import { getShopBrand } from '@/api/shopBrand'
import { useAppStore } from '@/store'
import defaultLogo from '@/assets/logo.svg'
import lingqiLogo from '@/assets/lingqi-logo-mark.png'
import { consumeAdminSessionNotice } from '@/utils/adminSession'
import { updateAdminBrowserLogo } from '@/utils/adminBrand'
import { safeAdminRedirect } from '@/utils/safeRedirect'
import {
  ADMIN_PORTAL_MERCHANT,
  ADMIN_PORTAL_PLATFORM,
  adminPortalForAccount,
  normalizeAdminPortal,
  saveAdminPortal,
} from '@/utils/adminPortal'

const router = useRouter()
const route = useRoute()
const store = useAppStore()
const formRef = ref()
const loading = ref(false)
const captchaImage = ref('')
const sessionNotice = ref(consumeAdminSessionNotice())
const brand = reactive({ brandName: localStorage.getItem('admin_brand_name') || '商城', logoUrl: '' })
const brandLogoLoadFailed = ref(false)
const loginLogoSrc = computed(() => brandLogoLoadFailed.value ? defaultLogo : (brand.logoUrl || defaultLogo))
const portal = computed(() => normalizeAdminPortal(route.meta.portal, ADMIN_PORTAL_MERCHANT))
const isPlatformPortal = computed(() => portal.value === ADMIN_PORTAL_PLATFORM)
const portalEyebrow = computed(() => isPlatformPortal.value ? 'PLATFORM CONSOLE' : 'MERCHANT CONSOLE')
const loginHeading = computed(() => `${brand.brandName}${isPlatformPortal.value ? '平台总后台' : '商家后台'}`)
const portalDescription = computed(() => isPlatformPortal.value
  ? '仅供平台管理人员登录'
  : '仅供已开通的商家账号登录')
const accountPlaceholder = computed(() => isPlatformPortal.value ? '平台管理员账号' : '商家账号')
const portalButtonLabel = computed(() => isPlatformPortal.value ? '平台总后台' : '商家后台')
watch(portal, (value) => saveAdminPortal(value), { immediate: true })
const handleLoginLogoError = () => {
  if (loginLogoSrc.value === defaultLogo) return
  brandLogoLoadFailed.value = true
  updateAdminBrowserLogo('')
}

const form = reactive({
  username: '',
  password: '',
  captchaId: '',
  captchaCode: '',
})

const rules = {
  username: [{ required: true, message: '请输入后台账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入图形验证码', trigger: 'blur' }],
}

const refreshCaptcha = async () => {
  const res = await getLoginCaptcha()
  form.captchaId = res.data.captchaId
  form.captchaCode = ''
  captchaImage.value = res.data.image
}

const loadBrand = async () => {
  try {
    const res = await getShopBrand()
    brand.brandName = res.data?.brandName?.trim() || '商城'
    brand.logoUrl = res.data?.logoUrl || ''
    brandLogoLoadFailed.value = false
    localStorage.setItem('admin_brand_name', brand.brandName)
    document.title = `${loginHeading.value}登录`
    updateAdminBrowserLogo(brand.logoUrl)
  } catch {
    // 品牌读取失败不阻断管理员登录。
  }
}

const handleLogin = async () => {
  await formRef.value?.validate()
  loading.value = true
  try {
    const res = await login({ ...form, portal: portal.value })
    const accountPortal = adminPortalForAccount(res.data?.admin)
    if (accountPortal !== portal.value) {
      throw new Error('登录入口与账号类型不一致')
    }
    saveAdminPortal(accountPortal)
    store.setAuth(res.data)
    ElMessage.success('登录成功')
    if (Number(res.data?.admin?.mustChangePassword) === 1) {
      router.replace('/change-password')
      return
    }
    const merchantHome = res.data?.admin?.merchantId ? '/audit/merchant-finance' : '/dashboard'
    const requestedRedirect = safeAdminRedirect(route.query.redirect, merchantHome)
    const redirect = requestedRedirect === '/dashboard' && res.data?.admin?.merchantId
      ? merchantHome : requestedRedirect
    router.replace(redirect)
  } catch (error) {
    await refreshCaptcha()
    throw error
  } finally {
    loading.value = false
  }
}

onMounted(() => Promise.allSettled([refreshCaptcha(), loadBrand()]))
</script>

<style lang="scss" scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background:
    radial-gradient(circle at 12% 18%, rgba(34, 125, 231, 0.14), transparent 30%),
    radial-gradient(circle at 86% 82%, rgba(22, 92, 186, 0.10), transparent 28%),
    #eef3f9;
  padding: 36px;
}

.login-shell {
  width: min(1040px, 100%);
  min-height: 600px;
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(420px, 0.88fr);
  overflow: hidden;
  border: 1px solid rgba(202, 213, 228, 0.88);
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 24px 70px rgba(35, 64, 102, 0.16);
}

.developer-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 44px 48px 40px;
  overflow: hidden;
  color: #fff;
  background: linear-gradient(145deg, #0d316f 0%, #0b55a5 55%, #168ad1 100%);

  &::before,
  &::after {
    position: absolute;
    content: '';
    border-radius: 50%;
    pointer-events: none;
  }

  &::before {
    top: -145px;
    right: -125px;
    width: 360px;
    height: 360px;
    border: 70px solid rgba(255, 255, 255, 0.06);
  }

  &::after {
    right: 54px;
    bottom: 48px;
    width: 120px;
    height: 120px;
    background: rgba(255, 255, 255, 0.05);
  }
}

.portal-description {
  margin: 5px 0 0;
  color: #7b8798;
  font-size: 13px;
  line-height: 1.5;
}

.developer-brand,
.developer-copy,
.service-statement {
  position: relative;
  z-index: 1;
}

.developer-brand {
  display: flex;
  align-items: center;
  gap: 14px;

  strong,
  span {
    display: block;
  }

  strong {
    font-size: 17px;
    line-height: 1.5;
  }

  span {
    margin-top: 3px;
    color: rgba(255, 255, 255, 0.68);
    font-size: 12px;
  }
}

.developer-logo-wrap {
  width: 54px;
  height: 54px;
  display: grid;
  flex: 0 0 54px;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 10px 26px rgba(1, 23, 59, 0.22);

  img {
    width: 42px;
    height: 42px;
    object-fit: contain;
  }
}

.developer-copy {
  margin: auto 0;
  padding: 54px 0 46px;

  h2 {
    margin: 22px 0 18px;
    font-size: clamp(34px, 4vw, 48px);
    line-height: 1.28;
    letter-spacing: 1px;
  }

  p {
    max-width: 470px;
    margin: 0;
    color: rgba(255, 255, 255, 0.76);
    font-size: 14px;
    line-height: 1.9;
  }
}

.service-badge {
  display: inline-flex;
  padding: 7px 12px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.09);
  color: rgba(255, 255, 255, 0.88);
  font-size: 12px;
}

.service-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 26px;

  span {
    padding: 8px 12px;
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.10);
    color: rgba(255, 255, 255, 0.86);
    font-size: 12px;
  }
}

.service-statement {
  padding-top: 18px;
  border-top: 1px solid rgba(255, 255, 255, 0.16);

  strong {
    font-size: 12px;
    letter-spacing: 1px;
  }

  p {
    max-width: 500px;
    margin: 8px 0 0;
    color: rgba(255, 255, 255, 0.62);
    font-size: 11px;
    line-height: 1.75;
  }
}

.login-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 48px 46px 32px;
  background: #fff;
}

.login-content {
  width: 100%;
  max-width: 360px;
  margin: 0 auto;
}

.login-eyebrow {
  margin-bottom: 14px;
  text-align: center;
  color: #9aa7b8;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2px;
}

.login-brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 28px;
  text-align: center;

  img {
    width: 58px;
    height: 58px;
    object-fit: contain;
  }

  h1 {
    margin: 0;
    font-size: 22px;
    font-weight: 700;
    color: #172235;
  }

}

.login-button {
  width: 100%;
  height: 44px;
  margin-top: 2px;
  border-radius: 8px;
  font-weight: 600;
}

.session-notice {
  margin-bottom: 18px;
}

.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.captcha-image {
  display: block;
  width: 120px;
  height: 40px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
}

.captcha-refresh {
  width: 120px;
  flex: 0 0 120px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #909399;
  cursor: pointer;
}

.captcha-refresh span {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  line-height: 1.2;
}

.mobile-provider {
  display: none;
}

:deep(.el-input__wrapper) {
  border-radius: 8px;
}

@media (max-width: 860px) {
  .login-page {
    padding: 24px;
  }

  .login-shell {
    width: min(470px, 100%);
    min-height: auto;
    grid-template-columns: 1fr;
  }

  .developer-panel {
    display: none;
  }

  .login-panel {
    padding: 40px 32px 26px;
  }

  .mobile-provider {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    margin-top: 26px;
    padding-top: 20px;
    border-top: 1px solid #edf0f4;
    color: #97a2b2;
    font-size: 11px;

    img {
      width: 22px;
      height: 22px;
      object-fit: contain;
    }
  }
}

@media (max-width: 480px) {
  .login-page {
    align-items: start;
    padding: 18px 14px;
  }

  .login-shell {
    border-radius: 14px;
  }

  .login-panel {
    padding: 32px 22px 22px;
  }

  .login-brand {
    h1 {
      font-size: 20px;
    }
  }

  .captcha-image {
    width: 108px;
  }
}
</style>
