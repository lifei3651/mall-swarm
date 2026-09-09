import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { commerceEnv } from './helpers/commerce-env.mjs'
import { runMiniScript } from './helpers/run-mini-script.mjs'
const event = (id, action) => ({ currentTarget: { dataset: { id, action } } })
const captcha = { captchaId: 'fixture', image: 'data:image/png;base64,AA==' }
const credentials = { account: 'FixtureAccount', phone: '13800000000', username: 'FixtureAccount', password: 'FixtureOnlyPassword', confirmPassword: 'FixtureOnlyPassword', captchaCode: 'ABCD', smsCode: '000000' }
const account = async (respond = () => captcha, mode = 'password') => {
  const env = commerceEnv(respond, ''), page = env.page('account-login')
  env.wx.redirectTo = options => env.routes.push(options.url)
  await page.onLoad({ mode }); page.setData({ agreed: true, form: { ...credentials } }); return { env, page }
}
test('会员注册和找回支持6至32位密码，5位拒绝，已有长密码登录不受影响', async () => {
  for (const mode of ['register','reset']) {
    const {page}=await account(()=>captcha,mode);page.setData({resetStep:2});
    for (const password of ['493827','Safer!Pass9','A'.repeat(32)]) {
      page.setData({'form.password':password,'form.confirmPassword':password});assert.equal(page.validate(),null);
    }
    for (const password of ['49382','A'.repeat(33)]) {
      page.setData({'form.password':password,'form.confirmPassword':password});assert.match(page.validate()[0],/6至32/);
    }
    page.onUnload();
  }
  const {page}=await account(()=>captcha,'password');page.setData({'form.password':'ExistingLongPassword!123'});assert.equal(page.validate(),null);page.onUnload();
})
test('R06: 首次图形验证码快速失败只请求一次，离页返回可重试', async () => {
  const { env, page } = await account(() => { throw Error('图形验证码暂不可用') })
  await page.onShow(); assert.equal(env.calls.filter(call => call.url === '/captcha').length, 1)
  page.onHide(); await page.onShow(); assert.equal(env.calls.filter(call => call.url === '/captcha').length, 2)
  page.onUnload()
})
test('R06: 密码/短信调用原生会话适配、同H5凭证，成功回到原任务', async () => {
  for (const mode of ['password', 'sms']) {
    const { env, page } = await account(options => options.url === '/captcha' ? captcha : { accessToken: 'native-fixture', member: { id: '71' } }, mode)
    page.redirect = '/pages/checkout/index'; await page.submit()
    const call = env.calls.find(item => item.method === 'POST')
    assert.equal(call.url, '/shop/wechat-mini-program/auth/account-login')
    assert.equal(call.data.credentials.loginType, mode); assert.equal(call.data.privacyAgreed, true)
    assert.equal(env.storage.get('mall_mini_access_token'), 'native-fixture')
    assert.deepEqual(env.routes, ['/pages/checkout/index']); assert.equal(page.data.form.password, '')
  }
})
test('R06: 未同意/缺验证码不发请求，不接受缺会员的登录结果', async () => {
  const { env, page } = await account(() => captcha)
  page.setData({ agreed: false }); await page.submit(); assert.equal(env.calls.some(item => item.method === 'POST'), false)
  page.setData({ agreed: true, 'form.captchaCode': '' }); await page.submit(); assert.equal(env.calls.some(item => item.method === 'POST'), false)
  page.setData({ 'form.captchaCode': 'ABCD' }); await page.submit()
  assert.equal(env.storage.get('mall_mini_access_token'), ''); assert.match(env.notices.join(' '), /登录结果不完整/)
})
test('R06: 注册沿用核对后的邀请码，不能用未核对/冲突的邀请提交', async () => {
  const { env, page } = await account(options => options.url === '/captcha' ? captcha : { accessToken: 'fixture', member: { id: '71' } }, 'register')
  page.setData({ inviteReady: false }); await page.submit(); assert.equal(env.calls.some(item => item.method === 'POST'), false)
  page.setData({ inviteReady: true }); page._verifiedInviteCode = 'ABCD1234'; await page.submit()
  const call = env.calls.find(item => item.method === 'POST'); assert.equal(call.url, '/shop/wechat-mini-program/auth/account-register')
  assert.equal(call.data.credentials.inviteCode, 'ABCD1234'); assert.deepEqual(env.routes, ['/pages/home/index'])
})
test('R06: 短信业务分别使用登录/注册/找回规则，倒计时不因切页重置', async () => {
  for (const mode of ['sms', 'register', 'reset']) {
    const { env, page } = await account(() => captcha, mode)
    await page.sendCode(); await page.sendCode()
    const calls = env.calls.filter(call => call.method === 'POST'); assert.equal(calls.length, 1)
    assert.equal(calls[0].url, mode === 'sms' ? '/sms/send/login' : '/sms/send')
    if (mode !== 'sms') assert.equal(calls[0].data.bizType, mode === 'register' ? 1 : 3)
    assert.equal(page.data.cooldown, 60); page.onHide(); await page.onShow(); assert.ok(page.data.cooldown > 0); page.onUnload()
  }
})
test('R06: 找回密码最终一次校验，不绕过验证码，不自动建立会话', async () => {
  const { env, page } = await account(() => captcha, 'reset')
  await page.submit(); assert.equal(page.data.resetStep, 2); assert.equal(env.calls.some(call => call.method === 'POST'), false)
  await page.submit(); const call = env.calls.find(call => call.method === 'POST')
  assert.equal(call.url, '/shop/auth/resetPassword'); assert.equal(call.data.newPassword, credentials.password)
  assert.equal(call.data.smsCode, credentials.smsCode); assert.equal(call.data.captchaCode, 'ABCD')
  assert.equal(page.data.mode, 'password'); assert.equal(env.storage.get('mall_mini_access_token'), undefined)
})
test('R06: 重复点击、隐藏和换号后的登录晚响应不写会话或跳页', async () => {
  for (const change of ['hide', 'account']) {
    let finish
    const { env, page } = await account(options => options.url === '/captcha' ? captcha : new Promise(resolve => { finish = resolve }))
    const task = page.submit(); await page.submit(); assert.equal(env.calls.filter(call => call.method === 'POST').length, 1)
    if (change === 'hide') page.onHide(); else env.token('other-member')
    finish({ accessToken: 'old-response', member: { id: '71' } }); await task
    assert.notEqual(env.storage.get('mall_mini_access_token'), 'old-response'); assert.equal(env.routes.length, 0)
  }
})
function updateEnv() {
  const callbacks = {}, notices = [], module = { exports: {} }; let applied = 0, pages = [{ route: 'pages/profile/index', data: {} }], modal
  const wx = { getUpdateManager: () => ({ onCheckForUpdate: fn => { callbacks.check = fn }, onUpdateReady: fn => { callbacks.ready = fn }, onUpdateFailed: fn => { callbacks.failed = fn }, applyUpdate: () => applied++ }), showModal: options => { modal = options } }
  wx.showToast = options => notices.push(options.title)
  runMiniScript(readFileSync(new URL('../utils/app-update.js', import.meta.url), 'utf8'), { module, wx, getCurrentPages: () => pages, require: () => ({ notice: content => notices.push(content) }) })
  return { api: module.exports, callbacks, notices, pages: value => { pages = value }, confirm: () => modal.success({ confirm: true }), get applied() { return applied } }
}
test('R07: 下载完成不自动重启，只在安全页面用户再次确认后更新', async () => {
  const env = updateEnv(); env.api.install(); env.callbacks.ready(); assert.equal(env.applied, 0)
  const task = env.api.check(); env.confirm(); await task; assert.equal(env.applied, 1)
})
test('R07: 支付/表单/授权/多页栈及确认期间新操作均不能应用更新', async () => {
  for (const pages of [[{ route: 'pages/checkout/index', data: {} }], [{ route: 'pages/profile/index', data: { loginVisible: true } }], [{ route: 'pages/profile/index', data: { busy: true } }], [{ route: 'pages/profile/index', data: {} }, { route: 'pages/account-login/index', data: {} }]]) {
    const env = updateEnv(); env.api.install(); env.callbacks.ready(); env.pages(pages); await env.api.check(); assert.equal(env.applied, 0); assert.match(env.notices[0], /先完成当前操作/)
  }
  const env = updateEnv(); env.api.install(); env.callbacks.ready(); const task = env.api.check(); env.pages([{ route: 'pages/checkout/index', data: {} }]); env.confirm(); await task; assert.equal(env.applied, 0)
})
test('R07: 下载失败/尚无更新都提供明确结果，不调用应用更新', async () => {
  const env = updateEnv(); env.api.install(); env.callbacks.failed(); await env.api.check(); assert.match(env.notices[0], /下载未完成/)
  env.callbacks.check({ hasUpdate: false }); await env.api.check(); assert.match(env.notices[1], /最新/); assert.equal(env.applied, 0)
})
test('R07: 加购和购物车限购核对期间不重启', async () => {
  for (const key of ['addingId', 'checkoutChecking', 'quantityChecking']) {
    const env = updateEnv(); env.api.install(); env.callbacks.ready()
    env.pages([{ route: 'pages/cart/index', data: { [key]: true } }]); await env.api.check(); assert.equal(env.applied, 0)
  }
  for (const key of ['hidden', '_inactive', 'purchaseInactive']) {
    const env = updateEnv(); env.api.install(); env.callbacks.ready()
    env.pages([{route:'pages/home/index',data:{},[key]:true}]); await env.api.check(); assert.equal(env.applied,0)
  }
})
test('D11: 微信提醒拒绝在页面返回后到达，恢复原组且不重复授权', async () => {
  let authorize, prompts = 0
  const templates = [{ templateId: 'fixture', title: '发货' }]
  const env = commerceEnv(() => templates), page = env.page('subscriptions')
  env.wx.requestSubscribeMessage = options => { prompts++; authorize = options.success }
  await page.onShow(); const task = page.subscribe(event('0')); page.onHide(); await page.onShow()
  authorize({ fixture: 'reject' }); await task
  assert.equal(page.data.groups.length, 1); assert.equal(prompts, 1)
  assert.equal(env.calls.some(call => call.method === 'POST'), false)
})
const studio = (status = 1, canStart = true) => ({ anchor: { anchor: { displayName: '本地测试主播' } }, canStart, rooms: [{ room: { id: '81', status, title: '本地演练', coverUrl: '' }, roomState: status === 2 ? 'LIVE' : 'UPCOMING', products: [] }] })
test('R09: 无角色/暂停角色不允许开播，直达陌生房间零写入', async () => {
  for (const response of [{}, studio(1, false)]) {
    const env = commerceEnv(() => response), page = env.page('live-studio'); await page.onShow()
    await page.control(event('81', 'start')); await page.control(event('999', 'start'))
    assert.equal(env.calls.some(call => call.method === 'POST'), false)
  }
})
test('R09: 本人开播须确认且重读权限，凭证离页清除、观众页路径正确', async () => {
  const env = commerceEnv(call => call.method === 'POST' ? { roomId: '81', pushUrl: 'rtmp://local.invalid/test', expireTime: '2099-01-01' } : studio()), page = env.page('live-studio')
  await page.onShow(); await page.control(event('81', 'start'))
  assert.equal(env.calls.filter(call => call.method === 'POST').length, 1)
  assert.ok(page.data.credential); assert.equal([...env.storage.keys()].some(key => /credential|push/i.test(key)), false)
  page.watch(event('81')); assert.deepEqual(env.routes, ['/pages/store-content/index?type=live&id=81'])
  page.onHide(); assert.equal(page.data.credential, null)
})
test('R09: 开播确认后资格撤销/离页/取消无写入', async () => {
  for (const state of ['revoked', 'hide', 'cancel']) {
    let reads = 0
    const env = commerceEnv(() => ++reads === 1 ? studio() : studio(1, false)), page = env.page('live-studio'); await page.onShow()
    if (state !== 'revoked') env.wx.showModal = options => { if (state === 'hide') page.onHide(); options.success({ confirm: state !== 'cancel' }) }
    await page.control(event('81', 'start')); assert.equal(env.calls.some(call => call.method === 'POST'), false)
  }
})
test('C04/C07: 历史单号回退为包裹、驳回可重申、折叠不隐藏金额', async () => {
  const detail = { order: { id: '9', status: 2, deliveryNo: 'FIXTURE-NO', deliveryCompany: '本地物流', payAmount: 99 }, items: [{ id: '71', quantity: 2 }], afterSaleWindowMode: 'RECEIVED', afterSales: [{ id: '81', status: 2, applyType: 1 }] }
  const env = commerceEnv(() => detail), page = env.page('order-detail'); page.onLoad({ id: '9' }); await page.load()
  assert.equal(page.data.rows[0].shipments[0].packageLabel, '包裹 1 · 2件商品')
  assert.equal(page.data.rows[0].afterSales[0].canReapply, true)
  page.toggleOrderInfo(event('9')); assert.equal(page.data.expandedOrders['9'], true)
  page.applyAfterSale(event('9')); assert.deepEqual(env.routes, ['/pages/after-sale/index?orderId=9'])
})
test('C04: 换号后的物流晚响应不暴露旧账号轨迹', async () => {
  let finish
  const env = commerceEnv(() => new Promise(resolve => { finish = resolve })), page = env.page('order-detail')
  page.setData({ rows: [{ order: { id: '9' } }] }); const task = page.loadTracking(event('9')); env.token('new')
  finish([{ deliveryNo: 'old-account-no', events: [] }]); await task; assert.deepEqual(page.data.trackingRows, [])
})
test('D11: 消息详情和提现列表离页/换号晚响应不显示旧记录', async () => {
  for (const name of ['message-detail', 'payout']) for (const change of ['hide', 'account']) {
    let finish
    const env = commerceEnv(() => new Promise(resolve => { finish = resolve })), page = env.page(name)
    page.onLoad({ id: '71' }); const task = page.onShow()
    if (change === 'hide') page.onHide(); else env.token('another-member')
    finish(name === 'payout' ? [{ id: '71', withdrawAmount: 999, status: 2, withdrawType: 2 }] : { id: '71', content: 'old-owner-private' }); await task
    assert.equal(name === 'payout' ? page.data.rows.length : page.data.message, name === 'payout' ? 0 : null)
    if (name === 'message-detail') { page.openTarget(); assert.equal(env.routes.length, 0) }
  }
})
test('D11: 钱包奖金/余额晚响应不覆盖切换后的账号', async () => {
  const resolvers = []
  const env = commerceEnv(call => call.url.includes('member-capabilities') ? {} : new Promise(resolve => resolvers.push([call.url, resolve])))
  const page = env.page('wallet'), task = page.onShow(); env.token('other')
  for (const [url, resolve] of resolvers) resolve(url.endsWith('/summary') ? { balance:99 } : url.endsWith('/bonus-summary') ? {issuedBonus:66,pendingBonus:33} : [])
  await task; await new Promise(resolve => setTimeout(resolve,0))
  assert.equal(page.data.balance, '--'); assert.equal(page.data.issuedBonus, '--')
})
test('D11: 地址换号清理未保存草稿，旧请求失败不弹到新账号', async () => {
  let fail
  const env = commerceEnv(() => new Promise((_,reject)=>{fail=reject})), page = env.page('address')
  page.onLoad(); page.owner = 'member'; page.setData({ 'form.receiverName':'旧账号草稿', showForm:true })
  const task = page.load(); env.token('other'); fail(Error('old-member-private-failure')); await task
  assert.equal(env.notices.length,0)
  env.token(''); page.onShow(); assert.equal(page.data.form.receiverName,'')
})
test('D11: 售后旧账号加载失败不覆盖新账号申请', async () => {
  let fail
  const env = commerceEnv(() => new Promise((_,reject)=>{fail=reject})), page = env.page('after-sale')
  page.onLoad({orderId:'71'}); const task = page.onShow(); env.token('other'); fail(Error('old-private-failure')); await task
  assert.equal(page.data.error,''); assert.equal(env.notices.length,0)
})
test('D11: 提现核对期间换号/离页不能调起原账号收款授权', async () => {
  for (const change of ['hide', 'account']) {
    let finish, transfers = 0
    const env = commerceEnv(() => new Promise(resolve => { finish = resolve })), page = env.page('payout')
    env.wx.canIUse = () => true; env.wx.showLoading = env.wx.hideLoading = () => {}; env.wx.requestMerchantTransfer = () => transfers++
    page.owner = 'member'; page.setData({ rows: [{ id: '71', canConfirm: true }] })
    const task = page.confirm(event('71')); if (change === 'hide') page.onHide(); else env.token('other')
    finish({ state: 'WAIT_USER_CONFIRM', mchId: 'fixture', appId: 'fixture', packageInfo: 'fixture' }); await task; assert.equal(transfers, 0)
  }
})
test('D11: 微信提醒授权在隐藏时返回，只在原账号返回后幂等同步', async () => {
  let authorize
  const templates = [{ templateId: 'fixture', title: '发货', availableGrants: 0 }]
  const env = commerceEnv(() => templates), page = env.page('subscriptions')
  env.wx.requestSubscribeMessage = options => { authorize = options.success }
  await page.onShow(); const task = page.subscribe(event('0')); page.onHide(); authorize({ fixture: 'accept' }); await task
  assert.equal(env.calls.some(call => call.method === 'POST'), false); assert.equal(page.data.pendingGrant, true)
  await page.onShow(); assert.equal(env.calls.filter(call => call.method === 'POST').length, 1)
})
test('D11: 提醒同步晚响应不能覆盖新账号模板或冒报授权成功', async () => {
  let finish
  const env = commerceEnv(call => call.method === 'POST' ? new Promise(resolve => { finish = resolve }) : []), page = env.page('subscriptions')
  page.pendingGrant = { requestId: 'fixture', acceptedTemplateIds: ['fixture'] }; page.pendingGrantToken = 'member'
  const task = page.syncGrant(); env.token('other'); await page.onShow(); finish([{ templateId: 'old', availableGrants: 99 }]); await task
  assert.deepEqual(page.data.templates, []); assert.equal(env.notices.some(text => /已开启/.test(text)), false)
})
test('R06: 找回密码离页后清除敏感输入并回到验证码步骤', async () => {
  const { page } = await account(() => captcha, 'reset'); await page.submit(); assert.equal(page.data.resetStep, 2)
  page.onHide(); await page.onShow(); assert.equal(page.data.resetStep, 1); assert.equal(page.data.form.smsCode, ''); page.onUnload()
})
test('D11: 首屏尚未加载时离开再返回，目录/活动/协议恢复且忽略旧失败', async () => {
  for (const name of ['category', 'campaign', 'legal']) {
    let fail, calls = 0
    const env = commerceEnv(call => ++calls === 1 ? new Promise((_, reject) => { fail = reject }) : call.url === '/shop/products' ? { list: [] } : name === 'legal' ? {} : [])
    const page = env.page(name), first = name === 'category' ? page.loadCategories() : page.load()
    page.onHide(); await page.onShow(); fail(new Error('old-hidden-failure')); await first
    assert.equal(page.data.loading, false); assert.equal(page.data.error, ''); assert.equal(env.notices.includes('old-hidden-failure'), false)
  }
})
test('D11: 首页首次加载隐藏后能重取，旧任务不能清除新任务锁或覆盖页面', async () => {
  let finish, reads = 0
  const env = commerceEnv(call => call.url === '/shop/home' ? (++reads === 1 ? new Promise(resolve => { finish = resolve }) : { brandName: '当前页面', displayConfig: {} }) : { list: [] }), page = env.page('home')
  const first = page.loadHome(); page.onHide(); await page.onShow(); finish({ brandName: '旧页面', displayConfig: {} }); await first
  assert.equal(page.data.home.brandName, '当前页面'); assert.equal(page.data.loading, false); page.onUnload()
  assert.deepEqual(env.rememberedThemes.map(item => item.brandName), ['当前页面'])
})
