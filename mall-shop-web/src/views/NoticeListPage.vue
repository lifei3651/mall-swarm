<template>
  <div class="page">
    <div class="page-head">
      <h1>公告中心</h1>
      <p>了解最新系统通知、活动资讯和物流动态</p>
    </div>

    <div class="chip-row">
      <button class="chip" :class="{ active: !filterType }" @click="filterType = ''">全部</button>
      <button class="chip" :class="{ active: filterType === 1 }" @click="filterType = 1">📢 系统公告</button>
      <button class="chip" :class="{ active: filterType === 2 }" @click="filterType = 2">🎉 活动公告</button>
      <button class="chip" :class="{ active: filterType === 3 }" @click="filterType = 3">🚚 物流公告</button>
    </div>

    <div v-if="loading" class="empty">加载中</div>
    <div v-else-if="filteredList.length === 0" class="empty">暂无公告</div>
    <div v-else class="notice-list">
      <RouterLink
        v-for="notice in filteredList"
        :key="notice.id"
        :to="`/notices/${notice.id}`"
        class="notice-card"
      >
        <span class="notice-type-icon">{{ typeIconMap[notice.noticeType] ?? '📢' }}</span>
        <div class="notice-info">
          <h3 class="notice-title">{{ notice.title }}</h3>
          <div class="notice-meta">
            <TagBadge :name="typeNameMap[notice.noticeType]" :color="typeColorMap[notice.noticeType]" />
            <span class="notice-time">{{ formatTime(notice.createTime) }}</span>
          </div>
        </div>
        <span class="notice-arrow">›</span>
      </RouterLink>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { listNotices } from '@/api/shop'
import TagBadge from '@/components/TagBadge.vue'

const notices = ref([])
const loading = ref(false)
const filterType = ref('')

const typeIconMap = { 1: '📢', 2: '🎉', 3: '🚚' }
const typeNameMap = { 1: '系统公告', 2: '活动公告', 3: '物流公告' }
const typeColorMap = { 1: 'var(--accent)', 2: '#e253e3', 3: '#10b981' }

const filteredList = computed(() => {
  if (filterType.value === '') return notices.value
  return notices.value.filter((n) => n.noticeType === filterType.value)
})

const formatTime = (time) => {
  if (!time) return ''
  const d = new Date(time)
  return d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

const fetchNotices = async () => {
  loading.value = true
  try {
    const res = await listNotices({ status: 1, pageSize: 50 })
    notices.value = res.data?.list || res.data || []
  } finally {
    loading.value = false
  }
}

onMounted(fetchNotices)
</script>

<style scoped>
.page-head {
  margin-bottom: 20px;
}

.page-head h1 {
  margin: 0 0 6px;
  font-size: 22px;
  color: var(--text);
}

.page-head p {
  margin: 0;
  font-size: 14px;
  color: var(--muted);
}

.notice-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.notice-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  background: var(--card, #fff);
  border-radius: 12px;
  text-decoration: none;
  transition: box-shadow 0.2s;
}

.notice-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.notice-type-icon {
  font-size: 28px;
  flex-shrink: 0;
}

.notice-info {
  flex: 1;
  min-width: 0;
}

.notice-title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.notice-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.notice-time {
  font-size: 12px;
  color: var(--muted);
}

.notice-arrow {
  font-size: 20px;
  color: var(--muted);
  flex-shrink: 0;
}
</style>
