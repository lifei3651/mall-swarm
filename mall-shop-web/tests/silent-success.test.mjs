import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
const read = path => readFileSync(new URL('../' + path, import.meta.url), 'utf8')
test('H5首页分类详情加购成功不显示浮层，失败提示保留', () => {
  for (const file of ['HomeView', 'CategoryView', 'ProductDetailView']) {
    const source = read(`src/views/${file}.vue`)
    assert.doesNotMatch(source, /showToast\([^\n]*(已加入购物车|加购成功)/)
    assert.match(source, /showToast\(error\?\.message/)
    assert.match(source, /\badd\(/)
  }
})
test('H5和小程序普通登录成功没有确认弹窗', () => {
  for (const file of ['src/views/LoginView.vue', 'src/surfaces/public/PublicLoginView.vue', '../mall-mini-program/utils/login-flow.js', '../mall-mini-program/pages/account-login/index.js']) {
    const source = read(file)
    assert.doesNotMatch(source, /(?:notice|showToast|showModal)\([^\n]*['"`]登录成功/)
    assert.doesNotMatch(source, /success.value\s*=\s*['"]登录成功/)
  }
})
