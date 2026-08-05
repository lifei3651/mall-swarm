<template>
  <div class="legal-page">
    <header class="legal-header">
      <button type="button" aria-label="返回" @click="goBack"><ChevronLeft :size="24" /></button>
      <h1>{{ title }}</h1>
      <span></span>
    </header>

    <section v-if="type === 'license'" class="info-card">
      <dl>
        <div><dt>运营主体</dt><dd>{{ config.companyName || '暂未配置' }}</dd></div>
        <div><dt>商城品牌</dt><dd>{{ config.brandName || '暂未配置' }}</dd></div>
        <div><dt>经营地址</dt><dd>{{ config.companyAddress || '暂未配置' }}</dd></div>
        <div><dt>ICP备案号</dt><dd>{{ config.icpNumber || '备案信息同步后展示' }}</dd></div>
        <div><dt>公安备案号</dt><dd><a v-if="safePoliceUrl" :href="safePoliceUrl" target="_blank" rel="noopener noreferrer">{{ config.policeRecordNumber || '查看备案' }}</a><span v-else>{{ config.policeRecordNumber || '完成备案后展示' }}</span></dd></div>
      </dl>
      <img v-if="config.businessLicenseUrl && config.showBusinessLicense !== false" class="license-image" :src="config.businessLicenseUrl" alt="营业执照" />
    </section>

    <section v-else-if="type === 'contact'" class="info-card">
      <dl>
        <div><dt>运营主体</dt><dd>{{ config.companyName || '暂未配置' }}</dd></div>
        <div><dt>客服电话</dt><dd><a v-if="config.servicePhone" :href="`tel:${config.servicePhone}`">{{ config.servicePhone }}</a><span v-else>暂未配置</span></dd></div>
        <div><dt>客服邮箱</dt><dd><a v-if="config.serviceEmail" :href="`mailto:${config.serviceEmail}`">{{ config.serviceEmail }}</a><span v-else>暂未配置</span></dd></div>
        <div><dt>联系地址</dt><dd>{{ config.companyAddress || '暂未配置' }}</dd></div>
      </dl>
    </section>

    <section v-else-if="type === 'faq'" class="faq-card">
      <div v-if="faqs.length" class="faq-list">
        <details v-for="(faq, index) in faqs" :key="`${index}-${faq.question}`" class="faq-item">
          <summary><span class="faq-index">Q{{ index + 1 }}</span>{{ faq.question }}</summary>
          <p>{{ faq.answer }}</p>
        </details>
      </div>
      <p v-else class="empty-copy">常见问题正在整理中，如需帮助请联系客服。</p>
    </section>

    <article v-else class="legal-content">{{ content || '该内容暂未配置，请联系客服。' }}</article>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronLeft } from 'lucide-vue-next'
import { getLegalConfig } from '@/api/shop'

const route = useRoute()
const router = useRouter()
const config = ref({})
const type = computed(() => route.params.type)
const titles = { agreement: '用户服务协议', privacy: '隐私政策', 'after-sale': '交易与售后规则', faq: '常见问题', license: '经营资质', contact: '联系客服' }
const title = computed(() => titles[type.value] || '商城说明')
const faqs = computed(() => {
  try {
    const parsed = JSON.parse(config.value.faqs || '[]')
    return Array.isArray(parsed)
      ? parsed.filter((item) => item && String(item.question || '').trim() && String(item.answer || '').trim())
        .map((item) => ({ question: String(item.question).trim(), answer: String(item.answer).trim() }))
      : []
  } catch (_) {
    return []
  }
})
const content = computed(() => ({
  agreement: config.value.userAgreement,
  privacy: config.value.privacyPolicy,
  'after-sale': config.value.afterSalePolicy,
}[type.value] || ''))
const safePoliceUrl = computed(() => /^https?:\/\//i.test(config.value.policeRecordUrl || '') ? config.value.policeRecordUrl : '')

const goBack = () => {
  if (route.query.from === 'register') {
    router.replace({ name: 'Register' })
    return
  }
  router.back()
}

const load = async () => { config.value = (await getLegalConfig()).data || {} }
watch(() => route.params.type, () => window.scrollTo({ top: 0 }))
onMounted(load)
</script>

<style scoped>
.legal-page { min-height: 100vh; max-width: 820px; margin: 0 auto; padding: 0 16px 120px; color: #222; }
.legal-header { height: 58px; display: grid; grid-template-columns: 40px 1fr 40px; align-items: center; border-bottom: 1px solid #eee; background: #fff; position: sticky; top: 0; z-index: 5; }
.legal-header button { border: 0; background: transparent; display: grid; place-items: center; padding: 8px; }
.legal-header h1 { margin: 0; text-align: center; font-size: 18px; }
.legal-content { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.85; font-size: 15px; padding: 22px 4px; }
.info-card { margin-top: 18px; padding: 18px; border-radius: 14px; background: #fff; box-shadow: 0 4px 18px rgba(0,0,0,.06); }
.info-card dl { margin: 0; }
.info-card dl div { display: grid; grid-template-columns: 96px 1fr; gap: 14px; padding: 13px 0; border-bottom: 1px solid #f0f0f0; }
.info-card dl div:last-child { border-bottom: 0; }
.info-card dt { color: #777; }
.info-card dd { margin: 0; overflow-wrap: anywhere; }
.info-card a { color: var(--theme-color, #e7193f); text-decoration: none; }
.license-image { display: block; width: 100%; max-width: 560px; margin: 20px auto 0; border-radius: 8px; }
.faq-card { margin-top: 18px; padding: 8px 18px; border-radius: 14px; background: #fff; box-shadow: 0 4px 18px rgba(0,0,0,.06); }
.faq-item { border-bottom: 1px solid #f0f0f0; }
.faq-item:last-child { border-bottom: 0; }
.faq-item summary { display:flex; align-items:flex-start; gap:10px; padding:16px 0; color:#222; font-size:15px; font-weight:600; line-height:1.55; cursor:pointer; list-style:none; }
.faq-item summary::-webkit-details-marker { display:none; }
.faq-item summary::after { margin-left:auto; color:#98a2b3; content:'＋'; font-size:18px; line-height:1; }
.faq-item[open] summary::after { content:'−'; }
.faq-item p { margin: -3px 0 16px 34px; color:#667085; font-size:14px; line-height:1.8; white-space:pre-line; }
.faq-index { flex:0 0 auto; color:var(--theme-color, #e7193f); font-size:12px; }
.empty-copy { padding:30px 0; color:#98a2b3; text-align:center; }
</style>
