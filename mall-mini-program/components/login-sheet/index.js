const flow = require('../../utils/login-flow')
const session = require('../../utils/session')
const { data, ...methods } = flow

Component({
  properties: { presentation: { type: String, value: 'sheet' } },
  data: { ...data, visible: false },
  lifetimes: { detached() { this.onUnload() } },
  pageLifetimes: { show() { if (this.data.visible) this.onShow() } },
  methods: {
    ...methods,
    open(redirect = '') {
      if (this.data.visible || this.data.submitting) return
      this._runtimeChecked = false
      this.setData({ ...data, visible: true, logoFailed: false })
      return this.onLoad({ redirect: encodeURIComponent(redirect) })
    },
    close() {
      if (this.data.submitting) return
      this.onUnload()
      this.setData({ visible: false, agreed: false, enabled: false, phoneEnabled: false })
      this.triggerEvent('close')
    },
    finish() {
      if (!session.getToken() || !this.data.visible || this._inactive) return
      this._inactive = true
      this.setData({ visible: false, agreed: false, submitting: false })
      this.triggerEvent('success', { redirect: this.redirect || '' })
    },
    stop() {}
  }
})
