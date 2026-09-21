import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
const read = path => readFileSync(new URL('../'+path, import.meta.url), 'utf8')

test('H5账户表单共用排版，验证码输入列允许收缩，辅助入口通栏且操作不拆行', () => {
  for (const view of ['ChangePaymentPassword','ChangeLoginPassword','ProfileSettings','RealNameVerification']) {
    const source = read(`src/views/${view}View.vue`)
    assert.match(source, /class="[^"]*account-form/)
    assert.match(source, /<style src="\.\.\/assets\/account-forms.css"><\/style>/)
  }
  const styles = read('src/assets/account-forms.css')
  assert.match(styles,/grid-template-columns: minmax\(0, 1fr\) auto/)
  assert.match(styles,/\.account-form \.field \{[^}]*min-width: 0;[^}]*height: 44px;/)
  assert.match(styles,/\.account-form \.account-help-row \{[^}]*width: 100%;[^}]*min-width: 0;/)
  assert.match(styles,/\.account-help-copy \{[^}]*min-width: 0;/)
  assert.match(styles,/\.account-help-action \{[^}]*flex: none;[^}]*white-space: nowrap;/)
})

test('支付密码账号引导等待读取成功，只展示给未设置账号者，不改密码校验', async () => {
  const source = read('src/views/ChangePaymentPasswordView.vue')
  const script = source.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*\n/gm, '')
  for (const [data, expected] of [[{phone:'13800008000'},true],[{username:'13800008000',phone:'13800008000'},true],[{username:'PreviewOnly',phone:'13800008000'},false],[null,false]]) {
    let mounted
    const context={ref:value=>({value}),useRouter:()=>({}),useRoute:()=>({query:{}}),onMounted:fn=>{mounted=fn},onBeforeUnmount:()=>{},getMe:async()=>({data}),getWalletSummary:async()=>({data:{hasPaymentPassword:false}})}
    vm.runInNewContext(script+'\nthis.page={canSetupAccount}',context)
    assert.equal(context.page.canSetupAccount.value,false)
    await mounted()
    assert.equal(context.page.canSetupAccount.value,expected)
  }
  assert.match(source,/v-if="canSetupAccount"[^>]*to="\/profile\/settings\?mode=account"/)
  assert.ok(source.includes("/^\\d{6}$/"))
  assert.match(source,/请输入当前登录密码/)
  assert.match(source,/请输入6位短信验证码/)
})

test('账号设置有常驻标签，手机号弹层可纵向滚动，隐私同意文本不挤出', () => {
  const profile = read('src/views/ProfileSettingsView.vue')
  for (const id of ['setup-username','setup-password']) {
    assert.ok(profile.includes(`label for="${id}"`))
    assert.ok(profile.includes(`input id="${id}"`))
  }
  assert.match(profile,/\.dialog-card \{[^}]*max-height:calc\(100dvh - 24px\);[^}]*overflow-y:auto;/)
  assert.match(read('src/assets/account-forms.css'),/\.account-form \.consent-row > span \{[^}]*min-width: 0;/)
})

test('个人资料明确手机号也是商城账号，并只读展示直属邀请关系', () => {
  const profile = read('src/views/ProfileSettingsView.vue')
  const api = read('src/api/shop.js')
  assert.match(api, /url: '\/shop\/auth\/account-identity'/)
  assert.match(profile, /手机号账号/)
  assert.match(profile, /直属邀请人/)
  assert.match(profile, /受邀注册若显示未绑定/)
  assert.doesNotMatch(profile, /商城账号[\s\S]{0,120}\? '未设置'/)
})

test('H5直属邀请关系完整覆盖已绑定、未绑定、待核验与查询失败', async () => {
  const source = read('src/views/ProfileSettingsView.vue')
  const script = source.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*\n/gm, '')
  const member = { username: 'MallTester', phone: '13800138000', nickname: '测试用户' }
  const cases = [
    [{ inviterStatus: 'BOUND', inviterName: '邀请人甲' }, '邀请人甲'],
    [{ inviterStatus: 'NONE', inviterName: '' }, '未绑定'],
    [{ inviterStatus: 'INVALID', inviterName: '' }, '关系待核验'],
    [new Error('邀请关系查询失败'), '暂不可查询']
  ]
  for (const [identity, expected] of cases) {
    let mounted
    const context = {
      ref: value => ({ value }),
      computed: getter => ({ get value() { return getter() } }),
      nextTick: async () => {},
      onMounted: fn => { mounted = fn },
      onBeforeUnmount: () => {},
      useRouter: () => ({ back() {}, replace: async () => {} }),
      useRoute: () => ({ query: {} }),
      getMe: async () => ({ data: member }),
      getAccountIdentity: async () => {
        if (identity instanceof Error) throw identity
        return { data: { accountMode: 'CUSTOM', accountDisplay: member.username, canSetupLoginAccount: false, ...identity } }
      },
      sendSmsCode: async () => {}, setupAccount: async () => {}, updateNickname: async () => ({ data: member }), updatePhone: async () => {},
      normalizeLoginAccountInput: value => value, validateLoginAccount: () => '', normalizeNicknameInput: value => value,
      validateNickname: () => '', isValidMainlandPhone: () => true, normalizeMainlandPhone: value => value, clearShopSession: () => {},
      window: { setTimeout: () => 1, clearTimeout() {}, setInterval: () => 1, clearInterval() {} }
    }
    vm.runInNewContext(script + '\nthis.page={accountIdentity,inviterDisplay,accountDisplay}', context)
    await mounted()
    assert.equal(context.page.inviterDisplay.value, expected)
    assert.equal(context.page.accountDisplay.value, 'MallTester')
    if (!(identity instanceof Error)) assert.equal(context.page.accountIdentity.value.inviterStatus, identity.inviterStatus)
    else assert.equal(context.page.accountIdentity.value.inviterStatus, 'UNKNOWN')
  }
})
