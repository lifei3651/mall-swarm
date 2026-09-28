// Ordinary browsing/loading/paging actions must inherit one color contract.
// Icon controls, choices, text links and destructive/transaction actions are separate roles.
export function auditSecondaryButtons(source) {
  const failures = []
  for (const [, attrs, body] of source.matchAll(/<button\b((?:[^>"']|"[^"]*"|'[^']*')*)>([\s\S]*?)<\/button>/g)) {
    if (/<\w/.test(body) || !/(查看全部商品|加载更多|更多商品|上一页|下一页|重新加载|重新搜索|重新读取短信设置|查看更多评价|^\s*重试\s*$)/.test(body)) continue
    const classes = attrs.match(/(?:^|\s)class="([^"]*)"/)?.[1] || ''
    if (!/(?:^|\s)(?:secondary-button|ui-button--secondary|ui-button--assist|ui-utility-button)(?:\s|$)/.test(classes)
        && !/(?:^|\s)btn\s+secondary(?:\s|$)/.test(classes)) {
      failures.push(`普通操作必须接入共用次按钮颜色：${body.trim()}`)
    }
  }
  return failures
}
