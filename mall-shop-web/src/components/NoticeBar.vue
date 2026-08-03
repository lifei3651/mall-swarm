<template>
  <div v-if="notices.length > 0" class="notice-bar" @click="goDetail">
    <span class="notice-icon">{{ icon }}</span>
    <div class="notice-content">
      <span class="notice-text">{{ notices[currentIndex]?.title }}</span>
    </div>
    <RouterLink to="/notices" class="notice-link" @click.stop>查看全部</RouterLink>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  notices: { type: Array, default: () => [] },
  interval: { type: Number, default: 3000 },
})

const router = useRouter()
const currentIndex = ref(0)
let timer = null

const typeIconMap = { 0: '📢', 1: '🎉', 2: '🚚' }

const icon = computed(() => {
  const type = props.notices[currentIndex.value]?.noticeType
  return typeIconMap[type] ?? '📢'
})

const goDetail = () => {
  const id = props.notices[currentIndex.value]?.id
  if (id) router.push('/notices/' + id)
}

onMounted(() => {
  if (props.notices.length > 1) {
    timer = setInterval(() => {
      currentIndex.value = (currentIndex.value + 1) % props.notices.length
    }, props.interval)
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.notice-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  background: linear-gradient(135deg, #fff7ed, #fef3c7);
  border-radius: 10px;
  margin-bottom: 16px;
  font-size: 13px;
  cursor: pointer;
}

.notice-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.notice-content {
  flex: 1;
  overflow: hidden;
}

.notice-text {
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
}

.notice-link {
  color: var(--accent);
  font-weight: 600;
  text-decoration: none;
  flex-shrink: 0;
  font-size: 12px;
}
</style>
