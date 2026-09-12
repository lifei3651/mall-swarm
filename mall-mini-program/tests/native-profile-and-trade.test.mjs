import { runMiniScript } from './helpers/run-mini-script.mjs'
import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const root = fileURLToPath(new URL('..', import.meta.url))
const plain = (value) => JSON.parse(JSON.stringify(value))
const eligibleWallet = { balance: '18.21', distributionActivated: true, realNameVerified: true, adultVerified: true, hasPaymentPassword: true, paymentPasswordLocked: false }
function environment({ respond = () => ({}), consent = true, realFeedback = false, wx: overrides = {} } = {}) {
  const storage = new Map([['mall_mini_access_token', 'owner-session'], ['mall_mini_member', { id: '1' }]])
  const calls = [], uploads = [], downloads = [], routes = [], removed = [], cache = new Map()
  let definition, component, currentPage, privacyListener
  const wx = {
    getStorageSync: (key) => storage.get(key), setStorageSync: (key, val) => storage.set(key, plain(val)), removeStorageSync: (key) => storage.delete(key),
    requirePrivacyAuthorize: ({ success, fail }) => consent ? success({}) : fail({ errMsg: 'deny' }),
    onNeedPrivacyAuthorization: (callback) => { privacyListener = callback },
    chooseAddress: ({ success }) => success({ userName: '测试收货人', telNumber: '13800000000', provinceName: '湖南省', cityName: '长沙市', countyName: '岳麓区', detailInfo: '测试街1号' }),
    uploadFile: (options) => { uploads.push(options); options.success({ statusCode: 200, data: JSON.stringify({ code: 200, data: '/api/shop/media/member-avatar/12/avatar.jpg' }) }) },
    downloadFile: (options) => { downloads.push(options); options.success({ statusCode: 200, tempFilePath: 'wxfile://tmp-avatar' }) },
    getFileSystemManager: () => ({ unlink: ({ filePath }) => removed.push(filePath) }),
    navigateTo: ({ url }) => routes.push(url), showToast() {}, showModal: ({ success }) => success({ confirm: true }), setNavigationBarTitle() {},
    ...overrides
  }
  function load(relative, parent = root) {
    if (relative.endsWith('.json')) return JSON.parse(readFileSync(resolve(parent, relative), 'utf8'))
    const file = resolve(parent, relative.endsWith('.js') ? relative : `${relative}.js`)
    if (file === resolve(root, 'utils/request.js')) return async (options) => { calls.push(plain(options)); return respond(options) }
    if (file === resolve(root, 'utils/auth.js')) return { requireLogin: () => Boolean(storage.get('mall_mini_access_token')) }
    if (file === resolve(root, 'utils/theme.js')) return { pageData: () => ({}), apply() {}, sync() {} }
    if (cache.has(file)) return cache.get(file).exports
    const module = { exports: {} }; cache.set(file, module)
    const run = realFeedback ? vm.runInNewContext : runMiniScript
    run(readFileSync(file, 'utf8'), {
      module, exports: module.exports, require: (id) => load(id, dirname(file)), wx,
      Page: (value) => { definition = value }, Component: (value) => { component = value },
      getCurrentPages: () => currentPage ? [currentPage] : [], setTimeout, clearTimeout
    }, { filename: file })
    return module.exports
  }
  function page(name) {
    load(`pages/${name}/index.js`)
    currentPage = { ...definition, data: plain(definition.data), setData(patch) {
      for (const [key, value] of Object.entries(plain(patch))) {
        const parts = key.split('.'); let target = this.data
        for (const part of parts.slice(0, -1)) target = target[part]
        target[parts.at(-1)] = value
      }
    } }
    return currentPage
  }
  return { load, page, storage, calls, uploads, downloads, routes, removed, wx,
    privacyListener: () => privacyListener, component: () => component }
}

test('消息首页五项分类不横滚，提醒设置保留关闭短信的入口', () => {
  const e=environment(), page=e.page('messages');
  assert.deepEqual(plain(page.data.categories).map(x=>x.label),['全部','订单','售后','资金','服务']);
  assert.equal(page.data.settingsVisible,false); page.toggleSettings(); assert.equal(page.data.settingsVisible,true);
  page.setData({smsBusy:true}); page.toggleSettings(); assert.equal(page.data.settingsVisible,true);
  page.setData({smsBusy:false}); page.toggleSettings(); assert.equal(page.data.settingsVisible,false);
  const view=readFileSync(resolve(root,'pages/messages/index.wxml'),'utf8');
  assert.doesNotMatch(view,/scroll-x|class="message-head"/); assert.match(view,/wx:if="\{\{settingsVisible\}\}"/);
  assert.match(view,/!smsPreference.available && !smsPreference.enabled/);
})

test('钱包记录按真实收支方向展示，空余额不能伪装为0元', async () => {
  const e = environment({ respond: ({ url }) => url.endsWith('/summary') ? { balance: '8.00' } : [
    { id: '1', changeType: 1, amount: '10', balanceBefore: '0', balanceAfter: '10' },
    { id: '2', changeType: 2, amount: '2', balanceBefore: '10', balanceAfter: '8' }
  ] }), page = e.page('wallet')
  await page.onShow()
  assert.equal(page.data.balance, '8.00'); assert.equal(page.data.flows[0].amount, '+10.00'); assert.equal(page.data.flows[1].amount, '-2.00')
  page.onHide(); assert.equal(page.data.flows.length, 0)
  const invalid = environment({ respond: ({ url }) => url.endsWith('/summary') ? { balance: null } : [] }).page('wallet')
  await invalid.onShow(); assert.equal(invalid.data.balance, '--'); assert.match(invalid.data.error, /信息不完整/)
})

test('奖金与余额分别读取，待发放不计入余额且未知金额不显示假零', async () => {
  const e=environment({respond:({url})=>url.endsWith('/summary') ? {balance:'18.21'} : url.endsWith('/bonus-summary') ? {issuedBonus:'61.12',pendingBonus:'24.50'} : []}), page=e.page('wallet')
  await page.onShow(); await new Promise(done=>setImmediate(done))
  assert.equal(page.data.balance,'18.21'); assert.equal(page.data.issuedBonus,'61.12'); assert.equal(page.data.pendingBonus,'24.50')
  page.onHide(); assert.equal(page.data.pendingBonus,'--')
  const bad=environment({respond:({url})=>url.endsWith('/summary') ? {balance:18.21} : []}).page('wallet')
  await bad.onShow(); await new Promise(done=>setImmediate(done)); assert.equal(bad.data.issuedBonus,'--'); assert.match(bad.data.bonusError,/暂不可用/)
})

test('本人会员等级只接受已激活且1至8的服务端等级，不从余额或邀请关系推导', async () => {
  for(const [membershipActive,membershipLevel,expected] of [[true,2,2],[true,99,0],[false,8,0]]) {
    const e=environment({respond:()=>({membershipActive,membershipLevel,canInvite:false,canViewWallet:true,canViewPayoutRecords:true})})
    const result=await e.load('utils/member-capabilities').load(); assert.equal(result.membershipLevel,expected)
    if(expected===2) assert.equal(result.membershipLabel,'VIP会员')
  }
})

test('上传域名错误可定位，已上传但下载失败必须说明已保存而非再次上传', async () => {
  const e=environment({wx:{uploadFile:({fail})=>fail({errMsg:'uploadFile:fail url not in domain list'}),downloadFile:({fail})=>fail({errMsg:'downloadFile:fail url not in domain list'})}})
  const avatar=e.load('utils/member-avatar')
  await assert.rejects(avatar.upload('wxfile://tmp-132'),/uploadFile 合法域名/)
  await assert.rejects(avatar.load('/api/shop/media/member-avatar/132/avatar.jpg',true),/已保存.*downloadFile 合法域名/)
  assert.equal(await avatar.load('/api/shop/media/member-avatar/132/avatar.jpg'),avatar.fallback)
})

test('提现保留资格、实名、成年与支付密码条件，不创建无法通过校验的新申请', async () => {
  for(const key of ['distributionActivated','realNameVerified','adultVerified','hasPaymentPassword']) {
    const e=environment({respond:({url})=>url.endsWith('/summary')?{...eligibleWallet,[key]:false}:{id:'132',phone:'13800000000'}}), page=e.page('withdraw')
    await page.onShow(); assert.ok(page.data.blockReason); await page.submit(); assert.equal(e.calls.length,2)
  }
})

test('提现校验金额/密码/验证码，仅提交微信本人提现且不可连点重复，离页清空秘密', async () => {
  let release; const result=new Promise(resolve=>{release=resolve})
  const e=environment({respond:({url,method})=>method==='POST'?result:url.endsWith('/summary')?eligibleWallet:{id:'132',phone:'13800000000'}}), page=e.page('withdraw')
  e.wx.redirectTo=({url})=>e.routes.push(url)
  await page.onShow(); await page.submit(); assert.equal(e.calls.length,2)
  const form={withdrawAmount:'20',accountName:'测试用户',paymentPassword:'123456',smsCode:'654321'}
  page.setData({form}); await page.submit(); assert.equal(e.calls.length,2)
  page.setData({form:{...form,withdrawAmount:'18.21'}})
  const first=page.submit(); await new Promise(done=>setImmediate(done)); await page.submit()
  assert.equal(e.calls.length,3); const call=e.calls.at(-1)
  assert.equal(call.url,'/shop/wallet/withdrawals'); assert.equal(call.data.withdrawType,2)
  assert.equal(call.data.withdrawAmount,'18.21'); assert.match(call.idempotencyKey,/^MINI-WITHDRAW-/)
  release({id:'1'}); await first; assert.equal(page.data.form.paymentPassword,'')
  assert.equal(e.routes.at(-1),'/pages/payout/index?history=1'); page.onHide(); assert.equal(page.data.form.smsCode,'')
})

test('会员身份独立核对，普通账号有余额不变会员；能力慢请求或失败不挡钱包', async () => {
  for (const mode of ['ordinary', 'slow', 'failed']) {
    const e = environment({ respond: ({ url }) => {
      if (url.endsWith('/member-capabilities')) {
        if (mode === 'slow') return new Promise(() => {})
        if (mode === 'failed') throw new Error('offline')
        return { membershipActive: false, canInvite: false, inviteCode: null, canViewWallet: true, canViewPayoutRecords: true }
      }
      return url.endsWith('/summary') ? { balance: '888' } : []
    } }), page = e.page('wallet')
    await page.onShow()
    assert.equal(page.data.loading, false)
    assert.equal(page.data.balance, '888.00')
    assert.equal(page.data.error, '')
    await new Promise((done) => setImmediate(done))
    if (mode === 'ordinary') assert.equal(page.data.membershipLabel, '购物账号')
    if (mode === 'failed') assert.match(page.data.membershipError, /暂未核对/)
    page.onHide(); assert.equal(page.data.membershipLabel, '')
  }
})

test('已到账提现通知能定位历史单据，不错误调用确认收款', async () => {
  const e = environment({ respond: () => [
    { id: '9007199254740995', status: 3, withdrawType: 2, withdrawAmount: '200' },
    { id: '2', status: 2, withdrawType: 2, withdrawAmount: '100' }
  ] }), page = e.page('payout')
  page.onLoad({ id: '9007199254740995', history: '1' }); await page.onShow()
  assert.equal(page.data.rows.length, 1); assert.equal(page.data.rows[0].canConfirm, false)
  await page.confirm({ currentTarget: { dataset: { id: '9007199254740995' } } })
  assert.equal(e.calls.length, 1); assert.equal(e.calls[0].method, undefined)
  const message = e.page('message-detail')
  message.owner = 'owner-session'
  message.setData({ message: { targetType: 'WITHDRAWAL', targetId: '9007199254740995' } }); message.openTarget()
  assert.equal(e.routes.at(-1), '/pages/payout/index?history=1&id=9007199254740995')
  message.setData({ message: { targetType: 'WALLET' } }); message.openTarget()
  assert.equal(e.routes.at(-1), '/pages/wallet/index')
})

test('物流查询只接受当前订单，未配置服务不伪造运输进度', async () => {
  const e = environment({ respond: () => [{ deliveryNo: 'TEST-001', configured: false, events: [] }] }), page = e.page('order-detail')
  page.setData({ rows: [{ order: { id: '12' } }] })
  await page.loadTracking({ currentTarget: { dataset: { id: '99' } } }); assert.equal(e.calls.length, 0)
  await page.loadTracking({ currentTarget: { dataset: { id: '12' } } })
  assert.equal(e.calls[0].url, '/shop/orders/12/tracking'); assert.equal(e.calls[0].method, undefined)
  assert.match(page.data.trackingRows[0].statusText, /尚未配置/)
  page.selectCarrier({ detail: { value: '0' } }); assert.equal(page.data.deliveryCompany, '顺丰速运')
})

test('直接购买独立于原购物车：原有2件，直接买1件仍为1件且不改变勾选', async () => {
  const e = environment({ respond: ({ method }) => method === 'POST' ? { allowed: true } : { product: { id: '10', status: 1, salePrice: 9, stock: 5 }, skus: [] } }), cart = e.load('utils/cart'), session = e.load('utils/session')
  cart.add({ productId: '10', quantity: 2, salePrice: 9 }); const before = JSON.stringify(cart.list())
  const page = e.page('product'); page.productId = '10'
  page.setData({ loading: false, soldOut: false, product: { id: '10', status: 1, salePrice: 9 }, quantity: 1 })
  await page.buyNow()
  assert.equal(cart.directItems()[0].quantity, 1)
  assert.equal(JSON.stringify(cart.list()), before)
  assert.equal(e.routes[0], '/pages/checkout/index?direct=1')
  cart.clearDirectCheckout(); assert.equal(cart.directItems().length, 0); assert.equal(JSON.stringify(cart.list()), before)
  cart.beginDirectCheckout({ productId: '10', quantity: 1 })
  session.clearSession(); assert.equal(cart.directItems().length, 0)
})

test('切换账号不能继承直接购买暂存，暂存不写本地存储', () => {
  const e = environment(), cart = e.load('utils/cart')
  cart.beginDirectCheckout({ productId: '10', quantity: 1 })
  assert.equal(e.storage.size, 2)
  e.storage.set('mall_mini_access_token', 'another-session')
  assert.equal(cart.directItems().length, 0)
})

test('拒绝隐私授权不调用微信地址接口，更不提交商城接口', async () => {
  let invoked = 0
  const e = environment({ consent: false, wx: { chooseAddress() { invoked++ } } })
  const page = e.page('address'); page.setData({ loading: false })
  await page.importWechatAddress()
  assert.equal(invoked, 0); assert.equal(e.calls.length, 0)
  assert.match(page.data.importMessage, /未获得授权/)
  assert.equal(page.data.form.receiverName, '')
})

test('微信地址选择后自动新增并返回列表，不覆盖现有/默认地址', async () => {
  const saved={id:'3',receiverName:'测试收货人',receiverPhone:'13800000000',province:'湖南省',city:'长沙市',district:'岳麓区',detailAddress:'测试街1号',isDefault:0}
  const dialogs = [], tips = []
  const e = environment({realFeedback:true,respond:({method})=>method==='POST'?saved:[{id:'2',isDefault:1},saved],wx:{showModal:options=>{dialogs.push(options);options.success({confirm:true})},showToast:options=>tips.push(options)}}), page = e.page('address')
  page.setData({ loading: false, rows: [{ id: '2', isDefault: 1 }] })
  await page.importWechatAddress()
  assert.equal(page.data.showForm,false); assert.equal(page.data.importing,false); assert.equal(page.data.saving,false)
  assert.equal(page.data.rows[0].isDefault, 1); assert.equal(page.data.rows[1].id,'3')
  const writes=e.calls.filter(x=>x.method==='POST'); assert.equal(writes.length,1)
  assert.deepEqual(writes[0].data,{receiverName:'测试收货人',receiverPhone:'13800000000',province:'湖南省',city:'长沙市',district:'岳麓区',detailAddress:'测试街1号',isDefault:0})
  assert.equal(dialogs.length,0);assert.equal(tips.length,0)
})

test('首次微信导入沿用首地址默认规则，结算模式直接使用服务端地址ID', async () => {
  const dialogs=[],tips=[]
  const saved={id:'4'}, e=environment({realFeedback:true,respond:()=>saved,wx:{showModal:options=>{dialogs.push(options);options.success({confirm:true})},showToast:options=>tips.push(options)}}),page=e.page('address'); let selected
  page.setData({loading:false}); page.selectMode=true; page.returnSelectedAddress=address=>{selected=address}
  await page.importWechatAddress()
  assert.equal(e.calls.length,1); assert.equal(e.calls[0].data.isDefault,1); assert.equal(selected.id,'4')
  assert.equal(page.data.showForm,false)
  assert.equal(dialogs.length,0);assert.equal(tips.length,0)
})

test('真实提示适配下微信导入失败仍有错误弹窗，没有成功提示或回跳', async () => {
  const dialogs=[],tips=[]
  const e=environment({realFeedback:true,respond:()=>{throw new Error('地址保存失败测试')},wx:{showModal:options=>{dialogs.push(options);options.success({confirm:true})},showToast:options=>tips.push(options)}}),page=e.page('address')
  page.setData({loading:false});page.selectMode=true
  page.returnSelectedAddress=()=>assert.fail('失败不得回跳结算')
  await page.importWechatAddress()
  assert.equal(dialogs.length,1);assert.match(dialogs[0].content,/地址保存失败测试/)
  assert.equal(tips.length,0);assert.equal(page.data.showForm,true);assert.equal(e.calls.length,1)
})

test('手动新增或编辑地址成功直接刷新或回跳，不弹成功提示', async () => {
  for (const selectMode of [false,true]) {
    const dialogs=[],tips=[],saved={id:'8',isDefault:0}
    const e=environment({realFeedback:true,respond:({method})=>method==='POST'?saved:[saved],wx:{showModal:options=>{dialogs.push(options);options.success({confirm:true})},showToast:options=>tips.push(options)}}),page=e.page('address')
    page.setData({loading:false,form:{id:selectMode?'8':null,receiverName:'测试',receiverPhone:'13800000000',region:['湖南省','长沙市','岳麓区'],detailAddress:'测试街1号',isDefault:false}})
    page.selectMode=selectMode;let selected
    page.returnSelectedAddress=address=>{selected=address.id}
    await page.save()
    assert.equal(e.calls.filter(item=>item.method==='POST').length,1)
    if(selectMode) assert.equal(selected,'8');else assert.equal(page.data.rows[0].id,'8')
    assert.equal(dialogs.length,0);assert.equal(tips.length,0);assert.equal(page.data.saving,false)
  }
})

test('微信省市区或手机号不完整不自动提交，弹窗并保留待补充地址', async () => {
  for(const partial of [{countyName:''},{telNumber:'+85212345678'}]) {
    const modals=[],e=environment({wx:{chooseAddress:({success})=>success({userName:'测试',telNumber:'13800000000',provinceName:'湖南省',cityName:'长沙市',countyName:'岳麓区',detailInfo:'测试街',...partial}),showToast:opts=>modals.push(opts)}}),page=e.page('address')
    page.setData({loading:false}); await page.importWechatAddress()
    assert.equal(e.calls.length,0); assert.equal(page.data.showForm,true); assert.equal(page.data.form.receiverName,'测试')
    assert.ok(modals.some(x=>x.title.includes('微信地址未保存')))
  }
})

test('微信导入保存失败或缺失服务端ID不报告成功，也不自动重试', async () => {
  for(const respond of [()=>{throw new Error('保存失败测试')},()=>({})]) {
    const modals=[],e=environment({respond,wx:{showToast:opts=>modals.push(opts)}}),page=e.page('address')
    page.setData({loading:false}); await page.importWechatAddress()
    assert.equal(e.calls.length,1); assert.equal(page.data.showForm,true); assert.equal(page.data.form.receiverName,'测试收货人')
    assert.equal(page.data.importing,false); assert.equal(page.data.saving,false)
    assert.ok(modals.length); assert.ok(!modals.some(x=>x.title==='微信地址已导入'))
  }
})

test('微信地址保存中重复点击和编辑均被阻止，换账号晚响应不更新列表', async () => {
  let resolve; const waiting=new Promise(done=>{resolve=done})
  const e=environment({respond:()=>waiting}),page=e.page('address');page.setData({loading:false})
  const pending=page.importWechatAddress();await new Promise(done=>setImmediate(done))
  await page.importWechatAddress();await page.save();page.startAdd();page.cancelEdit()
  assert.equal(e.calls.length,1);assert.equal(page.data.form.receiverName,'测试收货人')
  e.storage.set('mall_mini_access_token','new-owner');resolve({id:'7'});await pending
  assert.equal(page.data.rows.length,0);assert.equal(e.calls.length,1)
})

test('微信地址已保存但列表刷新失败时明确提示核对，不重复提交', async () => {
  const notices=[],e=environment({respond:({method})=>{if(method==='POST')return{id:'9'};throw new Error('网络不可用')},wx:{showToast:options=>notices.push(options.title)}}),page=e.page('address')
  page.setData({loading:false});await page.importWechatAddress()
  assert.equal(e.calls.filter(x=>x.method==='POST').length,1)
  assert.match(page.data.loadError,/已保存.*列表刷新失败/);assert.equal(page.data.showForm,false)
  assert.ok(notices.some(x=>x.includes('不要重复导入')));assert.ok(!notices.includes('微信地址已导入'))
})

test('取消导入保留已有未保存表单；跨账号晚到导入被丢弃', async () => {
  let choose
  const e = environment({ wx: { chooseAddress(options) { choose = options } } }), page = e.page('address')
  page.setData({ loading: false, 'form.receiverName': '原填写' })
  const pending = page.importWechatAddress(); await new Promise((r) => setImmediate(r))
  choose.fail({ errMsg: 'chooseAddress:fail cancel' }); await pending
  assert.equal(page.data.form.receiverName, '原填写')
  const second = page.importWechatAddress(); await new Promise((r) => setImmediate(r))
  e.storage.set('mall_mini_access_token', 'another-session')
  choose.success({ userName: '另一个地址' }); await second
  assert.equal(page.data.form.receiverName, '原填写'); assert.equal(e.calls.length, 0)
})

test('微信旧版/新版详细地址字段回填兼容，保留非大陆手机号供手动核对', () => {
  const normalize = environment().load('utils/wechat-address').normalize
  assert.equal(normalize({ streetName: '街道', detailInfo: '街道1号' }).detailAddress, '街道1号')
  assert.equal(normalize({ streetName: '街道', detailInfoNew: '1号', telNumber: '+85212345678' }).detailAddress, '街道1号')
  assert.equal(normalize({ telNumber: '+85212345678' }).receiverPhone, '+85212345678')
})

test('昵称提交使用微信表单最终值：内容审核清空后不恢复旧绑定值', async () => {
  const e = environment(), page = e.page('account-security')
  page.setData({ loading: false, member: { id: '12' }, nickname: '旧绑定值' })
  await page.saveNickname({ detail: { value: { nickname: '' } } })
  assert.equal(e.calls.length, 0); assert.match(page.data.error, /昵称/)
  await page.saveNickname({ detail: { value: { nickname: '微信昵称' } } })
  assert.equal(e.calls[0].data.nickname, '微信昵称')
  assert.deepEqual(Object.keys(e.calls[0].data), ['nickname'])
})

test('拒绝便捷昵称授权保持普通输入；拒绝头像授权不上传', async () => {
  const e = environment({ consent: false }), page = e.page('account-security')
  page.setData({ loading: false, member: { id: '12' } })
  await page.enableWechatNickname(); assert.equal(page.data.useWechatNickname, false)
  await page.chooseAvatar({ detail: { avatarUrl: 'wxfile://chosen' } })
  assert.equal(e.uploads.length, 0)
})

test('头像走同源HTTPS认证上传和私有读取，无凭据URL或密码存储', async () => {
  const e = environment(), page = e.page('account-security')
  page.setData({ loading: false, member: { id: '12', nickname: '测试用户' } })
  await page.chooseAvatar({ detail: { avatarUrl: 'wxfile://chosen' } })
  assert.equal(e.uploads.length, 1); assert.equal(e.downloads.length, 1)
  assert.equal(e.uploads[0].header.Authorization, 'Bearer owner-session')
  assert.equal(e.downloads[0].header.Authorization, 'Bearer owner-session')
  for (const request of [...e.uploads, ...e.downloads]) { assert.match(request.url, /^https:\/\/lingqimall.com\/api\/shop\/media\/member-avatar/); assert.doesNotMatch(request.url, /owner-session|token=/) }
  assert.equal(page.data.avatarSrc, 'wxfile://tmp-avatar')
  assert.deepEqual(Object.keys(e.storage.get('mall_mini_member')).sort(), ['avatarUrl', 'id', 'nickname'])
  page.onHide(); assert.ok(e.removed.includes('wxfile://tmp-avatar')); assert.equal(page.data.avatarSrc, '/assets/profile/user-round.png')
})

test('私有头像拒绝外域地址，下载期间换号丢弃并清理临时文件', async () => {
  const e = environment({ wx: { downloadFile(options) { e.storage.set('mall_mini_access_token', 'changed'); options.success({ statusCode: 200, tempFilePath: 'wxfile://late' }) } } })
  const avatar = e.load('utils/member-avatar')
  assert.equal(await avatar.load('https://outside.invalid/avatar.png'), avatar.fallback)
  assert.equal(await avatar.load('/api/shop/media/member-avatar/12/avatar.jpg'), avatar.fallback)
  assert.ok(e.removed.includes('wxfile://late'))
})

test('授权组件只由微信同意事件完成授权，拒绝和卸载结束所有待处理请求', () => {
  const e = environment(); e.load('components/privacy-consent/index')
  const definition = e.component(), results = []
  const panel = { ...definition.methods, data: {}, setData(patch) { Object.assign(this.data, patch) } }
  definition.lifetimes.attached.call(panel)
  panel.open((result) => results.push(result)); assert.equal(results.length, 0)
  panel.agree(); assert.equal(results[0].event, 'agree'); assert.equal(results[0].buttonId, 'privacy-agree')
  panel.open((result) => results.push(result)); definition.lifetimes.detached.call(panel)
  assert.equal(results[1].event, 'disagree')
  const view = readFileSync(resolve(root, 'components/privacy-consent/index.wxml'), 'utf8')
  assert.match(view, /open-type="agreePrivacyAuthorization" bindagreeprivacyauthorization="agree"/)
  assert.doesNotMatch(view, /bindtap="agree"/)
})

test('没有可见授权组件时不静默同意；旧微信不降级绕过授权', async () => {
  const e = environment({ wx: { requirePrivacyAuthorize: undefined } }), privacy = e.load('utils/privacy')
  privacy.install(); let result
  e.privacyListener()((value) => { result = value }); assert.equal(result.event, 'disagree')
  await assert.rejects(privacy.requireConsent(), /更新微信/)
})

test('商品最新价格、零价SKU、失效规格、库存和配置限购均重新核验', async () => {
  const product = { id: '1', status: 1, salePrice: 29, stock: 5, purchaseLimit: 2 }
  const e = environment({ respond: () => ({ product, skus: [{ id: '2', salePrice: 0, stock: 4 }] }) })
  const rows = await e.load('utils/catalog').refresh([
    { productId: '1', skuId: '2', quantity: 1, salePrice: 99 },
    { productId: '1', skuId: 'removed', quantity: 1 },
    { productId: '1', skuId: '2', quantity: 3 }
  ])
  assert.equal(e.calls.length, 1); assert.equal(rows[0].salePrice, 0); assert.match(rows[0].unavailable, /限购/)
  assert.match(rows[1].unavailable, /规格/); assert.match(rows[2].unavailable, /限购/)
  assert.equal(rows[2].quantity, 3, '不静默修改购买数量')
  const valid = await e.load('utils/catalog').refresh([{ productId: '1', skuId: '2', quantity: 1, salePrice: 99 }])
  assert.equal(valid[0].salePrice, 0); assert.equal(valid[0].unavailable, '')
})

test('商品读取失败、下架、无效价格必须阻止结算', async () => {
  for (const respond of [() => { throw Error('offline') }, () => ({ product: { status: 0 } }), () => ({ product: { status: 1, stock: 5, salePrice: 'bad' } })]) {
    const e = environment({ respond })
    const [row] = await e.load('utils/catalog').refresh([{ productId: '1', quantity: 1 }])
    assert.ok(row.unavailable)
  }
})

test('商品后台内容转换包含图文服务售后且拒绝明文HTTP媒体', () => {
  const format = environment().load('utils/format')
  const product = format.product({ coverUrl: 'https://assets.example/a.jpg', galleryUrls: '["https://assets.example/b.jpg","http://bad.example/c.jpg"]', detailImages: '["/api/shop/media/images/d.jpg"]', serviceTags: '[{"title":"已配置","description":"真实规则"},{"title":"关闭","enabled":false}]', afterSalePolicy: '商品专属政策', salePrice: 0, price: 20 })
  assert.equal(product.gallery.length, 2); assert.equal(product.detailImages.length, 1)
  assert.equal(product.serviceTags.length, 1); assert.equal(product.afterSalePolicy, '商品专属政策'); assert.equal(product.salePrice, 0)
  assert.equal(format.mediaUrl('http://assets.example/a.png'), '')
  assert.equal(format.mediaUrl('javascript:alert(1)'), '')
})
