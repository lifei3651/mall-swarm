<template>
  <div class="settings-shell">
    <aside class="settings-navigation" aria-label="设置分类">
      <h1>设置中心</h1>
      <form class="settings-search" role="search" @submit.prevent="search">
        <el-input v-model="keyword" aria-label="搜索设置" placeholder="搜索设置" clearable />
        <el-button native-type="submit" :icon="Search" aria-label="查找设置" />
      </form>
      <nav>
        <router-link v-for="group in groups" :key="group.key" :to="{ path: '/settings', query: { group: group.key } }" :class="{ active: group.key === activeGroup && !route.query.q }" :aria-current="group.key === activeGroup && !route.query.q ? 'page' : undefined">
          <span>{{ group.title }}</span><el-icon><ArrowRight /></el-icon>
        </router-link>
      </nav>
      <p class="settings-nav-note">仅显示已授权的设置</p>
    </aside>
    <main class="settings-body"><slot /></main>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, ArrowRight } from '@element-plus/icons-vue'
import { useAppStore } from '@/store'
import { SETTINGS_GROUPS, settingsEntriesFor, settingsGroupFor } from '@/utils/settingsCatalog'
const route = useRoute(), router = useRouter(), store = useAppStore()
const entries = computed(() => settingsEntriesFor(store))
const groups = computed(() => SETTINGS_GROUPS.filter((group) => entries.value.some((entry) => entry.group === group.key)))
const activeGroup = computed(() => settingsGroupFor(route, entries.value))
const keyword = ref(String(route.query.q || ''))
watch(() => route.query.q, (value) => { keyword.value = String(value || '') })
const search = () => router.push({ path: '/settings', query: keyword.value.trim() ? { q: keyword.value.trim() } : {} })
</script>
<style scoped lang="scss">
.settings-shell { display:grid; grid-template-columns:200px minmax(0,1fr); min-height:calc(100vh - 112px); background:#fff; border:1px solid var(--admin-border); border-radius:8px; }
.settings-navigation { padding:26px 16px; border-right:1px solid var(--admin-border); }
h1 { margin:0 8px 22px; font-size:20px; font-weight:600; }
.settings-search { display:flex; gap:6px; margin-bottom:20px; }
.settings-search :deep(.el-input) { min-width:0; }
.settings-search :deep(.el-button) { width:32px; padding:0; flex-shrink:0; }
nav { display:grid; gap:4px; }
nav a { display:flex; align-items:center; justify-content:space-between; min-height:44px; padding:10px 12px; color:#4b5563; text-decoration:none; border-radius:6px; font-size:14px; }
nav a:hover { background:#f6f7f9; }
nav a.active { background:var(--admin-primary-soft); color:var(--admin-primary); font-weight:600; }
nav .el-icon { font-size:12px; }
.settings-nav-note { margin:24px 8px 0; color:var(--admin-muted); font-size:12px; line-height:1.7; }
.settings-body { min-width:0; padding:28px 32px; }
.settings-body :deep(.page-container) { padding:0; min-height:0; border:0; }
.settings-body :deep(.el-card) { box-shadow:none!important; }
.settings-body :deep(.el-form-item__content) { min-width:0; }
.settings-body :deep(.el-radio-group) { display:flex; flex-wrap:wrap; row-gap:8px; }
@media(max-width:1200px) { .settings-shell { grid-template-columns:176px minmax(0,1fr); }.settings-body { padding:24px; } }
@media(max-width:900px) { .settings-shell { grid-template-columns:1fr; }.settings-navigation { padding:16px; border-right:0; border-bottom:1px solid var(--admin-border); }h1 { margin:0 0 12px; }.settings-search { max-width:360px; margin-bottom:12px; }nav { grid-template-columns:repeat(4,minmax(0,1fr)); }nav a { padding:8px; justify-content:center; }nav .el-icon,.settings-nav-note { display:none; }.settings-body { padding:20px; } }
@media(max-width:540px) { nav { grid-template-columns:repeat(2,minmax(0,1fr)); }.settings-body { padding:16px; } }
</style>
