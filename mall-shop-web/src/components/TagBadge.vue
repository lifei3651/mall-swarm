<template>
  <span class="tag-badge" :style="{ background: bgColor, color: textColor }">
    {{ name }}
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  name: { type: String, required: true },
  color: { type: String, default: 'var(--accent)' },
})

const bgColor = computed(() => {
  if (props.color.startsWith('#') || props.color.startsWith('rgb')) {
    return props.color + '18'
  }
  return 'color-mix(in srgb, ' + props.color + ' 12%, transparent)'
})

const textColor = computed(() => props.color)
</script>

<style scoped>
.tag-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.6;
  white-space: nowrap;
}
</style>
