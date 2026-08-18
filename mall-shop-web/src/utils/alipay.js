export const submitTrustedAlipayForm = (payHtml) => {
  const parsed = new DOMParser().parseFromString(String(payHtml || ''), 'text/html')
  const sourceForm = parsed.querySelector('form')
  if (!sourceForm) throw new Error('支付宝支付页生成失败，请稍后重试')

  let action
  try {
    action = new URL(sourceForm.getAttribute('action') || '')
  } catch {
    throw new Error('支付宝支付地址无效，请稍后重试')
  }
  const allowedHosts = new Set(['openapi.alipay.com', 'openapi.alipaydev.com'])
  if (action.protocol !== 'https:' || !allowedHosts.has(action.hostname)) {
    throw new Error('支付宝支付地址校验失败，请稍后重试')
  }

  const form = document.createElement('form')
  form.method = 'post'
  form.action = action.toString()
  form.style.display = 'none'
  sourceForm.querySelectorAll('input[name]').forEach((sourceInput) => {
    const input = document.createElement('input')
    input.type = 'hidden'
    input.name = sourceInput.getAttribute('name') || ''
    input.value = sourceInput.getAttribute('value') || ''
    form.appendChild(input)
  })
  document.body.appendChild(form)
  form.submit()
}
