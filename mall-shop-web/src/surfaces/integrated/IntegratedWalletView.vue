<template>
  <WalletView>
    <template #primary-action>
      <RouterLink class="wallet-action-link" to="/profile/wallet/transfer">
        <Send :size="21" /><span>余额转账</span>
      </RouterLink>
    </template>
  </WalletView>
</template>

<script setup>
import { Send } from 'lucide-vue-next'
import WalletView from '@/views/WalletView.vue'
</script>

<style scoped>
.wallet-action-link{min-height:72px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6px;color:#59616d;background:#fff;border:1px solid transparent;border-radius:14px;font-size:12px;text-decoration:none}.wallet-action-link:focus-visible{color:var(--brand-primary);border-color:var(--brand-primary);background:var(--brand-primary-soft)}
</style>
