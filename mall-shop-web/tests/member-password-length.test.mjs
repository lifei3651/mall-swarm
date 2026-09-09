import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
const read = path => readFileSync(new URL('../'+path,import.meta.url),'utf8')

test('H5注册、设置、修改与找回统一最低6位，保持32位上限和验证码',()=>{
  for (const path of ['src/views/LoginView.vue','src/surfaces/public/PublicLoginView.vue','src/views/ProfileSettingsView.vue','src/views/ChangeLoginPasswordView.vue','src/views/ForgotPasswordView.vue']) {
    const source=read(path);
    assert.match(source,/6至32/);assert.match(source,/minlength="6"/);assert.match(source,/length < 6/);assert.match(source,/maxlength="32"/);
    assert.doesNotMatch(source,/10至32|minlength="10"|length < 10/);
  }
  const payment=read('src/views/ChangePaymentPasswordView.vue');
  assert.ok(payment.includes('/^\\d{6}$/'));assert.match(payment,/短信验证码/);
})
