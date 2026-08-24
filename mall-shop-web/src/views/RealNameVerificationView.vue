<template>
  <div class="page sub-page real-name-page">
    <header class="sub-page-head">
      <button type="button" aria-label="返回" @click="router.back()"><ArrowLeft :size="22" /></button>
      <h2>实名认证</h2><span></span>
    </header>

    <section v-if="status.verified" class="verified-card">
      <BadgeCheck :size="40" />
      <h3>当前账号已完成实名认证</h3>
      <p>{{ status.maskedRealName }} · {{ status.maskedIdCard }}</p>
      <small>每个账号只能绑定一份实名信息；如需变更，请联系客服核验处理。</small>
      <button class="btn primary" type="button" @click="continueAfterVerified">返回继续</button>
    </section>

    <form v-else class="panel identity-form" @submit.prevent="submit">
      <h3>核验本人身份</h3>
      <p class="line-sub">同一身份证可以认证多个账号，但每个账号只能绑定一份实名信息。</p>
      <div class="form-item"><label for="real-name">真实姓名</label><input id="real-name" v-model.trim="form.realName" class="field" maxlength="64" autocomplete="name" placeholder="请输入身份证上的姓名" /></div>
      <div class="form-item"><label for="id-card">身份证号</label><input id="id-card" v-model.trim="form.idCard" class="field" maxlength="18" inputmode="text" autocomplete="off" placeholder="请输入18位身份证号" @input="normalizeIdCard" /></div>
      <label class="consent-row"><input v-model="form.sensitiveInfoConsent" type="checkbox" /><span>我已阅读并同意<RouterLink to="/legal/privacy" target="_blank">《隐私政策》</RouterLink>，授权将姓名和身份证号发送给权威实名认证服务进行一次核验。</span></label>
      <button class="btn primary submit-button" type="submit" :disabled="saving || !status.verificationAvailable">{{ saving ? '正在核验…' : '提交认证' }}</button>
      <p v-if="!status.verificationAvailable" class="service-note">实名认证通道尚未启用，请联系客服。</p>
      <p class="privacy-note">身份证号不会在页面完整回显；服务端加密保存，日志和核验审计不记录姓名及身份证原文。</p>
    </form>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, BadgeCheck } from 'lucide-vue-next'
import { getRealNameStatus, verifyRealName } from '@/api/shop'

const route = useRoute()
const router = useRouter()
const status = ref({ verified: false, adult: false, verificationAvailable: false })
const form = ref({ realName: '', idCard: '', sensitiveInfoConsent: false })
const saving = ref(false)
const error = ref('')
const normalizeIdCard = () => { form.value.idCard = form.value.idCard.replace(/[^0-9xX]/g, '').toUpperCase() }
const safeRedirect = () => String(route.query.redirect || '').startsWith('/') && !String(route.query.redirect).startsWith('//') ? String(route.query.redirect) : ''
const continueAfterVerified = () => safeRedirect() ? router.replace(safeRedirect()) : router.back()

const load = async () => {
  try { status.value = (await getRealNameStatus()).data || status.value }
  catch (e) { error.value = e.message || '认证状态加载失败' }
}
const submit = async () => {
  if (saving.value) return
  error.value = ''
  if (!form.value.realName) return error.value = '请输入真实姓名'
  if (!/^[1-9]\d{16}[0-9X]$/.test(form.value.idCard)) return error.value = '请输入正确的18位身份证号'
  if (!form.value.sensitiveInfoConsent) return error.value = '请先阅读并同意实名认证授权'
  saving.value = true
  try {
    status.value = (await verifyRealName(form.value)).data || status.value
    form.value.idCard = ''
    if (status.value.verified) continueAfterVerified()
  } catch (e) {
    form.value.idCard = ''
    error.value = e.message || '实名认证失败，请稍后重试'
  } finally { saving.value = false }
}
onMounted(load)
</script>

<style scoped>
.real-name-page{width:min(620px,calc(100% - 28px))}.sub-page-head{display:grid;grid-template-columns:40px 1fr 40px;align-items:center;margin-bottom:14px}.sub-page-head h2{margin:0;text-align:center;font-size:19px}.sub-page-head button{width:40px;height:40px;display:grid;place-items:center;padding:0;background:#fff;border:0;border-radius:50%}.verified-card{padding:30px;color:#0f6e50;background:#ecf9f4;border:1px solid #bce9d7;border-radius:18px;text-align:center}.verified-card h3{margin:12px 0 8px}.verified-card p{font-weight:700}.verified-card small{display:block;color:#54776c;line-height:1.6}.verified-card button{margin-top:20px}.identity-form{border:0;border-radius:17px}.identity-form>.form-item{margin-top:14px}.consent-row{display:flex;align-items:flex-start;gap:9px;margin-top:17px;color:#667085;font-size:12px;line-height:1.65}.consent-row input{margin-top:4px}.consent-row a{color:var(--brand-primary)}.submit-button{width:100%;margin-top:18px}.service-note{color:#b45309;text-align:center;font-size:12px}.privacy-note{margin:14px 0 0;color:#98a2b3;font-size:11px;line-height:1.65}.form-error{padding:11px 14px;color:#b42318;background:#fff1f0;border-radius:10px}
</style>
