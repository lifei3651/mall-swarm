// Real native taps against the isolated fixture project, never a live account.
import { createRequire } from 'node:module'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import assert from 'node:assert/strict'
const require = createRequire(import.meta.url)
const sdk = require(process.env.MINI_AUTOMATOR_PATH || '/private/tmp/lingqi-native-qa-tools.d9E1K0/node_modules/miniprogram-automator')
const mini = await sdk.connect({ wsEndpoint:'ws://127.0.0.1:9420' })
const out = resolve(process.argv[2] || 'document/qa/2026-09-08-parity-completion/interactions')
await mkdir(out,{recursive:true})
const records = []
const capture = async name => { await mini.screenshot({path:resolve(out,name+'.png')}); records.push({name,screenshot:name+'.png',passed:true}) }
const tap = async (page, selector) => { const element = await page.$(selector); assert.ok(element,selector); await element.tap(); await (await mini.currentPage()).waitFor(400) }
const tapText = async (page, text) => {
  for (const element of await page.$$('button')) if ((await element.text()).trim() === text) {
    await element.tap(); await (await mini.currentPage()).waitFor(500); return
  }
  throw Error('Visible button not found: '+text)
}
const waitRoute = async route => {
  for (let attempt=0; attempt<20; attempt++) {
    const page = await mini.currentPage()
    if (page.path.replace(/^\//,'') === route) return page
    await page.waitFor(250)
  }
  throw Error('Native navigation did not reach '+route)
}
try {
  assert.equal(await mini.evaluate(()=>wx.getStorageSync('mall_mini_access_token')),'LOCAL-UI-ONLY-NO-SERVER-AUTHORITY')
  await mini.evaluate(()=>{
    wx.setStorageSync('qa-settings',{})
    wx.setStorageSync('mall_mini_member',{id:'132',nickname:'本地界面验收',username:'PreviewOnly',phone:'13800000000'})
    wx.setStorageSync('mall_mini_cart_v2:member:132',[{key:'1:11',productId:'1',skuId:'11',productName:'本地商品',salePrice:99,quantity:1,selected:true}])
  })
  let page = await mini.reLaunch('/pages/home/index'); await page.waitFor(400)
  await (await page.$('.search-box input')).input('礼盒'); await page.waitFor(400)
  assert.equal(await page.data('keyword'),'礼盒'); await tap(page,'.search-box button')
  page = await waitRoute('pages/category/index'); await page.waitFor(600); assert.equal(await page.data('searchedKeyword'),'礼盒')
  assert.equal((await page.data('products')).length,1); await capture('search-carried-to-category')
  await tap(page,'.sort-tab[data-mode="price"]'); assert.equal(await page.data('sortMode'),'priceAsc')
  await tap(page,'.sort-tab[data-mode="price"]'); assert.equal(await page.data('sortMode'),'priceDesc'); await capture('price-sort-toggle')
  await tap(page,'.quick-cart-button[data-id="1"]')
  assert.equal((await mini.evaluate(()=>wx.getStorageSync('mall_mini_cart_v2:member:132')))[0].quantity,1)
  await capture('limit-rejection-cart-unchanged'); await mini.native().confirmModal(); await page.waitFor(250)
  page = await mini.reLaunch('/pages/order-detail/index?id=13201'); await page.waitFor(400)
  await tap(page,'.order-info-toggle'); assert.equal(await page.data('expandedOrders.13201'),true)
  await mini.pageScrollTo(99999); await capture('order-information-expanded')
  await tapText(page,'查询 / 刷新物流轨迹'); assert.ok((await page.data('trackingRows')).length); await capture('local-tracking-loaded')
  page = await mini.reLaunch('/pages/address/index'); await page.waitFor(400)
  await tapText(page,'新增地址'); assert.equal(await page.data('showForm'),true)
  await (await page.$('.paste-address textarea')).input('张三 13800000000 湖南省长沙市岳麓区测试路1号')
  await tap(page,'.paste-address button'); assert.equal(await page.data('form.receiverPhone'),'13800000000')
  assert.equal(await page.data('form.receiverName'),'张三'); await mini.pageScrollTo(99999); await capture('address-parsed-not-saved')
  await mini.evaluate(()=>{wx.removeStorageSync('mall_mini_access_token');wx.removeStorageSync('mall_mini_member')})
  page = await mini.reLaunch('/pages/profile/index'); await page.waitFor(400)
  await tap(page,'.profile-header')
  const sheet = await page.$('#login-sheet'); assert.equal(await sheet.data('visible'),true); assert.equal(await sheet.data('agreed'),false)
  await capture('guest-login-sheet-unchecked')
  await tapText(sheet,'其他方式登录 / 注册')
  page = await waitRoute('pages/account-login/index')
  assert.equal(await page.data('agreed'),false); await capture('secondary-account-login')
  await mini.native().confirmModal().catch(()=>{})
  await mini.navigateBack(); page = await mini.currentPage()
  assert.equal(page.path.replace(/^\//,''),'pages/profile/index'); assert.equal(await page.data('loginVisible'),false)
  await capture('cancel-account-login-back-to-profile')
} finally {
  await writeFile(resolve(out,'results.json'),JSON.stringify({at:new Date().toISOString(),kind:'Native UI taps with local fixtures; no real authorization/SMS/payments or saved addresses',records},null,2))
  mini.disconnect()
}
process.exit(0)
