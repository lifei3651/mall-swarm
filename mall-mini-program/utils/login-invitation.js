const invite = require('./invite')
const request = require('./request')

const data = { inviteExpanded: false, inviteCode: '', inviteFromLink: false, inviteReady: true,
  inviteBusy: false, inviterName: '', inviteError: '', inviteConflict: false, candidateName: '', candidateValid: false }
function fingerprint() { return JSON.stringify(invite.getState()) }
async function preview(code) {
  const result = await request({ url: `/shop/public/inviter-preview/${encodeURIComponent(code)}` })
  if (!result || result.valid !== true || typeof result.nickname !== 'string' || !result.nickname.trim()) throw new Error('未找到有效邀请人，请核对邀请码，或选择不使用邀请')
  return result.nickname.trim().slice(0, 40)
}
const methods = {
  syncInvitation(force = false) {
    const state = invite.getState(), key = JSON.stringify(state)
    if (!force && key === this._inviteFingerprint) return
    this._inviteFingerprint = key
    this._verifiedInviteCode = ''
    this._inviteSequence = (this._inviteSequence || 0) + 1
    this.setData({ ...data, inviteCode: state.selected?.code || '', inviteFromLink: state.selected?.source === 'link',
      inviteExpanded: !!state.selected || !!state.expired || !!state.invalid, inviteReady: !state.selected && !state.expired && !state.invalid, inviteConflict: !!state.candidate,
      inviteError: state.invalid ? '收到的邀请格式不正确，请重新扫码、填写邀请码，或明确选择不使用邀请'
        : state.expired ? '先前邀请已过期或无法确认时间，请重新扫码、填写邀请码，或明确选择不使用邀请' : '' })
    if (state.selected) return this.checkInvitation()
  },
  toggleInvitation() { if (!this.data.submitting) this.setData({ inviteExpanded: !this.data.inviteExpanded }) },
  invitationInput(event) {
    if (this.data.submitting || this.data.inviteFromLink || this.data.inviteConflict) return
    const value = String(event.detail.value || '').trim().toUpperCase().slice(0, 8)
    this._inviteSequence = (this._inviteSequence || 0) + 1
    this._verifiedInviteCode = ''
    this.setData({ inviteCode: value, inviteReady: false, inviteBusy: false, inviterName: '', inviteError: '' })
  },
  async checkInvitation() {
    if (this._inactive || this.data.submitting) return
    const code = invite.normalizeInviteCode(this.data.inviteCode)
    if (!code) { this.setData({ inviteReady: false, inviteError: '请输入8位字母或数字邀请码，或选择不使用邀请' }); return }
    const sequence = this._inviteSequence = (this._inviteSequence || 0) + 1
    const stateKey = fingerprint(), candidate = invite.getState().candidate
    this._verifiedInviteCode = ''
    this.setData({ inviteBusy: true, inviteReady: false, inviteError: '', inviterName: '', candidateName: '', candidateValid: false })
    try {
      let name = '', failure = ''
      try { name = await preview(code) } catch (error) { failure = error.message || '邀请人核对失败，请重试' }
      let candidateName = '', candidateValid = false
      if (candidate) {
        if (candidate.invalid) candidateName = '新邀请格式不正确，可保留当前邀请'
        else { try { candidateName = await preview(candidate.code); candidateValid = true } catch (_) { candidateName = '新邀请未核对成功，请保留当前邀请或重试' } }
      }
      if (this._inactive || sequence !== this._inviteSequence) return
      if (stateKey !== fingerprint()) { this.syncInvitation(true); return }
      if (name && !this.data.inviteFromLink && !candidate) {
        invite.setManualInvite(code)
        this._inviteFingerprint = fingerprint()
      }
      this._verifiedInviteCode = candidate || !name ? '' : code
      this.setData({ inviterName: name, inviteError: failure, candidateName, candidateValid, inviteConflict: !!candidate, inviteReady: !candidate && !!name })
    } catch (error) {
      if (!this._inactive && sequence === this._inviteSequence) this.setData({ inviteReady: false, inviteError: error.message || '邀请人核对失败，请重试' })
    } finally {
      if (!this._inactive && sequence === this._inviteSequence) this.setData({ inviteBusy: false })
    }
  },
  chooseInvitation(event) {
    if (this.data.submitting || this.data.inviteBusy) return
    const useNew = event.currentTarget.dataset.choice === 'new'
    if (useNew && !this.data.candidateValid) return
    invite.chooseCandidate(useNew)
    return this.syncInvitation(true)
  },
  clearInvitation() {
    if (this.data.submitting) return
    invite.clearPendingInvite()
    this.syncInvitation(true)
  },
  invitationReady() {
    if (this._inviteFingerprint !== fingerprint()) { this.syncInvitation(true); return false }
    if (this.data.inviteBusy || this.data.inviteConflict || !this.data.inviteReady) {
      wx.showToast({ title: '请先核对邀请人，或选择不使用邀请', icon: 'none' })
      return false
    }
    return true
  }
}
module.exports = { data, methods }
