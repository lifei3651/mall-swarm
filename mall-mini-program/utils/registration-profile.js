const session = require('./session')
const request = require('./request')
const avatar = require('./member-avatar')
const privacy = require('./privacy')

const data = { profileStep: false, profileStarted: false, profileSaving: false, profileNickname: '', profileNicknameFocus: false, profileAvatar: '', profileError: '' }
const methods = {
  beginProfile() {
    this._profileSequence = (this._profileSequence || 0) + 1
    this._profileToken = session.getToken()
    this._savedProfileNickname = ''
    this.setData({ ...data, profileStep: true })
  },
  profileCurrent() { return !this._inactive && this.data.visible && this._profileToken && this._profileToken === session.getToken() },
  clearProfile() {
    this._profileSequence = (this._profileSequence || 0) + 1
    avatar.release(this.data.profileAvatar)
    this._profileToken = ''
    this._savedProfileNickname = ''
    this.setData({ ...data })
  },
  chooseProfileAvatar(event) {
    const path = event && event.detail && event.detail.avatarUrl
    if (!this.profileCurrent() || this.data.profileSaving || !path) return
    avatar.release(this.data.profileAvatar)
    this.setData({ profileStarted: true, profileAvatar: path, profileNicknameFocus: true, profileError: '' })
  },
  profileNicknameInput(event) {
    if (this.profileCurrent() && !this.data.profileSaving) this.setData({ profileNickname: event.detail.value, profileError: '' })
  },
  profileNicknameBlur() { if (this.profileCurrent()) this.setData({ profileNicknameFocus: false }) },
  skipProfile() {
    if (this.data.profileSaving) return
    const current = this.profileCurrent()
    this.clearProfile()
    if (current) this.finish()
    else this.close()
  },
  async saveProfile(event) {
    if (this.data.profileSaving) return
    if (!this.profileCurrent()) { this.setData({ profileError: '登录状态已变化，请返回后重试' }); return }
    // Form value is authoritative: native nickname moderation can clear the input.
    const nickname = String(event?.detail?.value?.nickname || '').trim().replace(/\s+/g, ' ')
    if (nickname && !/^[\u3400-\u9fffA-Za-z0-9·_\- ]{2,20}$/.test(nickname)) {
      this.setData({ profileError: '昵称需为2至20个字符，支持中文、字母、数字、空格、·、-和_' }); return
    }
    const path = this.data.profileAvatar
    const sequence = this._profileSequence
    const current = () => this.profileCurrent() && sequence === this._profileSequence
    if (!nickname && !path) { this.skipProfile(); return }
    this.setData({ profileSaving: true, profileError: '' })
    try {
      await privacy.requireConsent()
      if (!current()) return
      if (nickname && nickname !== this._savedProfileNickname) {
        const member = await request({ url: '/shop/auth/nickname', method: 'PUT', data: { nickname } })
        if (!current()) return
        this._savedProfileNickname = nickname
        wx.setStorageSync('mall_mini_member', { ...session.getMember(), nickname: member?.nickname || nickname })
      }
      if (path) {
        const avatarUrl = await avatar.upload(path)
        if (!current()) return
        wx.setStorageSync('mall_mini_member', { ...session.getMember(), avatarUrl })
      }
      this.clearProfile()
      this.finish()
    } catch (error) {
      if (current()) this.setData({ profileError: (this._savedProfileNickname && path ? '昵称已保存，头像未完成。' : '') + (error.message || '资料保存失败，请重试或稍后完善') })
    } finally {
      if (!this._inactive && sequence === this._profileSequence) this.setData({ profileSaving: false })
    }
  }
}
module.exports = { data, methods }
