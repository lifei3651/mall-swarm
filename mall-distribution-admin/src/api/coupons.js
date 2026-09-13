import request from '@/utils/request'
export const listCoupons = (params) => request({ url: '/shop/admin/coupons', params })
export const couponProducts = (params) => request({ url: '/shop/admin/coupons/products', params })
export const couponMerchants = (params) => request({ url: '/shop/admin/coupons/merchants', params })
export const saveCoupon = (id, data) => request({ url: `/shop/admin/coupons${id ? `/${id}` : ''}`, method: id ? 'put' : 'post', data })
export const changeCouponStatus = (id, data) => request({ url: `/shop/admin/coupons/${id}/status`, method: 'put', data })
