import request from '@/utils/request'

export const listMerchants = (params) => request({ url: '/distribution/merchants', method: 'get', params })
export const saveMerchant = (id, data) => request({
  url: id ? `/distribution/merchants/${id}` : '/distribution/merchants',
  method: id ? 'put' : 'post',
  data,
  adminStepUp: { message: id ? '修改商户经营、收款或结算资料会影响后续交易，请再次验证。' : '新增商户会建立新的经营和结算主体，请再次验证。' },
})
export const updateMerchantStatus = (id, status) => request({ url: `/distribution/merchants/${id}/status`, method: 'put', params: { status }, adminStepUp: { message: '商户状态变更会影响其经营和历史订单处理。' } })
export const updateMerchantControls = (id, data) => request({ url: `/distribution/merchants/${id}/controls`, method: 'put', data, adminStepUp: { message: '商户业务控制会影响销售、履约、提现和结算。' } })
export const getMerchantExitReadiness = (id) => request({ url: `/distribution/merchants/${id}/exit-readiness`, method: 'get' })
export const listMerchantAccounts = (params) => request({ url: '/distribution/merchant-finance/accounts', method: 'get', params })
export const listMerchantSettlements = (params) => request({ url: '/distribution/merchant-finance/settlements', method: 'get', params })
export const listMerchantWithdrawals = (params) => request({ url: '/distribution/merchant-finance/withdrawals', method: 'get', params })
export const listMerchantDepositFlows = (params) => request({ url: '/distribution/merchant-finance/deposit-flows', method: 'get', params })
export const listMerchantLedger = (params) => request({ url: '/distribution/merchant-finance/ledger', method: 'get', params })
export const listMerchantReconciliation = (params) => request({ url: '/distribution/merchant-finance/reconciliation', method: 'get', params })
export const listMerchantWithdrawalEvents = (id) => request({ url: `/distribution/merchant-finance/withdrawals/${id}/events`, method: 'get' })
export const freezeMerchantDeposit = (data) => request({ url: '/distribution/merchant-finance/deposits/freeze', method: 'post', data, adminStepUp: { message: '冻结保证金会减少商户可提现余额，请再次验证。' } })
export const receiveMerchantDeposit = (data) => request({ url: '/distribution/merchant-finance/deposits/receive', method: 'post', data, adminStepUp: { message: '登记线下保证金会增加商户风险账户余额，请核对到账后再次验证。' } })
export const releaseMerchantDeposit = (data) => request({ url: '/distribution/merchant-finance/deposits/release', method: 'post', data, adminStepUp: { message: '释放保证金会改变商户可用资金，请再次验证。' } })
export const applyMerchantWithdrawal = (data) => request({ url: '/distribution/merchant-finance/withdrawals', method: 'post', data, adminStepUp: { message: '提交提现会冻结商户可提现余额，请再次验证。' } })
export const reviewMerchantWithdrawal = (id, data) => request({ url: `/distribution/merchant-finance/withdrawals/${id}/review`, method: 'put', data, adminStepUp: { message: '发票和打款调整会改变商户实际结算金额，请再次验证。' } })
export const payMerchantWithdrawal = (id, data) => request({ url: `/distribution/merchant-finance/withdrawals/${id}/pay`, method: 'post', data })
export const rejectMerchantWithdrawal = (id, data) => request({ url: `/distribution/merchant-finance/withdrawals/${id}/reject`, method: 'post', data, adminStepUp: { message: '驳回提现会释放被冻结资金，请再次验证。' } })
export const startMerchantWithdrawalPayment = (id) => request({ url: `/distribution/merchant-finance/withdrawals/${id}/payment-processing`, method: 'post', adminStepUp: { message: '开始付款会推进商户提现状态，请再次验证。' } })
export const failMerchantWithdrawalPayment = (id, data) => request({ url: `/distribution/merchant-finance/withdrawals/${id}/payment-failed`, method: 'post', data, adminStepUp: { message: '登记付款失败会回退商户提现状态，请再次验证。' } })
export const cancelMerchantWithdrawal = (id, data) => request({ url: `/distribution/merchant-finance/withdrawals/${id}/cancel`, method: 'post', data })
export const freezeMerchantWithdrawal = (id, data) => request({ url: `/distribution/merchant-finance/withdrawals/${id}/risk-freeze`, method: 'post', data, adminStepUp: { message: '风控冻结会阻止商户提现继续处理，请再次验证。' } })
export const resumeMerchantWithdrawal = (id, data) => request({ url: `/distribution/merchant-finance/withdrawals/${id}/risk-resume`, method: 'post', data, adminStepUp: { message: '解除风控冻结会恢复提现处理，请再次验证。' } })
export const completeMerchantWithdrawal = (id) => request({ url: `/distribution/merchant-finance/withdrawals/${id}/complete`, method: 'post' })
