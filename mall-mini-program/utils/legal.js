const titles = { agreement: '用户服务协议', privacy: '隐私政策', 'after-sale': '交易与售后规则', faq: '常见问题', license: '经营资质', contact: '联系客服' }
const miniPrivacy = '微信资料便捷填写补充说明\n\n1. 头像、昵称均为可选资料。仅在你主动选择后读取；选择头像即提交更新，昵称须点击保存才提交。头像保存在账号专属的非公开目录，仅本人登录后可读取。昵称用于账号识别；发布商品评价时按商城规则脱敏展示。\n\n2. 微信收货地址仅在点击导入并由你选择后读取收货人姓名、电话及省市区、街道门牌信息。先回填表单，经你核对并点击保存才提交商城，不自动覆盖已有或默认地址。为配送订单，必要收货信息会提供给履约商家和实际承运服务商，具体接收方及处理规则以商城完整隐私政策及订单为准。\n\n3. 微信手机号仅在你主动授权快捷注册或绑定时读取。拒绝头像、昵称或地址便捷授权不影响浏览；昵称及地址可手动填写。不自动获取精确位置，不持续定位，也不批量读取微信通讯录、相册或地址簿。\n\n4. 售后图片由你主动选择后，在提交申请时上传，用于核实订单售后问题。仅本人和获授权处理该订单的后台人员可访问，请勿上传无关人员、证件或其他非必要敏感信息。\n\n5. 图片和资料通过HTTPS传输，密码与验证码另经商城统一敏感字段加密处理。头像使用固定文件名覆盖，每个账号至多保留JPG、PNG各一份；客户端临时预览在离开资料页后尝试清理。账号及交易资料按下方政策和法定必要期限保存。需要查阅、更正、删除或撤回授权，可在微信小程序设置中管理权限，并联系商城客服处理已保存资料。撤回微信授权不等于自动删除商城中已保存的订单或地址。'
function resolveText(value, config = {}) {
  const replacements = {
    companyName: config.companyName || '本商城经营主体', brandName: config.brandName || '本商城',
    unifiedSocialCreditCode: config.unifiedSocialCreditCode || '以营业执照公示信息为准',
    companyAddress: config.companyAddress || '以经营资质公示信息为准', servicePhone: config.servicePhone || '商城客服',
    serviceEmail: config.serviceEmail || '客服邮箱', serviceHours: config.serviceHours || '以客服实际在线时间为准',
    thirdPartyServices: config.thirdPartyServices || '支付、短信、云服务及订单实际承运的物流服务商'
  }
  return String(value || '').replace(/\{\{\s*(\w+)\s*\}\}|\{(company|phone|email)\}/g, (match, key, legacy) => replacements[key || ({ company: 'companyName', phone: 'servicePhone', email: 'serviceEmail' })[legacy]] || match)
}
function content(type, config = {}) {
  return resolveText(config[({ agreement: 'userAgreement', privacy: 'privacyPolicy', 'after-sale': 'afterSalePolicy' })[type]], config)
}
function faqs(config = {}) {
  try {
    const items = typeof config.faqs === 'string' ? JSON.parse(config.faqs) : config.faqs
    return (Array.isArray(items) ? items : []).filter((item) => item && String(item.question || '').trim() && String(item.answer || '').trim())
      .map((item) => ({ question: String(item.question), answer: resolveText(item.answer, config) }))
  } catch (_) { return [] }
}
module.exports = { titles, resolveText, content, faqs, miniPrivacy }
