<template>
  <div class="merchant-home">
    <section class="welcome-card">
      <div>
        <span class="eyebrow">MERCHANT WORKSPACE</span>
        <h1>{{ merchantName }}</h1>
        <p>这里仅显示本商户可以处理的商品、订单、售后和货款，不包含平台管理功能。</p>
      </div>
      <el-tag type="success" effect="light" size="large">商家后台</el-tag>
    </section>

    <el-alert
      v-if="quickActions.length === 0"
      title="账号已开通，暂未分配业务权限"
      description="请联系平台管理员开通商品、订单或货款权限。账号仍可安全登录和修改密码。"
      type="warning"
      :closable="false"
      show-icon
    />

    <section v-else class="workspace-section">
      <div class="section-heading">
        <div>
          <h2>今日工作</h2>
          <p>入口会随账号权限自动显示，不会出现无权操作的平台页面。</p>
        </div>
      </div>
      <div class="action-grid">
        <button
          v-for="item in quickActions"
          :key="item.path"
          type="button"
          class="action-card"
          @click="router.push(item.path)"
        >
          <span class="action-icon"><el-icon><component :is="item.icon" /></el-icon></span>
          <span class="action-content">
            <strong>{{ item.title }}</strong>
            <small>{{ item.description }}</small>
          </span>
          <span v-if="item.value !== null" class="action-value">{{ item.value }}</span>
          <el-icon class="action-arrow"><ArrowRight /></el-icon>
        </button>
      </div>
    </section>

    <section class="scope-card">
      <div>
        <el-icon><Lock /></el-icon>
        <span>
          <strong>数据范围已锁定</strong>
          <small>商品、订单、售后与资金接口均按当前商户身份读取。</small>
        </span>
      </div>
      <div>
        <el-icon><Key /></el-icon>
        <span>
          <strong>权限按岗位分配</strong>
          <small>未授权入口不会显示，直接输入平台地址也会返回本工作台。</small>
        </span>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Goods, Key, Lock, OfficeBuilding, Service, ShoppingCart, User, Wallet } from '@element-plus/icons-vue'
import { useAppStore } from '@/store'
import { getAdminOrderWorkSummary } from '@/api/shop'
import { listMerchantAccounts } from '@/api/merchant'

const router = useRouter()
const store = useAppStore()
const orderSummary = ref({ pendingShipment: 0, afterSale: 0 })
const account = ref({})

const merchantName = computed(() => (
  store.userInfo?.merchantName
  || store.userInfo?.nickname
  || store.userInfo?.username
  || '商户工作台'
))

const money = (value) => `¥${Number(value || 0).toFixed(2)}`

const quickActions = computed(() => [
  {
    title: '入驻资料与认证',
    description: '填写经营主体、收款与开票资料并提交平台认证',
    path: '/merchant/profile',
    icon: OfficeBuilding,
    value: null,
  },
  store.hasPermission('merchant:staff-manage') && {
    title: '子账号与权限',
    description: '按商品、订单、售后和财务岗位分配权限',
    path: '/merchant/staff',
    icon: User,
    value: null,
  },
  store.hasPermission('shop:product') && {
    title: '商品管理',
    description: '维护商品、规格、库存与上下架申请',
    path: '/shop/products',
    icon: Goods,
    value: null,
  },
  store.hasPermission('shop:order') && {
    title: '待发货订单',
    description: '查看订单并完成发货处理',
    path: '/shop/orders',
    icon: ShoppingCart,
    value: orderSummary.value.pendingShipment,
  },
  store.hasPermission('shop:order') && {
    title: '售后与客服',
    description: '处理售后订单和客户工单',
    path: '/shop/service-tickets',
    icon: Service,
    value: orderSummary.value.afterSale,
  },
  store.hasPermission('finance:read') && {
    title: '货款账户',
    description: '查看待结算、可提现与资金流水',
    path: '/audit/merchant-finance',
    icon: Wallet,
    value: money(account.value.availableAmount),
  },
].filter(Boolean))

onMounted(async () => {
  const tasks = []
  if (store.hasPermission('shop:order')) {
    tasks.push(getAdminOrderWorkSummary().then((res) => {
      orderSummary.value = {
        pendingShipment: Number(res.data?.pendingShipment || 0),
        afterSale: Number(res.data?.afterSale || 0),
      }
    }))
  }
  if (store.hasPermission('finance:read')) {
    tasks.push(listMerchantAccounts().then((res) => {
      account.value = res.data?.[0] || {}
    }))
  }
  await Promise.allSettled(tasks)
})
</script>

<style scoped>
.merchant-home{display:flex;flex-direction:column;gap:22px;max-width:1180px;margin:0 auto;padding:26px}.welcome-card{display:flex;align-items:center;justify-content:space-between;gap:24px;padding:30px 32px;color:#fff;border-radius:20px;background:linear-gradient(135deg,#10264c 0%,#1859a6 68%,#2877ce 100%);box-shadow:0 16px 36px rgba(19,72,139,.2)}.eyebrow{font-size:12px;font-weight:700;letter-spacing:1.5px;color:#a9d3ff}.welcome-card h1{margin:8px 0 6px;font-size:28px}.welcome-card p{margin:0;color:#d8e9ff;line-height:1.7}.workspace-section,.scope-card{padding:24px;border:1px solid #e4eaf2;border-radius:18px;background:#fff;box-shadow:0 8px 24px rgba(28,52,84,.06)}.section-heading{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:18px}.section-heading h2{margin:0;color:#1e293b;font-size:20px}.section-heading p{margin:6px 0 0;color:#7b8799;font-size:14px}.action-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.action-card{display:grid;grid-template-columns:auto 1fr auto auto;align-items:center;gap:14px;min-height:96px;padding:18px;text-align:left;border:1px solid #dfe7f1;border-radius:14px;background:#f9fbfe;cursor:pointer;transition:transform .18s ease,border-color .18s ease,box-shadow .18s ease}.action-card:hover{transform:translateY(-2px);border-color:#79aef0;box-shadow:0 10px 24px rgba(32,91,162,.12)}.action-card:focus-visible{outline:3px solid rgba(64,158,255,.3);outline-offset:2px}.action-icon{display:grid;place-items:center;width:46px;height:46px;color:#1767ba;font-size:23px;border-radius:13px;background:#e8f2ff}.action-content{display:flex;flex-direction:column;gap:6px;min-width:0}.action-content strong{color:#25324b;font-size:16px}.action-content small{overflow:hidden;color:#7c8798;font-size:13px;text-overflow:ellipsis;white-space:nowrap}.action-value{color:#165faf;font-size:22px;font-weight:750}.action-arrow{color:#9aa6b6}.scope-card{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.scope-card>div{display:flex;align-items:flex-start;gap:12px;padding:16px;border-radius:12px;background:#f6f8fb}.scope-card .el-icon{margin-top:2px;color:#2b75bd;font-size:20px}.scope-card span{display:flex;flex-direction:column;gap:5px}.scope-card strong{color:#344054;font-size:14px}.scope-card small{color:#7b8799;line-height:1.6}@media(max-width:760px){.merchant-home{padding:16px}.welcome-card{align-items:flex-start;flex-direction:column;padding:24px}.action-grid,.scope-card{grid-template-columns:1fr}.action-card{grid-template-columns:auto 1fr auto}.action-value{grid-column:2}.action-arrow{grid-column:3;grid-row:1/3}.action-content small{white-space:normal}}
</style>
