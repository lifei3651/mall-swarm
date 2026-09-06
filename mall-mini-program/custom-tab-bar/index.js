const theme = require('../utils/theme')
const feedback = require('../utils/feedback')
Component({
  data: { ...theme.pageData(), active: 'home', hidden: false },
  lifetimes: { attached() { this.refresh() } },
  pageLifetimes: { show() { this.refresh() } },
  methods: {
    refresh(palette = theme.pageData()) {
      const pages = getCurrentPages()
      const page = pages[pages.length - 1]
      this.setData({ ...palette, active: String(page && page.route || '').split('/')[1] || 'home',
        hidden: Boolean(page && page.data && page.data.loginVisible) })
    },
    navigate(event) {
      if (this.data.hidden) return
      const item = this.data.bottomNav.find((entry) => entry.type === event.currentTarget.dataset.type)
      if (!item || item.type === this.data.active) return
      // 订单保持普通页面，兼容原来的筛选参数、微信订单找回及登录回跳。
      const method = item.type === 'orders' ? 'navigateTo' : 'switchTab'
      wx[method]({ url: item.path, fail: () => feedback.toast({ title: '页面暂时无法打开，请重试', icon: 'none' }) })
    }
  }
})
