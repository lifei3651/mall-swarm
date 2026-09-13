const flow = require('../../utils/login-flow')
const session = require('../../utils/session')
const profile = require('../../utils/registration-profile')
const { data, ...methods } = flow

Component({
  properties: { presentation: { type: String, value: 'sheet' } },
  data: { ...data, ...profile.data, visible: false },
  lifetimes: { detached() { this.clearProfile(); this.onUnload() } },
  pageLifetimes: {
    hide() { this._hostHidden = true },
    show() { this._hostHidden = false; if (this._pendingFinish) this.finish(); else if (this.data.visible && !this.data.profileStep) this.onShow() }
  },
  methods: {
    ...methods,
    ...profile.methods,
    open(redirect = '') {
      if (this.data.visible || this.data.submitting) return
      this._runtimeChecked = false
      this._pendingFinish = false
      this._hostHidden = false
      this.clearProfile()
      this.setData({ ...data, visible: true, logoFailed: false })
      return this.onLoad({ redirect: encodeURIComponent(redirect) })
    },
    close() {
      if (this.data.submitting || this.data.profileSaving) return
      if (this.data.profileStep) { this.skipProfile(); return }
      this.onUnload()
      this.setData({ visible: false, agreed: false, enabled: false, phoneEnabled: false })
      this.triggerEvent('close')
    },
    finish() {
      if (!session.getToken() || !this.data.visible || this._inactive) return
      if (this._hostHidden) { this._pendingFinish = true; return }
      this._pendingFinish = false
      this._inactive = true
      const token = session.getToken(), sequence = this._loginSequence
      this.setData({ visible: false, agreed: false, submitting: false, authorizingPhone: false }, () => {
        if (token !== session.getToken() || sequence !== this._loginSequence) return
        this.triggerEvent('success', { redirect: this.redirect || '' })
      })
    },
    stop() {}
  }
})
