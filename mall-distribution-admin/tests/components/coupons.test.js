import { beforeEach, describe, expect, it, vi } from 'vitest'
vi.unmock('element-plus')
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElMessageBox } from 'element-plus'
import Coupons from '../../src/views/shop/coupons.vue'
const api=vi.hoisted(()=>({listCoupons:vi.fn(),couponProducts:vi.fn(),couponMerchants:vi.fn(),saveCoupon:vi.fn(),changeCouponStatus:vi.fn(),store:{userInfo:{merchantId:null},hasPermission:()=>true}}))
vi.mock('@/api/coupons',()=>api)
vi.mock('@/store',()=>({useAppStore:()=>api.store}))
const row=()=>({id:71,title:'隔离优惠券',status:'DRAFT',version:2,scopeType:'PRODUCTS',productIdsJson:'[1]',businessTypesJson:'["NORMAL"]',amount:10,minimumAmount:100,merchantPercent:0,bonusBasis:'NET',refundRule:'FULL_RETURN',startsAt:'2026-09-13T00:00:00',endsAt:'2026-10-13T00:00:00',totalCount:10,perMemberLimit:1})
const mounted=()=>mount(Coupons,{global:{plugins:[ElementPlus]}})
beforeEach(()=>{vi.restoreAllMocks();vi.clearAllMocks();globalThis.ResizeObserver=class {observe(){} unobserve(){} disconnect(){}};api.store.userInfo.merchantId=null;api.listCoupons.mockResolvedValue({data:{list:[row()],total:1}});api.couponProducts.mockResolvedValue({data:[]});api.couponMerchants.mockResolvedValue({data:{list:[],totalPage:1}});api.saveCoupon.mockResolvedValue({data:{}})})
describe('优惠券配置影响确认',()=>{
  it('未明确选择规则不能保存，取消影响确认也不写入',async()=>{
    const w=mounted();await flushPromises();w.vm.edit();await w.vm.save();expect(api.saveCoupon).not.toHaveBeenCalled()
    w.vm.edit(row());vi.spyOn(ElMessageBox,'confirm').mockRejectedValue('cancel');await w.vm.save();expect(api.saveCoupon).not.toHaveBeenCalled();expect(w.vm.visible).toBe(true);w.unmount()
  })
  it('确认绑定当时配置和版本，等待期间不能重复提交或偷换内容',async()=>{
    const w=mounted();await flushPromises();w.vm.edit(row());let confirm
    vi.spyOn(ElMessageBox,'confirm').mockImplementation(()=>new Promise(resolve=>{confirm=resolve}))
    const save=w.vm.save();expect(w.vm.busy).toBe(true);w.vm.form.amount=99;await w.vm.save();confirm();await save
    expect(api.saveCoupon).toHaveBeenCalledTimes(1);expect(api.saveCoupon.mock.calls[0][1]).toMatchObject({amount:10,version:2,impactConfirmed:true,merchantId:null});w.unmount()
  })
  it('已发行条款只读，商家不能借入口发行平台券',async()=>{
    const w=mounted();await flushPromises();w.vm.edit({...row(),status:'PUBLISHED'});expect(w.vm.readonly).toBe(true);await w.vm.save();expect(api.saveCoupon).not.toHaveBeenCalled();w.unmount()
    api.store.userInfo.merchantId=88;const merchant=mounted();await flushPromises();merchant.vm.edit(row());expect(merchant.vm.canWrite).toBe(false);expect(merchant.vm.readonly).toBe(true);merchant.unmount()
  })
})
