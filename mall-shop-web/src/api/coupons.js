import request from './request'
export const listCoupons = (mine, params) => request({ url: mine ? '/shop/coupons/mine' : '/shop/coupons', params })
export const claimCoupon = (id, requestId) => request({ url: `/shop/coupons/${id}/claim`, method: 'post', data: { requestId } })
export const couponProducts = (id, params) => request({ url: `/shop/coupons/${id}/products`, params })
