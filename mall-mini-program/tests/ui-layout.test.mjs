import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = (file) => readFileSync(new URL(`../${file}`, import.meta.url), 'utf8')

test('全部已注册页面保留后台主题绑定，统一按钮支持换行和禁用态', () => {
  const app = JSON.parse(source('app.json'))
  for (const page of app.pages) {
    const view = page === 'pages/login/index' ? 'components/login-sheet/index.wxml' : `${page}.wxml`
    assert.match(source(view), /\{\{themeStyle\}\}/, page)
  }
  const css = source('app.wxss')
  assert.match(css, /--control-height:\s*88rpx;/)
  assert.match(css, /\.primary-button, \.secondary-button, \.danger-button, \.ui-button\s*\{[^}]*min-height: var\(--control-height\);[^}]*white-space: normal;/)
  assert.match(css, /\.primary-button\[disabled\], \.secondary-button\[disabled\], \.danger-button\[disabled\], \.ui-button\[disabled\]/)
})

test('地址字段有常驻标签，长地址和订单备注用多行输入且保留原提交绑定', () => {
  const address = source('pages/address/index.wxml')
  for (const label of ['收货人', '联系电话', '所在地区', '详细地址']) {
    assert.ok(address.includes(`<text class="field-label">${label}</text>`))
  }
  assert.match(address, /<textarea[^>]*data-field="detailAddress"[^>]*maxlength="200"[^>]*bindinput="input"/)
  assert.match(source('pages/checkout/index.wxml'), /<textarea[^>]*maxlength="500"[^>]*value="\{\{remarkDraft\}\}"[^>]*bindinput="remarkInput"/)
})

test('关键详情和表单页为安全区留白，订单操作固定单行且主操作最右', () => {
  for (const page of ['login', 'address', 'checkout', 'product', 'order-detail', 'payout', 'messages', 'subscriptions', 'account-security']) {
    const stylesheet = page === 'login' ? 'components/login-sheet/index.wxss' : `pages/${page}/index.wxss`
    assert.match(source(stylesheet), /env\(safe-area-inset-bottom\)/, page)
  }
  const view = source('pages/orders/index.wxml')
  const styles = source('app.wxss')
  assert.match(view, /class="order-actions ui-action-bar ui-order-actions"/)
  assert.match(styles, /\.ui-order-actions\s*\{[^}]*flex-wrap:\s*nowrap;[^}]*justify-content:\s*flex-end;/)
  assert.match(styles, /\.ui-order-action--primary\s*\{[^}]*order:\s*2;/)
})

test('订单操作按钮覆盖小程序原生自动外边距，单个和多个按钮都靠右相邻', () => {
  const styles = source('app.wxss')
  const list = source('pages/orders/index.wxml')
  const detail = source('pages/order-detail/index.wxml')
  const buttonRule = styles.match(/\.ui-action-button, \.ui-order-action\s*\{([^}]*)\}/)?.[1] || ''
  assert.match(buttonRule, /margin-left:\s*0\s*!important;/)
  assert.match(buttonRule, /margin-right:\s*0\s*!important;/)
  assert.match(styles, /\.ui-order-actions\s*\{[^}]*justify-content:\s*flex-end;[^}]*gap:\s*12rpx;/)
  assert.match(list, /class="order-actions ui-action-bar ui-order-actions"/)
  assert.match(detail, /class="product-actions ui-action-bar"/)
  assert.ok(list.indexOf('>再买一单</button>') < list.indexOf('>确认收货</button>'))
})

test('普通操作按钮由共用层固定为秀气自适应尺寸，复制为文字按钮', () => {
  const styles = source('app.wxss')
  const list = source('pages/orders/index.wxml')
  const detail = source('pages/order-detail/index.wxml')
  assert.match(styles, /--action-height:\s*56rpx;/)
  assert.match(styles, /--action-font-size:\s*26rpx;/)
  assert.match(styles, /--action-padding-x:\s*24rpx;/)
  assert.match(styles, /\.ui-action-button, \.ui-order-action\s*\{[^}]*flex:\s*none;[^}]*width:\s*auto\s*!important;/)
  assert.match(styles, /\.ui-action-bar, \.ui-action-group\s*\{[^}]*flex-wrap:\s*nowrap;[^}]*justify-content:\s*flex-end;/)
  assert.doesNotMatch(styles, /\.ui-utility-button--wide/)
  assert.match(detail, /class="ui-copy-action"[^>]*data-id="\{\{item\.order\.id\}\}"[^>]*bindtap="copyOrderNo"[^>]*>复制<\/button>/)
  assert.match(detail, /class="copy-inline ui-copy-action"[^>]*>复制单号<\/button>/)
  assert.doesNotMatch(list, /class="[^"]*primary-button[^"]*"[^>]*>去评价<\/button>/)
})

test('小程序订单详情按状态、本人收入、物流收货人、商品和全部信息顺序收口', () => {
  const detail = source('pages/order-detail/index.wxml')
  const positions = [
    'class="status-hero"',
    'class="income-card ui-card"',
    'class="section-card ui-card fulfillment-card',
    'class="section-card ui-card product-card"',
    'class="section-card ui-card amount-card"',
    'class="section-card ui-card all-order-info"',
  ].map((needle) => detail.indexOf(needle))
  assert.ok(positions.every((position) => position >= 0))
  assert.deepEqual([...positions].sort((a, b) => a - b), positions)
  assert.match(detail, /item\.memberIncome/)
  assert.match(detail, /class="recipient-summary"/)
  assert.match(detail, /class="item-service-tags"/)
  assert.match(detail, /售后期截止时间/)
  assert.match(detail, />查看物流<\/button>/)
  assert.match(detail, />还想买<\/button>/)
  assert.match(detail, />再买一单<\/button>/)
  assert.doesNotMatch(detail, /运费险/)
})

test('订单列表共用清晰商品快照卡片且操作集中在同一行', () => {
  const view = source('pages/orders/index.wxml')
  const logic = source('utils/order-list.js')
  assert.match(logic, /payLabel:\s*'立即支付'/)
  assert.match(logic, /cancelLabel:\s*'取消订单'/)
  assert.doesNotMatch(logic, /支付全部子单|取消联合订单/)
  const styles = source('app.wxss')
  for (const row of ['ui-order-header', 'ui-order-product', 'ui-order-logistics', 'ui-order-summary', 'ui-order-actions']) {
    assert.match(view, new RegExp(`class="[^"]*${row}`), row)
    assert.match(styles, new RegExp(`\\.${row}`), row)
  }
  assert.match(view, /class="ui-order-product" wx:for="\{\{item\.items\}\}"/)
  assert.match(view, /class="ui-order-product-name"/)
  assert.match(view, /class="ui-order-product-spec"/)
  assert.match(view, /class="ui-order-service-tags"/)
  assert.match(view, /零售价 ¥\{\{line\.retailPriceText\}\}/)
  assert.match(view, /class="ui-copy-action ui-order-copy"/)
  assert.match(styles, /\.ui-order-product > image\s*\{[^}]*width:\s*168rpx;[^}]*height:\s*168rpx;/)
  assert.match(view, />查看物流<\/button>/)
  assert.match(view, />退换\/售后<\/button>/)
  assert.match(view, />再买一单<\/button>/)
  assert.match(view, /class="ui-order-logistics"[\s\S]*未收到 \/ 拒收[\s\S]*class="ui-order-summary"/)
  assert.match(view, /ui-order-status is-\{\{item\.statusTone\}\}/)
  assert.match(logic, /statusTone:\s*row\.order\.status === 4 \? 'cancelled'/)
  assert.match(styles, /\.ui-order-status\.is-cancelled\s*\{[^}]*#7b8492[^}]*#f0f2f4/)
  assert.match(styles, /\.ui-order-status\.is-active\s*\{[^}]*var\(--brand\)[^}]*var\(--brand-soft\)/)
})

test('提现金额用原生 text 组件，账号昵称主次操作位于输入框之后', () => {
  const payout = source('pages/payout/index.wxml')
  assert.doesNotMatch(payout, /<\/?strong/)
  assert.match(payout, /<text class="payout-amount">¥\{\{item.amountText\}\}<\/text>/)
  const account = source('pages/account-security/index.wxml')
  assert.ok(account.indexOf('name="nickname"') < account.indexOf('bindtap="enableWechatNickname"'))
  assert.match(account, /form-type="submit"[^>]*>保存昵称/)
})

test('提现资格提示只保留统一主操作，不显示无效客服或手动刷新按钮', () => {
  const withdraw = source('pages/withdraw/index.wxml')
  const requirement = withdraw.match(/<view wx:if="\{\{blockReason\}\}" class="card requirement">([\s\S]*?)<\/view>/)?.[1] || ''
  assert.match(requirement, /class="primary-button" bindtap="security">前往支付安全<\/button>/)
  assert.doesNotMatch(requirement, /已完成，刷新状态|open-type="contact"|联系商城客服|secondary-button/)
  assert.equal((requirement.match(/<button\b/g) || []).length, 1)
})

test('首页和分类已售罄提示覆盖图片居中显示并提高字号', () => {
  const home = source('pages/home/index.wxss')
  const category = source('pages/category/index.wxss')
  assert.match(home, /\.sold-out-mask\s*\{[^}]*inset: 0;[^}]*align-items: center;[^}]*justify-content: center;[^}]*font-size: 28rpx;/)
  assert.match(category, /\.sold-out-label\s*\{[^}]*inset: 0;[^}]*align-items: center;[^}]*justify-content: center;[^}]*font-size: 28rpx;/)
  assert.doesNotMatch(category, /\.sold-out-label\s*\{[^}]*bottom: 0;/)
})

test('分类列表结束提示紧跟商品收口，不复用大间距空状态', () => {
  const view = source('pages/category/index.wxml')
  const category = source('pages/category/index.wxss')
  assert.match(view, /class="category-list-end">已显示全部商品 · 共 \{\{total\}\} 件/)
  assert.doesNotMatch(view, /class="empty">已显示全部商品/)
  assert.match(category, /\.category-list-end\s*\{[^}]*padding: 24rpx 18rpx 32rpx;[^}]*text-align: center;/)
  const rule = category.match(/\.category-list-end\s*\{([^}]*)\}/)?.[1] || ''
  assert.doesNotMatch(rule, /(?:^|;)\s*(?:min-height|height|flex)\s*:/)
})

test('首页和分类左栏的长分类名称最多显示两行且不撑乱布局', () => {
  const home = source('pages/home/index.wxss')
  const categoryView = source('pages/category/index.wxml')
  const category = source('pages/category/index.wxss')
  assert.match(home, /\.category-item > text\s*\{[^}]*min-height: 64rpx;[^}]*-webkit-line-clamp: 2;/)
  assert.match(categoryView, /class="side-item-label">\{\{item\.categoryName\}\}<\/text>/)
  assert.match(category, /\.side-item\s*\{[^}]*height: 112rpx;[^}]*align-items: center;[^}]*justify-content: center;/)
  assert.match(category, /\.side-item-label\s*\{[^}]*-webkit-line-clamp: 2;[^}]*overflow-wrap: anywhere;/)
})
