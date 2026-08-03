<template>
  <div class="page">
    <div v-if="loading" class="empty">加载中</div>
    <div v-else-if="!notice" class="empty">公告不存在</div>
    <div v-else class="detail-panel">
      <RouterLink to="/notices" class="back-link">← 返回公告列表</RouterLink>
      <h1 class="detail-title">{{ notice.title }}</h1>
      <div class="detail-meta">
        <TagBadge :name="typeNameMap[notice.noticeType]" :color="typeColorMap[notice.noticeType]" />
        <span class="detail-time">{{ formatTime(notice.createTime) }}</span>
      </div>
      <div class="detail-content">{{ notice.content }}</div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getNotice } from '@/api/shop'
import TagBadge from '@/components/TagBadge.vue'

const route = useRoute()
const notice = ref(null)
const loading = ref(false)

const typeNameMap = { 1: '系统公告', 2: '活动公告', 3: '物流公告' }
const typeColorMap = { 1: 'var(--accent)', 2: '#e253e3', 3: '#10b981' }

const formatTime = (time) => {
  if (!time) return ''
  const d = new Date(time)
  return d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

const fetchNotice = async () => {
  loading.value = true
  try {
    const res = await getNotice(route.params.id)
    notice.value = res.data?.notice || res.data
  } finally {
    loading.value = false
  }
}

onMounted(fetchNotice)
</script>

<style scoped>
.back-link {
  display: inline-block;
  color: var(--accent);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 20px;
}

.back-link:hover {
  text-decoration: underline;
}

.detail-panel {
  max-width: 720px;
}

.detail-title {
  margin: 0 0 12px;
  font-size: 22px;
  font-weight: 700;
  color: var(--text);
  line-height: 1.4;
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.detail-time {
  font-size: 13px;
  color: var(--muted);
}

.detail-content {
  font-size: 15px;
  line-height: 1.8;
  color: var(--text);
  white-space: pre-wrap;
  word-break: break-word;
  padding: 20px;
  background: var(--card, #fff);
  border-radius: 12px;
}
</style>
