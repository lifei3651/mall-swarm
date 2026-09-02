import request from '@/utils/request'

export const listMerchants = (params) => request({ url: '/distribution/merchants', method: 'get', params })
export const saveMerchant = (id, data) => request({
  url: id ? `/distribution/merchants/${id}` : '/distribution/merchants',
  method: id ? 'put' : 'post',
  data,
})
export const getCurrentMerchantProfile = () => request({ url: '/distribution/merchants/current-profile', method: 'get' })
export const submitCurrentMerchantProfile = (data) => request({ url: '/distribution/merchants/current-profile', method: 'put', data })
export const updateMerchantStatus = (id, status) => request({ url: `/distribution/merchants/${id}/status`, method: 'put', params: { status } })
export const updateMerchantControls = (id, data) => request({ url: `/distribution/merchants/${id}/controls`, method: 'put', data })
export const getMerchantExitReadiness = (id) => request({ url: `/distribution/merchants/${id}/exit-readiness`, method: 'get' })
export const listMerchantAccounts = (params) => request({ url: '/distribution/merchant-finance/accounts', method: 'get', params })
export const listMerchantSettlements = (params) => request({ url: '/distribution/merchant-finance/settlements', method: 'get', params })
export const listMerchantWithdrawals = (params) => request({ url: '/distribution/merchant-finance/withdrawals', method: 'get', params })
export const listMerchantDepositFlows = (params) => request({ url: '/distribution/merchant-finance/deposit-flows', method: 'get', params })
export const listMerchantLedger = (params) => request({ url: '/distribution/merchant-finance/ledger', method: 'get', params })
export const listMerchantReconciliation = (params) => request({ url: '/distribution/merchant-finance/reconciliation', method: 'get', params })
export const listMerchantWithdrawalEvents = (id) => request({ url: `/distribution/merchant-finance/withdrawals/${id}/events`, method: 'get' })
export const freezeMerchantDeposit = (data) => request({ url: '/distribution/merchant-finance/deposits/freeze', method: 'post', data })
export const receiveMerchantDeposit = (data) => request({ url: '/distribution/merchant-finance/deposits/receive', method: 'post', data })
export const releaseMerchantDeposit = (data) => request({ url: '/distribution/merchant-finance/deposits/release', method: 'post', data })
export const applyMerchantWithdrawal = (data) => request({ url: '/distribution/merchant-finance/withdrawals', method: 'post', data })
export const reviewMerchantWithdrawal = (id, data) => request({ url: `/distribution/merchant-finance/withdrawals/${id}/review`, method: 'put', data })
export const payMerchantWithdrawal = (id, data) => request({ url: `/distribution/merchant-finance/withdrawals/${id}/pay`, method: 'post', data })
export const rejectMerchantWithdrawal = (id, data) => request({ url: `/distribution/merchant-finance/withdrawals/${id}/reject`, method: 'post', data })
export const startMerchantWithdrawalPayment = (id) => request({ url: `/distribution/merchant-finance/withdrawals/${id}/payment-processing`, method: 'post' })
export const failMerchantWithdrawalPayment = (id, data) => request({ url: `/distribution/merchant-finance/withdrawals/${id}/payment-failed`, method: 'post', data })
export const cancelMerchantWithdrawal = (id, data) => request({ url: `/distribution/merchant-finance/withdrawals/${id}/cancel`, method: 'post', data })
export const freezeMerchantWithdrawal = (id, data) => request({ url: `/distribution/merchant-finance/withdrawals/${id}/risk-freeze`, method: 'post', data })
export const resumeMerchantWithdrawal = (id, data) => request({ url: `/distribution/merchant-finance/withdrawals/${id}/risk-resume`, method: 'post', data })
export const completeMerchantWithdrawal = (id) => request({ url: `/distribution/merchant-finance/withdrawals/${id}/complete`, method: 'post' })
