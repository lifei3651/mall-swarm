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
