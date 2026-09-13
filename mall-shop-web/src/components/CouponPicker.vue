<template>
  <div class="coupon-picker-mask" @click.self="$emit('close')" @keydown.esc="$emit('close')">
    <section ref="sheet" class="coupon-picker" role="dialog" aria-modal="true" aria-label="选择优惠券" @keydown.tab="trapFocus">
      <header><h3>选择优惠券</h3><button ref="closeButton" type="button" @click="$emit('close')">关闭</button></header>
      <p>每次结算限用一张，优惠以适用商品金额计算。</p>
      <button type="button" class="coupon-choice" :class="{selected:!selected}" @click="$emit('choose',null)"><span>不使用优惠券</span><span>{{!selected?'✓':''}}</span></button>
      <button v-for="c in options" :key="c.claimId" type="button" class="coupon-choice" :class="{selected:String(c.claimId)===String(selected)}" :disabled="!c.usable" @click="$emit('choose',c.claimId)"><span><strong>{{c.title}} · 减 ¥{{c.amount}}</strong><small>{{c.scopeLabel}} · 满 ¥{{c.minimumAmount}} 可用</small><small v-if="c.reason">{{c.reason}}</small></span><span>{{String(c.claimId)===String(selected)?'✓':'○'}}</span></button>
      <p v-if="!options.length">暂无已领取的优惠券</p>
      <RouterLink to="/profile/coupons?tab=catalog" @click="$emit('close')">去领券中心 ›</RouterLink>
    </section>
  </div>
</template>
<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
defineProps({options:{type:Array,default:()=>[]},selected:{type:[String,Number],default:null}})
defineEmits(['choose','close'])
const closeButton=ref(null),sheet=ref(null);let previousFocus,previousOverflow
function trapFocus(event){const nodes=sheet.value?.querySelectorAll('button:not(:disabled),a[href]');if(!nodes?.length)return;const first=nodes[0],last=nodes[nodes.length-1];if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus()}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus()}}
onMounted(()=>{previousFocus=document.activeElement;previousOverflow=document.body.style.overflow;document.body.style.overflow='hidden';closeButton.value?.focus()})
onBeforeUnmount(()=>{document.body.style.overflow=previousOverflow;previousFocus?.focus?.()})
</script>
<style scoped>
.coupon-picker-mask { --brand: var(--brand-primary, #e7193f); }
.coupon-picker-mask{position:fixed;inset:0;z-index:130;display:flex;align-items:flex-end;justify-content:center;background:#0006}.coupon-picker{width:100%;max-width:680px;max-height:76vh;overflow:auto;background:#fff;padding:20px 16px calc(24px + env(safe-area-inset-bottom));border-radius:18px 18px 0 0;box-sizing:border-box}.coupon-picker header{display:flex;justify-content:space-between;align-items:center}.coupon-picker h3{font-size:17px;margin:0}.coupon-picker p{font-size:13px;color:#858b94;line-height:1.7}.coupon-picker header button{border:0;background:none;min-height:44px;font-size:14px}.coupon-choice{display:flex;width:100%;box-sizing:border-box;justify-content:space-between;align-items:center;gap:12px;margin:10px 0;padding:16px;text-align:left;border:1px solid #e6e8eb;background:#fff;border-radius:10px;color:#242b35;font-size:14px;white-space:normal}.coupon-choice span:first-child{min-width:0;overflow-wrap:anywhere}.coupon-choice.selected{border-color:var(--brand);color:var(--brand)}.coupon-choice small{display:block;margin-top:6px;font-size:12px;color:#858b94;line-height:1.5}.coupon-choice:disabled{background:#f7f8f9;color:#999}.coupon-picker a{display:block;padding:16px 0;text-align:center;color:var(--brand);font-size:14px}
</style>
