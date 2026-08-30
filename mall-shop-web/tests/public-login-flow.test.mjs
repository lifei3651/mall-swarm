import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const readProjectFile = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8')

const sectionBetween = (source, start, end) => {
  const startIndex = source.indexOf(start)
  const endIndex = source.indexOf(end, startIndex)
  assert.notEqual(startIndex, -1, `missing section start: ${start}`)
  assert.notEqual(endIndex, -1, `missing section end: ${end}`)
  return source.slice(startIndex, endIndex)
}

test('public storefront registration SMS can be requested before image captcha', async () => {
  const [view, api] = await Promise.all([
    readProjectFile('src/surfaces/public/PublicLoginView.vue'),
    readProjectFile('src/api/shop.js'),
  ])
  const loginSender = sectionBetween(view, 'const sendLoginCode = async () => {', 'const sendRegistrationCode = async () => {')
  const registrationSender = sectionBetween(view, 'const sendRegistrationCode = async () => {', 'const validate = () => {')

  assert.match(api, /export function sendLoginSmsCode\(phone\) \{\s*return request\(\{\s*url: '\/sms\/send\/login',\s*method: 'post',\s*data: \{ phone \}/)
  assert.match(api, /export function sendSmsCode\(phone, bizType = 1, captcha = \{\}\) \{\s*return request\(\{\s*url: '\/sms\/send'/)

  assert.match(view, /@click="sendLoginCode"/)
  assert.match(loginSender, /await sendLoginSmsCode\(smsLoginForm\.phone\)/)
  assert.doesNotMatch(loginSender, /sendSmsCode|captcha\.|refreshCaptcha/)

  assert.match(view, /@click="sendRegistrationCode"/)
  assert.match(registrationSender, /await sendSmsCode\(registerForm\.phone, 1\)/)
  assert.doesNotMatch(registrationSender, /captcha\.|loadInviter|refreshCaptcha|sendLoginSmsCode/)
})

test('public storefront shows captcha only for registration and password login', async () => {
  const view = await readProjectFile('src/surfaces/public/PublicLoginView.vue')

  assert.match(view, /<div v-if="needsCaptcha" class="captcha-block">/)
  assert.match(view, /const needsCaptcha = computed\(\(\) => isRegister\.value \|\| loginType\.value === 'password'\)/)
  assert.match(view, /if \(!captcha\.id \|\| !\/\^\[A-Za-z0-9\]\{4\}\$\/\.test\(captcha\.code\)\) return '请输入4位图形验证码'/)
  assert.match(view, /captchaId: captcha\.id/)
  assert.match(view, /captchaCode: captcha\.code/)
  assert.doesNotMatch(view, /const needsCaptcha = true/)
})

test('public registration accepts an optional manual invite code and QR registration locks the prefilled code', async () => {
  const [view, api] = await Promise.all([
    readProjectFile('src/surfaces/public/PublicLoginView.vue'),
    readProjectFile('src/api/shop.js'),
  ])

  assert.match(view, /route\.query\.inviteCode \|\| route\.query\.code/)
  assert.match(view, /id="public-register-invite"/)
  assert.match(view, /邀请码 <span class="optional-mark">选填<\/span>/)
  assert.match(view, /:disabled="inviteCodeLocked"/)
  assert.match(view, /@click="loadInviter"/)
  assert.match(view, /const normalizedInviteCode = computed/)
  assert.match(view, /await getInviterPreview\(inviteCode\)/)
  assert.match(api, /url: `\/shop\/public\/inviter-preview\/\$\{encodeURIComponent\(inviteCode\)\}`/)
  assert.match(view, /<span>邀请人昵称<\/span>/)
  assert.doesNotMatch(view, /邀请码：\{\{ normalizedInviteCode \}\}/)
  assert.match(view, /return '注册并登录'/)
  assert.match(view, /registerPublic\(\{[\s\S]*\.\.\.registerForm,[\s\S]*inviteCode: hasInviteCode\.value \? normalizedInviteCode\.value : '',[\s\S]*captchaId: captcha\.id,[\s\S]*captchaCode: captcha\.code/)
  assert.match(view, /if \(isRegister\.value\) await router\.replace\(\{ name: 'Home' \}\)/)
  assert.doesNotMatch(view, /直推奖|团队分红|奖金比例|推广收益/)
})
