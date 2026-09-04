/**
 * 灵启商城基座体验版连接当前正式 HTTPS API；客户派生工具会重新写回安全占位域名，
 * 客户项目再改为自己的域名，并同步加入微信公众平台“request 合法域名”。
 */
module.exports = Object.freeze({
  API_BASE_URL: 'https://lingqimall.com/api',
  PRIVACY_CONSENT_VERSION: 'MINI_PROGRAM_PRIVACY_V1'
})
