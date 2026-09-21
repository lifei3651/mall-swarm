const request=require('../../utils/request')
const auth=require('../../utils/auth')
const session=require('../../utils/session')
const theme=require('../../utils/theme')
const format=require('../../utils/format')
const feedback=require('../../utils/feedback')
const statusLabels={AVAILABLE:'未使用',RESERVED:'待支付占用',USED:'已使用',EXPIRED:'已过期'}
function card(c){return {...c,id:format.identifier(c.id),claimId:format.identifier(c.claimId),key:format.identifier(c.claimId)||format.identifier(c.id),amountText:format.money(c.amount),minimumText:format.money(c.minimumAmount),startsText:String(c.startsAt||'').replace('T',' ').slice(0,16),endsText:String(c.endsAt||'').replace('T',' ').slice(0,16),statusText:statusLabels[c.status]||'',businessText:(c.businessTypes||[]).map(t=>t==='NORMAL'?'普通商城':'其他专区专用（当前商城不可用）').join(' / ')}}
Page({
  data:{...theme.pageData(),tab:'mine',rows:[],page:1,pages:1,loading:false,error:'',claiming:'',productTarget:null,products:[],productsLoading:false,productPage:0,productPages:1,productError:''},
  onLoad(options={}){this.keys={};if(options.tab==='catalog')this.setData({tab:'catalog'})},
  onShow(){this.inactive=false;const owner=session.getToken();if(this.owner!==owner){this.owner=owner;this.keys={};this.setData({rows:[],page:1,products:[],productTarget:null,productsLoading:false})}theme.apply(this);if(auth.requireLogin('/pages/coupons/index'))this.load()},
  onHide(){this.inactive=true;this.generation=(this.generation||0)+1;this.productGeneration=(this.productGeneration||0)+1;this.setData({productTarget:null})},
  onUnload(){this.onHide()},
  onPullDownRefresh(){this.setData({page:1});Promise.resolve(this.load()).finally(()=>wx.stopPullDownRefresh())},
  switchTab(e){const tab=e.currentTarget.dataset.tab;if(!['mine','catalog'].includes(tab)||tab===this.data.tab)return;this.setData({tab,page:1,rows:[]});this.load()},
  async load(){
    const generation=this.generation=(this.generation||0)+1,owner=session.getToken()
    this.setData({loading:true,error:''})
    try{const r=await request({url:this.data.tab==='mine'?'/shop/coupons/mine':'/shop/coupons',params:{pageNum:this.data.page,pageSize:20}});if(this.inactive||generation!==this.generation||owner!==session.getToken())return;this.setData({rows:(r.list||[]).map(card),pages:Math.max(1,Number(r.totalPage||1))})}
    catch(e){if(!this.inactive&&generation===this.generation)feedback.update(this,{error:e.message||'优惠券读取失败'})}
    finally{if(generation===this.generation)this.setData({loading:false})}
  },
  turn(e){if(this.data.loading)return;const page=this.data.page+Number(e.currentTarget.dataset.delta);if(page<1||page>this.data.pages)return;this.setData({page});this.load()},
  async claim(e){
    const id=format.identifier(e.currentTarget.dataset.id),c=this.data.rows.find(r=>r.id===id)
    if(this.data.claiming||!c||!c.usable)return
    const owner=session.getToken();this.setData({claiming:id,error:''});this.keys=this.keys||{}
    const key=owner+':'+id
    if(!this.keys[key])this.keys[key]=`MINI-COUPON-${Date.now()}-${Math.random().toString(36).slice(2,12)}`
    try{await request({url:`/shop/coupons/${id}/claim`,method:'POST',data:{requestId:this.keys[key]}});if(this.inactive||owner!==session.getToken())return;delete this.keys[key];feedback.success('优惠券已领取');await this.load()}
    catch(error){if(!this.inactive&&owner===session.getToken())feedback.update(this,{error:error.message||'领取失败，请重试'})}
    finally{this.setData({claiming:''})}
  },
  showProducts(e){const id=format.identifier(e.currentTarget.dataset.id),c=this.data.rows.find(r=>r.id===id);if(!c)return;this.productGeneration=(this.productGeneration||0)+1;this.setData({productTarget:c,products:[],productsLoading:false,productPage:0,productPages:1});this.loadProducts()},
  closeProducts(){this.productGeneration=(this.productGeneration||0)+1;this.setData({productTarget:null,productsLoading:false})},
  async loadProducts(){
    const c=this.data.productTarget;if(!c||this.data.productsLoading)return
    const generation=this.productGeneration=(this.productGeneration||0)+1,owner=session.getToken()
    this.setData({productsLoading:true,productError:''})
    try{const r=await request({url:`/shop/coupons/${c.id}/products`,params:{pageNum:this.data.productPage+1,pageSize:20}});if(this.inactive||generation!==this.productGeneration||owner!==session.getToken())return;this.setData({products:this.data.products.concat((r.list||[]).map(p=>({...p,id:format.identifier(p.id),coverUrl:format.mediaUrl(p.coverUrl),priceText:format.money(p.salePrice)}))),productPage:this.data.productPage+1,productPages:Number(r.totalPage||1)})}
    catch(e){if(!this.inactive&&generation===this.productGeneration)feedback.update(this,{productError:e.message||'适用商品读取失败'})}
    finally{if(generation===this.productGeneration)this.setData({productsLoading:false})}
  },
  product(e){const id=format.identifier(e.currentTarget.dataset.id);if(id)wx.navigateTo({url:`/pages/product/index?id=${encodeURIComponent(id)}`})},
  stopTap(){}
})
