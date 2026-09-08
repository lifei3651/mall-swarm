const invite = require('./utils/invite')

App({
  globalData: {
    brand: null
  },
  onLaunch(options) {
    invite.captureLaunchInvite(options)
    require('./utils/app-update').install()
  },
  onShow(options) {
    invite.captureLaunchInvite(options)
  }
})
