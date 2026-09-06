function transportError(error, action = '请求', domainType = 'request') {
  const raw = String(error && error.errMsg || '')
  if (/url not in domain|not.*domain list|domain.*not.*list|不在.*合法域名/i.test(raw)) return `${action}未能连接：商城尚未配置微信 ${domainType} 合法域名，请联系商城客服处理。`
  if (/timeout|timed out|超时/i.test(raw)) return `${action}超时，请稍后重试；若已提交操作，请先检查处理结果，勿重复提交。`
  if (/ssl|tls|certificate|cert_/i.test(raw)) return `${action}的安全连接失败，商城服务器证书需要检查，请联系客服。`
  if (/file.*not.*exist|no such file|enoent/i.test(raw)) return '所选图片已失效，请重新选择头像。'
  if (/cancel/i.test(raw)) return `已取消${action}，未提交新的操作。`
  return `${action}未完成，暂时无法连接商城服务。请确认网络可用后重试；仍失败请联系商城客服。`
}
function statusError(status, action = '请求') {
  if (status === 401) return '登录已过期，请先登录后再操作。'
  if (status === 403) return `${action}未获准，请核对账号权限或联系商城客服。`
  if (status === 413) return '图片超过上传限制，请选择2MB以内的JPG或PNG图片。'
  if (status === 415) return '图片格式不支持，请选择JPG或PNG图片。'
  if (status === 429) return '操作过于频繁，请稍后再试。'
  if (status >= 500) return `${action}服务暂不可用，请稍后重试或联系商城客服。`
  return `${action}未完成，请稍后重试。`
}
module.exports = { transportError, statusError }
