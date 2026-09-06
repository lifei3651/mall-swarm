const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const auth = require('../../utils/auth')
const theme = require('../../utils/theme')
const format = require('../../utils/format')
const capabilities = require('../../utils/member-capabilities')
const validMoney = (value) => value !== null && value !== undefined && value !== '' && Number.isFinite(Number(value)) && Number(value) >= 0
Page({
  data: { ...theme.pageData(), loading: true, error: '', balance: '--', flows: [], membershipLabel: '', membershipError: '', issuedBonus: '--', pendingBonus: '--', bonusLoading: false, bonusError: '' },
  onShow() { theme.apply(this); if (auth.requireLogin('/pages/wallet/index')) return this.load(); feedback.update(this, { loading: false, balance: '--', flows: [] }) },
  onHide() { this.version = (this.version || 0) + 1; feedback.update(this, { balance: '--', flows: [], membershipLabel: '', membershipError: '', issuedBonus: '--', pendingBonus: '--' }) },
  onUnload() { this.onHide() },
  async load() {
    const version = this.version = (this.version || 0) + 1
    feedback.update(this, { loading: true, error: '', membershipLabel: '', membershipError: '' })
    this.loadBonus(version)
    // A rights lookup failure cannot hide legitimate personal funds or manufacture team eligibility.
    capabilities.load().then((value) => {
      if (version === this.version && value.ready) feedback.update(this, { membershipLabel: value.membershipActive ? '会员服务已开通' : '购物账号' })
    }).catch(() => { if (version === this.version) feedback.update(this, { membershipError: '会员身份暂未核对，请稍后重试；本人资金记录不受影响' }) })
    try {
      const [summary, records] = await Promise.all([request({ url: '/shop/wallet/summary' }), request({ url: '/shop/wallet/flows' })])
      if (version !== this.version) return
      if (!summary || !validMoney(summary.balance) || !Array.isArray(records)
        || records.some((row) => !row || ![row.amount, row.balanceBefore, row.balanceAfter].every(validMoney) || ![1, 2, 3, 4, 5].includes(Number(row.changeType)))) throw new Error('钱包信息不完整，请重试')
      feedback.update(this, { balance: format.money(summary.balance), flows: records.map((row) => ({
        id: format.identifier(row.id), title: row.remark && /[\u3400-\u9fffA-Za-z]/.test(row.remark) ? row.remark : ({ 1: '余额入账', 2: '余额支付', 3: '余额转出', 4: '余额转入', 5: '余额扣减' })[row.changeType],
        time: String(row.createTime || '').replace('T', ' ').slice(0, 16),
        amount: `${[1, 4].includes(Number(row.changeType)) ? '+' : '-'}${format.money(row.amount)}`,
        before: format.money(row.balanceBefore), after: format.money(row.balanceAfter)
      })) })
    } catch (error) { if (version === this.version) feedback.update(this, { error: error.message || '钱包加载失败', balance: '--', flows: [] }) }
    finally { if (version === this.version) feedback.update(this, { loading: false }) }
  },
  history() { wx.navigateTo({ url: '/pages/payout/index?history=1' }) },
  async loadBonus(version = this.version) {
    feedback.update(this, { bonusLoading: true, bonusError: '', issuedBonus: '--', pendingBonus: '--' })
    try {
      const result = await request({ url: '/shop/wechat-mini-program/bonus-summary' })
      if (!result || !validMoney(result.issuedBonus) || !validMoney(result.pendingBonus)) throw new Error('奖金统计暂不可用，请稍后重试')
      if (version === this.version) feedback.update(this, { issuedBonus: format.money(result.issuedBonus), pendingBonus: format.money(result.pendingBonus) })
    } catch (_) { if (version === this.version) feedback.update(this, { bonusError: '奖金统计暂不可用，点击重试；余额记录不受影响' }) }
    finally { if (version === this.version) feedback.update(this, { bonusLoading: false }) }
  },
  retryBonus() { if (!this.data.bonusLoading) this.loadBonus() },
  bonusInfo(event) {
    const issued = event.currentTarget.dataset.type === 'issued'
    feedback.notice(issued ? '已发放：已完成结算并记入余额的奖金，已取消、已退回的记录不计入。不是当前可提现余额，也不是微信已经收款的金额。' : '待发放：尚未结算入账的奖金。订单须满足售后期及结算规则，实际结算成功后才转为已发放；期间退款可能调整金额。', issued ? '已发放奖金' : '待发放奖金')
  },
  withdraw() { wx.navigateTo({ url: '/pages/withdraw/index' }) },
  help() { wx.navigateTo({ url: '/pages/legal/index?type=contact' }) }
})
