import { runMiniScript } from './helpers/run-mini-script.mjs'
import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import path from 'node:path'
import { readFileSync } from 'node:fs'

const source = (file) => readFileSync(new URL(`../${file}`, import.meta.url), 'utf8')
const deferred = () => { let resolve; const promise = new Promise((done) => { resolve = done }); return { promise, resolve } }
const settle = async () => { for (let i = 0; i < 20; i++) await Promise.resolve() }
const rights = (active = true) => ({ membershipActive: active, canInvite: active, inviteCode: active ? 'SEND1234' : null, canViewWallet: true, canViewPayoutRecords: true })
function harness({ handle, token = '', login } = {}) {
  const storage = new Map(), calls = [], menus = [], toasts = [], routes = [], cache = new Map()
  let time = 1000000
  if (token) storage.set('mall_mini_access_token', token)
  const wx = {
    getStorageSync: (key) => storage.get(key), setStorageSync: (key, value) => storage.set(key, value), removeStorageSync: (key) => storage.delete(key),
    showToast: (value) => toasts.push(value.title), hideShareMenu: () => menus.push('hide'), showShareMenu: () => menus.push('show'),
    navigateTo: (value) => routes.push(value.url), redirectTo: (value) => routes.push(value.url), switchTab: (value) => routes.push(value.url),
    login: login || ((options) => options.success({ code: 'mock-login-code' }))
  }
  async function request(options) {
    calls.push(options)
    if (handle) { const result = handle(options); if (result !== undefined) return result }
    if (options.url.endsWith('/member-capabilities')) return rights()
    if (options.url.endsWith('/runtime')) return { enabled: true, phoneAuthorizationEnabled: true, privacyConsentVersion: load('config/runtime.js').PRIVACY_CONSENT_VERSION }
    if (options.url.includes('/inviter-preview/')) return { valid: true, nickname: options.url.endsWith('ABCD1234') ? '原邀请人' : '新邀请人' }
    if (options.url.endsWith('/auth/login')) return { accessToken: 'new-mock-session', member: { nickname: '测试账户' } }
    throw new Error(`Unexpected mock request: ${options.url}`)
  }
  function load(file) {
    if (file === 'utils/request.js') return request
    if (file === 'utils/theme.js') return { pageData: () => ({}), apply() {} }
    if (cache.has(file)) return cache.get(file).exports
    const module = { exports: {} }; cache.set(file, module)
    runMiniScript(source(file), { module, wx, Date: { now: () => time },
      require: (id) => load(path.posix.normalize(path.posix.join(path.posix.dirname(file), id + (id.endsWith('.js') ? '' : '.js')))) }, { filename: file })
    return module.exports
  }
  function page() {
    const flow = load('utils/login-flow.js')
    return { ...flow, data: { ...flow.data }, setData(patch) { Object.assign(this.data, patch) } }
  }
  return { load, page, calls, menus, toasts, routes, storage, advance: (ms) => { time += ms } }
}
const sharePage = () => ({ data: {}, setData(patch) { Object.assign(this.data, patch) } })
const phoneEvent = { detail: { errMsg: 'getPhoneNumber:ok', code: 'mock-approved-phone-code' } }

test('分享首页和商品只带后台确认的本人邀请码，不转发收到的他人归属', async () => {
  const h = harness({ token: 'sender' }), share = h.load('utils/share.js'), page = sharePage()
  // Even a pending invite left by an older version cannot become the outgoing inviter.
  h.load('utils/invite.js').setManualInvite('ABCD1234')
  await share.prepare(page)
  assert.equal(page.data.shareReady, true)
  assert.equal(share.message(page, '/pages/home/index', '商城').path, '/pages/home/index?inviteCode=SEND1234')
  assert.equal(share.message(page, '/pages/product/index?id=42', '商品').path, '/pages/product/index?id=42&inviteCode=SEND1234')
  assert.deepEqual(h.menus, ['hide', 'show'])
})

test('游客与普通购物账号可分享商品，但不会获得邀请资格', async () => {
  for (const token of ['', 'ordinary']) {
    const h = harness({ token, handle: ({ url }) => url.endsWith('/member-capabilities') ? rights(false) : undefined })
    const share = h.load('utils/share.js'), page = sharePage()
    await share.prepare(page)
    assert.equal(share.message(page, '/pages/product/index?id=42', '商品').path, '/pages/product/index?id=42')
    assert.equal(page.data.shareReady, true)
  }
})

test('换账号、页面隐藏和后台能力失败均不能复用上一人的邀请码', async () => {
  const h = harness({ token: 'sender' }), share = h.load('utils/share.js'), page = sharePage()
  await share.prepare(page)
  h.storage.set('mall_mini_access_token', 'another')
  assert.equal(share.message(page, '/pages/home/index', '商城').path, '/pages/home/index')
  assert.match(h.toasts[0], /身份已变化/)
  const late = deferred(), waiting = harness({ token: 'sender', handle: () => late.promise })
  const waitingShare = waiting.load('utils/share.js'), waitingPage = sharePage()
  const work = waitingShare.prepare(waitingPage)
  waitingShare.hide(waitingPage)
  late.resolve(rights()); await work
  assert.equal(waitingPage._shareState, null)
  assert.equal(waiting.menus.includes('show'), false)
  const failed = harness({ token: 'sender', handle: () => Promise.reject(new Error('offline')) })
  const failedPage = sharePage(); await failed.load('utils/share.js').prepare(failedPage)
  assert.equal(failedPage.data.shareReady, false)
  assert.match(failedPage.data.shareError, /重试/)
})

test('会员属性严格校验类型、资格和邀请码；余额和邀请关系不作为替代判断', async () => {
  const broken = harness({ token: 'member', handle: () => ({ ...rights(), membershipActive: 'true' }) })
  await assert.rejects(broken.load('utils/member-capabilities.js').load(), /信息不完整/)
  for (const data of [{ ...rights(), inviteCode: 'INVALID' }, { ...rights(), membershipActive: false, balance: 900, inviterId: 1 }]) {
    const h = harness({ token: 'member', handle: () => data })
    assert.equal((await h.load('utils/member-capabilities.js').load()).canInvite, false)
  }
})

test('收到分享后先显示公开邀请人；协议默认未选且邀请人未核对时不能授权登录', async () => {
  const late = deferred(), h = harness({ handle: ({ url }) => url.includes('/inviter-preview/') ? late.promise : undefined })
  h.load('utils/invite.js').captureLaunchInvite({ query: { inviteCode: 'ABCD1234' } })
  const page = h.page(); await page.onLoad()
  assert.equal(page.data.agreed, false)
  assert.equal(page.data.inviteBusy, true)
  page.data.agreed = true
  await page.phoneLogin(phoneEvent)
  assert.equal(h.calls.some(({ url }) => url.endsWith('/auth/login')), false)
  late.resolve({ valid: true, nickname: '公开昵称' }); await settle()
  assert.equal(page.data.inviterName, '公开昵称')
  assert.equal(page.data.inviteReady, true)
  assert.equal(page._verifiedInviteCode, 'ABCD1234')
})

test('手填邀请码可核对与取消，直接调用登录使用核对后的固定快照', async () => {
  let wxLogin
  const h = harness({ login: (options) => { wxLogin = options } }), page = h.page()
  await page.onLoad(); page.toggleInvitation()
  page.invitationInput({ detail: { value: 'abcd1234' } }); await page.checkInvitation()
  assert.equal(h.load('utils/invite.js').getPendingInvite(), 'ABCD1234')
  assert.equal(page.data.inviterName, '原邀请人')
  page.data.agreed = true
  const pending = page.phoneLogin(phoneEvent)
  h.load('utils/invite.js').captureLaunchInvite({ query: { inviteCode: 'EFGH5678' } })
  wxLogin.success({ code: 'mock-login-code' }); await pending
  const login = h.calls.find(({ url }) => url.endsWith('/auth/login'))
  assert.equal(login.data.inviteCode, 'ABCD1234')
  assert.equal(h.load('utils/invite.js').getPendingInvite(), '')
})

test('冲突邀请必须明确选新或保留；无效的旧邀请不阻止选择有效的新邀请', async () => {
  const h = harness({ handle: ({ url }) => url.endsWith('/ABCD1234') ? { valid: false } : undefined })
  const invite = h.load('utils/invite.js')
  invite.captureLaunchInvite({ query: { inviteCode: 'ABCD1234' } })
  invite.captureLaunchInvite({ query: { inviteCode: 'EFGH5678' } })
  const page = h.page(); await page.onLoad(); await settle()
  assert.equal(page.data.inviteConflict, true)
  assert.equal(page.invitationReady(), false)
  assert.equal(page.data.candidateValid, true)
  await page.chooseInvitation({ currentTarget: { dataset: { choice: 'new' } } })
  assert.equal(page._verifiedInviteCode, 'EFGH5678')
  assert.equal(page.invitationReady(), true)
})

test('无效候选不可选择，保留原邀请不产生静默改绑', async () => {
  const h = harness({ handle: ({ url }) => url.endsWith('/EFGH5678') ? { valid: false } : undefined })
  const invite = h.load('utils/invite.js')
  invite.captureLaunchInvite({ query: { inviteCode: 'ABCD1234' } }); invite.captureLaunchInvite({ query: { inviteCode: 'EFGH5678' } })
  const page = h.page(); await page.onLoad(); await settle()
  await page.chooseInvitation({ currentTarget: { dataset: { choice: 'new' } } })
  assert.equal(invite.getPendingInvite(), 'ABCD1234'); assert.equal(page.data.inviteConflict, true)
  await page.chooseInvitation({ currentTarget: { dataset: { choice: 'keep' } } })
  assert.equal(page.data.inviteConflict, false); assert.equal(page._verifiedInviteCode, 'ABCD1234')
})

test('异步核对期间清除邀请或关闭弹窗，晚到响应不能恢复旧归属', async () => {
  for (const action of ['clearInvitation', 'onUnload']) {
    const late = deferred(), h = harness({ handle: ({ url }) => url.includes('/inviter-preview/') ? late.promise : undefined })
    const page = h.page(); await page.onLoad()
    page.invitationInput({ detail: { value: 'ABCD1234' } })
    const work = page.checkInvitation(); page[action]()
    late.resolve({ valid: true, nickname: '晚到邀请人' }); await work
    assert.equal(h.load('utils/invite.js').getPendingInvite(), '')
    assert.equal(page._verifiedInviteCode, '')
  }
})

test('弹窗打开期间归属到期或变化，必须重新核对，不发送旧邀请码', async () => {
  const h = harness(), invite = h.load('utils/invite.js')
  invite.captureLaunchInvite({ query: { inviteCode: 'ABCD1234' } })
  const page = h.page(); await page.onLoad(); await settle(); page.data.agreed = true
  h.advance(25 * 3600000)
  await page.phoneLogin(phoneEvent)
  assert.equal(h.calls.some(({ url }) => url.endsWith('/auth/login')), false)
  assert.equal(page.data.inviteCode, '')
  assert.equal(page._verifiedInviteCode, '')
  assert.equal(page.data.inviteReady, false)
  assert.match(page.data.inviteError, /过期/)
  await page.phoneLogin(phoneEvent)
  assert.equal(h.calls.some(({ url }) => url.endsWith('/auth/login')), false)
  page.clearInvitation()
  assert.equal(page.data.inviteReady, true)
})

test('新注册默认商城首页，已有账号保留原任务；返回账号登录不提交新的邀请', async () => {
  for (const newMember of [false, true]) {
    const h = harness({ handle: ({ url }) => url.endsWith('/auth/login') ? { accessToken: 'mock-session', newMember } : undefined })
    h.load('utils/invite.js').captureLaunchInvite({ query: { inviteCode: 'ABCD1234' } })
    const page = h.page(); await page.onLoad({ redirect: encodeURIComponent('/pages/address/index') }); await settle()
    page.data.agreed = true
    if (newMember) await page.phoneLogin(phoneEvent)
    else await page.returningLogin()
    const submitted = h.calls.find(({ url }) => url.endsWith('/auth/login')).data
    assert.equal(submitted.inviteCode, newMember ? 'ABCD1234' : undefined)
    assert.equal(h.routes.at(-1), newMember ? '/pages/home/index' : '/pages/address/index')
    assert.equal(h.load('utils/invite.js').getPendingInvite(), '')
  }
})

test('退出账号清理归属缓存；拒绝手机号不注册、不消费邀请码', async () => {
  const h = harness(), invite = h.load('utils/invite.js'), page = h.page()
  invite.captureLaunchInvite({ query: { inviteCode: 'ABCD1234' } })
  await page.onLoad(); await settle(); page.data.agreed = true
  await page.phoneLogin({ detail: { errMsg: 'getPhoneNumber:fail user deny' } })
  assert.equal(invite.getPendingInvite(), 'ABCD1234')
  assert.equal(h.calls.some(({ url }) => url.endsWith('/auth/login')), false)
  h.load('utils/session.js').clearSession()
  assert.equal(invite.getPendingInvite(), '')
})

test('微信凭证或登录响应等待期间换账号，不发旧注册请求也不覆盖新会话', async () => {
  let callback
  const h = harness({ login: (options) => { callback = options } })
  const first = h.load('utils/auth.js').login({ phoneCode: 'mock-phone', inviteCode: 'ABCD1234' })
  h.storage.set('mall_mini_access_token', 'another-session')
  callback.success({ code: 'mock-code' })
  await assert.rejects(first, /登录状态已变化/)
  assert.equal(h.calls.length, 0)
  const late = deferred(), waiting = harness({ handle: () => late.promise })
  const second = waiting.load('utils/auth.js').login({ phoneCode: 'mock-phone', inviteCode: 'ABCD1234' })
  await settle()
  waiting.storage.set('mall_mini_access_token', 'another-session')
  late.resolve({ accessToken: 'stale-session' })
  await assert.rejects(second, /登录状态已变化/)
  assert.equal(waiting.load('utils/session.js').getToken(), 'another-session')
})

test('邀请补充说明明确暂存时限、选择和分享范围，不覆盖客户自己的政策', () => {
  const legal = harness().load('utils/legal.js')
  assert.match(legal.miniPrivacy, /最长24小时/)
  assert.match(legal.miniPrivacy, /不会自动替换/)
  assert.match(legal.miniPrivacy, /分享不包含手机号、登录凭据或团队明细/)
  assert.equal(legal.content('privacy', { privacyPolicy: '客户政策' }), '客户政策')
})

test('畸形分享不能静默转为无邀请注册，也不能覆盖已核对的有效邀请', async () => {
  const h = harness(), invite = h.load('utils/invite.js')
  invite.captureLaunchInvite({ query: { inviteCode: 'BAD-CODE' } })
  const page = h.page(); await page.onLoad(); page.data.agreed = true
  assert.match(page.data.inviteError, /格式不正确/)
  await page.phoneLogin(phoneEvent)
  assert.equal(h.calls.some(({ url }) => url.endsWith('/auth/login')), false)
  page.clearInvitation(); assert.equal(page.invitationReady(), true)
  invite.captureLaunchInvite({ query: { inviteCode: 'ABCD1234' } })
  invite.captureLaunchInvite({ query: { scene: '%broken' } })
  await page.syncInvitation(true)
  assert.equal(page.data.inviteConflict, true)
  assert.equal(page.data.candidateValid, false)
  await page.chooseInvitation({ currentTarget: { dataset: { choice: 'new' } } })
  assert.equal(invite.getPendingInvite(), 'ABCD1234')
  await page.chooseInvitation({ currentTarget: { dataset: { choice: 'keep' } } })
  assert.equal(page.invitationReady(), true)
})
