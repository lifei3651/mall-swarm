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

test('public storefront registration and SMS login use separate fixed-purpose endpoints', async () => {
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
  assert.match(registrationSender, /if \(!captcha\.id \|\| !captcha\.code\)/)
  assert.match(registrationSender, /await sendSmsCode\(registerForm\.phone, 1, \{ captchaId: captcha\.id, captchaCode: captcha\.code \}\)/)
  assert.doesNotMatch(registrationSender, /sendLoginSmsCode/)
})

test('public storefront shows captcha only for registration and password login', async () => {
  const view = await readProjectFile('src/surfaces/public/PublicLoginView.vue')

  assert.match(view, /<div v-if="needsCaptcha" class="captcha-block">/)
  assert.match(view, /const needsCaptcha = computed\(\(\) => isRegister\.value \|\| loginType\.value === 'password'\)/)
  assert.match(view, /if \(!captcha\.id \|\| !captcha\.code\) return '请输入图形验证码'/)
  assert.doesNotMatch(view, /const needsCaptcha = true/)
})

test('invite QR registration previews inviter and binds in the same registration submission', async () => {
  const [view, api] = await Promise.all([
    readProjectFile('src/surfaces/public/PublicLoginView.vue'),
    readProjectFile('src/api/shop.js'),
  ])

  assert.match(view, /route\.query\.inviteCode \|\| route\.query\.code/)
  assert.match(view, /await getInviterPreview\(inviteCodeFromUrl\.value\)/)
  assert.match(api, /url: `\/shop\/public\/inviter-preview\/\$\{encodeURIComponent\(inviteCode\)\}`/)
  assert.match(view, /提交注册后将一次性建立邀请关系，注册人不能自行修改/)
  assert.match(view, /注册并绑定邀请人/)
  assert.match(view, /registerPublic\(\{ \.\.\.registerForm, inviteCode: hasInviteLink\.value \? inviteCodeFromUrl\.value : '' \}\)/)
  assert.doesNotMatch(view, /直推奖|团队分红|奖金比例|推广收益/)
})
