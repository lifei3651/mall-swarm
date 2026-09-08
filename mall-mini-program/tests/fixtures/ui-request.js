// Native screenshot fixtures only. Copied by build-ui-qa, never imported by production.
const member = { id:'132', nickname:'本地界面验收', username:'PreviewOnly', phone:'13800000000', status:1 }
const cover = '/api/shop/media/images/59c08d53d27a4a919192cbcc229ea6c4.png'
const products = [
  {id:'1',productName:'轻奢焕活礼盒',subtitle:'本地界面样例，不发送真实交易',salePrice:99,marketPrice:129,stock:10,status:1,salesCount:61,categoryName:'护理套装',coverUrl:cover,purchaseLimit:3},
  {id:'27',productName:'长标题与长金额界面测试：多行商品名称不应挤掉价格及加购按钮',subtitle:'仅本地排版检查',salePrice:1234567.89,stock:10,status:1,salesCount:123456,categoryName:'健康生活',coverUrl:cover,purchaseLimit:3},
  {id:'28',productName:'售罄商品界面样例',salePrice:128,stock:0,status:1,salesCount:0,categoryName:'健康生活',coverUrl:cover},
  {id:'29',productName:'图片失败样例',salePrice:0,stock:10,status:1,categoryName:'护理套装',coverUrl:''}
]
const categories = ['护理套装','健康生活','尊享套装','复购专区','长分类名称换行验收'].map((categoryName,index)=>({id:String(index+1),categoryName,iconUrl:cover}))
const detail = (status = 2) => ({ order:{ id:'13201',orderNo:'LOCAL-QA-13201',status,payType:'BALANCE',payAmount:99,totalAmount:99,freightAmount:0,createTime:'2026-09-07T12:00:00',payTime:status===0?null:'2026-09-07T12:01:00',deliveryCompany:'本地样例快递',deliveryNo:'LOCAL-PACKAGE-ONE',receiverName:'本地收货人',receiverPhone:'13800000000',receiverAddress:'仅本地界面验收地址，不发起真实配送'},
  items:[{id:'13202',productId:'1',skuId:'11',productName:'轻奢焕活礼盒',skuName:'标准装',quantity:2,price:49.5,productCover:cover}],
  afterSaleWindowMode:'RECEIVED',afterSaleSelfServiceEnabled:true,autoReceiveEnabled:true,autoReceiveDeadline:'2099-01-01T12:00:00',
  shipments:[{id:'501',deliveryCompany:'本地快递一',deliveryNo:'LOCAL-PACKAGE-ONE',shipmentQuantity:1,deliveryTime:'2026-09-07T12:05:00'},{id:'502',deliveryCompany:'本地快递二',deliveryNo:'LOCAL-PACKAGE-TWO',shipmentQuantity:1}],afterSales:[] })
const ticket = {id:'91',ticketNo:'LOCAL-QA-91',subject:'本地订单咨询',content:'仅界面验收，不发送真实工单。长文字会自然换行并保留完整信息。',type:'CONSULTATION',status:'WAITING_MEMBER',orderId:'13201',orderNo:'LOCAL-QA-13201',createTime:'2026-09-07T12:00:00',lastReplyTime:'2026-09-07T13:00:00'}
const message = {id:'81',title:'订单进度提醒（本地）',content:'您的订单已发货，此消息仅为本地验收样例。',category:'ORDER_LOGISTICS',read:false,readStatus:0,targetType:'ORDER',targetId:'13201',createTime:'2026-09-07T12:00:00'}
const room = {room:{id:'71',title:'本地直播间',subtitle:'商品讲解样例',coverUrl:cover,status:2,commentEnabled:1,scheduledStartTime:'2099-01-01T12:00:00',watchUrl:''},roomState:'LIVE',products:[products[0]]}
module.exports = async ({url,method='GET',params={},data={}}) => {
  // Quote is a read-only calculation even though its production transport is POST.
  if (method === 'POST' && url === '/shop/orders/freight-quote') return {productAmount:99,freightAmount:0,payAmount:99}
  if (url.endsWith('/purchase-limit/check')) return {allowed:Number(data.quantity ?? params.quantity)<=1,purchaseLimit:3,remainingQuantity:1,message:'本地限购样例：最多还可购买1件'}
  if (method !== 'GET') throw Error('本地验收禁止写入，未发送真实请求')
  const qa = wx.getStorageSync('qa-settings') || {}
  if (qa.failure && url === qa.failure) throw Error('本地故障演练：加载失败，请重新加载')
  if (url === '/shop/home') return {brandName:'灵启商城 · 本地验收',logoUrl:'/assets/lingqi-logo-mark.png',themeColor:'#e7193f',productTemplate:'retail-red',brandCultureEnabled:true,
    displayConfig:{layoutTemplate:qa.layout || 'standard',categoryGuideTemplate:qa.guide || 'directory',liveSquareEnabled:true,newArrivalsEnabled:true,homeModules:['notice','category','products'].map((type,index)=>({type,sort:index+1,enabled:true})),extraConfigJson:qa.modules?JSON.stringify({categoryGuideModules:qa.modules}):'{}'},
    categoryList:categories,banners:[],notices:[{id:'61',title:'本地界面验收，不发送真实交易'}],newArrivals:[products[0]],liveRooms:[room]}
  if (url === '/shop/products') { const list = qa.empty ? [] : products.filter(p=>(!params.keyword || p.productName.includes(params.keyword))&&(!params.categoryName||p.categoryName===params.categoryName)); return {list,total:list.length,totalPage:1,pageNum:1} }
  if (url === '/shop/categories') return categories
  if (url.endsWith('/purchase-limit/check')) return {allowed:Number(params.quantity)<=1,purchaseLimit:3,remainingQuantity:1,message:'本地限购样例：最多还可购买1件'}
  if (/^\/shop\/products\/\d+\/reviews$/.test(url)) return {page:{list:[{id:'51',reviewerName:'本地样例',rating:5,content:'评价长文字排版，仅本地验收。',createTime:'2026-09-07'}],total:1,totalPage:1},reviewCount:1,averageRating:5,star5Count:1,canReview:true,reviewableOrderItems:[{orderItemId:'13202',orderId:'13201'}]}
  if (/^\/shop\/products\/\d+$/.test(url)) {const product = products.find(p=>p.id===url.split('/').pop())||products[0];return {product:{...product,gallery:[cover],description:'本地商品图文介绍',serviceTags:[{title:'七天无理由',description:'符合商城规则且商品完好时，可在规定期限内申请。'}]},skus:[{id:'11',skuName:'标准装',salePrice:product.salePrice,stock:product.stock,status:1,skuCode:'LOCAL-11'}]} }
  if (url === '/shop/auth/me') return member
  if (url.endsWith('/runtime')) return {enabled:true,phoneAuthorizationEnabled:true,privacyConsentVersion:require('../config/runtime').PRIVACY_CONSENT_VERSION}
  if (url.endsWith('/member-capabilities')) return {ready:true,membershipActive:true,membershipLevel:2,membershipLabel:'VIP会员',canInvite:true,inviteCode:'TEST0133',canViewWallet:true,canViewPayoutRecords:true}
  if (url.includes('/inviter-preview/')) return {valid:true,nickname:'本地邀请人'}
  if (url.endsWith('/order-summary')) return {pendingPayment:2,pendingShipment:1,pendingReceipt:2,pendingReview:1,afterSale:2}
  if (url === '/shop/pay/config') return {wechatPayEnabled:false,balancePayEnabled:true}
  if (url === '/payment/checkVerify') return {needVerify:false}
  if (url === '/shop/orders') return {list:qa.empty?[]:[detail(0),{...detail(2),order:{...detail(2).order,id:'13203'}}],total:qa.empty?0:2,pageNum:1,totalPage:1}
  if (/^\/shop\/orders\/\d+$/.test(url)) { const d=detail(qa.orderStatus ?? 2); if(qa.saleStatus!==undefined)d.afterSales=[{id:'41',orderId:'13201',status:qa.saleStatus,applyType:qa.saleType||2,afterSaleNo:'LOCAL-AFTERSALE-41',refundAmount:49.5,auditRemark:'本地审核说明，请补充凭证',returnAddress:'本地退货地址',items:[{id:'42',orderItemId:'13202',refundQuantity:1,productName:'轻奢焕活礼盒'}]}];return d }
  if (url.endsWith('/tracking')) return [{shipmentId:'501',deliveryCompany:'本地快递一',deliveryNo:'LOCAL-PACKAGE-ONE',configured:true,statusText:'运输中',events:[{eventTime:'2026-09-07T13:00:00',description:'本地示例：包裹正在运输',location:'测试站点'}]}]
  if (url === '/shop/addresses') return qa.empty?[]:[{id:'31',receiverName:'本地收货人',receiverPhone:'13800000000',province:'湖南省',city:'长沙市',district:'岳麓区',detailAddress:'本地测试地址，不真实配送',isDefault:1}]
  if (url === '/shop/wallet/summary') return {balance:'18.21',hasPaymentPassword:!qa.firstPassword,distributionActivated:true,realNameVerified:true,adultVerified:true,paymentPasswordLocked:false,canWithdraw:true}
  if (url.endsWith('/bonus-summary')) return {issuedBonus:'61.12',pendingBonus:'24.50'}
  if (url.endsWith('/withdrawals')) return qa.empty?[]:[{id:'21',withdrawAmount:18.21,withdrawType:2,status:2,statusText:'待确认收款',createTime:'2026-09-07'}]
  if (url.endsWith('/flows')) return qa.empty?[]:[{id:'11',remark:'奖金发放（本地样例）',changeType:1,amount:18.21,balanceBefore:0,balanceAfter:18.21,createTime:'2026-09-07'}]
  if (url === '/shop/real-name/status') return {verified:false,verificationAvailable:false,statusText:'仅本地检查，未连接实名服务'}
  if (url === '/shop/messages/preferences/sms') return {available:false,enabled:false,statusText:'未连接短信通道'}
  if (url.endsWith('/subscriptions')) return qa.empty?[]:[{templateId:'LOCAL-ONLY-NO-DELIVERY',title:'订单发货提醒',availableGrants:0}]
  if (url.endsWith('/unread')) return {total:3,categories:{ORDER_LOGISTICS:3}}
  if (url === '/shop/messages') return {list:qa.empty?[]:[message],total:qa.empty?0:1,totalPage:1,pageNum:1}
  if (/^\/shop\/messages\/\d+$/.test(url)) return message
  if (url === '/shop/service-tickets') return {list:qa.empty?[]:[ticket],total:qa.empty?0:1,totalPage:1,pageNum:1}
  if (/^\/shop\/service-tickets\/\d+$/.test(url)) return {ticket,replies:[{id:'92',senderType:'ADMIN',content:'本地客服回复样例，请核对订单。',createTime:'2026-09-07T13:00:00'}]}
  if (url === '/shop/notices') return qa.empty?[]:[{id:'61',title:'商城服务公告（本地）',noticeType:1,createTime:'2026-09-07',content:'本地公告内容，仅用于排版检查。'}]
  if (url === '/shop/notices/61') return {id:'61',title:'商城服务公告（本地）',noticeType:1,createTime:'2026-09-07',content:'本地公告内容，仅用于排版检查。'}
  if (url === '/shop/flash-sales') return qa.empty?[]:[{activity:{id:'101',productId:'1',activityName:'本地限时样例',status:1,perUserLimit:1,availableStock:3,flashPrice:19,startTime:'2020-01-01T00:00:00',endTime:'2099-01-01T00:00:00'},product:products[0],activityState:'ACTIVE'}]
  if (url === '/shop/new-arrivals') return qa.empty?[]:products
  if (url === '/shop/brand-culture') return {enabled:true,title:'品牌文化',subtitle:'本地品牌介绍样例',content:'完整说明应清楚呈现，不挤压操作按钮。',coverUrl:cover,detailImages:[cover]}
  if (url === '/shop/live-rooms') return qa.empty?[]:[room,{...room,room:{...room.room,id:'72',status:1},roomState:'UPCOMING'}]
  if (/^\/shop\/live-rooms\/\d+$/.test(url)) return room
  if (url === '/shop/live-reservations') return []
  if (url.endsWith('/comments')) return [{id:'73',displayName:'本地观众',content:'本地评论样例',createTime:'2026-09-07'}]
  if (url === '/shop/live-studio/me') return qa.noAnchor?{}:{anchor:{anchor:{displayName:'本地授权主播',companyName:'本地测试主体'}},canStart:true,statusMessage:'本地权限样例',rooms:qa.empty?[]:[room,{...room,room:{...room.room,id:'72',status:1},roomState:'UPCOMING'}]}
  if (url === '/shop/legal-config') return {brandName:'本地验收商城',companyName:'本地演示主体',userAgreement:'本地协议样例；正式内容来自商城后台。',privacyPolicy:'本地隐私说明样例；不提交真实资料。',afterSalePolicy:'本地售后规则样例。',faqs:[{question:'如何联系客服？',answer:'可从我的页面进入在线客服或客服工单。'}]}
  if (url === '/captcha') throw Error('本地验收不生成可用验证码；只检查表单与失败恢复')
  throw Error('本地验收尚未配置该接口样例：'+url)
}
