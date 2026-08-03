<template>
  <div class="page sub-page security-page">
    <header class="sub-page-head">
      <button type="button" aria-label="返回" @click="router.back()"><ArrowLeft :size="22" /></button>
      <h2>支付安全</h2><span></span>
    </header>

    <section class="security-status" :class="{ ready: wallet.hasPaymentPassword }">
      <span><ShieldCheck :size="28" /></span>
      <div>
        <strong>{{ wallet.hasPaymentPassword ? '支付密码已设置' : '请先设置支付密码' }}</strong>
        <p>{{ wallet.hasPaymentPassword ? '余额支付、转账和提现均受独立密码保护' : '首次交易前必须完成设置' }}</p>
      </div>
    </section>

    <section class="security-actions">
      <RouterLink to="/profile/security/change-login-password" class="security-action-btn">
        <span class="action-icon login-icon"><LockKeyhole :size="24" /></span>
        <span class="action-label">修改登录密码</span>
        <ChevronRight :size="18" />
      </RouterLink>
      <RouterLink :to="wallet.hasPaymentPassword ? '/profile/security/change-payment-password' : '/profile/security/change-payment-password'" class="security-action-btn">
        <span class="action-icon pay-icon"><KeyRound :size="24" /></span>
        <span class="action-label">{{ wallet.hasPaymentPassword ? '修改交易密码' : '设置交易密码' }}</span>
        <i v-if="!wallet.hasPaymentPassword" class="action-badge">待设置</i>
        <ChevronRight :size="18" />
      </RouterLink>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ChevronRight, KeyRound, LockKeyhole, ShieldCheck } from 'lucide-vue-next'
import { getWalletSummary } from '@/api/shop'

const router = useRouter()
const wallet = ref({ hasPaymentPassword: false, paymentPasswordLocked: false })

onMounted(async () => {
  try { wallet.value = (await getWalletSummary()).data || wallet.value } catch {}
})
</script>

<style scoped>
.security-page { width: min(620px, calc(100% - 28px)); }
.sub-page-head { display: grid; grid-template-columns: 40px 1fr 40px; align-items: center; margin-bottom: 14px; }
.sub-page-head h2 { margin: 0; text-align: center; font-size: 19px; }
.sub-page-head button { width: 40px; height: 40px; display: grid; place-items: center; padding: 0; background: #fff; border: 0; border-radius: 50%; }
.security-status { display: grid; grid-template-columns: 48px minmax(0, 1fr); align-items: center; gap: 12px; padding: 17px; color: #9a3412; background: #fff6e8; border: 1px solid #fed7aa; border-radius: 15px; }
.security-status.ready { color: #0f6e50; background: #ecf9f4; border-color: #bce9d7; }
.security-status > span { width: 48px; height: 48px; display: grid; place-items: center; background: rgba(255,255,255,.7); border-radius: 50%; }
.security-status strong { font-size: 15px; }
.security-status p { margin: 5px 0 0; opacity: .76; font-size: 12px; line-height: 1.5; }
.security-actions { display: grid; gap: 10px; margin-top: 14px; }
.security-action-btn { display: flex; align-items: center; gap: 12px; padding: 16px; background: #fff; border-radius: 15px; box-shadow: 0 4px 14px rgba(31,41,55,.05); }
.action-icon { width: 48px; height: 48px; display: grid; place-items: center; border-radius: 14px; }
.login-icon { color: #3867d6; background: #eef3ff; }
.pay-icon { color: #0f8a62; background: #eaf8f3; }
.action-label { flex: 1; font-size: 15px; font-weight: 600; color: var(--ink); }
.action-badge { padding: 3px 8px; color: #c2410c; background: #fff2e8; border-radius: 999px; font-size: 11px; font-style: normal; }
.security-action-btn > svg:last-child { color: #a1a8b0; }
</style>
