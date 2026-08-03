<template>
  <div class="china-region-select">
    <select :value="province" class="field" @change="changeProvince($event.target.value)">
      <option value="">请选择省</option>
      <option v-for="item in pcaTextArr" :key="item.value" :value="item.value">{{ item.label }}</option>
    </select>
    <select :value="city" class="field" :disabled="!province" @change="changeCity($event.target.value)">
      <option value="">请选择市</option>
      <option v-for="item in cityOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
    </select>
    <select :value="district" class="field" :disabled="!city" @change="changeDistrict($event.target.value)">
      <option value="">请选择区/县</option>
      <option v-for="item in districtOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
    </select>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { pcaTextArr } from 'element-china-area-data'

const props = defineProps({ modelValue: { type: Array, default: () => [] } })
const emit = defineEmits(['update:modelValue', 'change'])

const province = computed(() => props.modelValue?.[0] || '')
const city = computed(() => props.modelValue?.[1] || '')
const district = computed(() => props.modelValue?.[2] || '')
const provinceNode = computed(() => pcaTextArr.find((item) => item.value === province.value))
const cityOptions = computed(() => provinceNode.value?.children || [])
const cityNode = computed(() => cityOptions.value.find((item) => item.value === city.value))
const districtOptions = computed(() => cityNode.value?.children || [])

const update = (value) => { emit('update:modelValue', value); emit('change', value) }
const changeProvince = (value) => update(value ? [value] : [])
const changeCity = (value) => update(value ? [province.value, value] : [province.value])
const changeDistrict = (value) => update(value ? [province.value, city.value, value] : [province.value, city.value])
</script>

<style scoped>
.china-region-select { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.field:disabled { background: #f4f6f5; color: #9ca3af; cursor: not-allowed; }
@media (max-width: 680px) { .china-region-select { grid-template-columns: 1fr; } }
</style>
