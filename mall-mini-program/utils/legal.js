const titles = { agreement: '用户服务协议', privacy: '隐私政策', 'after-sale': '交易与售后规则', faq: '常见问题', license: '经营资质', contact: '联系客服' }
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
module.exports = { titles, resolveText, content, faqs }
