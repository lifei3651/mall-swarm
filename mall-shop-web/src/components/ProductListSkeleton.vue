<template>
  <div
    class="product-loading-skeleton"
    :class="`is-${variant}`"
    aria-label="商品加载中"
    aria-live="polite"
    aria-busy="true"
  >
    <div v-for="index in count" :key="index" class="skeleton-card">
      <span class="skeleton-image"></span>
      <span class="skeleton-copy">
        <i></i>
        <i></i>
        <i></i>
      </span>
    </div>
  </div>
</template>

<script setup>
defineProps({
  count: { type: Number, default: 4 },
  variant: { type: String, default: 'grid', validator: (value) => ['grid', 'list'].includes(value) },
})
</script>

<style scoped>
.product-loading-skeleton { pointer-events: none; }
.skeleton-card,.skeleton-image,.skeleton-copy i {
  background: linear-gradient(100deg,#eef0f2 25%,#fafafa 45%,#eef0f2 65%);
  background-size: 220% 100%;
  animation: product-skeleton-shimmer 1.2s infinite linear;
}
.is-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:14px; }
.is-grid .skeleton-card { overflow:hidden; padding-bottom:16px; background:#fff; border-radius:13px; }
.skeleton-image { display:block; aspect-ratio:1; border-radius:12px; }
.skeleton-copy { display:block; }
.skeleton-copy i { display:block; width:calc(100% - 24px); height:13px; margin:12px 12px 0; border-radius:7px; }
.skeleton-copy i:nth-child(2) { width:72%; }
.skeleton-copy i:last-child { width:52%; }
.is-list { padding:0; }
.is-list .skeleton-card { min-height:228px; display:grid; grid-template-columns:214px 1fr; gap:22px; padding:18px 22px; background:#fff; border-bottom:1px solid #eff1f2; }
.is-list .skeleton-copy { align-self:center; }
.is-list .skeleton-copy i { width:82%; height:18px; margin:16px 0 0; }
.is-list .skeleton-copy i:nth-child(2) { width:66%; }
.is-list .skeleton-copy i:last-child { width:45%; }
@keyframes product-skeleton-shimmer { to { background-position-x:-220%; } }
@media (max-width:760px) {
  .is-grid { grid-template-columns:repeat(2,minmax(0,1fr)); gap:8px; }
  .is-list .skeleton-card { min-height:144px; grid-template-columns:116px 1fr; gap:10px; padding:12px 10px; }
  .is-list .skeleton-copy i { height:13px; margin-top:12px; }
}
@media (max-width:390px) {
  .is-list .skeleton-card { grid-template-columns:105px 1fr; gap:6px; padding:10px 8px; }
}
</style>
