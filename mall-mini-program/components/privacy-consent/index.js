const feedback = require('../../utils/feedback')
const privacy = require('../../utils/privacy')
Component({
  data: { visible: false },
  lifetimes: {
    attached() { this.resolvers = []; privacy.install() },
    detached() { this.finish(false) }
  },
  methods: {
    open(resolve) { this.resolvers.push(resolve); feedback.update(this, { visible: true }) },
    agree() { this.finish(true) },
    decline() { this.finish(false) },
    finish(agreed) {
      const pending = this.resolvers || []
      this.resolvers = []
      feedback.update(this, { visible: false })
      pending.forEach((resolve) => resolve(agreed
        ? { event: 'agree', buttonId: 'privacy-agree' }
        : { event: 'disagree' }))
    },
    openContract() {
      wx.openPrivacyContract({ fail: () => feedback.toast({ title: '微信隐私指引暂不可用，请稍后重试', icon: 'none' }) })
    },
    stop() {}
  }
})
