<template>
  <main class="not-found">
    <div class="code">404</div>
    <h1>页面不存在</h1>
    <p>链接可能已失效，或当前版本没有这个页面。</p>
    <el-button type="primary" @click="router.replace('/dashboard')">返回工作台</el-button>
  </main>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<style scoped>
.not-found { min-height: 70vh; display: grid; place-content: center; justify-items: center; color: #606266; }
.code { font-size: 72px; font-weight: 700; color: #409eff; line-height: 1; }
h1 { margin: 20px 0 8px; color: #303133; }
p { margin: 0 0 24px; }
</style>
