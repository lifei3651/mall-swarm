<template>
  <main class="culture-page">
    <header class="culture-header">
      <button type="button" aria-label="返回" @click="goBack"><ArrowLeft :size="21" /></button>
      <strong>品牌文化</strong>
      <RouterLink to="/" aria-label="返回首页"><Home :size="20" /></RouterLink>
    </header>
    <section v-if="loading" class="culture-state"><LoaderCircle class="spin" :size="28" />正在加载品牌故事…</section>
    <section v-else-if="error" class="culture-state"><CircleAlert :size="30" /><strong>{{ error }}</strong><button type="button" @click="load">重新加载</button></section>
    <section v-else-if="!culture.enabled" class="culture-state"><BookOpen :size="32" /><strong>品牌文化页暂未开放</strong><RouterLink to="/">返回商城首页</RouterLink></section>
    <article v-else class="culture-content">
      <div v-if="culture.coverUrl" class="culture-cover"><img :src="culture.coverUrl" :alt="culture.title || '品牌文化封面'" @error="applyImageFallback" /></div>
      <div class="culture-title">
        <img v-if="culture.logoUrl" :src="culture.logoUrl" :alt="`${culture.brandName || '商城'} Logo`" @error="applyImageFallback" />
        <span v-else>{{ String(culture.brandName || '品牌').slice(0, 1) }}</span>
        <div><small>{{ culture.brandName || '品牌故事' }}</small><h1>{{ culture.title || '品牌文化' }}</h1><p v-if="culture.subtitle">{{ culture.subtitle }}</p></div>
      </div>
      <div class="culture-body">{{ culture.content }}</div>
    </article>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, BookOpen, CircleAlert, Home, LoaderCircle } from 'lucide-vue-next'
import { getBrandCulture } from '@/api/shop'
import { applyImageFallback } from '@/utils/imageFallback'

const router = useRouter()
const culture = ref({ enabled: false })
const loading = ref(false)
const error = ref('')
const goBack = () => window.history.length > 1 ? router.back() : router.push('/')
const load = async () => {
  loading.value = true
  error.value = ''
  try { culture.value = (await getBrandCulture()).data || { enabled: false } }
  catch (e) { error.value = e?.message || '品牌文化页暂时加载失败' }
  finally { loading.value = false }
}
onMounted(load)
</script>

<style scoped>
.culture-page{min-height:100vh;padding-bottom:82px;background:var(--shop-page-bg,#f5f6f8)}.culture-header{position:sticky;top:0;z-index:20;height:58px;display:grid;grid-template-columns:48px 1fr 48px;align-items:center;padding:0 max(8px,calc((100% - 1040px)/2));background:rgba(255,255,255,.95);border-bottom:1px solid #eceff1;backdrop-filter:blur(12px)}.culture-header>strong{text-align:center}.culture-header button,.culture-header>a{width:40px;height:40px;display:grid;place-items:center;color:#344054;background:transparent;border:0;border-radius:50%}.culture-header>a{justify-self:end}.culture-content{width:min(1040px,calc(100% - 32px));overflow:hidden;margin:22px auto;background:#fff;border:1px solid #e7ebf0;border-radius:26px;box-shadow:0 18px 50px rgba(15,23,42,.06)}.culture-cover{height:360px;overflow:hidden;background:#e8ecf1}.culture-cover img{width:100%;height:100%;object-fit:cover}.culture-title{display:grid;grid-template-columns:72px minmax(0,1fr);gap:18px;align-items:center;padding:32px 48px 22px}.culture-title>img,.culture-title>span{width:72px;height:72px;display:grid;place-items:center;object-fit:contain;color:#fff;background:var(--brand-primary);border-radius:20px;font-size:26px;font-weight:900}.culture-title small{color:var(--brand-primary);font-size:12px;font-weight:800;letter-spacing:1px}.culture-title h1{margin:5px 0 6px;color:#1d2939;font-size:34px;line-height:1.25}.culture-title p{margin:0;color:#667085;line-height:1.7}.culture-body{padding:0 48px 52px;color:#344054;font-size:16px;line-height:2;white-space:pre-line}.culture-state{width:min(1040px,calc(100% - 32px));min-height:420px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;margin:22px auto;color:#98a2b3;background:#fff;border-radius:24px}.culture-state strong{color:#475467}.culture-state button,.culture-state>a{padding:9px 16px;color:#fff;background:var(--brand-primary);border:0;border-radius:999px;text-decoration:none}.spin{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:760px){.culture-header{padding:0 8px}.culture-content,.culture-state{width:calc(100% - 16px);margin:10px auto;border-radius:18px}.culture-cover{height:210px}.culture-title{grid-template-columns:54px minmax(0,1fr);gap:12px;padding:22px 18px 16px}.culture-title>img,.culture-title>span{width:54px;height:54px;border-radius:15px;font-size:20px}.culture-title h1{font-size:25px}.culture-title p{font-size:13px}.culture-body{padding:0 18px 32px;font-size:15px;line-height:1.9}}
</style>
