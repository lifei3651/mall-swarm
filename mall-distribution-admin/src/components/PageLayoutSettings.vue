<template>
  <section class="page-layout-settings" aria-label="页面版型设置">
    <div class="layout-intro"><strong>页面版型</strong><p>品牌、模块内容与版型分开维护。默认三端共用；需要区别时，只覆盖对应页面。</p></div>
    <el-radio-group :model-value="platform" @change="changePlatform" aria-label="配置范围">
      <el-radio-button v-for="target in targets" :key="target.value" :value="target.value">{{ target.label }}</el-radio-button>
    </el-radio-group>
    <p class="scope-note">{{ platform === 'shared' ? '修改共用版型会同步影响所有仍在跟随的端。已单独配置的页面保持不变。' : '“跟随共用”会持续同步共用设置；单独选择只覆盖当前端、当前页面。' }}</p>
    <div class="layout-rows">
      <div v-for="page in pages" :key="page.key" class="layout-row">
        <label :for="'layout-' + page.key">{{ page.label }}</label>
        <el-select :id="'layout-' + page.key" :model-value="selection(page.key)" :aria-label="page.label + '版型'" @change="value => change(page.key, value)">
          <el-option v-if="platform !== 'shared'" value="inherit" label="跟随共用" />
          <el-option v-for="value in PAGE_LAYOUT_OPTIONS[page.key]" :key="value" :value="value" :label="labels[value]" />
        </el-select>
        <el-button text type="primary" @click="$emit('preview', { platform, page: page.key })">预览</el-button>
        <small>当前生效：{{ labels[effective[page.key]] }}</small>
      </div>
    </div>
    <p class="structure-note">版型不改变限购、支付、邀请关系、会员或奖金规则。对齐、间距和操作栏保持统一规范。</p>
  </section>
</template>
<script setup>
import { computed } from 'vue'
import { PAGE_LAYOUT_OPTIONS, normalizePageLayouts, resolvePageLayouts } from '../../../mall-shop-web/src/utils/pageLayouts.js'
const props = defineProps({ modelValue: { type: Object, required: true }, platform: { type: String, default: 'shared' } })
const emit = defineEmits(['update:modelValue', 'preview'])
const platform = computed(() => props.platform)
const targets = [{ value: 'shared', label: '三端共用' }, { value: 'h5', label: 'H5' }, { value: 'mini', label: '小程序' }, { value: 'app', label: 'App' }]
const pages = [{ key: 'home', label: '首页' }, { key: 'category', label: '分类页' }, { key: 'product', label: '商品详情' }]
const labels = { standard: '标准通栏', 'product-focus': '紧凑商品', 'category-focus': '分类导航', 'campaign-feed': '活动信息流', list: '商品列表', directory: '双栏目录', showcase: '品类橱窗', scenario: '场景导购', inset: '留白主图' }
const effective = computed(() => resolvePageLayouts({ pageLayouts: props.modelValue }, platform.value))
const selection = page => platform.value === 'shared' ? props.modelValue.shared[page] : props.modelValue.platforms[platform.value]?.[page] || 'inherit'
const changePlatform = value => { emit('preview', { platform: value, page: 'home' }) }
const change = (page, value) => {
  const next = normalizePageLayouts({ pageLayouts: props.modelValue })
  if (platform.value === 'shared') next.shared[page] = value
  else if (value === 'inherit') delete next.platforms[platform.value][page]
  else next.platforms[platform.value][page] = value
  emit('update:modelValue', next)
  emit('preview', { platform: platform.value, page })
}
</script>
<style scoped>
.page-layout-settings{background:#fff;border:1px solid #e6ebe7;border-radius:8px;padding:20px;color:#202823}
.layout-intro strong{font-size:17px;font-weight:600}.layout-intro p,.scope-note,.structure-note{font-size:13px;color:#647168;line-height:1.7;margin:10px 0 16px}
.layout-rows{border-top:1px solid #e6ebe7}.layout-row{display:grid;grid-template-columns:72px minmax(0,1fr) 50px;gap:8px 12px;align-items:center;padding:16px 0;border-bottom:1px solid #e6ebe7}
.layout-row label{font-size:14px}.layout-row small{grid-column:2/4;color:#647168;font-size:12px}.structure-note{margin-bottom:0}
.page-layout-settings :deep(.el-radio-group){display:flex;flex-wrap:wrap;gap:4px}.page-layout-settings :deep(.el-radio-button__inner){border-radius:4px;border:1px solid #dce3df;box-shadow:none}
@media(max-width:700px){.page-layout-settings{padding:16px}.layout-row{grid-template-columns:64px minmax(0,1fr) 44px;gap:8px}}
</style>
