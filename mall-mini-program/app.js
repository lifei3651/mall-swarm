const invite = require('./utils/invite')

App({
  globalData: {
    brand: null
  },
  onLaunch(options) {
    invite.captureLaunchInvite(options)
  },
  onShow(options) {
    invite.captureLaunchInvite(options)
  }
})
