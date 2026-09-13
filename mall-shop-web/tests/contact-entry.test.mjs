import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
const read = file => readFileSync(new URL('../src/views/' + file, import.meta.url), 'utf8')
test('登录密码精简说明，保留验证字段、找回密码及客服联系入口', () => {
  const view = read('ChangeLoginPasswordView.vue')
  assert.match(view, /修改后需在所有设备重新登录/)
  assert.match(view, /to="\/legal\/contact"/)
  assert.match(view, /to="\/forgot-password"/)
  for (const field of ['form.currentPassword','form.smsCode','form.newPassword','confirmPwd']) assert.ok(view.includes(`v-model="${field}"`))
})
test('联系页工单不依赖联系方式请求成功，空配置不虚构联系方式', () => {
  const view = read('LegalView.vue')
  assert.match(view, /class="support-entry" to="\/support"/)
  assert.match(view, /v-else-if="loadError"/)
  assert.match(view, /@click="load"/)
  assert.match(view, /暂未公布电话和邮箱/)
  assert.match(view, /catch \(_\) \{ loadError.value =/)
})
