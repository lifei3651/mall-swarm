<template>
  <section class="settings-index">
    <header class="settings-heading">
      <h2>{{ query ? '搜索设置' : group?.title || '设置中心' }}</h2>
      <p>{{ query ? `“${query}” · ${entries.length} 个可用入口${matchedPlanned.length ? `，${matchedPlanned.length} 项待接入` : ''}` : group?.description }}</p>
    </header>
    <div v-if="entries.length" class="setting-list">
      <router-link v-for="entry in entries" :key="entry.path + (entry.hash || '')" class="setting-row" :to="{ path: entry.path, hash: entry.hash }">
        <div><span v-if="query" class="result-category">{{ SETTINGS_GROUPS.find(item => item.key === entry.group)?.title }}</span><h3>{{ entry.title }}</h3><p>{{ entry.description }}</p></div>
        <span class="setting-action">{{ entry.editor || entry.hash ? '配置' : '进入' }}<el-icon><ArrowRight /></el-icon></span>
      </router-link>
    </div>
    <el-empty v-else-if="!matchedPlanned.length" :description="query ? '没有匹配的设置，请换一个关键词' : '当前账号没有可访问的设置'" :image-size="64" />
    <p v-if="!query && group?.key === 'marketing'" class="settings-footnote">优惠券发行前需确认承担方、商品范围、商家结算和团队奖金影响；进入设置中心不会自动发券。</p>
    <p v-if="!query && group?.key === 'integration'" class="settings-footnote">此处不表示支付、短信或实名认证通道已开通；相关凭据应由技术人员在服务端安全配置，不在页面公开展示。</p>
  </section>
</template>
<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import { useAppStore } from '@/store'
import { SETTINGS_GROUPS, settingsEntriesFor, settingsGroupFor } from '@/utils/settingsCatalog'
const route = useRoute(), store = useAppStore()
const query = computed(() => String(route.query.q || '').trim())
const allEntries = computed(() => settingsEntriesFor(store))
const group = computed(() => SETTINGS_GROUPS.find((item) => item.key === settingsGroupFor(route, allEntries.value)))
const entries = computed(() => query.value ? settingsEntriesFor(store, query.value) : allEntries.value.filter((item) => item.group === group.value?.key))
const matchedPlanned = computed(() => [])
</script>
<style scoped>
.settings-heading { padding-bottom:24px; border-bottom:1px solid var(--admin-border); }
h2 { margin:0 0 10px; font-size:22px; font-weight:600; line-height:1.4; }
p { margin:0; color:var(--admin-muted); font-size:13px; line-height:1.8; }
.setting-row { display:flex; justify-content:space-between; align-items:center; gap:24px; min-height:94px; padding:22px 0; border-bottom:1px solid var(--admin-border); color:var(--admin-text); text-decoration:none; }
.setting-row:hover { background:#fafbfc; }.setting-row:focus-visible { outline:2px solid var(--admin-primary); outline-offset:3px; }
.setting-row>div { min-width:0; }h3 { margin:0 0 7px; font-size:15px; font-weight:600; line-height:1.5; }
.setting-action { display:flex; align-items:center; gap:10px; flex-shrink:0; color:var(--admin-primary); font-size:13px; }
.setting-action .el-icon { font-size:12px; }
.result-category { display:block; color:var(--admin-primary); font-size:12px; margin-bottom:8px; }
.planned-settings { margin-top:32px; }.planned-settings>h3 { font-size:16px; }
.planned-row { display:flex; align-items:center; justify-content:space-between; gap:20px; padding:18px 0; border-bottom:1px solid var(--admin-border); }
.planned-row strong { display:block; font-size:14px; font-weight:500; margin-bottom:6px; }.planned-row .el-tag { flex-shrink:0; }
.settings-footnote { margin-top:24px; padding:16px; background:#f7f8fa; border-radius:6px; }
@media(max-width:540px) { .setting-row { gap:12px; }.planned-row { align-items:flex-start; } }
</style>
