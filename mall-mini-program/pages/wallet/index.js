const request = require('../../utils/request')
const auth = require('../../utils/auth')
const theme = require('../../utils/theme')
const format = require('../../utils/format')
const capabilities = require('../../utils/member-capabilities')
const validMoney = (value) => value !== null && value !== undefined && value !== '' && Number.isFinite(Number(value)) && Number(value) >= 0
Page({
  data: { ...theme.pageData(), loading: true, error: '', balance: '--', flows: [], membershipLabel: '', membershipError: '' },
  onShow() { theme.apply(this); if (auth.requireLogin('/pages/wallet/index')) return this.load(); this.setData({ loading: false, balance: '--', flows: [] }) },
  onHide() { this.version = (this.version || 0) + 1; this.setData({ balance: '--', flows: [], membershipLabel: '', membershipError: '' }) },
  onUnload() { this.onHide() },
  async load() {
    const version = this.version = (this.version || 0) + 1
    this.setData({ loading: true, error: '', membershipLabel: '', membershipError: '' })
    // A rights lookup failure cannot hide legitimate personal funds or manufacture team eligibility.
    capabilities.load().then((value) => {
      if (version === this.version && value.ready) this.setData({ membershipLabel: value.membershipActive ? '会员服务已开通' : '购物账号' })
    }).catch(() => { if (version === this.version) this.setData({ membershipError: '会员身份暂未核对，请稍后重试；本人资金记录不受影响' }) })
    try {
      const [summary, records] = await Promise.all([request({ url: '/shop/wallet/summary' }), request({ url: '/shop/wallet/flows' })])
      if (version !== this.version) return
      if (!summary || !validMoney(summary.balance) || !Array.isArray(records)
        || records.some((row) => !row || ![row.amount, row.balanceBefore, row.balanceAfter].every(validMoney) || ![1, 2, 3, 4, 5].includes(Number(row.changeType)))) throw new Error('钱包信息不完整，请重试')
      this.setData({ balance: format.money(summary.balance), flows: records.map((row) => ({
        id: format.identifier(row.id), title: row.remark || ({ 1: '入账', 2: '支付', 3: '转出', 4: '转入', 5: '扣减' })[row.changeType] || '余额变动',
        time: String(row.createTime || '').replace('T', ' ').slice(0, 16),
        amount: `${[1, 4].includes(Number(row.changeType)) ? '+' : '-'}${format.money(row.amount)}`,
        before: format.money(row.balanceBefore), after: format.money(row.balanceAfter)
      })) })
    } catch (error) { if (version === this.version) this.setData({ error: error.message || '钱包加载失败', balance: '--', flows: [] }) }
    finally { if (version === this.version) this.setData({ loading: false }) }
  },
  history() { wx.navigateTo({ url: '/pages/payout/index?history=1' }) },
  help() { wx.navigateTo({ url: '/pages/legal/index?type=contact' }) }
})
